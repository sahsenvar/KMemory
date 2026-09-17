package io.github.sahsenvar.kmemory.sample

import io.github.sahsenvar.kmemory.annotation.EraseAll
import io.github.sahsenvar.kmemory.annotation.Erase
import io.github.sahsenvar.kmemory.annotation.Preferences
import io.github.sahsenvar.kmemory.annotation.Read
import io.github.sahsenvar.kmemory.annotation.Write
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Nesne (OBJECT) yolunu kanitlayan ornek tip; diskte JSON metni olarak durur. */
@Serializable
data class ProfileSample(val id: String, val name: String)

/**
 * Islemcinin urettigi kodun davranisini dogrulayan ornek arayuz.
 *
 * Anahtarlar companion'daki `const` sabitlerdir: tuketicinin gercekte yazacagi bicim budur ve
 * KSP'nin anotasyon argumaninda sabit katlamayi (constant folding) yaptigini da kanitlar.
 */
@Preferences(name = "sample.preferences_pb")
interface SamplePreferences {

    @Read(KEY_COUNT)
    fun readCount(): Flow<Int?>

    @Read(KEY_COUNT)
    suspend fun readCountOnce(): Int?

    @Write(KEY_COUNT)
    suspend fun writeCount(value: Int)

    @Erase(KEY_COUNT)
    suspend fun eraseCount()

    @Read(KEY_PROFILE)
    fun readProfile(): Flow<ProfileSample?>

    @Write(KEY_PROFILE)
    suspend fun writeProfile(value: ProfileSample)

    @Read(KEY_LABEL)
    fun readLabel(): Flow<String?>

    @Write(KEY_LABEL)
    suspend fun writeLabel(value: String)

    @EraseAll
    suspend fun eraseAll()

    companion object {
        private const val KEY_COUNT = "3f1c0b2e-0001-4000-8000-000000000001"
        private const val KEY_PROFILE = "3f1c0b2e-0002-4000-8000-000000000002"
        private const val KEY_LABEL = "3f1c0b2e-0003-4000-8000-000000000003"
    }
}
