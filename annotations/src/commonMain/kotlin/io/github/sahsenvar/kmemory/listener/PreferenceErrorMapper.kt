package io.github.sahsenvar.kmemory.listener

import kotlin.coroutines.cancellation.CancellationException

/**
 * Uretilen kaynaklardan cikan ham hatayi, tuketicinin KENDI hata tipine cevirir.
 *
 * Ktor'un `HttpResponseValidator { handleResponseExceptionWithRequest { ... } }` kancasinin
 * karsiligi. Kutuphane hatayi SINIFLANDIRMAZ ve kendi hata hiyerarsisini dayatmaz; yalnizca
 * cevirme NOKTASINI verir. Hangi `IOException`in "disk dolu", hangisinin "bozuk dosya"
 * sayilacagi uygulamanin bilgisidir, kutuphanenin degil.
 *
 * ```
 * val memory = kmemory {
 *     storeFactory = { name -> ... }
 *     errorMapper = PreferenceErrorMapper { store, key, error ->
 *         error.toPreferenceError(PreferenceOperation(store, key))
 *     }
 * }
 * ```
 *
 * ## [PreferenceListener] ile farki
 *
 * Ikisi AYRI yollardir ve ayri kalmalari bilinclidir:
 *
 * | | ne gorur | nereye gider |
 * |---|---|---|
 * | [PreferenceListener.onError] | yalnizca [PreferenceFailure] — tip adi + yigin izi | raporlama (gunlukleme, crash reporter) |
 * | [PreferenceErrorMapper.map] | ORIJINAL [Throwable] | cagirana firlatilan hata |
 *
 * Dinleyicinin muhurlu olmasinin sebebi kayitli: `Preferences.toString()` store'un TUM
 * anahtar=deger ciftlerini basar, yani mesajinda bir anlik goruntu tasiyan herhangi bir
 * DataStore istisnasi saklanan degeri gunluge dusururdu. Bu arayuz o muhrü DELMEZ, cunku
 * urettigi hata gunluge degil CAGIRANA gider ve cagiran zaten bugun de orijinali aliyor.
 *
 * ⚠️ Donen hatanin orijinali [Throwable.cause] olarak tutup tutmayacagi TUKETICININ karari.
 * Tutmak taniyi zenginlestirir ama o hatayi mesajiyla loglayan her yol yukaridaki sizintiyi
 * geri acar. Karar bilincli verilmeli.
 *
 * ## Iptal cevrilmez
 *
 * [CancellationException] bu arayuze HIC ulasmaz: uretilen `report` onu cagirmadan once eler.
 * Iptal bir hata degildir ve baska bir tipe cevrilmesi corutin iptal zincirini sessizce kirardi.
 *
 * ## Neden `commonMain`
 *
 * [PreferenceFailure] ile ayni gerekce: sozlesmenin iki ucu ayni kaynak kumesinde olmazsa ortak
 * koddan yazilan bir tuketici onu okuyamaz.
 */
public fun interface PreferenceErrorMapper {

    /**
     * [store] dosyasindaki [key] uzerinde olusan [error]'un cagirana gidecek karsiligi.
     *
     * [key] `null` ise islem store olceginde (ornegin `eraseAll`).
     *
     * [store] ve [key] tani icin guvenlidir: ikisi de derleme zamaninda sabitlenmis
     * tanimlayicilardir, saklanan deger icermezler.
     */
    public fun map(store: String, key: String?, error: Throwable): Throwable

    public companion object {

        /**
         * Varsayilan: hicbir sey cevirme, orijinali dondur.
         *
         * Kanca OPSIYONELDIR. Bu varsayilan sayesinde kancayi vermeyen her kurulum -- yani
         * 0.2.0'a kadarki her tuketici -- aynen calismaya devam eder.
         */
        public val Passthrough: PreferenceErrorMapper =
            PreferenceErrorMapper { _, _, error -> error }
    }
}
