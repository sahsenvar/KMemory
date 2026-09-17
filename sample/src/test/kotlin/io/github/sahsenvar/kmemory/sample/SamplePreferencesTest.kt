package io.github.sahsenvar.kmemory.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.sahsenvar.kmemory.listener.PreferenceListener
import io.github.sahsenvar.kmemory.sample.model.SearchHistorySample
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
