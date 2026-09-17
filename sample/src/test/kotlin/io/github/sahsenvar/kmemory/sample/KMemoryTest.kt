package io.github.sahsenvar.kmemory.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.sahsenvar.kmemory.KMemory
import io.github.sahsenvar.kmemory.kmemory
import io.github.sahsenvar.kmemory.listener.PreferenceListener
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [KMemory] kurulum yuzeyinin ve URETILEN FABRIKA UZANTISININ testleri (spec §6.1).
 *
 * Bu dosya, uretilen sinifi elle kurarak degil `kmemory { }` + `kmemory.samplePreferences()`
 * yolundan gecerek calisir — cunku tuketiciye acilan yuzey budur. Sinifi dogrudan kuran yol
 * [SamplePreferencesTest]'teki hata yolu testlerinde korunuyor.
 *
 * Testler `annotations` modulunun degil `sample` modulunun test kaynaginda: dogrulama komutu
 * `:sample:test` + `:compiler:test`, ve uretilen uzanti yalnizca burada mevcut.
 */
class KMemoryTest {

    // --- store onbellegi: ad basina TEK DataStore (spec §6.1, KMemory.store KDoc) ------------

    /**
     * DataStore ayni dosya icin ikinci bir ornek kuruldugunda CALISMA ANINDA patlar
     * ("There are multiple DataStores active for the same file"). Onbellek bu yuzden konfor
     * degil zorunluluktur; sozlesme bu testle sabitleniyor.
     */
    @Test
    fun `ayni ad icin ayni DataStore ornegi doner`() {
        val istenen = mutableListOf<String>()
        val memory = memory(record = istenen)

        val ilk = memory.store(A_STORE)
        val ikinci = memory.store(A_STORE)

        assertSame(ilk, ikinci, "ayni ad icin ikinci bir DataStore ornegi uretilmemeli")
        assertEquals(listOf(A_STORE), istenen, "storeFactory ad basina tam bir kez cagrilmali")
    }

    @Test
    fun `farkli ad icin ayri DataStore ornegi doner`() {
        val istenen = mutableListOf<String>()
        val memory = memory(record = istenen)

        assertNotSame(memory.store(A_STORE), memory.store(B_STORE))
        assertEquals(listOf(A_STORE, B_STORE), istenen)
    }

    // --- uretilen fabrika uzantisi -----------------------------------------------------------

    /** Uzanti, arayuzun `@Preferences(name = ...)` adini store'a gecirmeli — baskasini degil. */
    @Test
    fun `fabrika uzantisi arayuzun bildirdigi store adini kullanir`() {
        val istenen = mutableListOf<String>()
        val memory = memory(record = istenen)

        memory.samplePreferences()
        memory.flowSamplePreferences()

        assertEquals(listOf("sample.preferences_pb", "flow_sample.preferences_pb"), istenen)
    }

    @Test
    fun `fabrika uzantisiyla kurulan kaynak yazip okur`() = runTest {
        val kaynak = memory().samplePreferences()

        kaynak.writeCount(7)

        assertEquals(7, kaynak.readCount().first())
    }

    @Test
    fun `Flow sekilli arayuzun fabrika uzantisi da calisir`() = runTest {
        val kaynak = memory().flowSamplePreferences()

        kaynak.writeCount(7).first()

        assertEquals(7, kaynak.readCount().first())
    }

    /**
     * Onbellegin tuketiciye gorunen sonucu: ayni [KMemory]'den iki kez uretilen kaynak AYNI
     * dosyayi gorur. Onbellek olmasaydi ikinci uretim ya ikinci bir DataStore kurup calisma
     * aninda patlardi ya da sessizce ayri bir duruma yazardi.
     */
    @Test
    fun `ayni KMemory'den iki kez uretilen kaynak ayni store'u paylasir`() = runTest {
        val memory = memory()
        val ilk = memory.samplePreferences()
        val ikinci = memory.samplePreferences()

        ilk.writeCount(7)

        assertEquals(7, ikinci.readCount().first())
    }

    @Test
    fun `fabrika uzantisi yapilandirilan dinleyiciyi baglar`() = runTest {
        val gorulen = mutableListOf<String>()
        val listener = object : PreferenceListener {
            override fun onWrite(store: String, key: String) { gorulen += "w:$store" }
        }

        memory(listener = listener).samplePreferences().writeCount(1)

        assertEquals(listOf("w:sample.preferences_pb"), gorulen)
    }

    /**
     * Uzanti `json`'i da gecirmeli. Gozlemlenebilir kilmak icin `prettyPrint` acik bir [Json]
     * veriliyor: varsayilan ornek kullanilsaydi diskteki metin tek satir olurdu.
     */
    @Test
    fun `fabrika uzantisi yapilandirilan Json'i baglar`() = runTest {
        val dizin = createTempDirectory("kmemory-json").toFile()
        val memory = kmemory {
            storeFactory = { name -> dosyaStore(dizin, name) }
            json = Json { prettyPrint = true }
        }

        memory.samplePreferences().writeProfile(ProfileSample(id = "1", name = "Sahan"))

        val ham = memory.store("sample.preferences_pb").data.first()[stringPreferencesKey(KEY_PROFILE)]
        assertTrue(
            ham.orEmpty().contains("\n"),
            "varsayilan Json kullanilmis: uzanti yapilandirilan ornegi gecirmiyor -> $ham",
        )
    }

    // --- kurulum dogrulamasi -----------------------------------------------------------------

    /**
     * `storeFactory` ZORUNLU. Kontrol olmadan hata `lateinit` erisiminden gelirdi: tipi
     * `UninitializedPropertyAccessException`, mesaji ise nasil duzeltilecegini soylemeyen
     * "lateinit property storeFactory has not been initialized".
     */
    @Test
    fun `storeFactory verilmeden kurulum net mesajla reddedilir`() {
        val hata = assertFailsWith<IllegalStateException> {
            kmemory { listener = PreferenceListener.None }
        }

        val mesaj = hata.message.orEmpty()
        assertTrue(mesaj.contains("storeFactory"), "mesaj eksik olani adlandirmali -> $mesaj")
        assertTrue(mesaj.contains("kmemory {"), "mesaj nasil verilecegini gostermeli -> $mesaj")
    }

    @Test
    fun `storeFactory verilince kurulum gecer`() {
        val dizin = createTempDirectory("kmemory-ok").toFile()

        val memory = kmemory { storeFactory = { name -> dosyaStore(dizin, name) } }

        assertSame(memory.store(A_STORE), memory.store(A_STORE))
    }

    // --- yardimcilar -------------------------------------------------------------------------

    /** Her cagri AYRI bir gecici dizin kullanir; testler dosya duzeyinde yalitilir. */
    private fun memory(
        listener: PreferenceListener = PreferenceListener.None,
        record: MutableList<String> = mutableListOf(),
    ): KMemory {
        val dizin = createTempDirectory("kmemory").toFile()
        return kmemory {
            storeFactory = { name ->
                record += name
                dosyaStore(dizin, name)
            }
            this.listener = listener
        }
    }

    private fun dosyaStore(dizin: File, name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath { dizin.resolve(name).toOkioPath() }

    private companion object {
        const val A_STORE = "a.preferences_pb"
        const val B_STORE = "b.preferences_pb"

        /** [SamplePreferences] companion'indaki ayni sabit; orada `private`. */
        const val KEY_PROFILE = "3f1c0b2e-0002-4000-8000-000000000002"
    }
}
