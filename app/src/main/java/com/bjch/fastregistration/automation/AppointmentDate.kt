package com.bjch.fastregistration.automation

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object AppointmentDate {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun parse(value: String): LocalDate? = try {
        LocalDate.parse(value.trim(), formatter)
    } catch (_: DateTimeParseException) {
        null
    }

    fun format(date: LocalDate): String = date.format(formatter)

    fun default(today: LocalDate = LocalDate.now()): LocalDate {
        val thisMonth = today.withDayOfMonth(11.coerceAtMost(today.lengthOfMonth()))
        return if (thisMonth.isBefore(today)) {
            today.plusMonths(1).withDayOfMonth(11)
        } else {
            thisMonth
        }
    }
}

