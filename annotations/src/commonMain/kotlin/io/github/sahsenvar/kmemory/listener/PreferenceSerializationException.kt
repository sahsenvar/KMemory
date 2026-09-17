package io.github.sahsenvar.kmemory.listener

import kotlinx.serialization.SerializationException

/**
 * [PreferenceListener.onError]'a serilestirme hatasi yerine verilen, DEGER TASIMAYAN sarmalayici.
 *
 * Gerekcesi tasarim §8'deki sozlesmedir: *"dinleyici degerleri asla gormez"*. Ham hata
 * verilseydi bu sozlesme sessizce ihlal olurdu, cunku kotlinx-serialization bozuk girdiyi
 * hata MESAJINA gomer:
 *
 * ```text
 * Unexpected JSON token at offset 41: ... JSON input: {"pin":"1234","token":"ey..."}
 * ```
 *
 * Boyle bir hata bir Crashlytics dinleyicisine ulastiginda saklanan deger uygulama disina
 * cikar. Bu sinif yalnizca [store], [key] ve orijinal hatanin SINIF ADINI ([failureType])
 * tasir; ucu de dinleyicinin zaten gordugu ya da degerden bagimsiz bilgilerdir.
 *
 * ## Neden `cause` yok
 * Orijinal hata bilerek `cause` olarak SAKLANMAZ. `Throwable.stackTraceToString()` ve cogu
 * gunlukleme altyapisi cause zincirini de basar; cause tutulsaydi mesaj — dolayisiyla deger —
 * ayni yoldan yine disari cikardi. Tanilama icin kalan `failureType`, hatanin "bozuk JSON mu,
 * eksik serilestirici mi" ayrimini korur.
 *
 * ## Cagirana ne gider
 * Hicbir sey degismez: uretilen kod bu sarmalayiciyi YALNIZCA dinleyiciye verir, cagiran
 * tarafa ORIJINAL hatayi yeniden firlatir. Cagiran zaten degere erisebilen kod oldugu icin
 * orada gizlilik kaybi yoktur, ayrica tam tani bilgisi korunur.
 *
 * @property store hatanin olustugu DataStore dosya adi
 * @property key ilgili anahtar; `null` ise store olcegindeki bir islem (eraseAll)
 * @property failureType orijinal hatanin sinif adi, or. `"JsonDecodingException"`
 */
public class PreferenceSerializationException internal constructor(
    public val store: String,
    public val key: String?,
    public val failureType: String,
) : RuntimeException("$failureType: '$store' deposunda '$key' anahtarı serileştirilemedi") {

    public companion object {

        /**
         * Dinleyiciye verilebilecek hale getirilmis hata.
         *
         * Serilestirme kaynakli hatalar ([SerializationException] ve alt tipleri, ki
         * `JsonDecodingException` bunlardan biridir) mesajsiz bir
         * [PreferenceSerializationException]'a cevrilir; diger hatalar (disk G/C, bozuk
         * dosya, iptal) DEGERI TASIMADIKLARI icin oldugu gibi gecer — onlarin mesajini
         * silmek tanilamayi bedelsiz yere korlestirirdi.
         *
         * Uretilen her `<Arayuz>Impl` bu fonksiyonu cagirir; elle cagrilmasi beklenmez.
         */
        public fun reportable(store: String, key: String?, error: Throwable): Throwable =
            if (error is SerializationException) {
                PreferenceSerializationException(
                    store = store,
                    key = key,
                    failureType = error::class.simpleName ?: "SerializationException",
                )
            } else {
                error
            }
    }
}
