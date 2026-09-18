package io.github.sahsenvar.kmemory.compiler

import io.github.sahsenvar.kmemory.compiler.model.Accessor
import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceType
import io.github.sahsenvar.kmemory.compiler.model.ReturnShape
import io.github.sahsenvar.kmemory.compiler.usecase.DeriveConstantSuffixUseCase
import io.github.sahsenvar.kmemory.compiler.usecase.GroupByKeyUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Companion'a yazilan TANIMLAYICILARIN tekilligi.
 *
 * Ek (`constantSuffix`) tekil olsa bile tanimlayici tekil olmayabilir: companion'da birden fazla
 * ad uzayi vardir (anahtar metni sabiti + `Preferences.Key` degiskeni) ve bir uzayin oneki
 * digerinin uzantisiysa iki FARKLI ek ayni tanimlayiciya cokebilir. Bu test tekilligi ekler
 * uzerinden degil, companion'a gercekten yazilacak adlar uzerinden olcer.
 */
internal class ConstantIdentifierUniquenessTest {

    /**
     * Yapisal kural: ad uzayi onekleri birbirinin oneki OLAMAZ.
     *
     * `KEY_` ile `KEY_NAME_` bu kurali ihlal ediyordu; `ek_A == "NAME_" + ek_B` olan her ad
     * ciftinde (`readNameSurname` + `readSurname`) iki uzay ayni tanimlayiciyi uretiyordu.
     * Onekler ayriksa carpisma tespit edilmez — imkansiz olur.
     */
    @Test
    fun `hicbir ad uzayi oneki digerinin oneki olamaz`() {
        val prefixes = namespacePrefixes()

        prefixes.forEach { prefix ->
            prefixes.filterNot { it === prefix }.forEach { other ->
                assertFalse(
                    prefix.startsWith(other),
                    "ad uzayi onekleri ayrik olmali; '$other' '$prefix' onekinin oneki",
                )
            }
        }
    }

    /**
     * Kemer + aski: onekler ayrik olsa bile tekillik SON tanimlayicilar uzerinden dogrulanir.
     * Ileride ucuncu bir ad uzayi eklenirse bu testin kirilmasi gerekir.
     */
    @Test
    fun `uretilen tum tanimlayicilarin kumesi ile listesi ayni boyuttadir`() {
        val models = modelsOf(
            "uuid-1" to "readNameSurname",
            "uuid-2" to "readSurname",
            "uuid-3" to "getNameFilter",
            "uuid-4" to "setFilter",
            "uuid-5" to "readNameFoo",
            "uuid-6" to "readFoo",
            "uuid-7" to "readProfile",
            "uuid-8" to "getProfile",
        )

        val identifiers = models.flatMap { listOf(it.keyNameProperty, it.keyProperty) }

        assertEquals(
            identifiers.size,
            identifiers.toSet().size,
            "companion'a ayni tanimlayici iki kez yazilir: $identifiers",
        )
    }

    // --- altyapi -------------------------------------------------------------------------

    /** Sabit adlarindan sentinel eki soyularak elde edilen ad uzayi onekleri. */
    private fun namespacePrefixes(): List<String> {
        val model = PreferenceModel(
            key = "uuid-1",
            constantSuffix = SENTINEL_SUFFIX,
            type = PreferenceType.STRING,
            declaredType = null,
            functions = emptyList(),
        )

        return listOf(model.keyNameProperty, model.keyProperty).map { it.removeSuffix(SENTINEL_SUFFIX) }
    }

    private fun modelsOf(vararg accessors: Pair<String, String>): List<PreferenceModel> =
        GroupByKeyUseCase(DeriveConstantSuffixUseCase())(
            accessors.map { (key, name) ->
                FunctionModel(
                    name = name,
                    accessor = Accessor.READ,
                    key = key,
                    returnShape = ReturnShape.FLOW,
                    declaredType = null,
                )
            }
        )

    private companion object {

        /** Hicbir onekle karisamayacak ek; onekler bunun soyulmasiyla bulunur. */
        const val SENTINEL_SUFFIX = "SENTINEL"
    }
}
