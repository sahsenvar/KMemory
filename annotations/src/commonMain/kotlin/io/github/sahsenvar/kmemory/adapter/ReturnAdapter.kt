package io.github.sahsenvar.kmemory.adapter

import kotlinx.coroutines.flow.Flow

/**
 * Ktorfit'in converter factory'lerinin karsiligi (tasarim §4.1).
 *
 * 0.1.0'da YERLESIK adaptor YOKTUR; islemci `Flow<T?>`, `T?` ve `Unit` sekillerini
 * dogrudan uretir. Bu arayuz genisletme seam'idir: tuketici kendi seklini yazar,
 * `kmemory { adapters += ... }` ile kaydeder ve isleyiciye
 * `kmemory.adapters=<FQN>` KSP secenegiyle tanitir. Tanitilmamis bir donus tipi
 * calisma-zamani hatasi degil, DERLEME hatasi olur.
 */
public interface ReturnAdapter {

    /** Karsiladigi bildirilen donus tipinin FQN'i, ornegin `"kotlin.Result"`. */
    public val declaredType: String

    /** Kanonik okuma formunu (`Flow<T?>`) bildirilen donus tipine uyarlar. */
    public fun adaptRead(source: Flow<Any?>): Any?

    /** Kanonik yazma formunu (`suspend () -> Unit`) bildirilen donus tipine uyarlar. */
    public fun adaptWrite(block: suspend () -> Unit): Any?
}
