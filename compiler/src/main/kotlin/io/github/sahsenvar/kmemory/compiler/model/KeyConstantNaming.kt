package io.github.sahsenvar.kmemory.compiler.model

/**
 * Uretilen companion'daki sabit AD UZAYLARI.
 *
 * Her anahtar grubu tek bir ekten ([PreferenceModel.constantSuffix]) birden fazla tanimlayici
 * uretir: anahtarin metnini tutan `const` sabit ve ondan yapilan `Preferences.Key`. Tanimlayici
 * daima `<onek><ek>` bicimindedir.
 *
 * **Kural: hicbir onek digerinin oneki OLAMAZ.** Sebep aritmetiktir: `P2 == P1 + R` ise
 * `ek_1 == R + ek_2` olan her ek cifti icin `P1 + ek_1 == P2 + ek_2` olur — yani FARKLI iki grup
 * ayni tanimlayiciyi uretir ve companion "Conflicting declarations" ile duser. Eski `KEY_` +
 * `KEY_NAME_` cifti tam bu tuzaga dusuyordu: `readNameSurname` (`NAME_SURNAME`) ile `readSurname`
 * (`SURNAME`) ikilisinde `KEY_` + `NAME_SURNAME` ile `KEY_NAME_` + `SURNAME` ayni ada iniyordu.
 *
 * `KEY_` ve `RAW_` ilk karakterlerinden itibaren ayrisir; bu yuzden ekler ne olursa olsun iki uzay
 * arasinda carpisma YAPISAL OLARAK imkansizdir. Geriye yalnizca ayni uzay icinde ayni ekin iki kez
 * uretilmesi kalir, onu da [io.github.sahsenvar.kmemory.compiler.usecase.DeriveConstantSuffixUseCase]
 * cozer.
 *
 * Degisen yalnizca TANIMLAYICI adidir; anahtarin kendisi `RAW_* = "<uuid>"` literalinde oldugu gibi
 * kalir ve `const` sabitler derleme zamaninda inline edildigi icin APK'da gorunmez.
 */
internal object KeyConstantNaming {

    /** Tipli `Preferences.Key<*>` degiskeninin oneki. */
    const val KEY_PREFIX = "KEY_"

    /** Anahtarin METIN degerini tutan `const` sabitin oneki. */
    const val RAW_PREFIX = "RAW_"

    /** Tum ad uzaylari; ayriksa (bkz. sinif dokumani) uzaylar arasi carpisma imkansizdir. */
    val PREFIXES = listOf(KEY_PREFIX, RAW_PREFIX)

    /** Verilen ekin companion'a yazacagi TUM tanimlayicilar. */
    fun identifiers(suffix: String): List<String> = PREFIXES.map { prefix -> prefix + suffix }
}
