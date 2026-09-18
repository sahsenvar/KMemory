package io.github.sahsenvar.kmemory.compiler.usecase

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Visibility
import io.github.sahsenvar.kmemory.annotation.Preferences
import io.github.sahsenvar.kmemory.compiler.logger.Logger
import io.github.sahsenvar.kmemory.compiler.model.Accessor
import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceType

/**
 * Bir `@Preferences` arayuzunun tam kaynak dosyasini uretir ve diske yazar (spec §6, §6.1).
 *
 * Uretilen dosya iki bildirim tasir:
 * 1. `internal class <Arayuz>Impl` — `DataStore<Preferences>`, kosullu `Json` ve varsayilanli
 *    `PreferenceListener` alan uygulama; govdeler `Generate{Read,Write,Erase}FunctionUseCase`
 *    tarafindan uretilir, anahtar sabitleri companion'a konur.
 * 2. `fun KMemory.<arayuzAdi>()` — disa acilan TEK yuzey. Sinifin `internal` olmasi, store
 *    tekilligini [io.github.sahsenvar.kmemory.KMemory] uzerinden gecmeye zorlar: DataStore
 *    ayni dosya icin ikinci bir ornek kuruldugunda calisma aninda patlar.
 *
 * `Context` hicbir yerde gecmez; upstream'in dosya kapsamli `Context.dataStore` uzanti
 * ozelligi ve `PreferencesConstructor` uretimi tamamen kalkti.
 */
