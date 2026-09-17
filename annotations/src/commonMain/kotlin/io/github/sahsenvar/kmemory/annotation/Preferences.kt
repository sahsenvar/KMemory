package io.github.sahsenvar.kmemory.annotation

/**
 * Bir DataStore dosyasina karsilik gelen arayuzu isaretler.
 *
 * [name] dogrudan dosya adidir (ornegin `"9c1f....preferences_pb"`); islemci bu degeri
 * uretilen sinifin `STORE_NAME` sabitine literal olarak gomer.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class Preferences(val name: String)
