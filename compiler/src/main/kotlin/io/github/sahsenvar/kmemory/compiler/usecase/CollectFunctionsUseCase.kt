package io.github.sahsenvar.kmemory.compiler.usecase

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.github.sahsenvar.kmemory.annotation.Erase
import io.github.sahsenvar.kmemory.annotation.EraseAll
import io.github.sahsenvar.kmemory.annotation.Read
import io.github.sahsenvar.kmemory.annotation.Write
import io.github.sahsenvar.kmemory.compiler.model.Accessor
import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.ReturnShape

/**
 * Bir `@Preferences` arayuzunun SOYUT fonksiyonlarini [FunctionModel] listesine cevirir.
 *
 * Girdi listesini kendisi TOPLAMAZ, disaridan alir: govdeli fonksiyonlarin elenmesi
 * [io.github.sahsenvar.kmemory.compiler.processor.Processor]'de tek yerde yapilir, boylece
 * dogrulamanin saydigi liste ile burada gezilen liste tanim geregi aynidir.
 *
 * Burada DOGRULAMA yapilmaz; anotasyon tasimayan fonksiyon sessizce elenir. Elenen fonksiyonu
 * hataya cevirmek [ValidateInterfaceUseCase]'in isidir ve bildirilen fonksiyon sayisi ile
 * donen model sayisinin karsilastirilmasiyla yapilir — bu yuzden buradaki siralama,
 * girdi siralamasini korur.
 *
 * Tip HER ZAMAN [RenderTypeUseCase]'den gecer. Once `declaration.simpleName` aliniyordu; bu
 * tip argumanlarini ve nullability'yi dusurup `List<SearchHistory>`'yi `List`'e,
 * `String?`'i `String`'e indiriyor ve uretilen sinifi derlenemez hale getiriyordu.
 *
 * [ReturnShape] dort erisimin HEPSI icin ayni sekilde, bildirilen donus tipinden cikarilir:
 * `Flow` donen fonksiyon [ReturnShape.FLOW], donmeyen [ReturnShape.SUSPEND]'dir. Sekil eskiden
 * yalnizca `@Read` icin tutuluyordu; yazma/silmenin `Flow<Unit>` sekli bu yuzden uretilemiyordu.
 */
internal class CollectFunctionsUseCase(
    private val renderTypeUseCase: RenderTypeUseCase,
) {

    /** @param declaredFunctions arayuzde BILDIRILEN soyut fonksiyonlar, bildirim sirasinda. */
    operator fun invoke(declaredFunctions: List<KSFunctionDeclaration>): List<FunctionModel> =
        declaredFunctions.mapNotNull(::toModel)

    private fun toModel(function: KSFunctionDeclaration): FunctionModel? {
        val returned = function.returnType?.resolve()
        val isFlow = returned?.declaration?.qualifiedName?.asString() == FLOW_FQ_NAME
        val shape = if (isFlow) ReturnShape.FLOW else ReturnShape.SUSPEND

        keyOf(function, Read::class.simpleName)?.let { key ->
            // Okunan deger `Flow`'un tip argumanidir; Flow olmayan sekilde donus tipinin kendisi.
            val value = if (isFlow) returned?.arguments?.firstOrNull()?.type?.resolve() else returned
            return FunctionModel(
                name = function.simpleName.asString(),
                accessor = Accessor.READ,
                key = key,
                returnShape = shape,
                declaredType = renderTypeUseCase(value),
            )
        }

        keyOf(function, Write::class.simpleName)?.let { key ->
            // Yazmanin tipi DONUS tipinden degil, tek parametreden gelir; iki sekilde de ayni.
            val parameter = function.parameters.firstOrNull()?.type?.resolve()
            return FunctionModel(
                name = function.simpleName.asString(),
                accessor = Accessor.WRITE,
                key = key,
                returnShape = shape,
                declaredType = renderTypeUseCase(parameter),
            )
        }

        keyOf(function, Erase::class.simpleName)?.let { key ->
            return FunctionModel(
                name = function.simpleName.asString(),
                accessor = Accessor.ERASE,
                key = key,
                returnShape = shape,
                declaredType = null,
            )
        }

        if (function.annotations.any { it.shortName.asString() == EraseAll::class.simpleName }) {
            return FunctionModel(
                name = function.simpleName.asString(),
                accessor = Accessor.ERASE_ALL,
                key = null,
                returnShape = shape,
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
