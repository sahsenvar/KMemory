package io.github.sahsenvar.kmemory.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.sahsenvar.kmemory.kmemory
import io.github.sahsenvar.kmemory.listener.PreferenceListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `@Write`/`@Erase`/`@EraseAll`'un `Flow<Unit>` seklinin davranis testleri (spec §4.0).
 *
 * [SamplePreferencesTest] `suspend` seklini dogruluyor; burasi Flow seklini dogruluyor. Iki
 * testin birlikte gecmesi, iki seklin de YERLESIK oldugunun kanitidir.
 */
class FlowSamplePreferencesTest {

    /**
     * Kaynak URETILEN FABRIKA UZANTISINDAN kurulur — spec §6.1'in tuketiciye acilan TEK
     * yuzeyi budur; boylece her test ayni zamanda `fun KMemory.flowSamplePreferences()`
     * uzantisini da kosturur. Her cagri AYRI bir gecici dizin kullanir: ayni dosya icin
     * ikinci bir DataStore ornegi calisma aninda patlar.
     *
     * Belirli bir store ornegi gereken tek yer asagidaki disk hatasi testidir; orasi sinifi
     * bilerek elle kurar ve dogrudan constructor yolunu da calisir tutar.
     */
    private fun prefs(listener: PreferenceListener = PreferenceListener.None): FlowSamplePreferences {
        val dizin = createTempDirectory("kmemory-flow").toFile()
        return kmemory {
            storeFactory = { name ->
                PreferenceDataStoreFactory.createWithPath { dizin.resolve(name).toOkioPath() }
            }
            this.listener = listener
        }.flowSamplePreferences()
    }

    // --- yazma / silme is yapiyor mu -------------------------------------------------------

    @Test
    fun `collect edilen Flow yazma diske yazar`() = runTest {
        val p = prefs()
        p.writeCount(42).collect()
        assertEquals(42, p.readCount().first())
    }

    @Test
    fun `collect edilen Flow yazma tek bir Unit yayar`() = runTest {
        // Cagiran `.first()` ile de `.collect()` ile de ayni sonucu almali; birden fazla
        // yayin, `flow { … }` govdesinin birden fazla kez kosmasi anlamina gelirdi.
        assertEquals(listOf(Unit), prefs().writeCount(1).toList())
    }

    @Test
    fun `collect edilen Flow erase yalnizca kendi anahtarini siler`() = runTest {
        val p = prefs()
        p.writeCount(42).collect()
        p.writeLabel("kalsin").collect()
        p.eraseCount().collect()
        assertNull(p.readCount().first())
        assertEquals("kalsin", p.readLabel().first())
    }

    @Test
    fun `collect edilen Flow eraseAll her seyi siler`() = runTest {
        val p = prefs()
        p.writeCount(42).collect()
        p.writeLabel("gitsin").collect()
        p.eraseAll().collect()
        assertNull(p.readCount().first())
        assertNull(p.readLabel().first())
    }

    @Test
    fun `Flow ile serializable nesne yazilip okunur`() = runTest {
        val p = prefs()
        val profile = ProfileSample(id = "1", name = "Sahan")
        p.writeProfile(profile).collect()
        assertEquals(profile, p.readProfile().first())
    }

    @Test
    fun `Flow ile nullable yazma null verilince anahtari siler`() = runTest {
        val p = prefs()
        p.writeToken("abc").collect()
        assertEquals("abc", p.readToken().first())
        p.writeToken(null).collect()
        assertNull(p.readToken().first())
    }

    // --- sogukluk: collect EDILMEZSE hicbir sey olmaz --------------------------------------

    @Test
    fun `collect edilmeyen Flow yazma hicbir sey yazmaz`() = runTest {
        val seen = mutableListOf<String>()
        val p = prefs(listener = recording(seen))

        // Donen Flow bilerek collect EDILMIYOR. Bu davranis spec §4.0'da bilincli bir karar:
        // yazma soguktur, collect edilmeyen yazma hic olmaz. Testle sabitleniyor ki ileride
        // "kolaylik olsun" diye sicak hale getirilmesin.
        p.writeCount(42)

        assertNull(p.readCount().first())
        assertTrue(seen.isEmpty(), "collect edilmeyen yazma dinleyiciyi de tetiklememeli: $seen")
    }

    @Test
    fun `collect edilmeyen Flow erase hicbir sey silmez`() = runTest {
        val seen = mutableListOf<String>()
        val p = prefs(listener = recording(seen))
        p.writeCount(42).collect()
        seen.clear()

        p.eraseCount()

        assertEquals(42, p.readCount().first())
        assertTrue(seen.isEmpty(), "collect edilmeyen silme dinleyiciyi de tetiklememeli: $seen")
    }

    @Test
    fun `collect edilmeyen Flow eraseAll hicbir sey silmez`() = runTest {
        val seen = mutableListOf<String>()
        val p = prefs(listener = recording(seen))
        p.writeCount(42).collect()
        seen.clear()

        p.eraseAll()

        assertEquals(42, p.readCount().first())
        assertTrue(seen.isEmpty(), "collect edilmeyen silme dinleyiciyi de tetiklememeli: $seen")
    }

    // --- dinleyici sozlesmesi --------------------------------------------------------------

    @Test
    fun `Flow sekli dinleyiciye yazma ve silmeyi bildirir`() = runTest {
        val seen = mutableListOf<String>()
        val p = prefs(listener = recording(seen))

        p.writeCount(42).collect()
        p.eraseCount().collect()
        p.eraseAll().collect()

        assertEquals(3, seen.size, seen.toString())
        assertTrue(seen[0].startsWith("w:7a2d1c3f-0001"), seen[0])
        assertTrue(seen[1].startsWith("e:7a2d1c3f-0001"), seen[1])
        assertEquals("e:null", seen[2])
    }

    @Test
    fun `Flow yazmasi patlarsa dinleyici bilgilendirilir ve hata cagirana gider`() = runTest {
        val reported = mutableListOf<Pair<String?, Throwable>>()
        val listener = object : PreferenceListener {
            override fun onError(store: String, key: String?, error: Throwable) {
                reported += key to error
            }
        }
        val p = FlowSamplePreferencesImpl(FailingDataStore, Json, listener)

        // Toplanmayan akis hicbir sey RAPORLAMAZ; hata da olusmaz.
        p.writeCount(42)
        assertTrue(reported.isEmpty(), "collect edilmeden hata olusmamali: $reported")

        val firlatilan = assertFailsWith<IOException> { p.writeCount(42).collect() }

        assertEquals(DISK_HATASI, firlatilan.message, "cagirana orijinal hata gitmeli")
        assertEquals(1, reported.size, reported.toString())
        assertTrue(reported.single().first?.startsWith("7a2d1c3f-0001") == true, reported.toString())
    }

    private fun recording(seen: MutableList<String>) = object : PreferenceListener {
        override fun onWrite(store: String, key: String) { seen += "w:$key" }
        override fun onErase(store: String, key: String?) { seen += "e:$key" }
    }

    /** Okumasi calisan ama her yazmada patlayan store; disk hatasi yolunu deterministik kilar. */
    private object FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw IOException(DISK_HATASI)
    }

    private companion object {
        const val DISK_HATASI = "disk yazilamadi"
    }
}
