package io.github.sahsenvar.kmemory.compiler.usecase

import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.KeyConstantNaming
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel

/**
 * Uretilen anahtar sabitlerinin ortak ekini turetir (bkz. [PreferenceModel.keyProperty]).
 *
 * Ad ANAHTARIN METNINDEN DEGIL, grubun ILK bildirilen fonksiyonunun adindan turer. Sebep:
 * Zad'in tum anahtarlari UUID; metinden turetilen ad her sabiti
 * `RAW_8B1D2C44000140008000_1` yapiyor, uretilen kodu review etmeyi ve stacktrace okumayi
 * zorlastiriyordu. `readProfile` ile ayni anahtari paylasan grup artik `KEY_PROFILE` uretir.
 *
 * Degisen yalnizca TANIMLAYICI adidir; anahtarin kendisi `RAW_* = "<uuid>"` literalinde
 * oldugu gibi kalir. Sabit adlari derleme zamaninda inline edildigi icin APK'da gorunmez —
 * bu bir guvenlik degisikligi DEGILDIR.
 *
 * Kural:
 * 1. Grubun ilk fonksiyonunun adini al.
 * 2. Basindaki erisim onekini ([ACCESS_PREFIXES]) kelime sinirinda, buyuk/kucuk harf duyarsiz soy;
 *    soyulduktan sonra bastaki ayirici karakterleri de kirp (`read_pin` -> `PIN`, `_PIN` degil).
 * 3. Kalani UPPER_SNAKE_CASE'e cevir.
 * 4. Kalan bos ya da gecerli bir tanimlayici son eki degilse kirpilmis-hex'e dus.
 * 5. Ayni TANIMLAYICIYI ureten CARPISAN TUM gruplara grup indeksi eklenir; carpismayanlar
 *    indekssiz kalir. Tekillik ekler uzerinden degil, [KeyConstantNaming] uzaylarinin uretecegi
 *    son adlar uzerinden olculur.
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
     *
     * Soyma ayirici karakteri geride birakir (`read_pin` -> `_pin`); o da kirpilir, yoksa
     * tanimlayici `KEY__PIN` gibi cift alt cizgili cikardi. Kirpma yalnizca BASTAN yapilir:
     * ortadaki alt cizgiler kelime sinirlaridir.
     */
    private fun String.withoutAccessPrefix(): String {
        val prefix = ACCESS_PREFIXES.firstOrNull { prefix ->
            startsWith(prefix, ignoreCase = true) && isWordBoundaryAt(prefix.length)
        }

        return if (prefix == null) this else substring(prefix.length).trimStart('_')
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

    /** Uretilen ad [KeyConstantNaming] oneklerinin ardina eklenecegi icin ASCII parcasi olmali. */
    private fun String.isValidSuffix(): Boolean =
        isNotEmpty() &&
            all { it in 'A'..'Z' || it in '0'..'9' || it == '_' } &&
            any { it.isLetterOrDigit() }

    /**
     * Ayni TANIMLAYICIYI ureten gruplari birbirinden ayirir.
     *
     * Olcum EKLER uzerinden degil, companion'a yazilacak son adlar uzerinden yapilir: bir ek
     * birden fazla ad uzayina ad uretir ve uzaylar ayrik degilse FARKLI iki ek ayni tanimlayiciya
     * cokebilir. [KeyConstantNaming] uzaylari bugun ayrik tuttugu icin bu katman kemer+aski
     * gorevi gorur; ileride ucuncu bir uzay eklenirse koruma buradadir.
     *
     * Indeks yalnizca CARPISAN gruplara eklenir: okunabilirlik, indeksi her ada ekleyerek degil
     * gerektiginde ekleyerek korunur. Son adim patolojik durumda (turetilen bir ad, baska bir
     * grubun indeksli adina esitse) TUM adlara indeks ekler; indeks konum basina tekil oldugu
     * icin ayrik uzaylarda bu adim tekilligi garanti eder ve dongu sonlanir.
     */
    private fun List<String>.disambiguated(): List<String> {
        var current = this
        repeat(MAX_DISAMBIGUATION_PASSES) {
            val clashing = current.clashingSuffixes()
            if (clashing.isEmpty()) return current
            current = current.mapIndexed { index, suffix ->
                if (suffix in clashing) "${suffix}_$index" else suffix
            }
        }

        return if (current.clashingSuffixes().isEmpty()) {
            current
        } else {
            current.mapIndexed { index, suffix -> "${suffix}_$index" }
        }
    }

    /**
     * Uretecegi tanimlayicilardan en az biri baska bir grupla paylasilan ekler.
     *
     * Ayni ekin iki kez gecmesi de, iki farkli ekin ayni ada cokmesi de bu sayimda gorunur:
     * her iki durumda da tanimlayicilardan biri birden fazla kez uretilir.
     */
    private fun List<String>.clashingSuffixes(): Set<String> {
        val occurrences = flatMap(KeyConstantNaming::identifiers).groupingBy { it }.eachCount()

        return filterTo(mutableSetOf()) { suffix ->
            KeyConstantNaming.identifiers(suffix).any { identifier ->
                (occurrences[identifier] ?: 0) > 1
            }
        }
    }

    private companion object {

        /** Soyulan erisim onekleri; hicbiri digerinin oneki degildir, sira onemsizdir. */
        val ACCESS_PREFIXES = listOf("delete", "erase", "clear", "write", "read", "get", "set", "put")

        const val HEX_LENGTH = 20
        const val MAX_DISAMBIGUATION_PASSES = 2
    }
}
