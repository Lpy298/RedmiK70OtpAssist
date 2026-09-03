package com.bjch.fastregistration.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AppointmentDateTest {
    @Test
    fun parsesOnlyTheConfiguredIsoDate() {
        assertEquals(LocalDate.of(2026, 9, 10), AppointmentDate.parse("2026-09-10"))
        assertNull(AppointmentDate.parse("9月10日"))
    }

    @Test
    fun defaultsToTheNextEleventh() {
        assertEquals(
            LocalDate.of(2026, 9, 11),
            AppointmentDate.default(LocalDate.of(2026, 9, 3))
        )
        assertEquals(
            LocalDate.of(2026, 10, 11),
            AppointmentDate.default(LocalDate.of(2026, 9, 12))
        )
    }
}

