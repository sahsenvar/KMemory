package io.github.sahsenvar.kmemory.listener

/**
 * [PreferenceListener.onError]'a verilen TEK hata tipi — dinleyici YABANCI bir `Throwable` gormez.
 *
 * ## Neden konum kapisi yetmedi
 * Onceki devirde yalnizca `encode`/`decode` cagrilarinin ETRAFINDAN cikan hatalar sanitize
 * ediliyordu; DataStore'un kendi hatalari "deger tasimaz" varsayimiyla oldugu gibi gecirilirdi.
 * Varsayim YANLIS: `androidx.datastore.preferences.core.Preferences.toString()` store'un TUM
 * anahtar=deger ciftlerini basar. Mesajinda bir anlik goruntu tasiyan herhangi bir DataStore
 * istisnasi — dogrulama, bozulma isleyicisi, gocu yazan tuketici kodu — saklanan degeri
 * dinleyiciye, dolayisiyla Crashlytics'e tasiyordu:
 *
 * ```text
 * java.lang.IllegalStateException: tercih dogrulamasi basarisiz: { 3f1c...0003 = SONDA-SIR-1001 }
 * ```
 *
 * Yazma, silme (`eraseAll` dahil) ve okuma dallarinin hepsi aciktir. Sizintinin kaynagi hatanin
 * NEREDEN ciktigi degil, ona baglanmis serbest metnin dinleyiciye ULASMASIDIR. Bu yuzden kapi
 * konumdan cikarilip TEK bir merkezi raporlama noktasina tasindi: uretilen `report(key, error)`
 * her dalda bu tipi kurar ve dinleyiciye yalnizca onu verir.
 *
 * ## Ne tasir, ne tasimaz
 * Tasir: [store], [key], [originalType] ve orijinalin KOPYALANMIS yigin izi. Ucu de degerden
 * bagimsizdir; bir sinif adi ve bir kare listesi tanim geregi saklanan veriyi tasiyamaz.
 *
 * Tasimaz: orijinalin **mesaji**, **`cause`**'u ve **`suppressed`** zinciri. Ucu de serbest
 * metindir ve `stackTraceToString()` ile birlikte basilir — biri tutulsaydi sizinti ayni yoldan
 * geri gelirdi.
 *
 * ## Cagiran etkilenmez
 * `report` firlatilacak hatayi DONDURUR ve cagri sekli `throw report(...)`'dir: `readX()` /
 * `writeX()` cagirani ORIJINAL hatayi tipi ve mesajiyla almaya devam eder. Cagiran zaten degere
 * erisebilen koddur; orada gizlilik kaybi yoktur. Tani boylece bolunur ama kaybolmaz —
 * dinleyici tip + yigin izi alir, cagiran tam hatayi alir.
 *
 * ## Neden `commonMain` degil
 * Yigin izini kopyalamak icin `Throwable.stackTrace` gerekir ve bu ozellik ortak stdlib'de
 * yoktur. Calisma-zamani yuzeyi zaten JVM + Android'e sabitlenmis durumda
 * ([io.github.sahsenvar.kmemory.KMemory] ayni kaynak kumesinde), uretilen kod da DataStore'a
 * bagli oldugu icin baska bir hedefte derlenmez. Anotasyonlar ve [PreferenceListener]
 * `commonMain`'de kalir.
 *
 * @property store hatanin olustugu DataStore dosya adi
 * @property key ilgili anahtar; `null` ise store olcegindeki bir islem (eraseAll)
 * @property originalType orijinal hatanin sinif adi, or. `"java.io.IOException"`
 */
public class PreferenceFailure(
    public val store: String,
    public val key: String?,
    public val originalType: String,
) : RuntimeException("$store/$key basarisiz: $originalType") {

    public companion object {

        /**
         * [error]'un dinleyiciye verilebilir karsiligi.
         *
         * [error]'dan YALNIZCA sinif adi ve yigin izi okunur; mesaji hicbir yere kopyalanmaz,
         * `cause` olarak da tutulmaz. Tip kontrolu YOKTUR ve olmamalidir: "hangi hata
         * tehlikeli" sorusunu tahmin etmeye calisan her kapi bir devir sonra yanlis cikti.
         *
         * Uretilen her `<Arayuz>Impl` bu fonksiyonu tek bir `report` noktasindan cagirir;
         * elle cagrilmasi beklenmez.
         */
        public fun from(store: String, key: String?, error: Throwable): PreferenceFailure {
            val failure = PreferenceFailure(
                store = store,
                key = key,
                originalType = error::class.java.name,
            )
            // Tani degerinin tamami burada: hangi cerceveler, hangi satirlar. Kurulum aninda
            // dolan iz (yani `report`'un kendisi) ise hicbir sey anlatmaz, o yuzden ezilir.
            failure.stackTrace = error.stackTrace
            return failure
        }
    }
}
