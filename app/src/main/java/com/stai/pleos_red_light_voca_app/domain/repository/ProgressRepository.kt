package com.stai.pleos_red_light_voca_app.domain.repository

import com.stai.pleos_red_light_voca_app.core.entity.Progress

interface ProgressRepository {
    suspend fun load(): Progress

    /** 챕터 완료 기록. STATE_ENDED에서만 호출한다. */
    suspend fun saveCompleted(contentId: String, chapterId: String)

    /** 콘텐츠 진도 초기화(완료한 콘텐츠를 다시 시작할 때). */
    suspend fun clear(contentId: String)

    /** 완료 콘텐츠 재시작: 완료 기록 삭제와 활성 콘텐츠 저장을 한 번에(원자적으로) 한다. */
    suspend fun restartContent(contentId: String)

    suspend fun saveActiveContent(contentId: String)

    /** 언어 등록과 그 언어에서 선택한 콘텐츠를 한 번에 저장한다. */
    suspend fun saveLearningSelection(languageId: String, contentId: String) {
        saveActiveContent(contentId)
    }

    /** 기존 단일 콘텐츠 진도를 다국어 학습 정보로 올릴 때 사용한다. */
    suspend fun migrateLearningSelections(
        enrolledLanguageIds: Set<String>,
        selectedLanguageId: String?,
        activeContentIdsByLanguage: Map<String, String>,
    ) = Unit
}
