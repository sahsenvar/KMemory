package io.github.sahsenvar.kmemory.compiler.usecase

import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceType

/**
 * `@Write` tasiyan bir fonksiyonun `override` govdesini uretir (spec §4.0, §6).
 *
 * Upstream'in `GenerateSetFunctionUseCase`'inin yerini alir. Iki fark var:
 * - Yazma basarili olunca [io.github.sahsenvar.kmemory.listener.PreferenceListener.onWrite]
 *   cagrilir; upstream'de hicbir kanca yoktu.
 * - Hata yutulmaz: once uretilen `report(key, error)` yardimcisi ile raporlanir (serilestirme
 *   hatalarinin mesaji saklanan degeri icerdigi icin dinleyiciye mesajsiz sarmalayici gider),
 *   sonra AYNI hata yeniden firlatilir.
 *   Upstream `dataStore.edit`'i ciplak birakiyordu, yani disk hatasi cagrildigi yere
 *   ham sekilde sizip hangi anahtarda oldugu bilgisini kaybediyordu.
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

        return "    override suspend fun ${function.name}(value: $parameterType) {\n" +
            "        try {\n" +
            editBlock(model, nullable) + "\n" +
            "            listener.onWrite(STORE_NAME, ${model.keyNameProperty})\n" +
            "        } catch (error: Throwable) {\n" +
            "            report(${model.keyNameProperty}, error)\n" +
            "            throw error\n" +
            "        }\n" +
            "    }"
    }

    /**
     * `dataStore.edit { }` blogu.
     *
     * Nullable parametrede `prefs[key] = value` DERLENMEZ — `Preferences.Key<T>` null kabul
     * etmez. Bu yuzden nullable yazmada `null` "anahtari sil" anlamina gelir: tek alternatif
     * olan "null'i yok say" cagirani sessizce yaniltirdi, cunku `readX()` eski degeri
     * dondurmeye devam ederdi.
     */
    private fun editBlock(model: PreferenceModel, nullable: Boolean): String {
        if (!nullable) {
            return "            dataStore.edit { prefs -> prefs[${model.keyProperty}] = ${storedExpression(model)} }"
        }

        return "            dataStore.edit { prefs ->\n" +
            "                val stored = ${nullableStoredExpression(model)}\n" +
            "                if (stored == null) {\n" +
            "                    prefs.remove(${model.keyProperty})\n" +
            "                } else {\n" +
            "                    prefs[${model.keyProperty}] = stored\n" +
            "                }\n" +
            "            }"
    }

    /**
     * Diske yazilacak degeri ureten ifade.
     *
     * [PreferenceType.OBJECT] diskte JSON metni olarak durur; okuma tarafindaki
     * `json.decodeFromString` ile simetriktir — biri degisirse digeri de degismek zorundadir,
     * bu yuzden iki ifade de tek bir tip ayrimindan turetilir.
     */
    private fun storedExpression(model: PreferenceModel): String =
        when (model.type) {
            PreferenceType.OBJECT -> "json.encodeToString(value)"
            else -> "value"
        }

    /** [storedExpression]'in nullable karsiligi; `null` deger serilestirilmeye calisilmaz. */
    private fun nullableStoredExpression(model: PreferenceModel): String =
        when (model.type) {
            PreferenceType.OBJECT -> "value?.let { json.encodeToString(it) }"
            else -> "value"
        }
}
