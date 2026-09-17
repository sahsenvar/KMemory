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
 * Gruba verilen indeks uretilen sabit adlarinin carpismasini onler (bkz.
 * [PreferenceModel.keyProperty]); bu yuzden `groupBy`'in koruduğu BILDIRIM sirasina dayanir.
 */
internal class GroupByKeyUseCase {

    operator fun invoke(functions: List<FunctionModel>): List<PreferenceModel> =
        functions
            .filter { it.key != null }
            .groupBy { checkNotNull(it.key) }
            .entries
            .mapIndexed { index, entry ->
                // Ayni anahtarda celisen tip bildirimi dogrulamada yakalanir; burada ilk
                // tipli fonksiyon belirleyicidir.
                val declaredType = entry.value.firstNotNullOfOrNull { it.declaredType }
                // Tip argumani tasiyan hicbir tip DataStore ilkeli olamaz; JSON'a duser.
                val primitive = declaredType
                    ?.takeUnless { it.hasArguments }
                    ?.let { PreferenceType.fromQualifiedName(it.rootFqName) }

                PreferenceModel(
                    index = index,
                    key = entry.key,
                    type = primitive ?: PreferenceType.OBJECT,
                    declaredType = declaredType,
                    functions = entry.value,
                )
            }
}
