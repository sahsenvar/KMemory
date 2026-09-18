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
 * Tasir: [store], [key], [originalType] ve — JVM/Android'de kuruldugunda — orijinalin
 * KOPYALANMIS yigin izi. Ucu de degerden bagimsizdir; bir sinif adi ve bir kare listesi tanim
 * geregi saklanan veriyi tasiyamaz.
 *
 * Tasimaz: orijinalin **mesaji**, **`cause`**'u ve **`suppressed`** zinciri. Ucu de serbest
 * metindir ve `stackTraceToString()` ile birlikte basilir — biri tutulsaydi sizinti ayni yoldan
 * geri gelirdi. Bu yuzden kurucu orijinal hatayi HIC almaz: yalnizca uc degersiz alan alir,
 * yani tipi yanlis kullanarak sizdirmak mumkun degildir.
 *
 * ## Cagiran etkilenmez
 * Uretilen `report` firlatilacak hatayi DONDURUR ve cagri sekli `throw report(...)`'dir:
 * `readX()` / `writeX()` cagirani ORIJINAL hatayi tipi ve mesajiyla almaya devam eder. Cagiran
 * zaten degere erisebilen koddur; orada gizlilik kaybi yoktur. Tani boylece bolunur ama
 * kaybolmaz — dinleyici tip + yigin izi alir, cagiran tam hatayi alir.
 *
 * ## Neden tip ortak, fabrika platforma ozel
 * Tip `commonMain`'dedir, [PreferenceListener] ile AYNI kaynak kumesinde. Onceden bu tip
 * `jvmAndAndroidMain`'deydi ve yuzey kendi kendini tutmuyordu: arayuz ortak, tip degil. Ortak
 * koddan dinleyiciyi implemente eden tuketici, KDoc'un "daima bir `PreferenceFailure` gelir"
 * sozune ragmen o tipe erisemiyordu.
 *
 * Tipte JVM'e ozel hicbir sey YOK: uc `String`/`String?` alan ve ortak stdlib'deki
 * `RuntimeException`. JVM'e ozel olan tek sey KURULUM — bir `Throwable`'in sinif adini
 * (`::class.java.name`) ve yigin karelerini (`Throwable.stackTrace`, yalnizca JVM'de
 * YAZILABILIR) okumak. O yuzden fabrika `jvmAndAndroidMain`'de bir companion uzantisidir:
 * `PreferenceFailure.Companion.from`.
 *
 * `expect/actual` SECILMEDI. Fabrikayi ortaga tasiyip iOS'a bir `actual` yazmak, yigin izini
 * kopyalayamayan (Native'de yazilamaz) ve sinif adini ancak `simpleName` ile verebilen sessizce
 * ZAYIF bir varyant uretirdi — hem de hicbir yerden cagrilmayan olu bir varyant: calisma-zamani
 * yuzeyi ([io.github.sahsenvar.kmemory.KMemory]) ve uretilen kod DataStore'a bagli oldugu icin
 * iOS'ta bir `PreferenceFailure` uretebilecek hicbir yol yok. Simdiki bolunme durumu oldugu gibi
 * anlatir: tipi her hedef GORUR, kuran yalnizca JVM/Android'dir.
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

    /**
     * BOS olmasi kasitlidir: `from` fabrikasi buraya `jvmAndAndroidMain`'den uzanti olarak
     * baglanir (bkz. sinif KDoc'unun son bolumu). Companion ortakta durmak zorundadir, yoksa
     * uzantinin baglanacagi alici olmaz ve uretilen kodun `PreferenceFailure.from(...)` cagri
     * sekli degisirdi.
     */
    public companion object
}