internal class GenerateImplementationUseCase(
    private val logger: Logger,
    private val codeGenerator: CodeGenerator,
    private val generateImportsUseCase: GenerateImportsUseCase,
    private val generateReadFunctionUseCase: GenerateReadFunctionUseCase,
    private val generateWriteFunctionUseCase: GenerateWriteFunctionUseCase,
    private val generateEraseFunctionUseCase: GenerateEraseFunctionUseCase,
) {

    /**
     * @param declaration Dogrulamadan gecmis `@Preferences` arayuzu.
     * @param functions Arayuzun fonksiyon modelleri; uretim sirasi BILDIRIM sirasidir.
     * @param models Anahtar gruplari; companion sabitleri ve constructor karari buradan cikar.
     */
    operator fun invoke(
        declaration: KSClassDeclaration,
        functions: List<FunctionModel>,
        models: List<PreferenceModel>,
    ) {
        val interfaceName = declaration.simpleName.asString()

        val sourceFile = declaration.containingFile
        if (sourceFile == null) {
            logger.error("$interfaceName: kaynak dosyası çözülemedi, üretim atlandı", declaration)
            return
        }

        val storeName = storeNameOf(declaration)
        if (storeName == null) {
            logger.error("$interfaceName: @Preferences(name = ...) okunamadı", declaration)
            return
        }

        val packageName = declaration.packageName.asString()
        val implementationName = interfaceName + IMPLEMENTATION_SUFFIX
        // `json` yalnizca serilestirilecek bir tip varsa constructor'a girer; kullanmayan
        // hicbir cagri yeri bedel odemez (spec §6).
        val needsJson = models.any { it.type == PreferenceType.OBJECT }

        val content = buildString {
            append(generateImportsUseCase(packageName, functions, models))
            appendLine()
            appendLine(classHeader(implementationName, interfaceName, needsJson))
            appendLine()
            functions.forEach { function ->
                bodyOf(function, models)?.let {
                    appendLine(it)
                    appendLine()
                }
            }
            if (needsJson) {
                appendLine(serializingFunction())
                appendLine()
            }
            appendLine(reportFunction(needsJson))
            appendLine()
            if (needsJson) {
                appendLine(boundaryMarkerClass())
                appendLine()
            }
            appendLine(companionObject(storeName, models))
            appendLine("}")
            appendLine()
            appendLine(
                factoryExtension(
                    interfaceName = interfaceName,
                    implementationName = implementationName,
                    needsJson = needsJson,
                    visibility = visibilityPrefixOf(declaration),
                )
            )
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(false, sourceFile),
            packageName = packageName,
            fileName = implementationName,
        ).bufferedWriter().use { it.write(content) }
    }

    /**
     * Tum hata yollarinin gectigi TEK raporlama noktasi.
     *
     * Dogrudan `listener.onError(STORE_NAME, key, error)` cagirmak tasarim §8'deki
     * "dinleyici degerleri asla gormez" sozlesmesini ihlal ediyordu: serilestirme sinirindan
     * cikan hatalar bozuk ya da gecersiz girdiyi mesajlarina gomer, yani bir Crashlytics
     * dinleyicisi saklanan PIN'i ya da token'i disari tasirdi.
     *
     * Kapi hatanin TIPINE degil ciktigi KONUMA bakar (bkz.
     * [io.github.sahsenvar.kmemory.listener.PreferenceSerializationException]): yalnizca
     * [serializingFunction]'in sardigi `encode`/`decode` cagrilari `SerializationFailure`
     * isareti tasir ve sanitize edilir. DataStore G/C hatalari bu isareti tasimadigi icin
     * oldugu gibi raporlanir — deger tasimazlar, tani icin gereklidirler.
     *
     * Fonksiyon `Unit` degil `Throwable` DONER: cagri yerleri `throw report(key, error)`
     * yazar. Boylece isaret sarmalayicisi cagirana asla ulasmaz, cagiran hala ORIJINAL
     * hatayi alir — yani sanitizasyon yalnizca dinleyici yolunu degistirir.
     *
     * @param needsJson Arayuzde serilestirilecek bir tip var mi; yoksa sinir hic uretilmez ve
     *   isaret kontrolu olu koda donusurdu.
     */
    private fun reportFunction(needsJson: Boolean): String {
        if (!needsJson) {
            return """
                |    private fun report(key: String?, error: Throwable): Throwable {
                |        listener.onError(STORE_NAME, key, error)
                |        return error
                |    }
            """.trimMargin()
        }

        return """
            |    private fun report(key: String?, error: Throwable): Throwable {
            |        if (error !is SerializationFailure) {
            |            listener.onError(STORE_NAME, key, error)
            |            return error
            |        }
            |        listener.onError(
            |            STORE_NAME,
            |            error.key,
            |            PreferenceSerializationException.atBoundary(STORE_NAME, error.key, error.original),
            |        )
            |        return error.original
            |    }
        """.trimMargin()
    }

    /**
     * Serilestirme SINIRI: `encode`/`decode` cagrilarinin etrafina sarilan tek kapi.
     *
     * Blok icinden cikan her hata — tipi ne olursa olsun — konum bilgisi tasiyan bir
     * [boundaryMarkerClass] ornegine cevrilir. Kapi burada oldugu icin `report` "bu hata
     * serilestirmeden mi geldi" sorusunu tahmin etmek zorunda kalmaz; upstream'in
     * `error is SerializationException` tahmini tam da bu yuzden yanlisti.
     */
    private fun serializingFunction(): String = """
        |    private fun <T> serializing(key: String, block: () -> T): T =
        |        try {
        |            block()
        |        } catch (error: Throwable) {
        |            throw SerializationFailure(key, error)
        |        }
    """.trimMargin()

    /**
     * Sinif-ici isaret tipi; `report` disinda kimse gormez.
     *
     * Orijinal hata `cause` OLARAK DEGIL ayri bir ozellik olarak tutulur: `cause` zinciri
     * `stackTraceToString()` ile basilir, yani isaret yanlislikla disari sizsa bile degeri
     * yaninda goturmez. Mesaji da yoktur.
     */
    private fun boundaryMarkerClass(): String = """
        |    private class SerializationFailure(
        |        val key: String,
        |        val original: Throwable,
        |    ) : RuntimeException()
    """.trimMargin()

    /** `@Preferences`'in `name` argumani; uretilen `STORE_NAME` sabitine literal gomulur. */
    private fun storeNameOf(declaration: KSClassDeclaration): String? =
        declaration.annotations
            .firstOrNull { it.shortName.asString() == Preferences::class.simpleName }
            ?.arguments
            ?.firstOrNull { it.name?.asString() == NAME_ARGUMENT }
            ?.value as? String

    private fun classHeader(
        implementationName: String,
        interfaceName: String,
        needsJson: Boolean,
    ): String {
        val parameters = buildList {
            add("    private val dataStore: DataStore<Preferences>,")
            if (needsJson) add("    private val json: Json,")
            add("    private val listener: PreferenceListener = PreferenceListener.None,")
        }.joinToString("\n")

        return "internal class $implementationName(\n$parameters\n) : $interfaceName {"
    }

    /**
     * Tek bir fonksiyonun `override` govdesi.
     *
     * `@EraseAll` anahtarsizdir, bu yuzden model aranmadan uretilir. Anahtarli bir fonksiyona
     * model bulunamamasi dogrulamanin kacirdigi bir durumdur; sessizce atlanir ki uretim
     * NoSuchElement ile patlamasin.
     */
    private fun bodyOf(function: FunctionModel, models: List<PreferenceModel>): String? {
        if (function.accessor == Accessor.ERASE_ALL) {
            return generateEraseFunctionUseCase.eraseAll(function)
        }

        val model = models.firstOrNull { it.key == function.key } ?: return null

        return when (function.accessor) {
            Accessor.READ -> generateReadFunctionUseCase(model, function)
            Accessor.WRITE -> generateWriteFunctionUseCase(model, function)
            Accessor.ERASE -> generateEraseFunctionUseCase.erase(model, function)
            Accessor.ERASE_ALL -> null
        }
    }

    /**
     * Store adi ve her anahtar icin metin sabiti + tipli `Preferences.Key`.
     *
     * `STORE_NAME` `private` DEGILDIR: uretilen uzanti fonksiyonu store'u isimle ister ve
     * sinifin disindadir. Sinif `internal` oldugu icin sabit de modul disina sizmaz.
     */
    private fun companionObject(storeName: String, models: List<PreferenceModel>): String {
        val keys = models.joinToString("\n") { model ->
            "        private const val ${model.keyNameProperty} = \"${model.key}\"\n" +
                "        private val ${model.keyProperty} = ${model.type.keyFactory}(${model.keyNameProperty})"
        }

        return "    companion object {\n" +
            "        const val STORE_NAME = \"$storeName\"\n" +
            keys +
            "\n    }"
    }

    /**
     * `fun KMemory.<arayuzAdi>(): <Arayuz>` — tuketicinin gordugu tek yuzey (spec §6.1).
     *
     * Gorunurluk ARAYUZUN bildirdigi gorunurlugu izler. Kosulsuz public uretilseydi
     * `internal interface AuthMemorySource` yazan bir tuketicide derleme
     * EXPOSED_FUNCTION_RETURN_TYPE ("'public' function exposes its 'internal' return type")
     * ile duserdi; yani kutuphane arayuzun modul-ici kalmasini imkansiz kilardi.
     *
     * `public` anahtar sozcugu ayrica yazilmaz; Kotlin'de varsayilan zaten odur.
     */
    private fun factoryExtension(
        interfaceName: String,
        implementationName: String,
        needsJson: Boolean,
        visibility: String,
    ): String {
        val functionName = interfaceName.replaceFirstChar { it.lowercase() }
        val arguments = buildList {
            add("store($implementationName.STORE_NAME)")
            if (needsJson) add("json")
            add("listener")
        }.joinToString(", ")

        return "${visibility}fun KMemory.$functionName(): $interfaceName =\n" +
            "    $implementationName($arguments)"
    }

    /**
     * Uzanti fonksiyonunun onune yazilacak gorunurluk degistiricisi.
     *
     * Yalnizca `internal` ozel islem ister: uretilen dosya tuketici modulde derlendigi icin
     * digerlerinin varsayilani (public) zaten dogrudur. `private`/`protected` bir arayuz
     * uretilen dosyadan zaten gorulemez, dolayisiyla o durumda uretilen kod ne yazarsa yazsin
     * derlenmez; buradaki karar onlari public varsayip Kotlin'in kendi hatasina birakmaktir.
     */
    private fun visibilityPrefixOf(declaration: KSClassDeclaration): String =
        when (declaration.getVisibility()) {
            Visibility.INTERNAL -> "internal "
            else -> ""
        }

    private companion object {
        const val IMPLEMENTATION_SUFFIX = "Impl"
        const val NAME_ARGUMENT = "name"
    }
}
