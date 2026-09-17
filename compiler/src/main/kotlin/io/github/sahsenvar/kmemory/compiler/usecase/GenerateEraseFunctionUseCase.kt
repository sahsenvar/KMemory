package io.github.sahsenvar.kmemory.compiler.usecase

import io.github.sahsenvar.kmemory.compiler.model.FunctionModel
import io.github.sahsenvar.kmemory.compiler.model.PreferenceModel

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
    fun erase(model: PreferenceModel, function: FunctionModel): String = """
        |    override suspend fun ${function.name}() {
        |        try {
        |            dataStore.edit { prefs -> prefs.remove(${model.keyProperty}) }
        |            listener.onErase(STORE_NAME, ${model.keyNameProperty})
        |        } catch (error: Throwable) {
        |            listener.onError(STORE_NAME, ${model.keyNameProperty}, error)
        |            throw error
        |        }
        |    }
    """.trimMargin()

    /**
     * Store'un tamamini silen `@EraseAll` govdesi.
     *
     * Anahtar bilgisi tasimaz; dinleyiciye `null` anahtar gider.
     *
     * @param function Uretilecek fonksiyonun modeli.
     * @return 4 bosluk girintili Kotlin kaynagi; basinda ve sonunda satir sonu YOKTUR.
     */
    fun eraseAll(function: FunctionModel): String = """
        |    override suspend fun ${function.name}() {
        |        try {
        |            dataStore.edit { prefs -> prefs.clear() }
        |            listener.onErase(STORE_NAME, null)
        |        } catch (error: Throwable) {
        |            listener.onError(STORE_NAME, null, error)
        |            throw error
        |        }
        |    }
    """.trimMargin()
}
