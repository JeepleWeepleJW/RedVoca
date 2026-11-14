package com.stai.pleos_red_light_voca_app.presentation.learning

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stai.pleos_red_light_voca_app.R
import com.stai.pleos_red_light_voca_app.core.common.LearningConfig
import com.stai.pleos_red_light_voca_app.core.entity.Content
import com.stai.pleos_red_light_voca_app.domain.gate.GatePhase
import com.stai.pleos_red_light_voca_app.domain.learning.PlaybackEnd
import com.stai.pleos_red_light_voca_app.domain.learning.PlaybackPhase
import com.stai.pleos_red_light_voca_app.domain.session.AudioStatus
import com.stai.pleos_red_light_voca_app.presentation.theme.VocaColors as Ink

private val LayoutScale = staticCompositionLocalOf { 1f }
private val stopSpeed = LearningConfig().stopSpeedKmh
@Composable
private fun Int.ui() = (this * LayoutScale.current).dp

@Composable
fun LearningContent(
    state: LearningUiState,
    onAction: (LearningAction) -> Unit,
    modifier: Modifier = Modifier,
    showStatus: Boolean = false,
    debugPanel: @Composable () -> Unit = {},
) {
    val s = state.learning
    val locked = (s.vehicle.speedKmh ?: 0f) > stopSpeed || s.learningOn && (
        s.vehicle.speedKmh == null || s.vehicle.speedFailure != null || s.vehicle.gearFailure != null)
    BackHandler(enabled = locked || s.learningOn || state.pane != LearningPane.HOME) {
        if (!locked && !s.learningOn) {
            onAction(LearningAction.GoBack)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Ink.Bg)) {
        val height = maxHeight
        val scale = minOf(maxWidth.value / 1920f, height.value / 1080f).coerceIn(.38f, 1.3f)
        CompositionLocalProvider(LayoutScale provides scale) {
            val hasDebugPanel = showStatus && !locked && !s.learningOn
            Column(if (hasDebugPanel) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth().height(height).padding(80.ui()),
                    verticalArrangement = Arrangement.spacedBy(28.ui()),
                ) {
                    when {
                        locked -> CenterMessage("주행 중")
                        state.isLoading -> CenterMessage("불러오는 중")
                        state.catalog == null -> CenterMessage(state.loadErrorMessage ?: "학습을 불러올 수 없어요")
                        else -> LearningPage(state, onAction)
                    }
                }
                if (hasDebugPanel) debugPanel()
            }
        }
    }
}

@Composable
private fun ColumnScope.CenterMessage(text: String) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Label(text, 80, bold = true)
    }
}

private val languageNames = mapOf("en" to "영어", "ja" to "일본어", "zh" to "중국어", "es" to "스페인어")
private val nativeNames = mapOf("en" to "English", "ja" to "日本語", "zh" to "中文", "es" to "Español")
private fun languageName(id: String) = languageNames[id] ?: id
private val Content.topic get() = title.substringAfter(": ").trim()

@Composable
private fun ColumnScope.LearningPage(state: LearningUiState, act: (LearningAction) -> Unit) {
    val s = state.learning
    val contents = state.catalog?.contents.orEmpty()
    val chosen = contents.find { it.contentId == s.contentId }
    val lang = if (s.learningOn) chosen?.languageId else s.selectedLanguageId
    val lessons = contents.filter { it.languageId == lang }
    val day = lessons.indexOf(chosen) + 1
    val language = languageName(lang ?: "en")
    val header = "$language · Day$day"
    if (s.learningOn && chosen != null) {
        PlaybackPage(state, chosen, header, act)
        return
    }
    when (state.pane) {
        LearningPane.HOME, LearningPane.LANGUAGE_PICKER -> LanguagePage(state, contents, act)
        LearningPane.CURRICULUM -> CurriculumPage(state, lessons, language, act)
        LearningPane.READY -> if (chosen != null) ReadyPage(state, chosen, header, act)
    }
}

private fun nextLesson(lessons: List<Content>, completed: Map<String, Int>) =
    lessons.firstOrNull { (completed[it.contentId] ?: 0) < it.chapters.size } ?: lessons.firstOrNull()

