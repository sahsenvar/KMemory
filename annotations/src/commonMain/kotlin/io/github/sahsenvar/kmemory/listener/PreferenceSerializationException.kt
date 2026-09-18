package io.github.sahsenvar.kmemory.listener

/**
 * [PreferenceListener.onError]'a serilestirme hatasi yerine verilen, DEGER TASIMAYAN sarmalayici.
 *
 * Gerekcesi tasarim §8'deki sozlesmedir: *"dinleyici degerleri asla gormez"*. Ham hata
 * verilseydi bu sozlesme sessizce ihlal olurdu, cunku serilestirme sinirindan cikan hatalar
 * bozuk ya da gecersiz girdiyi hata MESAJINA gomer:
 *
 * ```text
 * Unexpected JSON token at offset 41: ... JSON input: {"pin":"1234","token":"ey..."}
 * java.lang.IllegalArgumentException: gecersiz pin: 1234
 * ```
 *
 * Boyle bir hata bir Crashlytics dinleyicisine ulastiginda saklanan deger uygulama disina
 * cikar. Bu sinif yalnizca [store], [key] ve orijinal hatanin SINIF ADINI ([failureType])
 * tasir; ucu de dinleyicinin zaten gordugu ya da degerden bagimsiz bilgilerdir.
 *
 * ## Kapi TIPE degil KONUMA bakar
 * Ilk devirde kapi `error is SerializationException` idi. Bu YANLISTI: Kotlin'in en yaygin
 * dogrulama deyimi
 *
 * ```kotlin
 * @Serializable data class Pin(val value: String) { init { require(value.length > 40) { "gecersiz pin: $value" } } }
 * ```
 *
 * cozme sirasinda `IllegalArgumentException` firlatir ve kotlinx-serialization bunu
 * SARMALAMAZ — yani tip kapisindan gecer ve saklanan PIN oldugu gibi dinleyiciye gider.
 * Hatanin tipi guvenilir bir olcut degildir; guvenilir olan ciktigi KONUMDUR. Uretilen kod
 * bu yuzden `encode`/`decode` cagrisinin ETRAFINI sarar: o sinirdan cikan HER `Throwable`,
 * tipi ne olursa olsun, bu sarmalayiciya cevrilir.
 *
 * DataStore G/C hatalari (`IOException` vb.) bu sinirin DISINDA olustugu icin sanitize
 * EDILMEZ; deger tasimazlar ve tani icin gereklidirler.
 *
 * ## Neden `cause` yok
 * Orijinal hata bilerek `cause` olarak SAKLANMAZ. `Throwable.stackTraceToString()` ve cogu
 * gunlukleme altyapisi cause zincirini de basar; cause tutulsaydi mesaj — dolayisiyla deger —
 * ayni yoldan yine disari cikardi. Tanilama icin kalan [failureType], hatanin "bozuk JSON mu,
 * dogrulama mi, eksik serilestirici mi" ayrimini korur; bir sinif adi tanim geregi deger
 * tasiyamaz.
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
         * Serilestirme sinirindan cikan [error]'un dinleyiciye verilebilir karsiligi.
         *
         * [error]'dan YALNIZCA sinif adi okunur; mesaji hicbir yere kopyalanmaz ve `cause`
         * olarak da tutulmaz. Cagrilma yeri tek basina "bu hata serilestirme sinirindan
         * cikti" bilgisidir — bu yuzden tip kontrolu YOKTUR, olsaydi dogrulayici `init`
         * hatalari kapidan gecerdi.
         *
         * Uretilen her `<Arayuz>Impl` bu fonksiyonu cagirir; elle cagrilmasi beklenmez.
         */
        public fun atBoundary(store: String, key: String?, error: Throwable): PreferenceSerializationException =
            PreferenceSerializationException(
                store = store,
                key = key,
                failureType = error::class.simpleName ?: UNKNOWN_FAILURE_TYPE,
            )

        /** Sinif adi cozulemeyen (anonim) hatalar icin yer tutucu. */
        private const val UNKNOWN_FAILURE_TYPE = "Throwable"
    }
}
