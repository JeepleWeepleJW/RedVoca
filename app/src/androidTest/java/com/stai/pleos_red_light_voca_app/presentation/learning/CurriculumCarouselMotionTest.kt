package com.stai.pleos_red_light_voca_app.presentation.learning

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.stai.pleos_red_light_voca_app.presentation.theme.PleosredlightvocaappTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CurriculumCarouselMotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cards_grow_and_shrink_continuously_during_horizontal_drag() {
        val items = (1..5).map { day ->
            CurriculumCardModel(
                id = "day$day",
                dayLabel = "Day$day",
                title = "주제 $day",
                wordCount = 10,
                completedWords = if (day < 3) 10 else if (day == 3) 3 else 0,
                status = when {
                    day < 3 -> CurriculumCardStatus.COMPLETED
                    day == 3 -> CurriculumCardStatus.CURRENT
                    else -> CurriculumCardStatus.UPCOMING
                },
            )
        }

        composeRule.setContent {
            PleosredlightvocaappTheme {
                Box(Modifier.width(1200.dp).height(560.dp)) {
                    CurriculumCarousel(items, selectedId = "day3", onSelected = {})
                }
            }
        }
        composeRule.waitForIdle()

        val current = composeRule.onNodeWithTag("curriculum-card-day3")
        val next = composeRule.onNodeWithTag("curriculum-card-day4")
        val centeredCurrentWidth = current.fetchSemanticsNode().boundsInRoot.width
        val distantNextWidth = next.fetchSemanticsNode().boundsInRoot.width

        current.performTouchInput {
            down(center)
            repeat(12) { moveBy(Offset(-10f, 0f), 50L) }
            advanceEventTime(600L)
            up()
        }
        composeRule.waitForIdle()

        val movedCurrentWidth = current.fetchSemanticsNode().boundsInRoot.width
        val approachingNextWidth = next.fetchSemanticsNode().boundsInRoot.width
        assertTrue("중앙에서 멀어진 카드는 점차 작아져야 한다", movedCurrentWidth < centeredCurrentWidth)
        assertTrue("중앙으로 다가온 카드는 점차 커져야 한다", approachingNextWidth > distantNextWidth)
    }
}
