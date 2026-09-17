package io.github.sahsenvar.kmemory.compiler.model

/** Arayuzdeki bir fonksiyonun tasidigi erisim anotasyonu. */
internal enum class Accessor { READ, WRITE, ERASE, ERASE_ALL }

/**
 * Bir fonksiyonun donus sekli (spec §4.0).
 *
 * [FLOW] bildirilen donus tipinin `Flow<…>` oldugu, [SUSPEND] ise olmadigi durumdur. Ayrim
 * anotasyondan DEGIL, bildirilen donus tipinden yapilir ve dort erisimin hepsi icin gecerlidir:
 * okumada `Flow<T?>` ↔ `suspend fun (): T?`, yazma/silmede `Flow<Unit>` ↔ `suspend fun (): Unit`.
 *
 * Eskiden bu ayrim yalnizca `@Read` icin tutuluyordu (`ReadShape`) ve yazma/silmede `null`
 * kaliyordu; `Flow<Unit>` sekli tam o bosluktan dolayi uretilemiyordu.
 */
internal enum class ReturnShape { FLOW, SUSPEND }

/**
 * Tek bir arayuz fonksiyonunun modeli.
 *
 * @property name Fonksiyonun kisa adi; uretilen `override` bu adi tasir.
 * @property accessor Fonksiyonun tasidigi erisim anotasyonu.
 * @property key `@Read`/`@Write`/`@Erase` icin anahtar; `@EraseAll` icin `null`.
 * @property returnShape Bildirilen donus tipinden cikarilan sekil; dort erisim icin de dolu.
 * @property declaredType Bildirilen deger tipi — READ icin `T`, WRITE icin parametre tipi.
 *   `@Erase`/`@EraseAll` imzasinda tip bulunmadigi icin onlarda `null`; bu durumda tip ayni
 *   anahtardaki diger fonksiyonlardan cikarilir. Kisa ad DEGIL [TypeRef] tutulur: uretilen
 *   imza tip argumanlarini ve nullability'yi de tasimak zorundadir.
 */
internal data class FunctionModel(
    val name: String,
    val accessor: Accessor,
    val key: String?,
    val returnShape: ReturnShape,
    val declaredType: TypeRef?,
)
