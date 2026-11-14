package com.stai.pleos_red_light_voca_app.presentation.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class CurriculumCarouselTest {
    @Test fun centered_card_uses_maximum_scale() {
        assertEquals(1.06f, curriculumCardScale(0f, 300f), 0.0001f)
    }

    @Test fun scale_decreases_continuously_away_from_center() {
        assertEquals(1.045f, curriculumCardScale(75f, 300f), 0.0001f)
        assertEquals(1.03f, curriculumCardScale(150f, 300f), 0.0001f)
        assertEquals(1.015f, curriculumCardScale(225f, 300f), 0.0001f)
    }

    @Test fun distant_card_keeps_original_scale() {
        assertEquals(1f, curriculumCardScale(300f, 300f), 0.0001f)
        assertEquals(1f, curriculumCardScale(600f, 300f), 0.0001f)
    }

    @Test fun invalid_influence_distance_keeps_original_scale() {
        assertEquals(1f, curriculumCardScale(0f, 0f), 0.0001f)
    }
}
