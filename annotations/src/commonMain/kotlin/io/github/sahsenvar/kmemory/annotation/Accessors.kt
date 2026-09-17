package io.github.sahsenvar.kmemory.annotation

/**
 * Okuma. Iki sekilden biri:
 *  - `fun x(): Flow<T?>`
 *  - `suspend fun x(): T?`
 *
 * Varsayilan deger kavrami yoktur; anahtar yazilmamissa `null` gelir. Varsayilan
 * bir is kuralidir ve repository katmanina aittir.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class Read(val key: String)

/** `suspend fun x(value: T)` — tek parametrenin tipi saklanan tipi belirler. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class Write(val key: String)

/** `suspend fun x()` — yalnizca [key] anahtarini siler. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class Erase(val key: String)

/** `suspend fun x()` — store'un tamamini siler. Arayuz basina en fazla bir tane. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class EraseAll
