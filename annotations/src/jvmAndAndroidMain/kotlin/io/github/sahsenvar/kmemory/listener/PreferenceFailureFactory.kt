package io.github.sahsenvar.kmemory.listener

/**
 * [error]'un dinleyiciye verilebilir karsiligi.
 *
 * [error]'dan YALNIZCA sinif adi ve yigin izi okunur; mesaji hicbir yere kopyalanmaz, `cause`
 * olarak da tutulmaz. Tip kontrolu YOKTUR ve olmamalidir: "hangi hata tehlikeli" sorusunu
 * tahmin etmeye calisan her kapi bir devir sonra yanlis cikti.
 *
 * ## Neden burada, tipin yaninda degil
 * Iki satiri da yalnizca JVM tasiyabilir: `error::class.java.name` sinifin JVM ikili adini
 * verir (ortak `KClass.simpleName` paketi dusurur, `qualifiedName` ic siniflarda JVM adindan
 * ayrisir ve Native'de guvenilmez), `Throwable.stackTrace` ise ortak stdlib'de YOKTUR ve
 * Native'de yazilamaz. [PreferenceFailure]'in KENDISI `commonMain`'dedir — bkz. o tipin
 * KDoc'undaki "Neden tip ortak, fabrika platforma ozel" bolumu.
 *
 * Bu bir uzanti oldugu icin cagri yeri `PreferenceFailure.from(...)` olarak KALIR, ama cagiran
 * dosyada `io.github.sahsenvar.kmemory.listener.from` import'u bulunmalidir; uretilen
 * kaynaklara bu import'u `GenerateImportsUseCase` kosulsuz ekler.
 *
 * Uretilen her `<Arayuz>Impl` bu fonksiyonu tek bir `report` noktasindan cagirir; elle
 * cagrilmasi beklenmez.
 */
public fun PreferenceFailure.Companion.from(
    store: String,
    key: String?,
    error: Throwable,
): PreferenceFailure {
    val failure = PreferenceFailure(
        store = store,
        key = key,
        originalType = error::class.java.name,
    )
    // Tani degerinin tamami burada: hangi cerceveler, hangi satirlar. Kurulum aninda dolan iz
    // (yani `report`'un kendisi) ise hicbir sey anlatmaz, o yuzden ezilir.
    failure.stackTrace = error.stackTrace
    return failure
}
