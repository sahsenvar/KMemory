package io.github.sahsenvar.kmemory.compiler.usecase

import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceType

/**
 * Fonksiyonlari anahtara gore gruplar ve her grup icin tipi cikarir.
 *
 * `@EraseAll` anahtarsiz oldugu icin hicbir gruba girmez. Tipi cikarilamayan grup
 * [PreferenceType.OBJECT] + `null` tip ile doner; bunu hataya cevirmek
 * [ValidateInterfaceUseCase]'in isidir.
 *
 * Uretilen sabit adlari [DeriveConstantSuffixUseCase] tarafindan TOPLUCA turetilir (carpisma
 * kurali butun gruplari birlikte gormeyi gerektirir); bu yuzden `groupBy`'in koruduğu BILDIRIM
 * sirasina dayanir.
 */
internal class GroupByKeyUseCase(
    private val deriveConstantSuffixUseCase: DeriveConstantSuffixUseCase,
) {

    operator fun invoke(functions: List<FunctionModel>): List<PreferenceModel> {
        val groups = functions
            .filter { it.key != null }
            .groupBy { checkNotNull(it.key) }
            .map { (key, grouped) -> key to grouped }

        val suffixes = deriveConstantSuffixUseCase(groups)

        return groups.mapIndexed { index, (key, grouped) ->
            // Ayni anahtarda celisen tip bildirimi dogrulamada yakalanir; burada ilk
            // tipli fonksiyon belirleyicidir.
            val declaredType = grouped.firstNotNullOfOrNull { it.declaredType }
            // Tip argumani tasiyan hicbir tip DataStore ilkeli olamaz; JSON'a duser.
            val primitive = declaredType
                ?.takeUnless { it.hasArguments }
                ?.let { PreferenceType.fromQualifiedName(it.rootFqName) }

            PreferenceModel(
                key = key,
                constantSuffix = suffixes[index],
                type = primitive ?: PreferenceType.OBJECT,
                declaredType = declaredType,
                functions = grouped,
            )
        }
    }
}
