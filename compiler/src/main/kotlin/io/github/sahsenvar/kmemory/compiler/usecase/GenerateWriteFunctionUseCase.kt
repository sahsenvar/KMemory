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
 * - Hata yutulmaz: once `onError` ile raporlanir, sonra AYNI hata yeniden firlatilir.
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
        // Imzadaki tip adi; override imzasi bildirilen tipe birebir uymak zorunda oldugu icin
        // model'in cikarilmis tipi degil, fonksiyonun KENDI bildirdigi parametre tipi
        // kullanilir. WRITE'in tam bir parametresi oldugunu dogrulama garanti eder, bu yuzden
        // fallback pratikte ulasilmazdir.
        val type = function.declaredTypeName ?: model.type.simpleName

        return """
            |    override suspend fun ${function.name}(value: $type) {
            |        try {
            |            dataStore.edit { prefs -> prefs[${model.keyProperty}] = ${storedExpression(model)} }
            |            listener.onWrite(STORE_NAME, ${model.keyNameProperty})
            |        } catch (error: Throwable) {
            |            listener.onError(STORE_NAME, ${model.keyNameProperty}, error)
            |            throw error
            |        }
            |    }
        """.trimMargin()
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
}
