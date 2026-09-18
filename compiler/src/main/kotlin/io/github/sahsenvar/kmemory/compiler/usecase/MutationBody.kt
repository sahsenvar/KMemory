package io.github.sahsenvar.kmemory.compiler.usecase

/**
 * `@Write`/`@Erase`/`@EraseAll` govdelerinin ORTAK iskeleti (spec §4.0).
 *
 * Uc erisim de ayni sirayi izler: isi yap → dinleyiciyi bilgilendir → hata cikarsa
 * `throw report(key, error)` ile bildir ve cagirana AYNI hatayi firlat (`report` firlatilacak
 * hatayi dondurur; serilestirme sinirinin ic isareti boylece cagirana sizmaz). Tek degisen
 * "is" satirlari.
 * Iskelet tek yerde durmasaydi alti govde (uc erisim × iki donus sekli) bagimsiz kopyalar
 * olurdu ve dinleyici sozlesmesindeki bir duzeltmenin bir dali atlamasi kacinilmazdi —
 * onceki devirde `listener.onError` cagrilarinin dagilmis olmasi tam olarak bu sinifta bir
 * bug'a yol acmisti.
 *
 * [flowing] sekli bilerek SOGUKTUR: is `flow { }` govdesinin icindedir, yani collect
 * edilmeyen bir yazma HIC olmaz. Bu, spec §4.0'da acikca alinmis bir karardir.
 */
internal object MutationBody {

    /** `suspend fun x(…)` sekli; is cagri aninda yapilir. */
    fun suspending(
        name: String,
        parameters: String,
        work: String,
        notify: String,
        keyArgument: String,
    ): String = "    override suspend fun $name($parameters) {\n" +
        "        try {\n" +
        work + "\n" +
        "            $notify\n" +
        "        } catch (error: Throwable) {\n" +
        "            throw report($keyArgument, error)\n" +
        "        }\n" +
        "    }"

    /**
     * `fun x(…): Flow<Unit>` sekli; is COLLECT aninda yapilir ve tek bir `Unit` yayilir.
     *
     * Hata `try/catch` ile DEGIL `.catch { }` ile yakalanir: akis govdesinde firlatilan hata
     * zaten asagi akar, ama `catch` operatoru DOWNSTREAM'den gelen hatayi sahiplenmez. Govdeyi
     * try/catch'e sarmak, collector'in kendi firlattigi hatayi da yazma hatasi sanip
     * dinleyiciye yanlis bir kayit gecirirdi.
     *
     * `emit(Unit)` isten ve bildirimden SONRA gelir; oncesine alinsaydi collector ilk degeri
     * alip akisi iptal ettiginde (`first()`) is yarim kalirdi.
     */
    fun flowing(
        name: String,
        parameters: String,
        work: String,
        notify: String,
        keyArgument: String,
    ): String = "    override fun $name($parameters): Flow<Unit> = flow {\n" +
        work + "\n" +
        "        $notify\n" +
        "        emit(Unit)\n" +
        "    }.catch { error -> throw report($keyArgument, error) }"
}
