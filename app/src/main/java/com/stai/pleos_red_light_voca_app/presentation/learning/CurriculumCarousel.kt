package com.stai.pleos_red_light_voca_app.presentation.learning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stai.pleos_red_light_voca_app.presentation.theme.VocaColors
import kotlin.math.abs
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

enum class CurriculumCardStatus { COMPLETED, CURRENT, UPCOMING, UNRELEASED }

data class CurriculumCardModel(
    val id: String,
    val dayLabel: String,
    val title: String,
    val wordCount: Int,
    val completedWords: Int = 0,
    val status: CurriculumCardStatus,
)

/** 화면 중앙과의 거리에 따라 카드가 1.00~1.06배 사이에서 연속적으로 변하는 가로 커리큘럼. */
@Composable
fun CurriculumCarousel(
    items: List<CurriculumCardModel>,
    selectedId: String?,
    onSelected: (CurriculumCardModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val state = rememberLazyListState()
    val itemIds = items.map(CurriculumCardModel::id)
    val initialIndex = items.indexOfFirst { it.id == selectedId }
        .takeIf { it >= 0 }
        ?: items.indexOfFirst { it.status == CurriculumCardStatus.CURRENT }.coerceAtLeast(0)

    val centerIndex by remember(state, initialIndex) {
        derivedStateOf {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: initialIndex
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthBasedCardWidth = (maxWidth * 0.20f).coerceIn(224.dp, 352.dp)
        val cardWidth = if (maxHeight == Dp.Infinity) {
            widthBasedCardWidth
        } else {
            minOf(widthBasedCardWidth, (maxHeight - 32.dp).coerceAtLeast(0.dp) / 1.193f)
        }
        val cardHeight = cardWidth * 1.193f
        val contentScale = (cardWidth / 352.dp).coerceIn(0.64f, 1f)
        val edgePadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
        val itemSpacing = 24.dp
        val itemSpacingPx = with(LocalDensity.current) { itemSpacing.toPx() }

        LaunchedEffect(itemIds, initialIndex, maxWidth) {
            state.scrollToItem(initialIndex)
            val initialItem = snapshotFlow {
                state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == initialIndex }
            }.filterNotNull().first()
            val viewportCenter =
                (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2f
            state.scrollBy(initialItem.offset + initialItem.size / 2f - viewportCenter)
        }

        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = edgePadding, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                CurriculumCard(
                    item = item,
                    selected = index == centerIndex,
                    contentScale = contentScale,
                    onClick = { onSelected(item) },
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                        .graphicsLayer {
                            val layoutInfo = state.layoutInfo
                            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            val viewportCenter =
                                (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                            val scale = itemInfo?.let { info ->
                                curriculumCardScale(
                                    distanceFromCenterPx = abs(info.offset + info.size / 2f - viewportCenter),
                                    influenceDistancePx = info.size + itemSpacingPx,
                                )
                            } ?: 1f
                            scaleX = scale
                            scaleY = scale
                        }
                        .testTag("curriculum-card-${item.id}"),
                )
            }
        }
    }
}

internal fun curriculumCardScale(
    distanceFromCenterPx: Float,
    influenceDistancePx: Float,
    maximumScaleIncrease: Float = 0.06f,
): Float {
    if (influenceDistancePx <= 0f) return 1f
    val proximity = 1f - (distanceFromCenterPx / influenceDistancePx).coerceIn(0f, 1f)
    return 1f + maximumScaleIncrease * proximity
}

@Composable
private fun CurriculumCard(
    item: CurriculumCardModel,
    selected: Boolean,
    contentScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = curriculumColors(item.status)
    val fill by animateColorAsState(if (selected) Color(0xFF15354E) else colors.container, label = "card fill")
    val stroke by animateColorAsState(if (selected) VocaColors.Primary else colors.border, label = "card outline")
    val shape = RoundedCornerShape(20.dp)
    Card(
        colors = CardDefaults.cardColors(containerColor = fill),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            stroke,
        ),
        shape = shape,
        modifier = modifier
            .clip(shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp * contentScale, vertical = 24.dp * contentScale),
        ) {
            Text(
                text = item.dayLabel,
                color = colors.secondaryText,
                fontSize = 28.sp * contentScale,
                lineHeight = 38.sp * contentScale,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = item.title,
                color = colors.primaryText,
                fontSize = 32.sp * contentScale,
                lineHeight = 42.sp * contentScale,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp * contentScale),
            )
            CurriculumCardDetails(
                item = item,
                colors = colors,
                contentScale = contentScale,
                modifier = Modifier.padding(top = 32.dp * contentScale),
            )
        }
    }
}

@Composable
private fun CurriculumCardDetails(
    item: CurriculumCardModel,
    colors: CurriculumColors,
    contentScale: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (item.status == CurriculumCardStatus.CURRENT) {
            Text(
                text = "${item.wordCount}개 단어",
                color = colors.primaryText,
                fontSize = 18.sp * contentScale,
                lineHeight = 26.sp * contentScale,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp * contentScale))
        }
        Text(
            text = curriculumFooter(item),
            color = colors.secondaryText,
            fontSize = 16.sp * contentScale,
            lineHeight = 24.sp * contentScale,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (item.status == CurriculumCardStatus.CURRENT) {
            Spacer(Modifier.height(12.dp * contentScale))
            LinearProgressIndicator(
                progress = { curriculumProgress(item) },
                color = VocaColors.Primary,
                trackColor = VocaColors.Track,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp * contentScale),
            )
        }
    }
}

private data class CurriculumColors(
    val container: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val border: Color,
)

private fun curriculumColors(status: CurriculumCardStatus) = when (status) {
    CurriculumCardStatus.COMPLETED -> CurriculumColors(
        Color(0xFF19342C), VocaColors.Text, Color(0xFFA4DDC1), Color(0xFF2B574A),
    )
    CurriculumCardStatus.CURRENT -> CurriculumColors(
        VocaColors.Surface, VocaColors.Text, Color(0xFFBBE1FA), VocaColors.DisabledBorder,
    )
    CurriculumCardStatus.UPCOMING, CurriculumCardStatus.UNRELEASED -> CurriculumColors(
        VocaColors.DisabledBg, VocaColors.Text, VocaColors.TextMuted, VocaColors.DisabledBorder,
    )
}

private fun curriculumFooter(item: CurriculumCardModel): String = when (item.status) {
    CurriculumCardStatus.COMPLETED -> "학습 완료 · ${item.completedWords}/${item.wordCount}"
    CurriculumCardStatus.CURRENT -> "오늘의 학습 · ${item.completedWords}/${item.wordCount}"
    CurriculumCardStatus.UPCOMING -> "학습 예정"
    CurriculumCardStatus.UNRELEASED -> "아직 열리지 않았어요"
}

private fun curriculumProgress(item: CurriculumCardModel): Float =
    if (item.wordCount <= 0) 0f else (item.completedWords.toFloat() / item.wordCount).coerceIn(0f, 1f)
