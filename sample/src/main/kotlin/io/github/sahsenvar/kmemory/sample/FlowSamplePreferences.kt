package io.github.sahsenvar.kmemory.sample

import io.github.sahsenvar.kmemory.annotation.Erase
import io.github.sahsenvar.kmemory.annotation.EraseAll
import io.github.sahsenvar.kmemory.annotation.Preferences
import io.github.sahsenvar.kmemory.annotation.Read
import io.github.sahsenvar.kmemory.annotation.Write
import kotlinx.coroutines.flow.Flow

/**
 * Yazma/silmenin `Flow<Unit>` seklini kanitlayan ornek arayuz (spec §4.0).
 *
 * [SamplePreferences] `suspend` seklini kapsiyor; ikisi AYRI arayuzlerde duruyor cunku bir
 * arayuzde yalnizca tek bir `@EraseAll` bulunabilir. Ayri store adi da sart: ayni dosya icin
 * ikinci bir DataStore ornegi calisma aninda patlar.
 *
 * Zad'in kullanacagi sekil budur — hicbir uye `suspend` degil, hepsi `Flow` donuyor; boylece
 * `MemorySource`'lar `RemoteSource`'larla ayni sekli tasir.
 */
@Preferences(name = "flow_sample.preferences_pb")
interface FlowSamplePreferences {

    @Read(KEY_COUNT)
    fun readCount(): Flow<Int?>

    @Write(KEY_COUNT)
    fun writeCount(value: Int): Flow<Unit>

    @Erase(KEY_COUNT)
    fun eraseCount(): Flow<Unit>

    @Read(KEY_LABEL)
    fun readLabel(): Flow<String?>

    @Write(KEY_LABEL)
    fun writeLabel(value: String): Flow<Unit>

    /** Nesne yolu: `json.encodeToString` de Flow govdesinin icinde kalmali. */
    @Read(KEY_PROFILE)
    fun readProfile(): Flow<ProfileSample?>

    @Write(KEY_PROFILE)
    fun writeProfile(value: ProfileSample): Flow<Unit>

    /** Nullable yazmanin Flow sekli: `null` verilince anahtar silinir. */
    @Read(KEY_TOKEN)
    fun readToken(): Flow<String?>

    @Write(KEY_TOKEN)
    fun writeToken(value: String?): Flow<Unit>

    @EraseAll
    fun eraseAll(): Flow<Unit>

    companion object {
        private const val KEY_COUNT = "7a2d1c3f-0001-4000-8000-000000000001"
        private const val KEY_LABEL = "7a2d1c3f-0002-4000-8000-000000000002"
        private const val KEY_PROFILE = "7a2d1c3f-0003-4000-8000-000000000003"
        private const val KEY_TOKEN = "7a2d1c3f-0004-4000-8000-000000000004"
    }
}
