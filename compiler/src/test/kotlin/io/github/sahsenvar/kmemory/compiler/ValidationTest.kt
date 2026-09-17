package io.github.sahsenvar.kmemory.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import io.github.sahsenvar.kmemory.compiler.processor.ProcessorProvider
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Spec §10 dogrulama kurallarinin negatif testleri.
 *
 * Her test tek bir kurali ihlal eden bir fixture derler ve o kuralin metnini bekler.
 * Fixture'lar kasten "yalnizca o kurali" ihlal eder; baska bir kural da patlarsa test
 * hangi mesaji aradigini soyledigi icin yine dogru yeri gosterir.
 */
@OptIn(ExperimentalCompilerApi::class)
class ValidationTest {

    // --- pozitif kontrol -----------------------------------------------------------------

    @Test
    fun `gecerli arayuz hatasiz derlenir`() {
        val result = compile(
            """
            @Read("k") fun readK(): Flow<Int?>
            @Read("k") suspend fun readKOnce(): Int?
            @Write("k") suspend fun writeK(value: Int)
            @Erase("k") suspend fun eraseK()
            @EraseAll suspend fun eraseAll()
            """
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        // Salt "hata yok" yetmez: uretim gercekten kostu ve urettigi kod da derlendi mi?
        result.classLoader.loadClass("fixture.TestPreferencesImpl")
    }

    // --- spec §10 kurallari --------------------------------------------------------------

    @Test
    fun `anotasyonsuz fonksiyon hata verir`() {
        val result = compile(
            """
            @Read("k") fun readK(): Flow<Int?>
            @Write("k") suspend fun writeK(value: Int)
            fun etiketsiz()
            """
        )

        assertError(result, "her fonksiyon @Read/@Write/@Erase/@EraseAll'dan birini taşımalı")
    }

    @Test
    fun `birden fazla EraseAll hata verir`() {
        val result = compile(
            """
            @Read("k") fun readK(): Flow<Int?>
            @Write("k") suspend fun writeK(value: Int)
            @EraseAll suspend fun temizle()
            @EraseAll suspend fun hepsiniSil()
            """
        )

        assertError(result, "birden fazla @EraseAll bildirilmiş")
    }

    @Test
    fun `Write iki parametre alirsa hata verir`() {
        val result = compile(
            """
            @Write("k") suspend fun writeK(value: Int, digeri: Int)
            """
        )

        assertError(result, "WRITE tam 1 parametre almalı")
    }

    @Test
    fun `Write Flow donerse hata verir`() {
        val result = compile(
            """
            @Write("k") fun writeK(value: Int): Flow<Unit>
            """
        )

        assertError(result, "yerleşik bir dönüş şekli değil")
    }

    @Test
    fun `Result donen fonksiyon hata verir`() {
        val result = compile(
            """
            @Read("k") suspend fun readK(): Result<Int>
            """
        )

        assertError(result, "yerleşik bir dönüş şekli değil")
    }

    @Test
    fun `ayni anahtarda celisen tip hata verir`() {
        val result = compile(
            """
            @Read("k") fun readK(): Flow<Int?>
            @Write("k") suspend fun writeK(value: String)
            """
        )

        assertError(result, "aynı anahtar için farklı tipler bildirilmiş")
    }

    @Test
    fun `yalnizca Erase ile gecen anahtar hata verir`() {
        val result = compile(
            """
            @Erase("k") suspend fun eraseK()
            """
        )

        assertError(result, "tip çıkarılamıyor; bu anahtar için bir @Read veya @Write gerekli")
    }

    @Test
    fun `Flow donen Read suspend olursa hata verir`() {
        val result = compile(
            """
            @Read("k") suspend fun readK(): Flow<Int?>
            """
        )

        assertError(result, "Flow dönen fonksiyon suspend olamaz (Flow soğuktur)")
    }

    @Test
    fun `Flow donmeyen Read suspend degilse hata verir`() {
        val result = compile(
            """
            @Read("k") fun readK(): Int?
            """
        )

        assertError(result, "Flow dönmeyen fonksiyon suspend olmalı")
    }

    @Test
    fun `Read degeri nullable degilse hata verir`() {
        val result = compile(
            """
            @Read("k") fun readK(): Flow<Int>
            """
        )

        assertError(result, "@Read değeri nullable olmalı")
    }

    // --- tip sadakati ve anahtar adi carpismasi ------------------------------------------

    @Test
    fun `tip argumanli deger tam imzayla uretilir`() {
        val result = compile(
            """
            @Read("k") fun readItems(): Flow<List<String>?>
            @Write("k") suspend fun writeItems(value: List<String>)
            """
        )

        // Tip argumani dusurulurse uretilen imza `Flow<List?>` / `value: List` olur ve
        // "overrides nothing" ile derlenmez; OK kodu tam tipin korundugunun kanitidir.
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        result.classLoader.loadClass("fixture.TestPreferencesImpl")
    }

    @Test
    fun `ic ice tip argumani korunur`() {
        val result = compile(
            """
            @Read("k") fun readGroups(): Flow<Map<String, List<Int>>?>
            @Write("k") suspend fun writeGroups(value: Map<String, List<Int>>)
            """
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        result.classLoader.loadClass("fixture.TestPreferencesImpl")
    }

    @Test
    fun `nullable Write parametresi imzada korunur`() {
        val result = compile(
            """
            @Read("k") fun readToken(): Flow<String?>
            @Write("k") suspend fun writeToken(value: String?)
            """
        )

        // Nullability dusurulurse uretilen `value: String` arayuzdeki `value: String?`'i
        // override edemez.
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        result.classLoader.loadClass("fixture.TestPreferencesImpl")
    }

    @Test
    fun `ilk 20 karakteri ayni olan iki anahtar catismaz`() {
        val result = compile(
            """
            @Read("notification_settings_enabled") fun readEnabled(): Flow<Boolean?>
            @Write("notification_settings_enabled") suspend fun writeEnabled(value: Boolean)
            @Read("notification_settings_muted") fun readMuted(): Flow<Boolean?>
            @Write("notification_settings_muted") suspend fun writeMuted(value: Boolean)
            """
        )

        // Iki anahtarin alfanumerikleri ilk 20 karakterde AYNI ("NOTIFICATIONSETTINGS");
        // sabit adi yalnizca bu onekten turerse companion "Conflicting declarations" verir.
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        result.classLoader.loadClass("fixture.TestPreferencesImpl")
    }

    // --- govdeli fonksiyon, miras uye, adapter bypass -------------------------------------

    @Test
    fun `govdesi olan arayuz fonksiyonu uretimi engellemez`() {
        val result = compile(
            body = """
            @Read("k") fun readAuthToken(): Flow<String?>
            @Write("k") suspend fun writeAuthToken(value: String)

            // Zad'daki AuthMemorySource.isUserLoggedIn'in birebir sekli: govdesi var,
            // uretilmesine gerek yok, hicbir accessor anotasyonuna uymuyor.
            suspend fun isUserLoggedIn(): Boolean = readAuthToken().firstOrNull() != null
            """,
            extraImports = listOf("kotlinx.coroutines.flow.firstOrNull"),
        )

        // Saf sayi karsilastirmasi (declaredFunctions.size != functions.size) bu arayuzu
        // tumden reddediyordu; anotasyon zorunlulugu yalnizca ABSTRACT uyelere aittir.
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        result.classLoader.loadClass("fixture.TestPreferencesImpl")
    }

    @Test
    fun `bos marker supertype uretimi engellemez`() {
        val result = compile(
            body = """
            @Read("k") fun readK(): Flow<Int?>
            @Write("k") suspend fun writeK(value: Int)
            """,
            supertype = "MemorySource",
            extraDeclarations = "interface MemorySource",
        )

        // Zad'in MemorySource marker'i tam olarak boyle: uyesi yok, sorun da olmamali.
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        result.classLoader.loadClass("fixture.TestPreferencesImpl")
    }

    @Test
    fun `supertype fonksiyonu net hata verir`() {
        val result = compile(
            body = """
            @Read("k") fun readK(): Flow<Int?>
            @Write("k") suspend fun writeK(value: Int)
            """,
            supertype = "LegacySource",
            extraDeclarations = """
            interface LegacySource {
                suspend fun eskiTemizle()
            }
            """,
        )

        // Uretilen Impl bu uyeyi implemente edemez. Hata TUKETICIDE, uretilen dosyada
        // "is not abstract and does not implement" olarak cikiyordu; beklenen, kutuphanenin
        // kendi kaynakta konumlanan NET mesaji.
        assertError(result, "miras alınan soyut üye üretilemez")
        assertContains(result.messages, "eskiTemizle")
        assertContains(result.messages, "LegacySource")
    }

    @Test
    fun `supertype ozelligi net hata verir`() {
        val result = compile(
            body = """
            @Read("k") fun readK(): Flow<Int?>
            @Write("k") suspend fun writeK(value: Int)
            """,
            supertype = "HasName",
            extraDeclarations = """
            interface HasName {
                val kullaniciAdi: String
            }
            """,
        )

        // Soyut OZELLIK de uretilemez; fonksiyon sayimi bunu hic gormuyordu.
        assertError(result, "miras alınan soyut üye üretilemez")
        assertContains(result.messages, "kullaniciAdi")
        assertContains(result.messages, "HasName")
    }

    @Test
    fun `supertype uyesi arayuzde yeniden bildirilince uretilir`() {
        val result = compile(
            body = """
            @Read("k") override fun readK(): Flow<Int?>
            @Write("k") suspend fun writeK(value: Int)
            """,
            supertype = "LegacySource",
            extraDeclarations = """
            interface LegacySource {
                fun readK(): Flow<Int?>
            }
            """,
        )

        // Yukaridaki hata mesaji "uyeyi bu arayuzde yeniden bildirip isaretleyin" diyor;
        // bu test o care'nin gercekten calistigini kanitlar, yoksa mesaj yalan olurdu.
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        result.classLoader.loadClass("fixture.TestPreferencesImpl")
    }

    @Test
    fun `kmemory adapters tanitilan tipi gevsetmez`() {
        val result = compile(
            body = """
            @Read("k") suspend fun readK(): Result<Int>
            """,
            processorOptions = mapOf("kmemory.adapters" to "kotlin.Result"),
        )

        // Bypass dogrulamayi gevsetiyor ama uretimi hic degistirmiyordu: tip dogrulamadan
        // gecip uretimde yok sayiliyor, tuketicide patliyordu.
        assertError(result, "yerleşik bir dönüş şekli değil")
        assertContains(result.messages, "0.2.0")
    }

    @Test
    fun `kmemory adapters secenegi desteklenmiyor uyarisi verir`() {
        val result = compile(
            body = """
            @Read("k") fun readK(): Flow<Int?>
            @Write("k") suspend fun writeK(value: Int)
            """,
            processorOptions = mapOf("kmemory.adapters" to "kotlin.Result"),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertContains(result.messages, "kmemory.adapters henüz desteklenmiyor")
    }

    // --- altyapi -------------------------------------------------------------------------

    private fun assertError(result: JvmCompilationResult, message: String) {
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertContains(result.messages, message)
    }

    /**
     * [body] bir `@Preferences` arayuzunun govdesidir; gerisi sabit kalir.
     *
     * @param supertype arayuzun turedigi tip; `null` ise supertype yazilmaz.
     * @param extraDeclarations fixture dosyasina eklenen ust-duzey bildirimler (supertype'lar).
     * @param extraImports temel import listesine eklenenler.
     * @param processorOptions KSP islemci secenekleri (spec §9.1).
     */
    private fun compile(
        body: String,
        supertype: String? = null,
        extraDeclarations: String = "",
        extraImports: List<String> = emptyList(),
        processorOptions: Map<String, String> = emptyMap(),
    ): JvmCompilationResult {
        val source = buildString {
            appendLine("package fixture")
            appendLine()
            (BASE_IMPORTS + extraImports).forEach { appendLine("import $it") }
            appendLine()
            if (extraDeclarations.isNotBlank()) {
                appendLine(extraDeclarations.trimIndent())
                appendLine()
            }
            appendLine("""@Preferences(name = "test.preferences_pb")""")
            appendLine("interface TestPreferences" + supertype?.let { " : $it" }.orEmpty() + " {")
            appendLine(body.trimIndent().prependIndent("    "))
            appendLine("}")
        }

        return KotlinCompilation().apply {
            // useKsp2() KSP aracini kurar; symbolProcessorProviders ONCESINDE cagrilmali,
            // aksi halde "KSP not configured" ile patlar.
            useKsp2()
            sources = listOf(SourceFile.kotlin("TestPreferences.kt", source))
            symbolProcessorProviders = mutableListOf(ProcessorProvider())
            kspProcessorOptions = processorOptions.toMutableMap()
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()
    }

    private companion object {
        val BASE_IMPORTS = listOf(
            "io.github.sahsenvar.kmemory.annotation.Erase",
            "io.github.sahsenvar.kmemory.annotation.EraseAll",
            "io.github.sahsenvar.kmemory.annotation.Preferences",
            "io.github.sahsenvar.kmemory.annotation.Read",
            "io.github.sahsenvar.kmemory.annotation.Write",
            "kotlinx.coroutines.flow.Flow",
        )
    }
}
