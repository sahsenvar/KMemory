package io.github.sahsenvar.kmemory.compiler.usecase

import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceType
import io.github.sahsenvar.kmemory.compiler.model.ReturnShape

/**
 * `@Write` tasiyan bir fonksiyonun `override` govdesini uretir (spec §4.0, §6).
 *
 * Upstream'in `GenerateSetFunctionUseCase`'inin yerini alir. Iki fark var:
 * - Yazma basarili olunca [io.github.sahsenvar.kmemory.listener.PreferenceListener.onWrite]
 *   cagrilir; upstream'de hicbir kanca yoktu.
 * - Hata yutulmaz: `throw report(key, error)` ile once raporlanir (serilestirme hatalarinin
 *   mesaji saklanan degeri icerdigi icin dinleyiciye mesajsiz sarmalayici gider), sonra AYNI
 *   hata cagirana firlatilir.
 *   Upstream `dataStore.edit`'i ciplak birakiyordu, yani disk hatasi cagrildigi yere
 *   ham sekilde sizip hangi anahtarda oldugu bilgisini kaybediyordu.
 *
 * Iki donus sekli de yerlesiktir (spec §4.0):
 * - [ReturnShape.SUSPEND] → `suspend fun x(value: T)`, is cagri aninda yapilir.
 * - [ReturnShape.FLOW] → `fun x(value: T): Flow<Unit>`, is COLLECT aninda yapilir ve ardindan
 *   tek bir `Unit` yayilir. Sogukluk bilinclidir: collect edilmeyen yazma HIC olmaz. Zad'in
 *   `RemoteSource`'lari da ayni sekli tasidigi icin repository idiomu birebir ayni kalir
 *   (`flow { emitAll(source.writeX(v)) }.flowOn(ioContext)`).
 *
 * `json` serilestirici ADI ile kullanilir (upstream'in `Json` companion nesnesi degil):
 * ornek yapilandirilabilir olmali ki tuketici `ignoreUnknownKeys` gibi ayarlari secebilsin.
 */
internal class GenerateWriteFunctionUseCase {

    /**
     * @param model Fonksiyonun ait oldugu anahtar grubu; anahtar sabitinin adi ve diskteki
     *   temsil (ilkel mi, JSON metni mi) buradan gelir.
     * @param function Uretilecek fonksiyonun modeli; [FunctionModel.accessor] degerinin
     *   [io.github.sahsenvar.kmemory.compiler.model.Accessor.WRITE] oldugu varsayilir.
     * @return Sinif govdesine oldugu gibi eklenebilecek, 4 bosluk girintili Kotlin kaynagi;
     *   basinda ve sonunda satir sonu YOKTUR.
     */
    operator fun invoke(model: PreferenceModel, function: FunctionModel): String {
        // Imzadaki tip, fonksiyonun KENDI bildirdigi parametre tipinin tam metnidir: tip
        // argumanlari ve nullability dahil. `List<SearchHistory>` `List`'e ya da `String?`
        // `String`'e indirgenirse uretilen fonksiyon "overrides nothing" ile patlar.
        // WRITE'in tam bir parametresi oldugunu dogrulama garanti eder, bu yuzden fallback
        // pratikte ulasilmazdir.
        val parameterType = function.declaredType?.source ?: model.type.simpleName
        val nullable = function.declaredType?.isNullable == true
        val parameters = "value: $parameterType"

        return when (function.returnShape) {
            ReturnShape.SUSPEND -> MutationBody.suspending(
                name = function.name,
                parameters = parameters,
                work = editBlock(model, nullable, indent = INDENT_SUSPEND),
                notify = "listener.onWrite(STORE_NAME, ${model.keyNameProperty})",
                keyArgument = model.keyNameProperty,
            )

            ReturnShape.FLOW -> MutationBody.flowing(
                name = function.name,
                parameters = parameters,
                work = editBlock(model, nullable, indent = INDENT_FLOW),
                notify = "listener.onWrite(STORE_NAME, ${model.keyNameProperty})",
                keyArgument = model.keyNameProperty,
            )
        }
    }

    /**
     * `dataStore.edit { }` blogu.
     *
     * Nullable parametrede `prefs[key] = value` DERLENMEZ — `Preferences.Key<T>` null kabul
     * etmez. Bu yuzden nullable yazmada `null` "anahtari sil" anlamina gelir: tek alternatif
     * olan "null'i yok say" cagirani sessizce yaniltirdi, cunku `readX()` eski degeri
     * dondurmeye devam ederdi.
     *
     * Serilestirme `dataStore.edit`'in DISINA alinir. Iki gerekcesi var: `edit`'in donusumu
     * yeniden kosulabilir, ve iceride firlatilan bir hatanin DataStore tarafindan sarmalanip
     * sarmalanmadigina guvenmek gerekmez — sanitizasyon kapisi (`serializing`) o zaman
     * hatayi taniyamaz ve saklanan deger dinleyiciye sizardi.
     *
     * @param indent Blogun ilk satirinin onune yazilacak girinti; iki donus sekli farkli
     *   derinlikte oldugu icin disaridan verilir.
     */
    private fun editBlock(model: PreferenceModel, nullable: Boolean, indent: String): String {
        val serializes = model.type == PreferenceType.OBJECT

        if (!nullable) {
            if (!serializes) {
                return "${indent}dataStore.edit { prefs -> prefs[${model.keyProperty}] = value }"
            }
            return "${indent}val stored = ${storedExpression(model)}\n" +
                "${indent}dataStore.edit { prefs -> prefs[${model.keyProperty}] = stored }"
        }

        return "${indent}val stored = ${nullableStoredExpression(model)}\n" +
            "${indent}dataStore.edit { prefs ->\n" +
            "$indent    if (stored == null) {\n" +
            "$indent        prefs.remove(${model.keyProperty})\n" +
            "$indent    } else {\n" +
            "$indent        prefs[${model.keyProperty}] = stored\n" +
            "$indent    }\n" +
            "$indent}"
    }

    /**
     * Diske yazilacak degeri ureten ifade.
     *
     * [PreferenceType.OBJECT] diskte JSON metni olarak durur; okuma tarafindaki
     * `json.decodeFromString` ile simetriktir — biri degisirse digeri de degismek zorundadir,
     * bu yuzden iki ifade de tek bir tip ayrimindan turetilir. Okuma tarafi gibi burasi da
     * `serializing(key) { }` ile sarilir: sanitizasyon kapisi serilestirmenin OLDUGU yerdedir.
     */
    private fun storedExpression(model: PreferenceModel): String =
        when (model.type) {
            PreferenceType.OBJECT ->
                "serializing(${model.keyNameProperty}) { json.encodeToString(value) }"

            else -> "value"
        }

    /** [storedExpression]'in nullable karsiligi; `null` deger serilestirilmeye calisilmaz. */
    private fun nullableStoredExpression(model: PreferenceModel): String =
        when (model.type) {
            PreferenceType.OBJECT ->
                "value?.let { raw -> serializing(${model.keyNameProperty}) { json.encodeToString(raw) } }"

            else -> "value"
        }

    private companion object {
        /** `try {` icindeki govdenin girintisi. */
        const val INDENT_SUSPEND = "            "

        /** `flow {` icindeki govdenin girintisi. */
        const val INDENT_FLOW = "        "
    }
}
