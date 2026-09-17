package io.github.sahsenvar.kmemory.compiler.model

/**
 * Ayni anahtari paylasan fonksiyonlarin olusturdugu grup.
 *
 * Tip anahtar basina TEK'tir: `@Erase` imzasinda tip tasimadigi icin tipini ayni gruptaki
 * `@Read`/`@Write`'tan alir.
 *
 * @property index Grubun arayuz icindeki sirasi; yalnizca uretilen sabit adlarini
 *   CARPISMASIZ yapmak icin vardir (asagiya bakiniz).
 * @property key Anahtarin literal degeri; uretilen koda oldugu gibi gomulur.
 * @property type Gruptan cikarilan tip.
 * @property declaredType Gruptan cikarilan tipin tam hali; [PreferenceType.OBJECT] icin
 *   serilestirilecek tipi ve import'unu tasir. Tip hic cikarilamadiysa `null` kalir ve
 *   dogrulama bunu hataya cevirir.
 * @property functions Bu anahtari kullanan fonksiyonlar.
 */
internal data class PreferenceModel(
    val index: Int,
    val key: String,
    val type: PreferenceType,
    val declaredType: TypeRef?,
    val functions: List<FunctionModel>,
) {

    /**
     * Sabit adlarinin ortak eki.
     *
     * Anahtarin ilk [SUFFIX_LENGTH] alfanumerigi TEK BASINA yeterli degildir: uzun ortak
     * onekli anahtarlar ("notification_settings_enabled" ile "notification_settings_muted")
     * ayni eke inip companion'da "Conflicting declarations" uretiyordu. Grup indeksi eklenerek
     * ad carpismasi tanim geregi imkansiz hale getirildi; okunabilirlik icin anahtarin oneki
     * yine de korunur. Anahtarin KENDISI zaten `KEY_NAME_*` sabitine literal yaziliyor, yani
     * uretilen dosyada anahtarin tam degeri her zaman gorunur.
     */
    private val suffix: String = key.sanitizedPrefix() + "_" + index

    /** Uretilen companion'daki anahtar metninin sabit adi. */
    val keyNameProperty: String = "KEY_NAME_$suffix"

    /** Uretilen companion'daki `Preferences.Key<*>` degiskeninin adi. */
    val keyProperty: String = "KEY_$suffix"

    private companion object {

        private const val SUFFIX_LENGTH = 20

        /** Anahtari gecerli bir Kotlin tanimlayicisina cevirir (UUID'lerdeki tireler duser). */
        fun String.sanitizedPrefix(): String =
            filter { it.isLetterOrDigit() }.uppercase().take(SUFFIX_LENGTH)
    }
}