@Composable
private fun ColumnScope.LanguagePage(state: LearningUiState, contents: List<Content>, act: (LearningAction) -> Unit) {
    val s = state.learning
    val enrolled = s.enrolledLanguageIds
    val available = languageNames.keys.filterNot { it in enrolled }
    val languageList = available.joinToString(" | ") { languageName(it) }
    val context = LocalContext.current
    val picker = state.pane == LearningPane.LANGUAGE_PICKER
    val pickerIds = languageNames.keys.filter { it in available || it == state.languageChosenInPicker }
    val first = enrolled.isEmpty()
    Header(if (picker || first) "공부할 언어 고르기" else "어떤 언어를 학습할까요?",
        if (picker || first) "공부할 언어를 선택하세요." else "기존 학습을 이어가거나 새로운 언어를 추가하세요.",
        onBack = if (picker) ({ act(LearningAction.GoBack) }) else null)
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
        if (first && !picker) {
            Choice("새로운 학습", "학습할 언어를 선택하세요", languageList,
                Modifier.widthIn(max = 864.ui()).fillMaxWidth(), outline = true) { act(LearningAction.ClickAddLanguage) }
        } else {
            val cards: @Composable (Modifier) -> Unit = { slot ->
                if (picker) {
                    pickerIds.forEach { id ->
                        val open = contents.any { it.languageId == id }
                        val detail = nativeNames[id].orEmpty()
                        Choice("새로운 학습", languageName(id), detail, slot) {
                            if (open) act(LearningAction.SelectLanguage(id))
                            else Toast.makeText(context, "${languageName(id)} 학습은 아직 열리지 않았어요.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    enrolled.forEach { id ->
                        val list = contents.filter { it.languageId == id }
                        val resume = list.find {
                            it.contentId == s.activeContentIdsByLanguage[id] &&
                                (s.completedByContent[it.contentId] ?: 0) < it.chapters.size
                        } ?: nextLesson(list, s.completedByContent)
                        Choice("기존 학습", languageName(id), "Day${list.indexOf(resume) + 1}", slot) {
                            act(LearningAction.OpenExistingLearning(id))
                        }
                    }
                    if (available.isNotEmpty()) Choice("새로운 학습", "학습할 언어를 선택하세요", languageList, slot, outline = true) {
                        act(LearningAction.ClickAddLanguage)
                    }
                }
            }
            if (maxWidth < 520.dp) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cards(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.ui())) {
                    cards(Modifier.weight(1f))
                }
            }
        }
    }
}

private val upcomingTopics = listOf(
    "인사와 소개", "식사와 주문", "여행과 숙소", "쇼핑과 결제", "시간과 약속",
    "건강과 생활", "날씨와 계절", "감정과 대화", "문화와 취미", "복습과 정리",
)

@Composable
private fun ColumnScope.CurriculumPage(state: LearningUiState, lessons: List<Content>, language: String, act: (LearningAction) -> Unit) {
    val s = state.learning
    Header("$language 커리큘럼", "좌우로 넘겨 학습을 선택하세요.") { act(LearningAction.GoBack) }
    val today = nextLesson(lessons, s.completedByContent)
    val items = lessons.mapIndexed { i, content ->
        val done = s.completedByContent[content.contentId] ?: 0
        CurriculumCardModel(content.contentId, "Day${i + 1}", content.topic, content.chapters.size, status = when {
            done >= content.chapters.size -> CurriculumCardStatus.COMPLETED
            content == today -> CurriculumCardStatus.CURRENT
            else -> CurriculumCardStatus.UPCOMING
        }, completedWords = done)
    }
    val previews = if (s.selectedLanguageId == "en") {
        upcomingTopics.mapIndexedNotNull { index, topic ->
            val day = index + 3
            if (day <= lessons.size) null else CurriculumCardModel(
                id = "preview-en-$day", dayLabel = "Day$day", title = topic,
                wordCount = 0, status = CurriculumCardStatus.UNRELEASED,
            )
        }
    } else emptyList()
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        CurriculumCarousel(items + previews, today?.contentId, { item ->
            if (item.status == CurriculumCardStatus.UNRELEASED) {
                Toast.makeText(context, "${item.dayLabel} 학습은 아직 열리지 않았어요.", Toast.LENGTH_SHORT).show()
            } else act(LearningAction.SelectContent(item.id))
        })
    }
}

@Composable
private fun ColumnScope.ReadyPage(state: LearningUiState, chosen: Content, header: String, act: (LearningAction) -> Unit) {
    val s = state.learning
    Header(header, chosen.topic) { act(LearningAction.GoBack) }
    val checking = state.audio == AudioStatus.Checking
    val unavailable = !checking && !state.audio.isReadyFor(chosen.contentId)
    if (unavailable) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
            Column(verticalArrangement = Arrangement.spacedBy(20.ui())) {
                Label("음원이 준비되지 않았어요", 52, bold = true)
                Label("다른 콘텐츠를 선택해 학습을 시작할 수 있어요.", 28, Ink.TextMuted)
            }
        }
        ActionButton("다른 콘텐츠 고르기", R.drawable.ic_play_circle, Modifier.widthIn(max = 680.ui()).fillMaxWidth()) {
            act(LearningAction.OpenCurriculum)
        }
    } else {
        CenterMessage("오늘 ${chosen.chapters.size}개 단어")
        Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 680.ui()).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.ui())) {
            val refusal = state.startRefusedMessage ?: s.disabledReason?.let { "차량 신호를 확인할 수 없어요" }
            if (refusal != null) Label(refusal, 22, Ink.TextMuted)
            ActionButton(if (checking) "음원 확인 중" else "학습 시작", R.drawable.ic_play_circle, Modifier.fillMaxWidth(),
                enabled = !checking && s.disabledReason == null) { act(LearningAction.ClickStart) }
            val shape = RoundedCornerShape(12.ui())
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(Ink.Surface)
                .border(1.dp, Ink.Border, shape).clickable(role = Role.Button) { act(LearningAction.OpenCurriculum) }
                .padding(horizontal = 24.ui(), vertical = 10.ui()), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Label("다른 콘텐츠 고르기", 22)
                Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.size(24.ui()), Ink.TextMuted)
            }
        }
    }

}

