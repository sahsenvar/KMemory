package io.github.sahsenvar.kmemory.compiler.usecase

import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceType
import io.github.sahsenvar.kmemory.compiler.model.ReturnShape

/**
 * `@Read` tasiyan bir fonksiyonun `override` govdesini uretir (spec §4.0, §6).
 *
 * Upstream'in `GenerateGetFunctionUseCase` + `GenerateGetFlowFunctionUseCase` ciftinin yerini
 * alir. Iki sekil tek yerde uretiliyor cunku aralarindaki tek fark akisin nasil toplandigidir;
 * anahtar okuma ve nesne cozme ifadesi ikisinde de AYNI olmak zorunda — ayri dosyalarda
 * durduklarinda upstream'de tam bu ifade ikisi arasinda ayrisiyordu.
 *
 * Uretilen kod varsayilan deger TASIMAZ (spec §5): anahtar yoksa `null` doner.
 *
 * Hata hicbir sekilde yutulmaz: once [io.github.sahsenvar.kmemory.listener.PreferenceListener]
 * bilgilendirilir, sonra AYNI hata yeniden firlatilir. Yutulsaydi cagiran taraf "anahtar yok"
 * ile "disk okunamadi" durumlarini ayirt edemezdi; ikisi de `null` gorunurdu.
 *
 * Bildirim `listener.onError` ile DOGRUDAN degil, uretilen `report(key, error)` yardimcisi
 * uzerinden yapilir: dinleyici hicbir dalda yabanci bir `Throwable` gormemelidir (tasarim §8).
 * Hangi hatanin tehlikeli oldugunu tahmin eden bir kapi YOKTUR — ne tipe ne konuma bakilir,
 * cunku hem cozme hatasinin hem de `Preferences` anlik goruntusu tasiyan bir DataStore
 * hatasinin mesaji saklanan degeri icerebilir. `report` firlatilacak hatayi DONDURUR, bu
 * yuzden cagri sekli `throw report(...)`'dir ve cagiran orijinal hatayi almaya devam eder.
 */
internal class GenerateReadFunctionUseCase {

    /**
     * @param model Fonksiyonun ait oldugu anahtar grubu; anahtar sabitlerinin adlari ve
     *   diskteki temsil (ilkel mi, JSON metni mi) buradan gelir.
     * @param function Uretilecek fonksiyonun modeli; [FunctionModel.accessor] degerinin
     *   [io.github.sahsenvar.kmemory.compiler.model.Accessor.READ] oldugu varsayilir.
     * @return Sinif govdesine oldugu gibi eklenebilecek, 4 bosluk girintili Kotlin kaynagi.
     */
    operator fun invoke(model: PreferenceModel, function: FunctionModel): String {
        // Imzadaki tip, fonksiyonun KENDI bildirdigi tipin tam metnidir: tip argumanlari ve
        // nullability dahil. Model'in cikarilmis tipi kullanilsaydi `Flow<List<String>?>`
        // `Flow<List?>`'e iner ve override kirilirdi. Dogrulama `@Read` degerinin nullable
        // olmasini garanti ettigi icin `?` metnin icinde zaten vardir; ayrica eklenmez.
        val signatureType = function.declaredType?.source ?: "${model.type.simpleName}?"
        // `decodeFromString<T>` null kabul etmez; ayni tipin nullability'siz hali kullanilir.
        val decodeType = function.declaredType?.nonNull ?: model.type.simpleName
        val read = readExpression(model, decodeType)

        return when (function.returnShape) {
            ReturnShape.FLOW -> generateFlow(model, function, signatureType, read)
            ReturnShape.SUSPEND -> generateSuspend(model, function, signatureType, read)
        }
    }

    /**
     * `fun x(): Flow<T?>` — soguk akis, her degisimde yeniden yayinlar.
     *
     * Okunan deger `map` lambda'sinin icinde TIPLI BIR YERELE baglanir; gerekcesi
     * [typedBinding]'dedir. Tek satirlik `.map { prefs -> prefs[KEY] }` sekli, diskteki tip
     * uyusmazliginin `.catch`'in ASAGISINDA patlamasina yol aciyordu.
     */
    private fun generateFlow(
        model: PreferenceModel,
        function: FunctionModel,
        type: String,
        read: String,
    ): String = """
        |    override fun ${function.name}(): Flow<$type> =
        |        dataStore.data
        |            .map { prefs ->
        |${typedBinding(type, read, indent = 16)}
        |            }
        |            .catch { error -> throw report(${model.keyNameProperty}, error) }
    """.trimMargin()

