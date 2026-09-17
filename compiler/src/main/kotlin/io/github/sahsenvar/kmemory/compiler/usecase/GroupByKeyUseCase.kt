package io.github.sahsenvar.kmemory.compiler.usecase

import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceType

/**
 * Fonksiyonlari anahtara gore gruplar ve her grup icin tipi cikarir.
 *
 * `@EraseAll` anahtarsiz oldugu icin hicbir gruba girmez. Tipi cikarilamayan grup
 * [PreferenceType.OBJECT] + `null` FQN ile doner; bunu hataya cevirmek
 * [ValidateInterfaceUseCase]'in isidir.
 */
internal class GroupByKeyUseCase {

    operator fun invoke(functions: List<FunctionModel>): List<PreferenceModel> =
        functions
            .filter { it.key != null }
            .groupBy { checkNotNull(it.key) }
            .map { (key, group) ->
                // Ayni anahtarda celisen tip bildirimi dogrulamada yakalanir; burada ilk
                // tipli fonksiyon belirleyicidir.
                val typed = group.firstOrNull { it.declaredTypeName != null }
                val primitive = typed?.declaredTypeName?.let { PreferenceType.fromSimpleName(it) }
                PreferenceModel(
                    key = key,
                    type = primitive ?: PreferenceType.OBJECT,
                    objectTypeFqName = if (primitive == null) typed?.declaredTypeFqName else null,
                    functions = group,
                )
            }
}
