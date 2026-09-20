package io.github.sahsenvar.kmemory.compiler.model

/**
 * Uretilen kaynaga gomulecek dizgeleri Kotlin string-literal kurallarina gore kacisla yazar.
 *
 * ## Neden gerekli
 *
 * KSP'nin `KSValueArgument.value`'su kaynaktaki LITERAL METNI degil, COZULMUS Kotlin `String`
 * degerini dondurur. Yani `@Read(key = "user\$name")` yazan bir kullanicida deger, gercek dolar
 * isareti tasiyan `user${'$'}name` olur. Bu deger kacissiz olarak
 *
 * ```
 * private const val RAW_X = "user${'$'}name"
 * ```
 *
 * seklinde uretilirse Kotlin `${'$'}name`'i bir string-template referansi sanar ve uretilen dosya
 * "unresolved reference" ile duser. Bir cift tirnak ise literal'i erkenden kapatip dosyanin
 * geri kalanini parse hatasina cevirir. Iki durumda da hata KULLANICININ arayuzunde degil,
 * URETILEN dosyada gorunur — yani teshisi en zor bicimde.
 *
 * Bu, kutuphanenin bilinen sert kisitlarindan biri DEGIL; kod-uretim tarafinda bir bosluktu.
 */
internal object KotlinStringLiteral {

    /** [value]'yu cift tirnak ICINE konabilecek bicimde kacisla. Tirnaklari EKLEMEZ. */
    fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when {
                character == '\\' -> append("\\\\")
                character == '"' -> append("\\\"")
                // Dolar isareti kacilmazsa string-template olarak yorumlanir.
                character == '$' -> append("\\$")
                character == '\n' -> append("\\n")
                character == '\r' -> append("\\r")
                character == '\t' -> append("\\t")
                // Geri kalan kontrol karakterleri literal'e ham konamaz.
                character.isControlCharacter() -> append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
    }

    /** [value]'yu kacislayip cift tirnak ICINE alir. */
    fun quoted(value: String): String = "\"${escape(value)}\""

    private fun Char.isControlCharacter(): Boolean = code < 0x20 || code == 0x7F
}
