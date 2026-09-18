package io.github.sahsenvar.kmemory.listener

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * ORTAK koddan yazilan bir dinleyicinin [PreferenceListener.onError]'a gelen degeri
 * okuyabildiginin kaniti.
 *
 * ## Neden bu test var
 * [PreferenceListener] `commonMain`'de, [PreferenceFailure] ise bir devir boyunca
 * `jvmAndAndroidMain`'deydi. Sonuc, sozlesmesi kendi kendini tutmayan bir yuzeydi: KDoc
 * "[onError]'a DAIMA bir `PreferenceFailure` gelir" diyordu ama ortak koddan arayuzu
 * implemente eden tuketici o tipi GOREMIYORDU — cast satiri "unresolved reference" ile
 * patliyordu. Bilgi vardi, erisim yoktu.
 *
 * ## Neden `commonTest`
 * Bu dosya `commonTest`'tedir, cunku asimetri yalnizca ORTAK derlemede gorunur. Ayni dosya
 * `jvmTest`'te olsaydi hicbir sey kanitlamazdi: JVM test derlemesinin siniflar yolunda
 * `jvmAndAndroidMain` zaten vardir, yani duzeltme yapilmadan da yesil olurdu. Kirmizi/yesil
 * farki yalnizca `jvmAndAndroid`'i GORMEYEN bir hedefte — ornegin
 * `compileTestKotlinIosSimulatorArm64` — olusur.
 *
 * Calisma-zamani (KMemory, uretilen kaynaklar, DataStore) hala JVM + Android'e sabittir;
 * burada kanitlanan sey calisma degil GORUNURLUKTUR: paylasilan kodda yazilan bir dinleyici
 * ortak modulde DERLENEBILMELIDIR.
 */
class CommonListenerSurfaceTest {

    @Test
    fun `ortak kodda yazilan dinleyici hatayi PreferenceFailure olarak okuyabilir`() {
        val dinleyici = OrtakDinleyici()
        val hata = PreferenceFailure(store = STORE, key = KEY, originalType = TIP)

        dinleyici.onError(STORE, KEY, hata)

        val alinan = dinleyici.sonHata
        assertSame(hata, alinan, "dinleyici hatayi tipiyle birlikte almali")
        assertEquals(STORE, alinan?.store)
        assertEquals(KEY, alinan?.key)
        assertEquals(TIP, alinan?.originalType)
    }

    /**
     * Tipin KENDISI degersizdir: ortak kaynak kumesine tasinmasi bu garantiyi degistirmemeli.
     *
     * Uc saldiri testiyle kazanilan sozlesme (`sample`'daki `PreferenceFailureTest`) orijinal
     * hatanin mesajinin, `cause`'unun ve `suppressed`'inin TASINMAMASIDIR. Buradaki kontrol
     * onun ortak taraftaki yarisidir: kurulan hatanin metni yalnizca store + key + tip
     * tasir, `cause` bostur.
     */
    @Test
    fun `kurulan hata store key ve tip disinda metin tasimaz`() {
        val hata = PreferenceFailure(store = STORE, key = KEY, originalType = TIP)

        assertEquals("$STORE/$KEY basarisiz: $TIP", hata.message)
        assertNull(hata.cause, "cause serbest metin tasir, tutulmamali")
    }

    /**
     * Tuketicinin paylasilan kodda yazacagi seyin birebir kendisi: arayuzu ortak kaynak
     * kumesinde implemente eder ve gelen degeri [PreferenceFailure]'a cast eder.
     *
     * Bu siniftaki cast satiri testin ASIL iddiasidir; assert'ler onun yaninda ikincildir.
     */
    private class OrtakDinleyici : PreferenceListener {

        var sonHata: PreferenceFailure? = null
            private set

        override fun onError(store: String, key: String?, error: Throwable) {
            sonHata = error as PreferenceFailure
        }
    }

    private companion object {
        const val STORE = "ornek_store"
        const val KEY = "3f1c0b2e-0003"
        const val TIP = "java.io.IOException"
    }
}
