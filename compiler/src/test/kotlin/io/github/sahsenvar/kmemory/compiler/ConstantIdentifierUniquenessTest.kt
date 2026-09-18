package io.github.sahsenvar.kmemory.compiler

import io.github.sahsenvar.kmemory.compiler.model.Accessor
import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.KeyConstantNaming
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
 * ad uzayi vardir (anahtar metni sabiti + `Preferences.Key`) ve bir uzayin oneki digerinin
 * uzantisiysa iki FARKLI ek ayni tanimlayiciya cokebilir. Bu test tekilligi ekler uzerinden degil,
 * companion'a gercekten yazilacak adlar uzerinden olcer.
 */
internal class ConstantIdentifierUniquenessTest {

    /**
     * Yapisal kural 1: ad uzayi onekleri birbirinin oneki OLAMAZ.
     *
     * `KEY_` ile `KEY_NAME_` bu kurali ihlal ediyordu; `ek_A == "NAME_" + ek_B` olan her ad
     * ciftinde (`readNameSurname` + `readSurname`) iki uzay ayni tanimlayiciyi uretiyordu.
     * Onekler ayriksa carpisma tespit edilmez — imkansiz olur.
     */
    @Test
    fun `hicbir ad uzayi oneki digerinin oneki olamaz`() {
        val prefixes = KeyConstantNaming.PREFIXES

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
     * Yapisal kural 2: hicbir onek, ekten TUREMEYEN companion sabitlerini uretemez.
     *
     * `STORE_NAME` ucuncu bir tanimlayici uzayidir ama hicbir yerde modellenmiyordu. Bugun guvenli
     * olmasi tesadufti: oneklere `""` ya da `"STORE_"` eklenmesi companion'i sessizce kirardi ve
     * kural 1 testi bunu hic gormezdi.
     */
    @Test
    fun `hicbir onek ekten turemeyen sabitleri uretemez`() {
        KeyConstantNaming.RESERVED_IDENTIFIERS.forEach { reserved ->
            KeyConstantNaming.PREFIXES.forEach { prefix ->
                assertFalse(
                    reserved.startsWith(prefix),
                    "'$prefix' oneki rezerve '$reserved' sabitini uretebilir; ek '${reserved.removePrefix(prefix)}'",
                )
            }
        }
    }

    /**
     * Onek KUMESI tek kaynakli olmali: [KeyConstantNaming.PREFIXES].
     *
     * Onceki bicim onek DEGERLERINI modelden turetiyordu ama KUMESINI degil — sabit bir
     * `listOf(keyNameProperty, keyProperty)` yaziyordu. Dolayisiyla [PreferenceModel]'e UCUNCU bir
     * turetilmis tanimlayici eklenseydi ne `identifiers()` ne de koruma testi onu gorurdu.
     *
     * Burada model, ekten turetilmis alanlari icin yansima ile taranir: SENTINEL ek tasiyan her
     * alan ya ekin kendisi olmali ya da [KeyConstantNaming.identifiers] tarafindan uretilmis
     * olmali. Yeni bir turetilmis alan, onegi PREFIXES'e eklenmedikce bu testi kirar.
     */
    @Test
    fun `modelin ekten turettigi tum alanlar tek kaynaktan gelir`() {
        val model = sentinelModel()
        val expected = KeyConstantNaming.identifiers(SENTINEL_SUFFIX).toSet() + SENTINEL_SUFFIX

        val derived = PreferenceModel::class.java.declaredFields
            .filter { it.type == String::class.java }
            .mapNotNull { field ->
                field.isAccessible = true
                (field.get(model) as? String)?.takeIf { SENTINEL_SUFFIX in it }?.let { field.name to it }
            }

        derived.forEach { (name, value) ->
            assertEquals(
                true,
                value in expected,
                "'$name' ekten tanimlayici uretiyor ama KeyConstantNaming.PREFIXES'te yok: '$value'",
            )
        }
    }

    /** Kemer + aski: onekler ayrik olsa bile tekillik SON tanimlayicilar uzerinden dogrulanir. */
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

    /**
     * Iki ayirma pasinin YETMEDIGI patolojik girdi; son care dali burada atesler.
     *
     * Adlar literal `_<rakam>` tasidigi icin ayirma pasinin urettigi indeksli adla carpisir:
     * `readA` x2 -> `A_0`/`A_1`, ama `readA_0` zaten `A_0`. Ikinci pas `A_0_0` uretir, `readA_0_0`
     * de oyle. Dalin kalici testi yoktu; davranisi buraya sabitliyoruz.
     *
     * Kritik ozellik: `readZebra` hicbir seyle carpismiyor ve adini KORUR. Onceki bicim indeksi
     * ayrimsiz herkese ekliyordu ve bu masum grup `ZEBRA_4` oluyordu.
     */
    @Test
    fun `son care pasi yalnizca carpisan gruplarin adini degistirir`() {
        val suffixes = modelsOf(
            "uuid-1" to "readA",
            "uuid-2" to "readA",
            "uuid-3" to "readA_0",
            "uuid-4" to "readA_0_0",
            "uuid-5" to "readZebra",
        ).map { it.constantSuffix }

        assertEquals(listOf("A_0_0", "A_1", "A_0_2", "A_0_0_3", "ZEBRA"), suffixes)
    }

    /**
     * Ad turetilemeyen yolda da ek ASCII'dir.
     *
     * `isValidSuffix()` turetilmis yolda ASCII disi eki reddedip fallback'e dusuruyordu ama
     * fallback'in KENDISI dogrulanmiyordu: anahtar metnindeki ASCII disi harfler tanimlayiciya
     * gecerdi (`RAW_ÜRÜNDEĞERI_0`). Anahtarda hic ASCII alfanumerik yoksa govde sabittir.
     */
    @Test
    fun `turetilemeyen adlarin eki de ASCII kalir`() {
        val suffixes = modelsOf(
            "urun-değeri" to "read",
            "日本語" to "read",
        ).map { it.constantSuffix }

        assertEquals(listOf("URUNDEERI_0", "PREF_1"), suffixes)
        suffixes.forEach { suffix ->
            assertEquals(
                true,
                suffix.all { it in 'A'..'Z' || it in '0'..'9' || it == '_' },
                "ek ASCII olmali: '$suffix'",
            )
        }
    }

    // --- altyapi -------------------------------------------------------------------------

    private fun sentinelModel(): PreferenceModel = PreferenceModel(
        key = "uuid-1",
        constantSuffix = SENTINEL_SUFFIX,
        type = PreferenceType.STRING,
        declaredType = null,
        functions = emptyList(),
    )

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

        /** Hicbir onekle karisamayacak ek; turetilmis alanlar bunun aranmasiyla bulunur. */
        const val SENTINEL_SUFFIX = "SENTINEL"
    }
}
