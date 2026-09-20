package io.github.sahsenvar.kmemory.compiler.usecase

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import io.github.sahsenvar.kmemory.compiler.logger.Logger
import io.github.sahsenvar.kmemory.compiler.model.Accessor
import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel

/**
 * Spec §10'daki yedi derleme-zamani kurali.
 *
 * Kisa devre YAPILMAZ: ilk hatada durulmaz, tum kurallar calistirilir ve butun hatalar tek
 * derlemede raporlanir. Donen `false` yalnizca "bu arayuz icin uretim yapma" anlamina gelir.
 */
internal class ValidateInterfaceUseCase(
    private val logger: Logger,
) {

    operator fun invoke(
        declaration: KSClassDeclaration,
        declaredFunctions: List<KSFunctionDeclaration>,
        functions: List<FunctionModel>,
        models: List<PreferenceModel>,
    ): Boolean {
        val name = declaration.simpleName.asString()
        var valid = true

        // CollectFunctions iki AYRI sebeple fonksiyon eler ve ikisi ayri hata ister:
        //   (a) hic accessor anotasyonu yok,
        //   (b) anotasyon VAR ama `key`/`name` argumani derleme-zamani String sabitine
        //       cozulemedi (`?.value as? String` null doner).
        // Eskiden ikisi de tek bir "her fonksiyon ... birini taşımalı" mesajina dusuyordu; (b)
        // durumundaki kullanici anotasyonu YAZMIS oluyor ve olmayan bir eksigi ariyordu.
        val modelledNames = functions.mapTo(mutableSetOf()) { it.name }
        declaredFunctions.forEach { function ->
            if (function.simpleName.asString() in modelledNames) return@forEach

            val hasAccessor = function.annotations.any { annotation ->
                annotation.shortName.asString() in ACCESSOR_ANNOTATION_NAMES
            }
            val label = "$name.${function.simpleName.asString()}"
            if (hasAccessor) {
                logger.error(
                    "$label: accessor anotasyonu var ama 'key' argümanı derleme zamanında bir " +
                        "String sabitine çözülemedi. Değer literal ya da erişilebilir bir " +
                        "`const val` olmalı; `private` bir companion sabiti ya da niteliksiz " +
                        "referans çözülmez.",
                    function,
                )
            } else {
                logger.error(
                    "$label: her fonksiyon @Read/@Write/@Erase/@EraseAll'dan birini taşımalı",
                    function,
                )
            }
            valid = false
        }

        if (!validateAbstractMembers(declaration, functions)) valid = false

        if (functions.count { it.accessor == Accessor.ERASE_ALL } > 1) {
            logger.error("$name: birden fazla @EraseAll bildirilmiş", declaration)
            valid = false
        }

        // Bir fonksiyon TAM BIR accessor anotasyonu tasimali.
        //
        // [CollectFunctionsUseCase.toModel] anotasyonlara SIRAYLA bakip ilk eslesende donuyor
        // (Read -> Write -> Erase -> EraseAll). Yani `@Erase @EraseAll fun x()` yazan bir
        // kullanicida ikincisi SESSIZCE yok sayiliyor ve kullanici yazmadigi bir davranisi
        // aliyordu. Sayim burada yapilir cunku toplama asamasi coklu anotasyonu tek modele
        // indirgiyor; fark ancak bildirime bakarak gorulur.
        declaredFunctions.forEach { function ->
            val accessorCount = function.annotations.count { annotation ->
                annotation.shortName.asString() in ACCESSOR_ANNOTATION_NAMES
            }
            if (accessorCount > 1) {
                logger.error(
                    "$name.${function.simpleName.asString()}: birden fazla accessor anotasyonu " +
                        "taşıyor ($accessorCount); @Read/@Write/@Erase/@EraseAll'dan yalnızca biri olmalı",
                    function,
                )
                valid = false
            }
        }

        pairDeclarations(declaredFunctions, functions).forEach { (function, model) ->
            if (!validateFunction(name, function, model)) valid = false
        }

        models.forEach { model ->
            if (!validateModel(declaration, model)) valid = false
        }

        return valid
    }

    @Suppress("CyclomaticComplexMethod")
    private fun validateFunction(
        interfaceName: String,
        function: KSFunctionDeclaration,
        model: FunctionModel,
    ): Boolean {
        var valid = true
        val label = "$interfaceName.${model.name}"

        val expectedParameters = if (model.accessor == Accessor.WRITE) 1 else 0
        if (function.parameters.size != expectedParameters) {
            logger.error("$label: ${model.accessor} tam $expectedParameters parametre almalı", function)
            valid = false
        }

        val returned = function.returnType?.resolve()
        val outerFqName = returned?.declaration?.qualifiedName?.asString()
        val returnsFlow = outerFqName == FLOW_FQ_NAME
        val isSuspend = function.modifiers.contains(Modifier.SUSPEND)

        // spec §4.0 — TEK kural, dort anotasyon icin de ayni:
        // Flow donen suspend OLAMAZ, Flow donmeyen suspend OLMALI.
        if (returnsFlow && isSuspend) {
            logger.error("$label: Flow dönen fonksiyon suspend olamaz (Flow soğuktur)", function)
            valid = false
        }
        if (!returnsFlow && !isSuspend) {
            logger.error("$label: Flow dönmeyen fonksiyon suspend olmalı", function)
            valid = false
        }

        val valueType = if (returnsFlow) returned?.arguments?.firstOrNull()?.type?.resolve() else returned
        val builtIn = isBuiltInShape(model.accessor, valueType)

        // spec §4.1 — SIMDILIK yalnizca yerlesik sekiller gecer.
        //
        // `kmemory.adapters` bypass'i buradan KALDIRILDI: dogrulamayi gevsetiyor ama uretimi hic
        // degistirmiyordu; tanitilan tip dogrulamadan gecip uretimde yok sayiliyor ve tuketicide
        // "return type is not a subtype" ile patliyordu. ReturnAdapter arayuzu ileriye donuk seam
        // olarak KALIR, isleyici hicbir tipi gevsetmez.
        if (!builtIn) {
            logger.error(
                "$label: '${shapeLabel(returnsFlow, outerFqName, valueType)}' yerleşik bir " +
                    "dönüş şekli değil. " +
                    "Yerleşik şekiller — @Read: Flow<T?> ya da suspend fun (): T?; " +
                    "@Write/@Erase/@EraseAll: Flow<Unit> ya da suspend fun (): Unit. " +
                    "Adaptör bağlantısı henüz gelmedi; kmemory.adapters hiçbir tipi " +
                    "gevşetmez (spec §4.1).",
                function,
            )
            valid = false
        }

        // spec §4.1 — YAZMANIN PARAMETRE TIPI de yazilabilir olmali.
        //
        // [isBuiltInShape] WRITE dalinda yalnizca DONUS degerinin `Unit` oldugunu dogruluyor;
        // diske giden asil deger ise PARAMETREdir ve hicbir kontrolden GECMIYORDU. Sonuc:
        // `@Write fun f(value: () -> Unit)` gibi bir imza dogrulamadan sessizce gecip uretilen
        // dosyada `json.encodeToString(Function0)` ile patliyordu — hata kullanicinin arayuzunde
        // degil URETILEN kodda cikiyordu, yani teshisi en zor bicimde.
        if (model.accessor == Accessor.WRITE) {
            // Parametre SAYISI zaten yukarida dogrulandi; eksikse ikinci bir hata basma.
            val parameterType = function.parameters.singleOrNull()?.type?.resolve()
            if (parameterType != null && !isStorableShape(parameterType)) {
                logger.error(
                    "$label: @Write parametresi " +
                        "'${parameterType.declaration.simpleName.asString()}' diske yazılabilir " +
                        "bir tip değil (Unit, yıldız izdüşümü ya da tanınmayan jenerik sarmalayıcı).",
                    function,
                )
                valid = false
            }
        }

        // Nullability yalnizca YERLESIK okuma sekli icin zorlanir; adaptorlu bir sekilde
        // nullability adaptorun sozlesmesine aittir ve burada bilinemez.
        if (model.accessor == Accessor.READ && builtIn && valueType?.isMarkedNullable != true) {
            logger.error(
                "$label: @Read değeri nullable olmalı — Flow<T?> ya da suspend fun (): T? " +
                    "(varsayılan değer yoktur)",
                function,
            )
            valid = false
        }

        return valid
    }

    /**
     * Uretilemeyen soyut uyeyi TUKETICIYE birakma (BULGU 2).
     *
     * Uretim yalnizca `@Read/@Write/@Erase/@EraseAll` tasiyan BILDIRILEN fonksiyonlardan olur.
     * Supertype'tan miras alinip arayuzde yeniden bildirilmeyen bir soyut uye ne dogrulaniyor ne
     * uretiliyordu: uretilen `*Impl` o uyeyi implemente etmemis oluyor, derleme TUKETICIDE ve hem
     * de URETILEN dosyada patliyordu ("Class 'XImpl' is not abstract and does not implement
     * abstract member"). Ayni delik soyut OZELLIKLERDE de acikti — fonksiyon sayimi ozellikleri
     * hic gormez.
     *
     * Bu yuzden butun soyut uyeler (miras dahil) taranir ve uretilemeyen her biri icin NET bir
     * hata verilir. BILDIRILEN fonksiyonlar tarama disidir: onlarin anotasyon zorunlulugu
     * yukaridaki sayim kuralina aittir, iki kez raporlanmasinlar.
     *
     * Uyesi olmayan marker supertype'lar (Zad'in `MemorySource`'u tam olarak oyle) hicbir uye
     * uretmedigi icin dogal olarak sessizdir.
     */
    private fun validateAbstractMembers(
        declaration: KSClassDeclaration,
        functions: List<FunctionModel>,
    ): Boolean {
        val interfaceName = declaration.simpleName.asString()
        val ownFqName = declaration.qualifiedName?.asString()
        val generated = functions.mapTo(mutableSetOf()) { it.name }
        var valid = true

        val inheritedFunctions = declaration.getAllFunctions()
            .filter { it.isAbstract && !it.isConstructor() }
            .filterNot { it.ownerFqName() == ownFqName }
            // `equals`/`hashCode`/`toString` her arayuzun ustundedir ve uretilmez.
            .filterNot { it.ownerFqName() in ANY_FQ_NAMES }

        val abstractProperties = declaration.getAllProperties().filter { it.isAbstract() }

        // Isim kumesi YALNIZCA fonksiyonlara uygulanir.
        //
        // Eskiden tek bir `filterNot { simpleName in generated }` hem fonksiyonlari hem
        // property'leri suzuyordu. Ama [generated] uretilen FONKSIYON adlarini tasir ve isleyici
        // hicbir property URETMEZ; dolayisiyla uretilen bir fonksiyonla ayni ada sahip soyut bir
        // property sessizce "uretildi" sayilip atlanıyordu. Sonuc: uretilen sinif o uyeyi
        // uygulamadigi icin derleme, kullanicinin arayuzunde degil URETILEN dosyada patliyordu.
        // Karsilastirma artik (uye turu, ad) ciftine gore: property'ler istisnasiz raporlanir.
        val unimplementedFunctions = inheritedFunctions
            .filterNot { it.simpleName.asString() in generated }

        (unimplementedFunctions + abstractProperties)
            .forEach { member ->
                logger.error(memberError(interfaceName, ownFqName, member), declaration)
                valid = false
            }

        return valid
    }

    /**
     * Uretilemeyen soyut uyenin hata metni: hangi uye, nereden geldigi, ne yapilmasi gerektigi.
     */
    private fun memberError(interfaceName: String, ownFqName: String?, member: KSDeclaration): String {
        val label = member.simpleName.asString()
        val ownerFqName = member.ownerFqName()
        val owner = (member.parentDeclaration as? KSClassDeclaration)?.simpleName?.asString() ?: "?"

        val head = if (ownerFqName == ownFqName) {
            "'$label' soyut üyesi üretilemez"
        } else {
            "'$owner.$label': miras alınan soyut üye üretilemez"
        }

        return "$interfaceName: $head. Üyeyi $interfaceName içinde yeniden bildirip " +
            "@Read/@Write/@Erase/@EraseAll ile işaretleyin, ya da supertype'tan kaldırın. " +
            "Üyesi olmayan marker supertype'lar sorun değildir."
    }

    /** Uyeyi bildiren tipin FQN'i; ust-duzey bildirimlerde `null`. */
    private fun KSDeclaration.ownerFqName(): String? =
        (parentDeclaration as? KSClassDeclaration)?.qualifiedName?.asString()

    private fun validateModel(declaration: KSClassDeclaration, model: PreferenceModel): Boolean {
        var valid = true
        val declaredTypes = model.functions.mapNotNull { it.declaredType?.qualified }.distinct()

        if (declaredTypes.size > 1) {
            logger.error("${model.key}: aynı anahtar için farklı tipler bildirilmiş: $declaredTypes", declaration)
            valid = false
        }
        if (declaredTypes.isEmpty()) {
            logger.error("${model.key}: tip çıkarılamıyor; bu anahtar için bir @Read veya @Write gerekli", declaration)
            valid = false
        }

        return valid
    }

    /**
     * spec §4.1 — yerlesik donus sekilleri.
     *
     * `@Read`: `Flow<T?>` ya da `T?`, T [isStorableShape]'e uymak kaydiyla.
     *
     * `@Write` / `@Erase` / `@EraseAll`: `Flow<Unit>` ya da `Unit`.
     *
     * Olcut her iki erisimde de DIS tipe degil TASINAN degere bakar: [valueType], `Flow`
     * donuyorsa tip argumani, donmuyorsa donus tipinin kendisidir. Boylece `Flow<Unit>` ile
     * `Unit` tek kuraldan gecer ve `Flow<Int>` gibi bir sekil elenir — yazmanin yayacak bir
     * degeri yoktur. Yildiz izdusumunde (`Flow<*>`) argumanin cozulmus tipi yoktur, yani
     * [valueType] `null` kalir ve sekil reddedilir.
     */
    private fun isBuiltInShape(accessor: Accessor, valueType: KSType?): Boolean =
        when (accessor) {
            Accessor.READ -> valueType != null && isStorableShape(valueType)
            else -> valueType?.declaration?.qualifiedName?.asString() == UNIT_FQ_NAME
        }

    /**
     * Hata mesajinda gosterilecek donus sekli.
     *
     * Ciplak `'kotlinx.coroutines.flow.Flow'` yazmak yaniltici olurdu: `Flow<Unit>` yerlesik,
     * `Flow<Int>` degil — fark tip argumanindadir ve mesaj onu gostermek zorunda.
     */
    private fun shapeLabel(returnsFlow: Boolean, outerFqName: String?, valueType: KSType?): String {
        if (!returnsFlow) return outerFqName ?: UNKNOWN_TYPE

        val argument = valueType?.let { type ->
            type.declaration.simpleName.asString() + if (type.isMarkedNullable) "?" else ""
        } ?: UNKNOWN_TYPE

        return "Flow<$argument>"
    }

    /**
     * Okunan degerin diske yazilabilir bir tip olup olmadigi.
     *
     * Olcut "tip argumani tasimasin" DEGILDIR: o kural `List<String>` gibi kotlinx-serialization'in
     * kutudan destekledigi koleksiyonlari da reddediyordu. Bunun yerine tip argumani tasiyan bir
     * tip yalnizca TANINAN bir koleksiyon ise ve tum argumanlari da yazilabilir ise gecer. Boylece
     * `Result<T>` gibi sarmalayicilar hala yakalanir — aksi halde yalnizca "nullable olmali"
     * hatasi verip nullable bir sarmalayiciyi (`Result<T>?`) tamamen gecirirlerdi.
     *
     * Jenerik OLMAYAN tiplerde eskisi gibi davranilir: `Unit` disinda her sey gecer ve gecersiz
     * bir tip uretilen kodun serilestirme hatasinda yakalanir.
     */
    private fun isStorableShape(type: KSType): Boolean {
        val fqName = type.declaration.qualifiedName?.asString() ?: return false
        if (fqName == UNIT_FQ_NAME) return false
        if (type.arguments.isEmpty()) return true
        if (fqName !in SERIALIZABLE_CONTAINER_FQ_NAMES) return false

        return type.arguments.all { argument ->
            // Yildiz izdusumunun somut bir tipi yoktur; serilestirilemez.
            argument.variance != Variance.STAR &&
                argument.type?.resolve()?.let(::isStorableShape) == true
        }
    }

    /**
     * Bildirilen fonksiyonlari modelleriyle eslestirir.
     *
     * Duz `zip` KULLANILMAZ: [CollectFunctionsUseCase] anotasyonsuz fonksiyonu sessizce eledigi
     * icin listeler ayni uzunlukta olmayabilir ve tek bir eleme sonraki tum ciftleri kaydirir —
     * o zaman hatalar yanlis fonksiyona atfedilir. Bunun yerine bildirim sirasi korunarak ada
     * gore tuketilir; ayni ada sahip asiri yuklemeler de bildirim sirasinda eslesir.
     */
    private fun pairDeclarations(
        declaredFunctions: List<KSFunctionDeclaration>,
        functions: List<FunctionModel>,
    ): List<Pair<KSFunctionDeclaration, FunctionModel>> {
        val remaining = declaredFunctions.toMutableList()
        return functions.mapNotNull { model ->
            val index = remaining.indexOfFirst { it.simpleName.asString() == model.name }
            if (index < 0) null else remaining.removeAt(index) to model
        }
    }

    private companion object {
        const val FLOW_FQ_NAME = "kotlinx.coroutines.flow.Flow"
        const val UNIT_FQ_NAME = "kotlin.Unit"

        /** Cozulemeyen tipin hata mesajindaki karsiligi (`Flow<*>` gibi). */
        const val UNKNOWN_TYPE = "*"

        /**
         * Dort accessor anotasyonunun BASIT adlari.
         *
         * `shortName` ile karsilastiriliyor cunku KSP anotasyonun tam nitelikli adini ancak
         * cozulmus tipten verir; toplama asamasi da (`CollectFunctionsUseCase.keyOf`) ayni
         * sekilde `shortName` kullaniyor — iki taraf ayni olcutu kullanmali.
         */
        val ACCESSOR_ANNOTATION_NAMES = setOf("Read", "Write", "Erase", "EraseAll")

        /** Her tipin ustundeki uyeler; uretime konu degildir. */
        val ANY_FQ_NAMES = setOf("kotlin.Any", "java.lang.Object")

        /** kotlinx-serialization'in yerlesik serilestiricisi bulunan koleksiyon tipleri. */
        val SERIALIZABLE_CONTAINER_FQ_NAMES = setOf(
            "kotlin.Array",
            "kotlin.Pair",
            "kotlin.Triple",
            "kotlin.collections.Collection",
            "kotlin.collections.List",
            "kotlin.collections.Map",
            "kotlin.collections.Set",
        )
    }
}
