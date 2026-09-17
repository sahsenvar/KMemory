package io.github.sahsenvar.kmemory.compiler.usecase

import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel
import io.github.sahsenvar.kmemory.compiler.model.ReturnShape

/**
 * `@Erase` ve `@EraseAll` tasiyan fonksiyonlarin `override` govdelerini uretir (spec §4.0, §6).
 *
 * Iki sekil ayri isimli fonksiyonlarla uretilir, `operator invoke` ile DEGIL: `@EraseAll`'un
 * anahtari yoktur, yani [PreferenceModel] de yoktur. Ikisini tek imzada toplamak modeli
 * nullable yapmayi gerektirirdi ve cagiran tarafta "hangi sekil" karari cagri anindan
 * kaybolurdu.
 *
 * Dinleyici sozlesmesi: tek anahtar silindiginde `onErase(store, key)`, store'un tamami
 * silindiginde `onErase(store, null)` (spec §7). `null` "anahtar bilinmiyor" degil, bilerek
 * "store olcegi" demektir.
 *
 * Donus sekli [FunctionModel.returnShape]'den gelir: `suspend fun x()` ya da
 * `fun x(): Flow<Unit>` (spec §4.0). Iki sekil de yerlesiktir; ortak iskelet [MutationBody]'de.
 *
 * Upstream'in `GenerateClearFunctionUseCase`'inin yerini alir; tek anahtar silme sekli
 * upstream'de HIC yoktu.
 */
internal class GenerateEraseFunctionUseCase {

    /**
     * Tek bir anahtari silen `@Erase` govdesi.
     *
     * `prefs.remove(...)` tipli anahtar aldigi icin unchecked cast gerekmez; tip anahtar
     * grubundan gelir, `@Erase` imzasinin kendisi tip tasimaz.
     *
     * @param model Silinecek anahtarin grubu.
     * @param function Uretilecek fonksiyonun modeli.
     * @return 4 bosluk girintili Kotlin kaynagi; basinda ve sonunda satir sonu YOKTUR.
     */
    fun erase(model: PreferenceModel, function: FunctionModel): String = body(
        function = function,
        removal = "dataStore.edit { prefs -> prefs.remove(${model.keyProperty}) }",
        notify = "listener.onErase(STORE_NAME, ${model.keyNameProperty})",
        keyArgument = model.keyNameProperty,
    )

    /**
     * Store'un tamamini silen `@EraseAll` govdesi.
     *
     * Anahtar bilgisi tasimaz; dinleyiciye `null` anahtar gider.
     *
     * @param function Uretilecek fonksiyonun modeli.
     * @return 4 bosluk girintili Kotlin kaynagi; basinda ve sonunda satir sonu YOKTUR.
     */
    fun eraseAll(function: FunctionModel): String = body(
        function = function,
        removal = "dataStore.edit { prefs -> prefs.clear() }",
        notify = "listener.onErase(STORE_NAME, null)",
        keyArgument = "null",
    )

    private fun body(
        function: FunctionModel,
        removal: String,
        notify: String,
        keyArgument: String,
    ): String = when (function.returnShape) {
        ReturnShape.SUSPEND -> MutationBody.suspending(
            name = function.name,
            parameters = "",
            work = INDENT_SUSPEND + removal,
            notify = notify,
            keyArgument = keyArgument,
        )

        ReturnShape.FLOW -> MutationBody.flowing(
            name = function.name,
            parameters = "",
            work = INDENT_FLOW + removal,
            notify = notify,
            keyArgument = keyArgument,
        )
    }

    private companion object {
        /** `try {` icindeki govdenin girintisi. */
        const val INDENT_SUSPEND = "            "

        /** `flow {` icindeki govdenin girintisi. */
        const val INDENT_FLOW = "        "
    }
}
