package io.github.sahsenvar.kmemory.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.sahsenvar.kmemory.listener.PreferenceFailure
import io.github.sahsenvar.kmemory.listener.PreferenceListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Spec §8'in "dinleyici degerleri ASLA gormez" sozlesmesinin dal dal kaniti.
 *
 * Onceki devirde kapi hatanin ciktigi KONUMA bakiyordu: yalnizca `encode`/`decode` cagrilarinin
 * etrafindan cikan hatalar sanitize ediliyor, DataStore'un kendi hatalari "deger tasimaz"
 * varsayimiyla dinleyiciye ham gidiyordu. Varsayim yanlisti —
 * `androidx.datastore.preferences.core.Preferences.toString()` store'un TUM anahtar=deger
 * ciftlerini basar, yani mesajinda bir anlik goruntu tasiyan herhangi bir DataStore istisnasi
 * saklanan degeri disari cikariyordu.
 *
 * Buradaki sonda tam olarak o sekildir: mesajinda bir anlik goruntu tasiyan, serilestirme
 * siniriyla hicbir ilgisi olmayan bir hata. Yazma, silme, `eraseAll` ve okumanin iki sekli —
 * ayrica `Flow` donen mutasyon sekilleri — ayri ayri kosturulur; her dal tek merkezi `report`
 * noktasindan gecmek zorundadir.
 */
class PreferenceFailureTest {

    /**
     * Her dalda ayni uc sey dogrulanir: dinleyiciye giden metinde imza YOK, [originalType]
     * dogru, yigin izi BOS DEGIL.
     *
     * Kaynak dogrudan constructor'dan kurulur (spec §6.1): gereken, her islemde ayni sonda
     * hatasini firlatan bir store.
     */
    @Test
    fun `suspend sekli hicbir dalda imzayi dinleyiciye sizdirmaz`() = runTest {
        val raporlanan = mutableListOf<Throwable>()
        val kaynak = SamplePreferencesImpl(SondaStore, Json, kaydeden(raporlanan))

        assertFailsWith<SondaHatasi> { kaynak.writeCount(42) }
        assertFailsWith<SondaHatasi> { kaynak.eraseCount() }
        assertFailsWith<SondaHatasi> { kaynak.eraseAll() }
        assertFailsWith<SondaHatasi> { kaynak.readCount().first() }
        assertFailsWith<SondaHatasi> { kaynak.readCountOnce() }

        assertEquals(BEKLENEN_DAL_SAYISI, raporlanan.size, "her dal raporlamali: $raporlanan")
        raporlanan.forEach(::sozlesmeyiDogrula)
    }

    /**
     * `Flow` donen yazma/silme sekilleri ayri bir iskeletten (`MutationBody.flowing`) uretilir;
     * `.catch { }` dali da ayni merkezi noktadan gecmek zorunda. Zad'in kullandigi sekil budur.
     */
    @Test
    fun `Flow sekli hicbir dalda imzayi dinleyiciye sizdirmaz`() = runTest {
        val raporlanan = mutableListOf<Throwable>()
        val kaynak = FlowSamplePreferencesImpl(SondaStore, Json, kaydeden(raporlanan))

        assertFailsWith<SondaHatasi> { kaynak.writeCount(42).collect() }
        assertFailsWith<SondaHatasi> { kaynak.eraseCount().collect() }
        assertFailsWith<SondaHatasi> { kaynak.eraseAll().collect() }
        assertFailsWith<SondaHatasi> { kaynak.readCount().first() }

        assertEquals(BEKLENEN_FLOW_DAL_SAYISI, raporlanan.size, "her dal raporlamali: $raporlanan")
        raporlanan.forEach(::sozlesmeyiDogrula)
    }

    /**
     * Sanitizasyon YALNIZCA dinleyici yolunu degistirir.
     *
     * Cagiran zaten degere erisebilen koddur; orada gizlilik kaybi yoktur ve tam tani
     * korunmalidir. Bu test olmazsa "mesaji sil" duzeltmesi sessizce cagirani da korlestirebilir.
     */
    @Test
    fun `cagirana firlatilan hata hala orijinal tip ve mesajdir`() = runTest {
        val kaynak = SamplePreferencesImpl(SondaStore, Json, PreferenceListener.None)

        val dallar = listOf<Pair<String, suspend () -> Any?>>(
            "writeCount" to { kaynak.writeCount(42) },
            "eraseCount" to { kaynak.eraseCount() },
            "eraseAll" to { kaynak.eraseAll() },
            "readCount" to { kaynak.readCount().first() },
            "readCountOnce" to { kaynak.readCountOnce() },
        )

        dallar.forEach { (ad, dal) ->
            val firlatilan = assertFailsWith<SondaHatasi>(ad) { dal() }
            assertEquals(SONDA_MESAJI, firlatilan.message, "$ad: cagirana orijinal mesaj gitmeli")
        }
    }

    /**
     * Dinleyiciye ulasan seyin tasidigi ve tasimadigi her sey.
     *
     * Olcut `toString() + stackTraceToString()`'dir: gunlukleme altyapilari hatayi tam olarak
     * boyle basar, yani sizinti mesajdan, `cause` zincirinden ya da `suppressed`'dan gelse bile
     * bu metinde gorunur.
     */
    private fun sozlesmeyiDogrula(error: Throwable) {
        val metin = error.toString() + "\n" + error.stackTraceToString()
        assertFalse(
            metin.contains(SONDA_IMZASI),
            "dinleyiciye giden hata saklanan degeri tasiyor: $metin",
        )

        val hata = assertIs<PreferenceFailure>(error, "dinleyici yabanci bir Throwable almamali")
        assertEquals(SondaHatasi::class.java.name, hata.originalType, "tani tipi korunmali")
        assertTrue(hata.stackTrace.isNotEmpty(), "orijinalin yigin izi kopyalanmali")
    }

    private fun kaydeden(hedef: MutableList<Throwable>) = object : PreferenceListener {
        override fun onError(store: String, key: String?, error: Throwable) {
            hedef += error
        }
    }

    /**
     * Mesajinda bir store anlik goruntusu tasiyan sonda hatasi.
     *
     * Serilestirme siniriyla ilgisi YOKTUR ve tipi de serilestirmeye ait degildir: konum ya da
     * tip kapisi olan her tasarim bu hatayi kacirir.
     */
    private class SondaHatasi : IOException(SONDA_MESAJI)

    /** Okumasi da yazmasi da ayni sonda hatasiyla patlayan store. */
    private object SondaStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw SondaHatasi() }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw SondaHatasi()
    }

    private companion object {

        /** Saklanan degeri temsil eden benzersiz imza; dinleyiciye ulasan metinde GECMEMELI. */
        const val SONDA_IMZASI = "SONDA-SIR-1001"

        /** `Preferences.toString()`'in urettigi bicimi taklit eder. */
        const val SONDA_MESAJI = "tercih dogrulamasi basarisiz: { 3f1c0b2e-0003 = $SONDA_IMZASI }"

        /** writeCount + eraseCount + eraseAll + iki okuma sekli. */
        const val BEKLENEN_DAL_SAYISI = 5

        /** writeCount + eraseCount + eraseAll + Flow okuma. */
        const val BEKLENEN_FLOW_DAL_SAYISI = 4
    }
}
