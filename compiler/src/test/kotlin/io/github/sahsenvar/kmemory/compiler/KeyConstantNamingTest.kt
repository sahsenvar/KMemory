package io.github.sahsenvar.kmemory.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import io.github.sahsenvar.kmemory.compiler.processor.ProcessorProvider
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Uretilen anahtar sabitlerinin ADLANDIRILMASI.
 *
 * Sabitin adi anahtarin metninden DEGIL, grubun ilk bildirilen accessor'unun adindan turer:
 * Zad'in tum anahtarlari UUID oldugu icin metinden turetilen ad (`KEY_8B1D2C44000140008000_0`)
 * uretilen kodu ve stacktrace'i okunamaz kiliyordu.
 *
 * Anahtarin KENDISI degismez; degisen yalnizca tanimlayici adidir. Sabit adlari derleme
 * zamaninda inline edildigi icin bu bir guvenlik degisikligi DEGILDIR.
 */
@OptIn(ExperimentalCompilerApi::class)
class KeyConstantNamingTest {

    @Test
    fun `bir anahtari paylasan uc accessor tek ve okunabilir sabit uretir`() {
        val source = generatedSource(
            """
            @Read("8b1d2c44-0001-4000-8000-000000000001") fun readProfile(): Flow<Int?>
            @Write("8b1d2c44-0001-4000-8000-000000000001") suspend fun writeProfile(value: Int)
            @Erase("8b1d2c44-0001-4000-8000-000000000001") suspend fun eraseProfile()
            """
        )

        assertContains(source, """private const val KEY_NAME_PROFILE = "8b1d2c44-0001-4000-8000-000000000001"""")
        assertContains(source, "private val KEY_PROFILE = intPreferencesKey(KEY_NAME_PROFILE)")
    }

    @Test
    fun `camelCase accessor adi UPPER_SNAKE_CASE olur`() {
        val source = generatedSource(
            """
            @Read("k") fun readPinCode(): Flow<String?>
            @Write("k") suspend fun writePinCode(value: String)
            """
        )

        assertContains(source, """private const val KEY_NAME_PIN_CODE = "k"""")
    }

    @Test
    fun `carpismayan gruplar indeks tasimaz`() {
        val source = generatedSource(
            """
            @Read("a") fun readProfile(): Flow<Int?>
            @Read("b") fun readSearchHistory(): Flow<String?>
            """
        )

        assertContains(source, """private const val KEY_NAME_PROFILE = "a"""")
        assertContains(source, """private const val KEY_NAME_SEARCH_HISTORY = "b"""")
        // Okunabilirlik indeks EKLEYEREK degil, GEREKTIGINDE ekleyerek korunur.
        assertFalse(source.contains("KEY_PROFILE_"), source)
        assertFalse(source.contains("KEY_SEARCH_HISTORY_"), source)
    }

    @Test
    fun `ayni tabani ureten iki grubun IKISI DE indekslenir`() {
        val result = compile(
            """
            @Read("a") fun readProfile(): Flow<Int?>
            @Write("a") suspend fun writeProfile(value: Int)
            @Read("b") fun getProfile(): Flow<String?>
            @Write("b") suspend fun setProfile(value: String)
            """
        )

        // Uretilen dosya DERLENMELI: iki sabit ayni adi tasirsa companion
        // "Conflicting declarations" ile duser.
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val source = readGenerated(result)
        assertContains(source, """private const val KEY_NAME_PROFILE_0 = "a"""")
        assertContains(source, """private const val KEY_NAME_PROFILE_1 = "b"""")
    }

    @Test
    fun `onek tasimayan fonksiyon adi oldugu gibi kullanilir`() {
        val source = generatedSource("""@Read("k") fun flag(): Flow<Boolean?>""")

        assertContains(source, """private const val KEY_NAME_FLAG = "k"""")
    }

    @Test
    fun `yalnizca onekten ibaret ad kirpilmis hex'e duser`() {
        val source = generatedSource(
            """
            @Read("k") fun read(): Flow<Int?>
            @Write("k") suspend fun write(value: Int)
            """
        )

        // Geriye ad kalmadi; bugunku kirpilmis-hex + grup indeksi bicimi devreye girer.
        assertContains(source, """private const val KEY_NAME_K_0 = "k"""")
    }

    // --- altyapi -------------------------------------------------------------------------

    private fun generatedSource(body: String): String {
        val result = compile(body)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return readGenerated(result)
    }

    /**
     * Uretilen `TestPreferencesImpl.kt` dosyasinin METNI.
     *
     * Metin okunur cunku bu testin konusu davranis degil, uretilen kodun OKUNABILIRLIGIDIR;
     * sinif yuklenerek sabit adlari dogrulanamaz (`const` alanlar inline edilir).
     */
    private fun readGenerated(result: JvmCompilationResult): String =
        result.outputDirectory.parentFile.walkTopDown()
            .firstOrNull { it.name == "$IMPLEMENTATION.kt" }
            ?.readText()
            ?: error("uretilen kaynak bulunamadi: ${result.outputDirectory.parentFile}")

    private fun compile(body: String): JvmCompilationResult {
        val source = buildString {
            appendLine("package fixture")
            appendLine()
            BASE_IMPORTS.forEach { appendLine("import $it") }
            appendLine()
            appendLine("""@Preferences(name = "test.preferences_pb")""")
            appendLine("interface TestPreferences {")
            appendLine(body.trimIndent().prependIndent("    "))
            appendLine("}")
        }

        return KotlinCompilation().apply {
            useKsp2()
            sources = listOf(SourceFile.kotlin("TestPreferences.kt", source))
            symbolProcessorProviders = mutableListOf(ProcessorProvider())
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()
    }

    private companion object {

        const val IMPLEMENTATION = "TestPreferencesImpl"

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
