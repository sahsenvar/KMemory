package io.github.sahsenvar.kmemory.compiler.usecase

import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceType
import io.github.sahsenvar.kmemory.compiler.model.ReadShape

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
        // Imzadaki tip adi; override imzasi bildirilen tipe birebir uymak zorunda oldugu icin
        // model'in cikarilmis tipi degil, fonksiyonun KENDI bildirdigi ad kullanilir.
        val type = function.declaredTypeName ?: model.type.simpleName
        val read = readExpression(model, type)

        return when (function.readShape) {
            ReadShape.FLOW -> generateFlow(model, function, type, read)
            // readShape yalnizca READ disindaki erisimlerde null olur; bu kullanim yeri READ
            // oldugu icin null pratikte olusmaz, olustugunda suspend sekli guvenli varsayimdir.
            ReadShape.SUSPEND, null -> generateSuspend(model, function, type, read)
        }
    }

    /** `fun x(): Flow<T?>` — soguk akis, her degisimde yeniden yayinlar. */
    private fun generateFlow(
        model: PreferenceModel,
        function: FunctionModel,
        type: String,
        read: String,
    ): String = """
        |    override fun ${function.name}(): Flow<$type?> =
        |        dataStore.data
        |            .map { prefs -> $read }
        |            .catch { error ->
        |                listener.onError(STORE_NAME, ${model.keyNameProperty}, error)
        |                throw error
        |            }
    """.trimMargin()

    /** `suspend fun x(): T?` — tek seferlik okuma; akisin ilk degeri alinir. */
    private fun generateSuspend(
        model: PreferenceModel,
        function: FunctionModel,
        type: String,
        read: String,
    ): String = """
        |    override suspend fun ${function.name}(): $type? =
        |        try {
        |            dataStore.data.first().let { prefs -> $read }
        |        } catch (error: Throwable) {
        |            listener.onError(STORE_NAME, ${model.keyNameProperty}, error)
        |            throw error
        |        }
    """.trimMargin()

    /**
     * Tek bir `Preferences` anlik goruntusunden degeri okuyan ifade.
     *
     * [PreferenceType.OBJECT] diskte JSON metni olarak durdugu icin once metin okunur, sonra
     * cozulur; `?.let` sayesinde anahtar yoksa cozme hic denenmez ve `null` doner.
     */
    private fun readExpression(model: PreferenceModel, type: String): String =
        when (model.type) {
            PreferenceType.OBJECT -> "prefs[${model.keyProperty}]?.let { json.decodeFromString<$type>(it) }"
            else -> "prefs[${model.keyProperty}]"
        }
}
