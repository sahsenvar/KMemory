package io.github.sahsenvar.kmemory.listener

/**
 * Uretilen kaynaklarin yazma/silme/hata kancalari.
 *
 * Dinleyici degerleri ASLA gormez; yalnizca store adi, anahtar ve hatanin TIPI verilir.
 * Aksi halde bir hata ayiklama gunlukcusu PIN'i ya da oturum token'ini logcat'e dusururdu.
 *
 * Hatalar yutulmaz: uretilen kod once [onError] ile raporlar, sonra yeniden firlatir.
 */
public interface PreferenceListener {

    /** [store] dosyasindaki [key] anahtarina yazildi. */
    public fun onWrite(store: String, key: String) {}

    /** [key] silindi; [key] `null` ise store'un tamami silindi (eraseAll). */
    public fun onErase(store: String, key: String?) {}

    /**
     * [key] uzerinde hata olustu; [key] `null` ise store olcegindeki bir islemde.
     *
     * [error] DAIMA bir `PreferenceFailure`'dir — uretilen kod yabanci bir `Throwable`'i
     * dinleyiciye hicbir dalda gecirmez. Orijinal hatadan yalnizca sinif adi ve yigin izi
     * tasinir; mesaji, `cause`'u ve `suppressed`'i tasinmaz, cunku ucu de saklanan degeri
     * icerebilir ve `stackTraceToString()` hepsini basar. Cagirana firlatilan hata
     * DEGISMEZ: `readX()` / `writeX()` cagirani orijinali tipi ve mesajiyla alir.
     *
     * Parametre tipi yine de `Throwable`: `PreferenceFailure` calisma-zamani yuzeyiyle
     * birlikte JVM + Android kaynak kumesinde durur, bu arayuz ise `commonMain`'dedir.
     */
    public fun onError(store: String, key: String?, error: Throwable) {}

    /** Hicbir sey yapmayan varsayilan. */
    public object None : PreferenceListener
}
