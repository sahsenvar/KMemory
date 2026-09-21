package io.github.sahsenvar.kmemory.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.sahsenvar.kmemory.kmemory
import io.github.sahsenvar.kmemory.listener.PreferenceErrorMapper
import io.github.sahsenvar.kmemory.listener.PreferenceFailure
import io.github.sahsenvar.kmemory.listener.PreferenceListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [PreferenceErrorMapper] sozlesmesi: Ktor'daki `HttpResponseValidator` kancasinin karsiligi.
 *
 * Kutuphane hatayi SINIFLANDIRMAZ; yalnizca cevirme NOKTASINI verir. Burada pinlenen dort sey:
 *
 * 1. Mapper verilirse cagiran CEVRILMIS hatayi alir (her dalda).
 * 2. Mapper VERILMEZSE bugunku davranis aynen surer -- bu kanca opsiyoneldir ve mevcut
 *    tuketicileri kirmamalidir.
 * 3. [CancellationException] ne raporlanir ne cevrilir. Iptal bir hata degildir; Ktor tarafinda
 *    ayni koruma `if (cause is CancellationException) throw cause` satiridir.
 * 4. Mapper varken de dinleyici muhrü BOZULMAZ: `onError` hala yalnizca [PreferenceFailure]
 *    gorur. Iki yol ayridir -- dinleyici raporlar, mapper cagirana giden hatayi uretir.
 */
class PreferenceErrorMapperTest {

    @Test
    fun `mapper verilirse cagiran CEVRILMIS hatayi alir`() = runTest {
        val kaynak = SamplePreferencesImpl(
            dataStore = SondaStore,
            json = Json,
            listener = PreferenceListener.None,
            errorMapper = { store, key, error -> AlanHatasi(store, key, error) },
        )

        val dallar = listOf<Pair<String, suspend () -> Any?>>(
            "writeCount" to { kaynak.writeCount(42) },
            "eraseCount" to { kaynak.eraseCount() },
            "eraseAll" to { kaynak.eraseAll() },
            "readCount" to { kaynak.readCount().first() },
            "readCountOnce" to { kaynak.readCountOnce() },
        )

        dallar.forEach { (ad, dal) ->
            val firlatilan = assertFailsWith<AlanHatasi>(ad) { dal() }
            assertEquals(SamplePreferencesImpl.STORE_NAME, firlatilan.store, "$ad: store adi tasinmali")
            assertIs<SondaHatasi>(firlatilan.cause, "$ad: orijinal cause olarak tasinmali")
        }
    }

    /**
     * `Flow` donen sekil ayri bir iskeletten (`MutationBody.flowing`, `.catch { }`) uretilir;
     * ayni merkezi noktadan gecmek zorunda. Zad'in kullandigi sekil budur.
     */
    @Test
    fun `Flow sekli de cevrilir`() = runTest {
        val kaynak = FlowSamplePreferencesImpl(
            dataStore = SondaStore,
            json = Json,
            listener = PreferenceListener.None,
            errorMapper = { store, key, error -> AlanHatasi(store, key, error) },
        )

        assertFailsWith<AlanHatasi> { kaynak.writeCount(42).collect() }
        assertFailsWith<AlanHatasi> { kaynak.eraseCount().collect() }
        assertFailsWith<AlanHatasi> { kaynak.eraseAll().collect() }
        assertFailsWith<AlanHatasi> { kaynak.readCount().first() }
    }

    /**
     * Kanca OPSIYONEL. Verilmeyen her kurulum -- yani bugune kadarki her tuketici -- orijinal
     * hatayi tipi ve mesajiyla almaya devam etmeli.
     */
    @Test
    fun `mapper VERILMEZSE orijinal hata aynen gelir`() = runTest {
        val kaynak = SamplePreferencesImpl(SondaStore, Json, PreferenceListener.None)

        val firlatilan = assertFailsWith<SondaHatasi> { kaynak.writeCount(42) }
        assertEquals(SONDA_MESAJI, firlatilan.message, "cagirana orijinal mesaj gitmeli")
    }

