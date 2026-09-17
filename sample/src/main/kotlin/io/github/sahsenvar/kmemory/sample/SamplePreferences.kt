package io.github.sahsenvar.kmemory.sample

import io.github.sahsenvar.kmemory.annotation.EraseAll
import io.github.sahsenvar.kmemory.annotation.Erase
import io.github.sahsenvar.kmemory.annotation.Preferences
import io.github.sahsenvar.kmemory.annotation.Read
import io.github.sahsenvar.kmemory.annotation.Write
import io.github.sahsenvar.kmemory.sample.model.SearchHistorySample
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

    /** Tip argumanli deger: imzadaki `List<String>` uretilen koda birebir tasinmali. */
    @Read(KEY_ITEMS)
    fun readItems(): Flow<List<String>?>

    @Write(KEY_ITEMS)
    suspend fun writeItems(value: List<String>)

    /** Baska paketteki serilestirilebilir tipin listesi; import da uretilmek zorunda. */
    @Read(KEY_HISTORY)
    fun readHistory(): Flow<List<SearchHistorySample>?>

    @Write(KEY_HISTORY)
    suspend fun writeHistory(value: List<SearchHistorySample>)

    /** Nullable yazma: `null` verilince anahtar silinir. */
    @Read(KEY_TOKEN)
    suspend fun readToken(): String?

    @Write(KEY_TOKEN)
    suspend fun writeToken(value: String?)

    // Asagidaki iki anahtarin alfanumerikleri ilk 20 karakterde AYNI; uretilen sabit adlari
    // yalnizca bu onekten turerse companion'da "Conflicting declarations" olusur.
    @Read(KEY_NOTIFICATION_ENABLED)
    fun readNotificationEnabled(): Flow<Boolean?>

    @Write(KEY_NOTIFICATION_ENABLED)
    suspend fun writeNotificationEnabled(value: Boolean)

    @Read(KEY_NOTIFICATION_MUTED)
    fun readNotificationMuted(): Flow<Boolean?>

    @Write(KEY_NOTIFICATION_MUTED)
    suspend fun writeNotificationMuted(value: Boolean)

    @EraseAll
    suspend fun eraseAll()

    companion object {
        private const val KEY_COUNT = "3f1c0b2e-0001-4000-8000-000000000001"
        private const val KEY_PROFILE = "3f1c0b2e-0002-4000-8000-000000000002"
        private const val KEY_LABEL = "3f1c0b2e-0003-4000-8000-000000000003"
        private const val KEY_ITEMS = "3f1c0b2e-0004-4000-8000-000000000004"
        private const val KEY_TOKEN = "3f1c0b2e-0005-4000-8000-000000000005"
        private const val KEY_HISTORY = "3f1c0b2e-0006-4000-8000-000000000006"
        private const val KEY_NOTIFICATION_ENABLED = "notification_settings_enabled"
        private const val KEY_NOTIFICATION_MUTED = "notification_settings_muted"
    }
}
