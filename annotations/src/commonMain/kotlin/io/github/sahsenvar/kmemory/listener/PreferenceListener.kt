package io.github.sahsenvar.kmemory.listener

/**
 * Uretilen kaynaklarin yazma/silme/hata kancalari.
 *
 * Dinleyici degerleri ASLA gormez; yalnizca store adi ve anahtar verilir.
 * Aksi halde bir hata ayiklama gunlukcusu PIN'i ya da oturum token'ini logcat'e dusururdu.
 *
 * Hatalar yutulmaz: uretilen kod once [onError] ile raporlar, sonra yeniden firlatir.
 */
public interface PreferenceListener {

    /** [store] dosyasindaki [key] anahtarina yazildi. */
    public fun onWrite(store: String, key: String) {}

    /** [key] silindi; [key] `null` ise store'un tamami silindi (eraseAll). */
    public fun onErase(store: String, key: String?) {}

    /** [key] uzerinde hata olustu; [key] `null` ise store olcegindeki bir islemde. */
    public fun onError(store: String, key: String?, error: Throwable) {}

    /** Hicbir sey yapmayan varsayilan. */
    public object None : PreferenceListener
}
