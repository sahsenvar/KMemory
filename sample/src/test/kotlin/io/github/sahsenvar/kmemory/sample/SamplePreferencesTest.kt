package io.github.sahsenvar.kmemory.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.sahsenvar.kmemory.kmemory
import io.github.sahsenvar.kmemory.listener.PreferenceListener
import io.github.sahsenvar.kmemory.listener.PreferenceSerializationException
import io.github.sahsenvar.kmemory.sample.model.SearchHistorySample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Uretilen `SamplePreferencesImpl`'in davranis testleri (plan Task 8, spec §12).
 *
 * Modul JVM'dir: uretilen sinif `Context` degil `DataStore<Preferences>` aldigi icin
 * Robolectric'siz, gercek bir gecici dosya uzerinde kosuyor.
 */
class SamplePreferencesTest {

    /**
     * Her cagri AYRI bir gecici dizinde store acar.
     *
     * Ayni dosya icin ikinci bir DataStore ornegi calisma aninda patlar; testlerin
     * birbirinden yalitilmasi bu yuzden dizin duzeyinde yapilir.
     */
    private fun store(): DataStore<Preferences> {
        val dir = createTempDirectory("kmemory").toFile()
        return PreferenceDataStoreFactory.createWithPath {
            dir.resolve("sample.preferences_pb").toOkioPath()
        }
    }

    /**
     * Kaynak URETILEN FABRIKA UZANTISINDAN kurulur — spec §6.1'in tuketiciye acilan TEK
     * yuzeyi budur. Boylece asagidaki her test ayni zamanda `fun KMemory.samplePreferences()`
     * uzantisini de kosturmus olur. Sinifi dogrudan kuran yol, hata yolu testlerinde
     * (bkz. [Kayit] kullanan testler) bilerek korunuyor.
     */
    private fun prefs(listener: PreferenceListener = PreferenceListener.None): SamplePreferences {
        val dizin = createTempDirectory("kmemory").toFile()
        return kmemory {
            storeFactory = { name ->
                PreferenceDataStoreFactory.createWithPath { dizin.resolve(name).toOkioPath() }
            }
            this.listener = listener
        }.samplePreferences()
    }

    @Test
    fun `yazilmamis anahtar null yayar`() = runTest {
        assertNull(prefs().readCount().first())
    }

    @Test
    fun `yazilan primitive hem Flow hem tek seferlik okunur`() = runTest {
        val p = prefs()
        p.writeCount(42)
        assertEquals(42, p.readCount().first())
        assertEquals(42, p.readCountOnce())
    }

    @Test
    fun `erase yalnizca kendi anahtarini siler`() = runTest {
        val p = prefs()
        p.writeCount(42)
        p.writeLabel("kalsin")
        p.eraseCount()
        assertNull(p.readCount().first())
        assertEquals("kalsin", p.readLabel().first())
    }

    @Test
    fun `eraseAll her seyi siler`() = runTest {
        val p = prefs()
        p.writeCount(42)
        p.writeLabel("gitsin")
        p.eraseAll()
        assertNull(p.readCount().first())
        assertNull(p.readLabel().first())
    }

    @Test
    fun `serializable nesne yazilip okunur`() = runTest {
        val p = prefs()
        val profile = ProfileSample(id = "1", name = "Sahan")
        p.writeProfile(profile)
        assertEquals(profile, p.readProfile().first())
    }

    @Test
    fun `listener yazma ve silmeyi bildirir`() = runTest {
        val seen = mutableListOf<String>()
        val listener = object : PreferenceListener {
            override fun onWrite(store: String, key: String) { seen += "w:$key" }
            override fun onErase(store: String, key: String?) { seen += "e:$key" }
        }
        val p = prefs(listener)
        p.writeCount(42)
        p.eraseCount()
        p.eraseAll()
        assertEquals(3, seen.size)
        assertTrue(seen[0].startsWith("w:3f1c0b2e-0001"))
        assertTrue(seen[1].startsWith("e:3f1c0b2e-0001"))
        assertEquals("e:null", seen[2])
    }

    @Test
    fun `tip argumanli liste yazilip okunur`() = runTest {
        val p = prefs()
        p.writeItems(listOf("a", "b"))
        assertEquals(listOf("a", "b"), p.readItems().first())
    }

