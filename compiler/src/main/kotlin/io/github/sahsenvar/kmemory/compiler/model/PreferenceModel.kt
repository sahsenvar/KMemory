package io.github.sahsenvar.kmemory.compiler.model

/**
 * Ayni anahtari paylasan fonksiyonlarin olusturdugu grup.
 *
 * Tip anahtar basina TEK'tir: `@Erase` imzasinda tip tasimadigi icin tipini ayni gruptaki
 * `@Read`/`@Write`'tan alir.
 *
 * @property key Anahtarin literal degeri; uretilen koda oldugu gibi gomulur.
 * @property type Gruptan cikarilan tip.
 * @property objectTypeFqName [type] [PreferenceType.OBJECT] ise serilestirilecek tipin tam
 *   nitelikli adi; ilkel tiplerde `null`. Tip hic cikarilamadiysa da `null` kalir ve
 *   dogrulama bunu hataya cevirir.
 * @property functions Bu anahtari kullanan fonksiyonlar.
 */
internal data class PreferenceModel(
    val key: String,
    val type: PreferenceType,
    val objectTypeFqName: String?,
    val functions: List<FunctionModel>,
) {

    /** Uretilen companion'daki anahtar metninin sabit adi. */
    val keyNameProperty: String = "KEY_NAME_" + key.sanitizedSuffix()

    /** Uretilen companion'daki `Preferences.Key<*>` degiskeninin adi. */
    val keyProperty: String = "KEY_" + key.sanitizedSuffix()

    private companion object {

        private const val SUFFIX_LENGTH = 20

        /** Anahtari gecerli bir Kotlin tanimlayicisina cevirir (UUID'lerdeki tireler duser). */
        fun String.sanitizedSuffix(): String =
            filter { it.isLetterOrDigit() }.uppercase().take(SUFFIX_LENGTH)
    }
}
