package io.github.sahsenvar.kmemory.compiler.usecase

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.github.sahsenvar.kmemory.annotation.Erase
import io.github.sahsenvar.kmemory.annotation.EraseAll
import io.github.sahsenvar.kmemory.annotation.Read
import io.github.sahsenvar.kmemory.annotation.Write
import io.github.sahsenvar.kmemory.compiler.model.Accessor
import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.ReadShape

/**
 * `@Preferences`'li bir arayuzun bildirilen fonksiyonlarini [FunctionModel] listesine cevirir.
 *
 * Burada DOGRULAMA yapilmaz; anotasyon tasimayan fonksiyon sessizce elenir. Elenen fonksiyonu
 * hataya cevirmek [ValidateInterfaceUseCase]'in isidir ve bildirilen fonksiyon sayisi ile
 * donen model sayisinin karsilastirilmasiyla yapilir — bu yuzden buradaki siralama,
 * `getDeclaredFunctions()` siralamasini korur.
 *
 * Tip HER ZAMAN [RenderTypeUseCase]'den gecer. Once `declaration.simpleName` aliniyordu; bu
 * tip argumanlarini ve nullability'yi dusurup `List<SearchHistory>`'yi `List`'e,
 * `String?`'i `String`'e indiriyor ve uretilen sinifi derlenemez hale getiriyordu.
 */
internal class CollectFunctionsUseCase(
    private val renderTypeUseCase: RenderTypeUseCase,
) {

    operator fun invoke(declaration: KSClassDeclaration): List<FunctionModel> =
        declaration.getDeclaredFunctions().mapNotNull(::toModel).toList()

    private fun toModel(function: KSFunctionDeclaration): FunctionModel? {
        keyOf(function, Read::class.simpleName)?.let { key ->
            val returned = function.returnType?.resolve()
            val isFlow = returned?.declaration?.qualifiedName?.asString() == FLOW_FQ_NAME
            val value = if (isFlow) returned?.arguments?.firstOrNull()?.type?.resolve() else returned
            return FunctionModel(
                name = function.simpleName.asString(),
                accessor = Accessor.READ,
                key = key,
                readShape = if (isFlow) ReadShape.FLOW else ReadShape.SUSPEND,
                declaredType = renderTypeUseCase(value),
            )
        }

        keyOf(function, Write::class.simpleName)?.let { key ->
            val parameter = function.parameters.firstOrNull()?.type?.resolve()
            return FunctionModel(
                name = function.simpleName.asString(),
                accessor = Accessor.WRITE,
                key = key,
                readShape = null,
                declaredType = renderTypeUseCase(parameter),
            )
        }

        keyOf(function, Erase::class.simpleName)?.let { key ->
            return FunctionModel(
                name = function.simpleName.asString(),
                accessor = Accessor.ERASE,
                key = key,
                readShape = null,
                declaredType = null,
            )
        }

        if (function.annotations.any { it.shortName.asString() == EraseAll::class.simpleName }) {
            return FunctionModel(
                name = function.simpleName.asString(),
                accessor = Accessor.ERASE_ALL,
                key = null,
                readShape = null,
                declaredType = null,
            )
        }

        return null
    }

    /** Verilen anotasyonun `key` argumanini cozer; anotasyon yoksa `null` doner. */
    private fun keyOf(function: KSFunctionDeclaration, annotationName: String?): String? =
        function.annotations
            .firstOrNull { it.shortName.asString() == annotationName }
            ?.arguments
            ?.firstOrNull { it.name?.asString() == KEY_ARGUMENT }
            ?.value as? String

    private companion object {
        private const val FLOW_FQ_NAME = "kotlinx.coroutines.flow.Flow"
        private const val KEY_ARGUMENT = "key"
    }
}
