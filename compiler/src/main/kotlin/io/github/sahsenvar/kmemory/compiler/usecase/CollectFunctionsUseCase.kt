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
 */
internal class CollectFunctionsUseCase {

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
                declaredTypeName = value?.declaration?.simpleName?.asString(),
                declaredTypeFqName = value?.declaration?.qualifiedName?.asString(),
            )
        }

        keyOf(function, Write::class.simpleName)?.let { key ->
            val parameter = function.parameters.firstOrNull()?.type?.resolve()
            return FunctionModel(
                name = function.simpleName.asString(),
                accessor = Accessor.WRITE,
                key = key,
                readShape = null,
                declaredTypeName = parameter?.declaration?.simpleName?.asString(),
                declaredTypeFqName = parameter?.declaration?.qualifiedName?.asString(),
            )
        }

        keyOf(function, Erase::class.simpleName)?.let { key ->
            return FunctionModel(
                name = function.simpleName.asString(),
                accessor = Accessor.ERASE,
                key = key,
                readShape = null,
                declaredTypeName = null,
                declaredTypeFqName = null,
            )
        }

        if (function.annotations.any { it.shortName.asString() == EraseAll::class.simpleName }) {
            return FunctionModel(
                name = function.simpleName.asString(),
                accessor = Accessor.ERASE_ALL,
                key = null,
                readShape = null,
                declaredTypeName = null,
                declaredTypeFqName = null,
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
