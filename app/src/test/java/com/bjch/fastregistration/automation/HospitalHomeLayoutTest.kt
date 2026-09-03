package com.bjch.fastregistration.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class HospitalHomeLayoutTest {
    @Test
    fun registrationCardPointMatchesTheRedmiK70Screenshot() {
        val (x, y) = HospitalHomeLayout.registrationCardTapPoint(
            screenWidth = 1_440,
            screenHeight = 3_200
        )

        assertEquals(1_058.4f, x, 0.01f)
        assertEquals(1_032f, y, 0.01f)
    }
}

