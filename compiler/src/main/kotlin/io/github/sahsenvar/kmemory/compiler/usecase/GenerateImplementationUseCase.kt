package io.github.sahsenvar.kmemory.compiler.usecase

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
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
            appendLine(companionObject(storeName, models))
            appendLine("}")
            appendLine()
            appendLine(factoryExtension(interfaceName, implementationName, needsJson))
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(false, sourceFile),
            packageName = packageName,
            fileName = implementationName,
        ).bufferedWriter().use { it.write(content) }
    }

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
     * `public` anahtar sozcugu yazilmaz; uretilen dosya tuketici modulde derlendigi icin
     * varsayilan gorunurluk zaten public'tir.
     */
    private fun factoryExtension(
        interfaceName: String,
        implementationName: String,
        needsJson: Boolean,
    ): String {
        val functionName = interfaceName.replaceFirstChar { it.lowercase() }
        val arguments = buildList {
            add("store($implementationName.STORE_NAME)")
            if (needsJson) add("json")
            add("listener")
        }.joinToString(", ")

        return "fun KMemory.$functionName(): $interfaceName =\n" +
            "    $implementationName($arguments)"
    }

    private companion object {
        const val IMPLEMENTATION_SUFFIX = "Impl"
        const val NAME_ARGUMENT = "name"
    }
}
