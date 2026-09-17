package io.github.sahsenvar.kmemory.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.sahsenvar.kmemory.listener.PreferenceListener
import io.github.sahsenvar.kmemory.listener.PreferenceSerializationException
import io.github.sahsenvar.kmemory.sample.model.SearchHistorySample
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
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

    private fun prefs(listener: PreferenceListener = PreferenceListener.None) =
        SamplePreferencesImpl(store(), Json, listener)

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

    private companion object {
        /** Bozuk JSON'un icindeki taninabilir metin; hata mesajinda GORUNMEMELI. */
        const val SIZINTI_KANITI = "SIZINTI-KANITI-PIN-4321"

        /** Kapanmamis nesne: kotlinx-serialization bunu cozemez ve girdiyi mesaja gomer. */
        const val BOZUK_JSON = "{\"id\":\"1\",\"name\":\"$SIZINTI_KANITI"

        /** [SamplePreferences] companion'indaki ayni sabit; orada `private`. */
        const val KEY_PROFILE = "3f1c0b2e-0002-4000-8000-000000000002"
    }
}
