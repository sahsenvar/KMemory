package io.github.sahsenvar.kmemory.compiler.model

import io.github.sahsenvar.kmemory.compiler.usecase.DeriveConstantSuffixUseCase

/**
 * Ayni anahtari paylasan fonksiyonlarin olusturdugu grup.
 *
 * Tip anahtar basina TEK'tir: `@Erase` imzasinda tip tasimadigi icin tipini ayni gruptaki
 * `@Read`/`@Write`'tan alir.
 *
 * @property key Anahtarin literal degeri; uretilen koda oldugu gibi gomulur.
 * @property constantSuffix Uretilen sabit adlarinin ortak eki; [DeriveConstantSuffixUseCase]
 *   tarafindan grubun ilk fonksiyonunun adindan turetilir ve arayuz icinde TEKILDIR. Model
 *   bunu kendisi hesaplayamaz: carpisma kurali butun gruplari birlikte gormeyi gerektirir.
 * @property type Gruptan cikarilan tip.
 * @property declaredType Gruptan cikarilan tipin tam hali; [PreferenceType.OBJECT] icin
 *   serilestirilecek tipi ve import'unu tasir. Tip hic cikarilamadiysa `null` kalir ve
 *   dogrulama bunu hataya cevirir.
 * @property functions Bu anahtari kullanan fonksiyonlar.
 */
internal data class PreferenceModel(
    val key: String,
    val constantSuffix: String,
    val type: PreferenceType,
    val declaredType: TypeRef?,
    val functions: List<FunctionModel>,
) {

    /** Uretilen companion'daki anahtar metninin sabit adi. */
    val keyNameProperty: String = "KEY_NAME_$constantSuffix"

    /** Uretilen companion'daki `Preferences.Key<*>` degiskeninin adi. */
    val keyProperty: String = "KEY_$constantSuffix"
}