    @Test
    fun `nullable yazma null verilince anahtari siler`() = runTest {
        val p = prefs()
        p.writeToken("abc")
        assertEquals("abc", p.readToken())
        p.writeToken(null)
        assertNull(p.readToken())
    }

    @Test
    fun `ilk 20 karakteri ayni iki anahtar birbirini ezmez`() = runTest {
        val p = prefs()
        p.writeNotificationEnabled(true)
        p.writeNotificationMuted(false)
        assertEquals(true, p.readNotificationEnabled().first())
        assertEquals(false, p.readNotificationMuted().first())
    }

    @Test
    fun `baska paketteki tipin listesi yazilip okunur`() = runTest {
        val p = prefs()
        val history = listOf(SearchHistorySample("zad"), SearchHistorySample("kmemory"))
        p.writeHistory(history)
        assertEquals(history, p.readHistory().first())
    }

    /**
     * Spec §8: "dinleyici degerleri ASLA gormez".
     *
     * kotlinx-serialization bozuk girdiyi hata MESAJINA gomer ("... JSON input: {...}"), yani
     * ham `Throwable` dinleyiciye verildiginde bir Crashlytics kaydi saklanan degeri disari
     * tasir. Test diske icinde taninabilir bir metin olan bozuk bir JSON yazar ve dinleyiciye
     * ulasan hatanin TUM metninde (cause zinciri dahil) o metnin gecmedigini dogrular.
     */
    @Test
    fun `bozuk JSON hatasi dinleyiciye saklanan degeri sizdirmaz`() = runTest {
        val dataStore = store()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(KEY_PROFILE)] = BOZUK_JSON
        }
        val reported = mutableListOf<Throwable>()
        val listener = object : PreferenceListener {
            override fun onError(store: String, key: String?, error: Throwable) {
                reported += error
            }
        }
        val p = SamplePreferencesImpl(dataStore, Json, listener)

        // Cagirana firlatilan hata DEGISMEZ: hala serilestirme hatasinin kendisi.
        assertFailsWith<SerializationException> { p.readProfile().first() }
        assertFailsWith<SerializationException> { p.readProfileOnce() }

        assertEquals(2, reported.size, "her iki okuma sekli de raporlamali")
        reported.forEach { error ->
            val metin = error.stackTraceToString()
            assertFalse(
                metin.contains(SIZINTI_KANITI),
                "dinleyiciye giden hata saklanan degeri tasiyor: $metin",
            )
            // Sarmalayici tanisiz olmamali: hangi depo, hangi anahtar, hangi hata sinifi.
            val sarmalayici = assertIs<PreferenceSerializationException>(error)
            assertEquals(KEY_PROFILE, sarmalayici.key)
            assertEquals("JsonDecodingException", sarmalayici.failureType)
            assertNull(sarmalayici.cause, "cause tutulursa mesaj zincir uzerinden yine okunur")
        }
    }

    /**
     * Spec §8 — sanitizasyon TIPE degil ciktigi KONUMA baglidir.
     *
     * `init { require(...) }` Kotlin'de en yaygin dogrulama deyimidir ve
     * [IllegalArgumentException] firlatir; kotlinx-serialization bunu sarmalamaz, yani
     * "hata `SerializationException` mi" diye bakan bir kapi bu yolu KACIRIR ve saklanan PIN
     * mesajla birlikte dinleyiciye — dolayisiyla Crashlytics'e — gider. Bu testin varligi,
     * kapinin serilestirme SINIRINA bagli kalmasini zorunlu kilar: sinirdan cikan her hata,
     * tipi ne olursa olsun, degersiz bir sarmalayiciya cevrilir.
     *
     * Bozuk JSON testinden farki bilerektir: orada hata zaten serilestirme tipindeydi, burada
     * DEGIL. Ikisi birlikte "tip kapisi yeterli" iddiasini kapatir.
     */
    @Test
    fun `dogrulayici init hatasi dinleyiciye saklanan degeri sizdirmaz`() = runTest {
        val dataStore = store()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(KEY_PIN)] = GECERSIZ_PIN_JSON
        }
        val raporlanan = mutableListOf<Throwable>()
        val listener = object : PreferenceListener {
            override fun onError(store: String, key: String?, error: Throwable) {
                raporlanan += error
            }
        }
        val p = SamplePreferencesImpl(dataStore, Json, listener)

        // Cagirana firlatilan hata DEGISMEZ: hala dogrulayicinin kendi hatasi.
        assertFailsWith<IllegalArgumentException> { p.readPin().first() }
        assertFailsWith<IllegalArgumentException> { p.readPinOnce() }

        assertEquals(2, raporlanan.size, "her iki okuma sekli de raporlamali")
        raporlanan.forEach { error ->
            val metin = error.toString() + "\n" + error.stackTraceToString()
            assertFalse(
                metin.contains(PIN_SIZINTI_KANITI),
                "dinleyiciye giden hata saklanan PIN'i tasiyor: $metin",
            )
            val sarmalayici = assertIs<PreferenceSerializationException>(error)
            assertEquals(KEY_PIN, sarmalayici.key)
            assertEquals("IllegalArgumentException", sarmalayici.failureType)
            assertNull(sarmalayici.cause, "cause tutulursa mesaj zincir uzerinden yine okunur")
        }
    }

    /**
     * Kapinin OTEKI yarisi: serilestirme SINIRININ DISINDA olusan hata sanitize EDILMEZ.
     *
     * Konum kapisi "her hatayi sarmala"ya kaysaydi disk hatalarinin mesaji da silinir ve tani
     * bedelsiz yere korlesirdi. DataStore G/C hatasi saklanan degeri tasimaz; dinleyiciye
     * oldugu gibi gitmeli. Test bunu bilerek SERILESTIRILEN bir anahtar uzerinde kosar —
     * yani kapinin "anahtar nesne mi" degil "hata nereden cikti" sorusuna baktigini gosterir.
     */
    @Test
    fun `disk hatasi sanitize edilmez ve tani mesajiyla raporlanir`() = runTest {
        val raporlanan = mutableListOf<Throwable>()
        val listener = object : PreferenceListener {
            override fun onError(store: String, key: String?, error: Throwable) {
                raporlanan += error
            }
        }
        val p = SamplePreferencesImpl(OkumasiPatlayanStore, Json, listener)

        assertFailsWith<IOException> { p.readProfile().first() }
        assertFailsWith<IOException> { p.readProfileOnce() }

        assertEquals(2, raporlanan.size, "her iki okuma sekli de raporlamali")
        raporlanan.forEach { error ->
            assertEquals(DISK_HATASI, assertIs<IOException>(error).message, "tani mesaji korunmali")
        }
    }

    // --- hata yolu: suspend sekli (spec §8 "raporla ve YENIDEN FIRLAT") --------------------

    /**
     * Uretilen suspend govdelerinin `try/catch` dali hicbir testte kosmuyordu; §8'in
     * "raporla ve yeniden firlat" vaadi korumasizdi. Asagidaki testler dort erisim icin de
     * ayni uc maddeyi dogrular: hata dinleyiciye BILDIRILIR, basari kancasi cagrilMAZ, ve
     * AYNI hata cagirana yeniden firlatilir.
     *
     * Kaynak burada bilerek ELLE kurulur: gereken, her islemde patlayan bir store — fabrika
     * uzantisi ise dosya store'u uretir. Ayni zamanda dogrudan constructor yolunun calisir
     * kaldiginin kanitidir (spec §6.1: "test, KMemory'yi hic kurmadan sinifi dogrudan da
     * insa edebilir").
     */
    @Test
    fun `suspend yazma patlarsa dinleyici bilgilendirilir ve hata cagirana gider`() = runTest {
        val kayit = Kayit()
        val p = SamplePreferencesImpl(YazmasiPatlayanStore, Json, kayit)

        val firlatilan = assertFailsWith<IOException> { p.writeCount(42) }

        assertEquals(DISK_HATASI, firlatilan.message, "cagirana orijinal hata gitmeli")
        assertEquals(listOf("err:$KEY_COUNT"), kayit.gorulen, "onWrite kosmamali")
    }

    @Test
    fun `suspend erase patlarsa dinleyici bilgilendirilir ve hata cagirana gider`() = runTest {
        val kayit = Kayit()
        val p = SamplePreferencesImpl(YazmasiPatlayanStore, Json, kayit)

        val firlatilan = assertFailsWith<IOException> { p.eraseCount() }

        assertEquals(DISK_HATASI, firlatilan.message)
        assertEquals(listOf("err:$KEY_COUNT"), kayit.gorulen, "onErase kosmamali")
    }

    /** `@EraseAll` anahtarsizdir; hata da anahtarsiz (`null`) raporlanmali. */
    @Test
    fun `suspend eraseAll patlarsa dinleyici anahtarsiz bilgilendirilir`() = runTest {
        val kayit = Kayit()
        val p = SamplePreferencesImpl(YazmasiPatlayanStore, Json, kayit)

        val firlatilan = assertFailsWith<IOException> { p.eraseAll() }

        assertEquals(DISK_HATASI, firlatilan.message)
        assertEquals(listOf("err:null"), kayit.gorulen)
    }

    /** Okumanin iki sekli de ayni sozlesmeyi tasir: `.catch` dali ve `try/catch` dali. */
    @Test
    fun `okuma patlarsa iki sekil de dinleyiciyi bilgilendirir ve hata cagirana gider`() = runTest {
        val kayit = Kayit()
        val p = SamplePreferencesImpl(OkumasiPatlayanStore, Json, kayit)

        assertEquals(DISK_HATASI, assertFailsWith<IOException> { p.readCount().first() }.message)
        assertEquals(DISK_HATASI, assertFailsWith<IOException> { p.readCountOnce() }.message)

        assertEquals(listOf("err:$KEY_COUNT", "err:$KEY_COUNT"), kayit.gorulen)
    }

    /** Uc kancayi ayni listede toplar; hem siralama hem "kosmamasi gereken" gorunur olur. */
    private class Kayit : PreferenceListener {
        val gorulen = mutableListOf<String>()
        override fun onWrite(store: String, key: String) { gorulen += "w:$key" }
        override fun onErase(store: String, key: String?) { gorulen += "e:$key" }
        override fun onError(store: String, key: String?, error: Throwable) { gorulen += "err:$key" }
    }

    /** Okumasi calisan ama her yazmada patlayan store; disk hatasini deterministik kilar. */
    private object YazmasiPatlayanStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw IOException(DISK_HATASI)
    }

    /** Okumasi da patlayan store; okuma yolundaki raporlamayi kosturur. */
    private object OkumasiPatlayanStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException(DISK_HATASI) }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw IOException(DISK_HATASI)
    }

    private companion object {
        /** Bozuk JSON'un icindeki taninabilir metin; hata mesajinda GORUNMEMELI. */
        const val SIZINTI_KANITI = "SIZINTI-KANITI-PIN-4321"

        /** Kapanmamis nesne: kotlinx-serialization bunu cozemez ve girdiyi mesaja gomer. */
        const val BOZUK_JSON = "{\"id\":\"1\",\"name\":\"$SIZINTI_KANITI"

        /** Dogrulayicinin reddedecegi PIN; hatanin hicbir metninde GORUNMEMELI. */
        const val PIN_SIZINTI_KANITI = "SIZINTI-KANITI-PIN-9876"

        /**
         * Sozdizimi GECERLI, is kurali GECERSIZ bir JSON.
         *
         * Cozme sirasinda `require` duser; hata serilestirme tipinde degil
         * [IllegalArgumentException] olur ve mesaji PIN'i tasir.
         */
        const val GECERSIZ_PIN_JSON = "{\"pin\":\"$PIN_SIZINTI_KANITI\"}"

        /** [SamplePreferences] companion'indaki ayni sabitler; orada `private`. */
        const val KEY_PROFILE = "3f1c0b2e-0002-4000-8000-000000000002"
        const val KEY_COUNT = "3f1c0b2e-0001-4000-8000-000000000001"
        const val KEY_PIN = "3f1c0b2e-0007-4000-8000-000000000007"

        /** Yalnizca hata yolu testlerinde kullanilan disk hatasi mesaji. */
        const val DISK_HATASI = "disk okunamadi/yazilamadi"
    }
}
