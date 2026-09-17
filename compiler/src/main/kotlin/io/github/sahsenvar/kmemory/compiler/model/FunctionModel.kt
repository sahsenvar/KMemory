package io.github.sahsenvar.kmemory.compiler.model

/** Arayuzdeki bir fonksiyonun tasidigi erisim anotasyonu. */
internal enum class Accessor { READ, WRITE, ERASE, ERASE_ALL }

/**
 * `@Read`'in iki sekli (spec 4.0).
 *
 * [FLOW] `fun x(): Flow<T?>`, [SUSPEND] ise `suspend fun x(): T?` demektir. Ayrim
 * anotasyondan degil, bildirilen donus tipinden yapilir.
 */
internal enum class ReadShape { FLOW, SUSPEND }

/**
 * Tek bir arayuz fonksiyonunun modeli.
 *
 * @property name Fonksiyonun kisa adi; uretilen `override` bu adi tasir.
 * @property accessor Fonksiyonun tasidigi erisim anotasyonu.
 * @property key `@Read`/`@Write`/`@Erase` icin anahtar; `@EraseAll` icin `null`.
 * @property readShape Yalnizca [Accessor.READ] icin dolu.
 * @property declaredTypeName Deger tipinin kisa adi — READ icin `T`, WRITE icin parametre
 *   tipi. `@Erase`/`@EraseAll` imzasinda tip bulunmadigi icin onlarda `null`; bu durumda tip
 *   ayni anahtardaki diger fonksiyonlardan cikarilir.
 * @property declaredTypeFqName Ayni tipin tam nitelikli adi; nesne tiplerinin import'u ve
 *   tip catismasi dogrulamasi bunun uzerinden yapilir.
 */
internal data class FunctionModel(
    val name: String,
    val accessor: Accessor,
    val key: String?,
    val readShape: ReadShape?,
    val declaredTypeName: String?,
    val declaredTypeFqName: String?,
)
