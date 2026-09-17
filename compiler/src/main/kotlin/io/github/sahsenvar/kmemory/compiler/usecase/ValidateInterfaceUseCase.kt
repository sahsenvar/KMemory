package io.github.sahsenvar.kmemory.compiler.usecase

import com.google.devtools.ksp.symbol.KSClassDeclaration
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
    /** `kmemory.adapters` KSP secenegindeki FQN'ler; bos olabilir. */
    private val registeredAdapterTypes: Set<String>,
) {

    operator fun invoke(
        declaration: KSClassDeclaration,
        declaredFunctions: List<KSFunctionDeclaration>,
        functions: List<FunctionModel>,
        models: List<PreferenceModel>,
    ): Boolean {
        val name = declaration.simpleName.asString()
        var valid = true

        // CollectFunctions anotasyonsuz fonksiyonu sessizce eler; fark buradan yakalanir.
        if (declaredFunctions.size != functions.size) {
            logger.error("$name: her fonksiyon @Read/@Write/@Erase/@EraseAll'dan birini taşımalı", declaration)
            valid = false
        }

        if (functions.count { it.accessor == Accessor.ERASE_ALL } > 1) {
            logger.error("$name: birden fazla @EraseAll bildirilmiş", declaration)
            valid = false
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
        val builtIn = isBuiltInShape(model.accessor, outerFqName, valueType)

        // spec §4.1 — yerlesik sekiller + kmemory.adapters ile tanitilanlar disi reddedilir.
        if (!builtIn && outerFqName !in registeredAdapterTypes) {
            logger.error(
                "$label: '$outerFqName' yerleşik bir dönüş şekli değil. " +
                    "Bir ReturnAdapter yazıp kmemory.adapters KSP seçeneğiyle tanıtın (spec §4.1).",
                function,
            )
            valid = false
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
     * `@Write` / `@Erase` / `@EraseAll`: yalnizca `Unit`.
     */
    private fun isBuiltInShape(accessor: Accessor, outerFqName: String?, valueType: KSType?): Boolean =
        when (accessor) {
            Accessor.READ -> valueType != null && isStorableShape(valueType)
            else -> outerFqName == UNIT_FQ_NAME
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
