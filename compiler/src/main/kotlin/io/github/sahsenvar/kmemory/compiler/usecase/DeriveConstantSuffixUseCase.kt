package io.github.sahsenvar.kmemory.compiler.usecase

import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel

/**
 * Uretilen anahtar sabitlerinin ortak ekini turetir (bkz. [PreferenceModel.keyProperty]).
 *
 * Ad ANAHTARIN METNINDEN DEGIL, grubun ILK bildirilen fonksiyonunun adindan turer. Sebep:
 * Zad'in tum anahtarlari UUID; metinden turetilen ad her sabiti
 * `KEY_NAME_8B1D2C44000140008000_1` yapiyor, uretilen kodu review etmeyi ve stacktrace okumayi
 * zorlastiriyordu. `readProfile` ile ayni anahtari paylasan grup artik `KEY_PROFILE` uretir.
 *
 * Degisen yalnizca TANIMLAYICI adidir; anahtarin kendisi `KEY_NAME_* = "<uuid>"` literalinde
 * oldugu gibi kalir. Sabit adlari derleme zamaninda inline edildigi icin APK'da gorunmez —
 * bu bir guvenlik degisikligi DEGILDIR.
 *
 * Kural:
 * 1. Grubun ilk fonksiyonunun adini al.
 * 2. Basindaki erisim onekini ([ACCESS_PREFIXES]) kelime sinirinda, buyuk/kucuk harf duyarsiz soy.
 * 3. Kalani UPPER_SNAKE_CASE'e cevir.
 * 4. Kalan bos ya da gecerli bir tanimlayici son eki degilse kirpilmis-hex'e dus.
 * 5. Iki grup ayni tabani uretirse CARPISAN TUM gruplara grup indeksi eklenir; carpismayanlar
 *    indekssiz kalir.
 */
internal class DeriveConstantSuffixUseCase {

    /**
     * @param groups anahtar -> o anahtari paylasan fonksiyonlar, BILDIRIM sirasinda.
     * @return her grup icin, girdiyle ayni sirada, birbirinden FARKLI son ekler.
     */
    operator fun invoke(groups: List<Pair<String, List<FunctionModel>>>): List<String> {
        val candidates = groups.mapIndexed { index, (key, functions) ->
            functions.firstOrNull()?.name?.let(::accessorSuffix) ?: fallbackSuffix(key, index)
        }

        return candidates.disambiguated()
    }

    /** Erisim oneki soyulmus, UPPER_SNAKE_CASE'e cevrilmis ad; turetilemezse `null`. */
    private fun accessorSuffix(functionName: String): String? =
        functionName.withoutAccessPrefix()
            .toUpperSnakeCase()
            .takeIf { it.isValidSuffix() }

    /**
     * Ad turetilemediginde kullanilan eski bicim: anahtarin ilk [HEX_LENGTH] alfanumerigi +
     * grup indeksi. Indeks burada ZORUNLUDUR: uzun ortak onekli anahtarlar
     * ("notification_settings_enabled" ile "notification_settings_muted") ayni kirpilmis one
     * inip companion'da "Conflicting declarations" uretir.
     */
    private fun fallbackSuffix(key: String, index: Int): String =
        key.filter { it.isLetterOrDigit() }.uppercase().take(HEX_LENGTH) + "_" + index

    /**
     * Onek yalnizca KELIME SINIRINDA soyulur: `readPinCode` -> `PinCode`, ama `reader` -> `reader`
     * (onek bir kelimenin bassa da parcasi olabilir).
     */
    private fun String.withoutAccessPrefix(): String {
        val prefix = ACCESS_PREFIXES.firstOrNull { prefix ->
            startsWith(prefix, ignoreCase = true) && isWordBoundaryAt(prefix.length)
        }

        return if (prefix == null) this else substring(prefix.length)
    }

    /**
     * Adin tamami onekten ibaretse (`read()`) sinir yine gecerlidir; geriye bos ad kalir ve
     * [accessorSuffix] kirpilmis-hex'e duser.
     */
    private fun String.isWordBoundaryAt(position: Int): Boolean {
        val character = getOrNull(position) ?: return position == length
        return character.isUpperCase() || character.isDigit() || character == '_'
    }

    /**
     * `PinCode` -> `PIN_CODE`, `HTTPTimeout` -> `HTTP_TIMEOUT`, `pin_code` -> `PIN_CODE`.
     *
     * Ardisik buyuk harf dizisi BOLUNMEZ (kisaltmalar); dizinin son harfi kucuk harfle
     * devam ediyorsa yeni kelime oradan baslar.
     */
    private fun String.toUpperSnakeCase(): String {
        val source = this
        return buildString {
            source.forEachIndexed { position, character ->
                // Komsular KAYNAKTAN okunur: `buildString` icinde `this` StringBuilder'dir ve
                // `getOrNull` sessizce YAZILANI okur — ilk deneme tam bu yuzden "PINCODE" uretti.
                val previous = source.getOrNull(position - 1)
                val next = source.getOrNull(position + 1)
                val startsNewWord = character.isUpperCase() &&
                    previous != null &&
                    previous != '_' &&
                    (!previous.isUpperCase() || next?.isLowerCase() == true)

                if (startsNewWord) append('_')
                append(character.uppercaseChar())
            }
        }
    }

    /** Uretilen ad `KEY_`/`KEY_NAME_` onune eklenecegi icin ASCII tanimlayici parcasi olmali. */
    private fun String.isValidSuffix(): Boolean =
        isNotEmpty() &&
            all { it in 'A'..'Z' || it in '0'..'9' || it == '_' } &&
            any { it.isLetterOrDigit() }

    /**
     * Ayni tabani ureten gruplari birbirinden ayirir.
     *
     * Indeks yalnizca CARPISAN gruplara eklenir: okunabilirlik, indeksi her ada ekleyerek degil
     * gerektiginde ekleyerek korunur. Son adim patolojik durumda (turetilen bir ad, baska bir
     * grubun indeksli adina esitse) TUM adlara indeks ekler; indeks konum basina tekil oldugu
     * icin bu adim tekilligi garanti eder ve dongu sonlanir.
     */
    private fun List<String>.disambiguated(): List<String> {
        var current = this
        repeat(MAX_DISAMBIGUATION_PASSES) {
            val duplicated = current.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (duplicated.isEmpty()) return current
            current = current.mapIndexed { index, suffix ->
                if (suffix in duplicated) "${suffix}_$index" else suffix
            }
        }

        return if (current.toSet().size == current.size) {
            current
        } else {
            current.mapIndexed { index, suffix -> "${suffix}_$index" }
        }
    }

    private companion object {

        /** Soyulan erisim onekleri; hicbiri digerinin oneki degildir, sira onemsizdir. */
        val ACCESS_PREFIXES = listOf("delete", "erase", "clear", "write", "read", "get", "set", "put")

        const val HEX_LENGTH = 20
        const val MAX_DISAMBIGUATION_PASSES = 2
    }
}