@Composable
private fun ColumnScope.PlaybackPage(state: LearningUiState, lesson: Content, header: String, act: (LearningAction) -> Unit) {
    val s = state.learning
    val index = lesson.indexOf(s.currentChapterId.orEmpty()).coerceAtLeast(0)
    val chapter = lesson.chapters[index]
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label("$header ${lesson.topic}", 30, Ink.TextMuted, modifier = Modifier.weight(1f))
        Label("${index + 1}/${lesson.chapters.size}", 32, bold = true)
    }
    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.ui(), Alignment.CenterVertically)) {
        val status = when {
            s.playback != PlaybackPhase.IDLE -> "듣는 중"
            s.lastPlaybackEnd == PlaybackEnd.ERROR -> "음원을 재생할 수 없어요"
            s.lastPlaybackEnd == PlaybackEnd.USER_PAUSE -> "일시정지"
            s.gatePhase == GatePhase.STOP_CONFIRMING -> "정차 확인 중"
            else -> "다음 정차 대기"
        }
        Label(status, 28, Ink.StatusReady)
        Label(chapter.word, 144, bold = true, centered = true)
        Label(chapter.meaningKo, 56, centered = true)
    }
    val listen = ListenButton.of(s, state.audio)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.ui())) {
        ActionButton(listen.label, if (listen.command == ListenCommand.PAUSE) R.drawable.ic_pause else R.drawable.ic_play_arrow,
            Modifier.weight(1f), enabled = listen.enabled) { act(LearningAction.ClickListen) }
        ActionButton("학습 종료", R.drawable.ic_stop_circle, Modifier.weight(1f), outline = true) { act(LearningAction.ClickStop) }
    }
}

@Composable
private fun Header(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.ui())) {
        if (onBack == null) Label("레드보카", 22, Ink.TextMuted)
        else Row(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onBack).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_arrow_back), null, Modifier.size(24.dp), Ink.Text)
            Label("이전", 24)
        }
        Label(title, 48, bold = true)
        Label(subtitle, 24, Ink.TextMuted)
    }
}

@Composable
private fun Choice(overline: String, title: String, detail: String, modifier: Modifier, outline: Boolean = false, click: () -> Unit) {
    val shape = RoundedCornerShape(16.ui())
    Column(modifier.height(360.ui()).clip(shape)
        .then(if (outline) Modifier.border(1.dp, Ink.Border, shape) else Modifier.background(Ink.Surface))
        .clickable(role = Role.Button, onClick = click).padding(40.ui()),
        verticalArrangement = Arrangement.spacedBy(16.ui(), Alignment.CenterVertically)) {
        Label(overline, 22, Ink.TextMuted)
        Label(title, 46, bold = true)
        Label(detail, 26, Ink.TextMuted)
    }
}

@Composable
private fun ActionButton(label: String, icon: Int, modifier: Modifier, enabled: Boolean = true, outline: Boolean = false, click: () -> Unit) {
    val shape = RoundedCornerShape(12.ui())
    val fg = when { !enabled -> Ink.TextMuted; outline -> Ink.Primary; else -> Ink.OnPrimary }
    Row(modifier.height(88.ui().coerceAtLeast(48.dp)).clip(shape)
        .then(if (outline) Modifier.border(2.dp, Ink.Primary, shape) else Modifier.background(if (enabled) Ink.Primary else Ink.DisabledBg))
        .clickable(enabled = enabled, role = Role.Button, onClick = click).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.ui(), Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(26.ui().coerceAtLeast(16.dp)), tint = fg)
        Label(label, 28, fg, bold = true)
    }
}

@Composable
private fun Label(text: String, size: Int, color: Color = Ink.Text, bold: Boolean = false, centered: Boolean = false, modifier: Modifier = Modifier) {
    val fontSize = (size * LayoutScale.current).coerceAtLeast(13f)
    Text(text, modifier, color = color, fontSize = fontSize.sp, lineHeight = (fontSize * 1.3f).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start, maxLines = 2, overflow = TextOverflow.Ellipsis)
}
