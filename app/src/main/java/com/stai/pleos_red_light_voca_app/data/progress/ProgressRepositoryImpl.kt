package com.stai.pleos_red_light_voca_app.data.progress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.stai.pleos_red_light_voca_app.core.entity.Progress
import com.stai.pleos_red_light_voca_app.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import timber.log.Timber

/**
 * DataStore(Preferences) 기반 진도·학습 언어 저장소.
 * - `completed.<contentId>` = 마지막 완료 chapterId
 * - `activeContentId` = 마지막 학습 콘텐츠 ID
 * - `enrolledLanguageIds`, `selectedLanguageId`, `activeLanguage.<languageId>` = 언어별 학습 선택
 */
class ProgressRepositoryImpl(private val dataStore: DataStore<Preferences>) : ProgressRepository {

    override suspend fun load(): Progress {
        val prefs = dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()
        return prefs.toProgress()
    }

    override suspend fun saveCompleted(contentId: String, chapterId: String) {
        dataStore.edit { it[completedKey(contentId)] = chapterId }
    }

    override suspend fun clear(contentId: String) {
        dataStore.edit { it.remove(completedKey(contentId)) }
    }

    override suspend fun restartContent(contentId: String) {
        dataStore.edit {
            it.remove(completedKey(contentId))
            it[ACTIVE_KEY] = contentId
        }
    }

    override suspend fun saveActiveContent(contentId: String) {
        dataStore.edit { it[ACTIVE_KEY] = contentId }
    }

    override suspend fun saveLearningSelection(languageId: String, contentId: String) {
        dataStore.edit {
            it[ENROLLED_LANGUAGES_KEY] = it[ENROLLED_LANGUAGES_KEY].orEmpty() + languageId
            it[SELECTED_LANGUAGE_KEY] = languageId
            it[ACTIVE_KEY] = contentId
            it[activeLanguageKey(languageId)] = contentId
        }
    }

    override suspend fun migrateLearningSelections(
        enrolledLanguageIds: Set<String>,
        selectedLanguageId: String?,
        activeContentIdsByLanguage: Map<String, String>,
    ) {
        dataStore.edit { prefs ->
            prefs[ENROLLED_LANGUAGES_KEY] = prefs[ENROLLED_LANGUAGES_KEY].orEmpty() + enrolledLanguageIds
            selectedLanguageId?.let { prefs[SELECTED_LANGUAGE_KEY] = it }
            activeContentIdsByLanguage.forEach { (languageId, contentId) ->
                prefs[activeLanguageKey(languageId)] = contentId
            }
        }
    }

    companion object {
        internal const val COMPLETED_PREFIX = "completed."
        internal val ACTIVE_KEY = stringPreferencesKey("activeContentId")
        internal val ENROLLED_LANGUAGES_KEY = stringSetPreferencesKey("enrolledLanguageIds")
        internal val SELECTED_LANGUAGE_KEY = stringPreferencesKey("selectedLanguageId")
        internal const val ACTIVE_LANGUAGE_PREFIX = "activeLanguage."
        private fun completedKey(contentId: String) = stringPreferencesKey(COMPLETED_PREFIX + contentId)
        private fun activeLanguageKey(languageId: String) = stringPreferencesKey(ACTIVE_LANGUAGE_PREFIX + languageId)

        @Volatile private var instance: ProgressRepositoryImpl? = null

        /**
         * 같은 파일에 활성 DataStore가 둘이면 예외가 나므로 프로세스당 하나만 만든다.
         * 이 함수가 단일 인스턴스(더블 체크 락 싱글턴)를 보장하므로 여러 번 호출해도 안전하다.
         * [log]는 진도 파일 손상 시 경고 로그(기본은 Timber 경고).
         */
        fun create(context: Context, log: (String) -> Unit = { Timber.w(it) }): ProgressRepositoryImpl =
            instance ?: synchronized(this) {
                instance ?: ProgressRepositoryImpl(
                    PreferenceDataStoreFactory.create(
                        corruptionHandler = ReplaceFileCorruptionHandler { e ->
                            log("progress file corrupted, reset to empty: ${e.message}")
                            emptyPreferences()
                        },
                    ) {
                        context.applicationContext.preferencesDataStoreFile("learning_progress")
                    },
                ).also { instance = it }
            }
    }
}

private fun Preferences.toProgress(): Progress {
    val completed = LinkedHashMap<String, String>()
    val activeByLanguage = LinkedHashMap<String, String>()
    for ((key, value) in asMap()) {
        if (key.name.startsWith(ProgressRepositoryImpl.COMPLETED_PREFIX) && value is String) {
            completed[key.name.removePrefix(ProgressRepositoryImpl.COMPLETED_PREFIX)] = value
        }
        if (key.name.startsWith(ProgressRepositoryImpl.ACTIVE_LANGUAGE_PREFIX) && value is String) {
            activeByLanguage[key.name.removePrefix(ProgressRepositoryImpl.ACTIVE_LANGUAGE_PREFIX)] = value
        }
    }
    return Progress(
        lastCompletedChapterIds = completed,
        activeContentId = this[ProgressRepositoryImpl.ACTIVE_KEY],
        enrolledLanguageIds = this[ProgressRepositoryImpl.ENROLLED_LANGUAGES_KEY].orEmpty(),
        selectedLanguageId = this[ProgressRepositoryImpl.SELECTED_LANGUAGE_KEY],
        activeContentIdsByLanguage = activeByLanguage,
    )
}