    /**
     * `suspend fun x(): T?` — tek seferlik okuma; akisin ilk degeri alinir.
     *
     * Bu sekilde donusum `try` blogunun ICINDE zaten oluyordu (derleyici `try` ifadesinin
     * degerini bildirilen tipe cevirmek zorunda). Baglama yine de ACIKCA yaziliyor: ayni
     * garantinin iki sekilde de AYNI mekanizmadan gelmesi, birinin sessizce kaymasini
     * engeller.
     */
    private fun generateSuspend(
        model: PreferenceModel,
        function: FunctionModel,
        type: String,
        read: String,
    ): String = """
        |    override suspend fun ${function.name}(): $type =
        |        try {
        |${typedBinding(type, "dataStore.data.first().let { prefs -> $read }", indent = 12)}
        |        } catch (error: Throwable) {
        |            throw report(${model.keyNameProperty}, error)
        |        }
    """.trimMargin()

    /**
     * Okunan degeri, bildirilen tipiyle ADLANDIRILMIS bir yerele baglar ve yereli sonuc yapar.
     *
     * Tek amaci JVM'de `checkcast`'i buraya — yani akisin ve `try`'in ICINE — cektirmektir.
     *
     * Sorun sudur: `Preferences.get` jeneriktir (`fun <T> get(key: Key<T>): T?`), yani degeri
     * KONTROLSUZ cevirir; gercek kontrol, derleyicinin donus degerini bildirilen tipe
     * cevirdigi YERDE olusur. `.map { prefs -> prefs[KEY] }` yazildiginda lambda'nin donus
     * tipi `map`'in tip degiskeni `R`'dir ve `R` `Object`'e silinir — cevrilecek bir sey
     * yoktur, `checkcast` uretilmez. Deger akistan `Object` olarak cikar ve ilk kez CAGIRANIN
     * kodunda cevrilir: `.catch` bu hatayi hicbir zaman gormez, dinleyici hic haberdar
     * olmaz. (Diskte ayni anahtara `stringPreferencesKey` ile yazilmis bir deger
     * `intPreferencesKey` ile okundugunda olan tam olarak budur.)
     *
     * Bildirilen tipi `.map<Preferences, T?> { ... }` diye ACIKCA yazmak bunu COZMEZ:
     * cikarim zaten ayni tipi buluyordu, uretilen imza degismez, lambda'nin silinmis donus
     * tipi yine `Object` kalir. Coz'en sey tip ARGUMANI degil, JENERIK OLMAYAN BIR HEDEFE
     * BAGLAMADIR — bu yuzden tipli bir yerel kullaniliyor. (Ayni etki `as T?` ile de
     * alinabilirdi ama ifade zaten o tipte oldugu icin `USELESS_CAST` uyarisi dogar; uyariyi
     * hataya ceviren tuketicilerde uretilen kod derlenmezdi.)
     *
     * Sonucta olusan [ClassCastException] akisin/`try`'in ICINDE dogar, yani `report`'tan
     * gecer ve dinleyici bunu `originalType = "java.lang.ClassCastException"` olarak gorur.
     * Gercek bir veri bozulmasi isaretidir; kaybolmamasi gereken tani tam olarak budur.
     *
     * @param type Fonksiyonun bildirdigi deger tipi, or. `Int?` ya da `List<String>?`.
     * @param expression Degeri ureten ifade.
     * @param indent Uretilen iki satirin basina konacak bosluk sayisi.
     */
    private fun typedBinding(type: String, expression: String, indent: Int): String {
        val padding = " ".repeat(indent)
        return "${padding}val $VALUE_NAME: $type = $expression\n$padding$VALUE_NAME"
    }

    /**
     * Tek bir `Preferences` anlik goruntusunden degeri okuyan ifade.
     *
     * [PreferenceType.OBJECT] diskte JSON metni olarak durdugu icin once metin okunur, sonra
     * cozulur; `?.let` sayesinde anahtar yoksa cozme hic denenmez ve `null` doner.
     *
     * `let` parametresi `raw` diye ADLANDIRILIR: adsiz birakilan `it` okuyani yaniltirdi;
     * ayrica [typedBinding]'in urettigi `stored` yerelini golgelememesi gerekir.
     */
    private fun readExpression(model: PreferenceModel, decodeType: String): String =
        when (model.type) {
            PreferenceType.OBJECT ->
                "prefs[${model.keyProperty}]?.let { raw -> json.decodeFromString<$decodeType>(raw) }"

            else -> "prefs[${model.keyProperty}]"
        }

    private companion object {

        /** [typedBinding]'in urettigi yerelin adi. */
        const val VALUE_NAME = "stored"
    }
}