    /**
     * Iptal bir hata DEGILDIR.
     *
     * Bugun KMemory'de [CancellationException] hic ele alinmiyor: `report` onu da bir hata sanip
     * dinleyiciye veriyor, yani iptal edilen her corutin sahte bir hata kaydi uretiyor. Kanca
     * eklenirken bu iki kat kotulesirdi -- iptal ayrica ALAN HATASINA da cevrilirdi ve cagiran
     * `CancellationException` yerine alakasiz bir tip gorurdu, ki bu corutin iptal zincirini
     * sessizce kirar.
     */
    @Test
    fun `iptal ne raporlanir ne cevrilir`() = runTest {
        val raporlanan = mutableListOf<Throwable>()
        var mapperCagrildi = false
        val kaynak = SamplePreferencesImpl(
            dataStore = IptalStore,
            json = Json,
            listener = object : PreferenceListener {
                override fun onError(store: String, key: String?, error: Throwable) {
                    raporlanan += error
                }
            },
            errorMapper = { store, key, error ->
                mapperCagrildi = true
                AlanHatasi(store, key, error)
            },
        )

        assertFailsWith<CancellationException> { kaynak.writeCount(42) }

        assertTrue(raporlanan.isEmpty(), "iptal raporlanmamali: $raporlanan")
        assertTrue(!mapperCagrildi, "iptal cevrilmemeli")
    }

    /**
     * Kanca dinleyici muhrunu DELMEZ.
     *
     * Mapper orijinal [Throwable]'i gorur -- cagiran zaten onu aliyordu, yeni bilgi acilmiyor.
     * Dinleyici ise hala yalnizca [PreferenceFailure] gorur: tip adi + yigin izi, mesaj YOK.
     * Bu ayrimin sebebi kayitli: `Preferences.toString()` store'un TUM anahtar=deger ciftlerini
     * basar, yani dinleyiciye ham hata gitseydi saklanan deger Crashlytics'e duserdi.
     */
    @Test
    fun `mapper varken de dinleyici yalnizca PreferenceFailure gorur`() = runTest {
        val raporlanan = mutableListOf<Throwable>()
        val kaynak = SamplePreferencesImpl(
            dataStore = SondaStore,
            json = Json,
            listener = object : PreferenceListener {
                override fun onError(store: String, key: String?, error: Throwable) {
                    raporlanan += error
                }
            },
            errorMapper = { store, key, error -> AlanHatasi(store, key, error) },
        )

        assertFailsWith<AlanHatasi> { kaynak.writeCount(42) }

        val hata = assertIs<PreferenceFailure>(raporlanan.single())
        val metin = hata.toString() + "\n" + hata.stackTraceToString()
        assertTrue(!metin.contains(SONDA_IMZASI), "dinleyiciye giden metin saklanan degeri tasiyor: $metin")
    }

    /** Kanca `kmemory { }` DSL'inden de verilebilmeli; Zad'in kullanacagi yol budur. */
    @Test
    fun `kmemory DSL uzerinden verilen mapper uretilen kaynaga ulasir`() = runTest {
        val memory = kmemory {
            storeFactory = { SondaStore }
            errorMapper = PreferenceErrorMapper { store, key, error -> AlanHatasi(store, key, error) }
        }

        assertFailsWith<AlanHatasi> { memory.samplePreferences().writeCount(42) }
    }

    /** Tuketicinin kendi hata tipi; kutuphane boyle bir tip TANIMLAMAZ, yalnizca noktayi verir. */
    private class AlanHatasi(
        val store: String,
        val key: String?,
        cause: Throwable,
    ) : RuntimeException("tercih islemi basarisiz: $store/$key", cause)

    /** Okumasi da yazmasi da ayni sonda hatasiyla patlayan store. */
    private object SondaStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw SondaHatasi() }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw SondaHatasi()
    }

    /** Her islemde iptal firlatan store. */
    private object IptalStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw CancellationException("iptal") }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw CancellationException("iptal")
    }

    private class SondaHatasi : IOException(SONDA_MESAJI)

    private companion object {
        const val SONDA_IMZASI = "SONDA-SIR-2002"
        const val SONDA_MESAJI = "tercih dogrulamasi basarisiz: { 3f1c0b2e-0003 = $SONDA_IMZASI }"
    }
}
