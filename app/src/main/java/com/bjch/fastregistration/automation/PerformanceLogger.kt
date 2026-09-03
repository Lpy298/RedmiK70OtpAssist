package com.bjch.fastregistration.automation

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class PerformanceLogger(private val context: Context) {
    private val marks = ConcurrentHashMap<String, Long>()
    private val logFile = File(context.filesDir, "automation.log")
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)

    @Synchronized
    fun event(name: String, details: String = "", nanoTime: Long = SystemClock.elapsedRealtimeNanos()) {
        marks[name] = nanoTime
        rotateIfNeeded()
        logFile.appendText(
            "${timestampFormat.format(Date())}\t$nanoTime\t$name\t${singleLine(details)}\n",
            Charsets.UTF_8
        )
    }

    @Synchronized
    fun eventOnce(name: String, details: String = "", nanoTime: Long = SystemClock.elapsedRealtimeNanos()) {
        if (marks.putIfAbsent(name, nanoTime) != null) return
        rotateIfNeeded()
        logFile.appendText(
            "${timestampFormat.format(Date())}\t$nanoTime\t$name\t${singleLine(details)}\n",
            Charsets.UTF_8
        )
    }

    @Synchronized
    fun info(message: String) {
        rotateIfNeeded()
        logFile.appendText(
            "${timestampFormat.format(Date())}\t${SystemClock.elapsedRealtimeNanos()}\tINFO\t${singleLine(message)}\n",
            Charsets.UTF_8
        )
    }

    fun clearFastPathMarks() {
        FAST_PATH_MARKS.forEach(marks::remove)
    }

    fun summary(targetDoctor: String): String {
        fun elapsed(start: String, end: String): String {
            val from = marks[start] ?: return "—"
            val to = marks[end] ?: return "—"
            return String.format(Locale.US, "%.1fms", (to - from) / 1_000_000.0)
        }

        return buildString {
            appendLine("${targetDoctor}有号 detected → click: ${elapsed("target_doctor_available_detected", "target_doctor_clicked")}")
            appendLine("click → time dialog: ${elapsed("target_doctor_clicked", "time_dialog_opened")}")
            appendLine("time dialog → selected: ${elapsed("time_dialog_opened", "time_clicked")}")
            appendLine("time selected → confirmation page: ${elapsed("time_clicked", "booking_confirm_page_opened")}")
            appendLine("confirm booking → system dialog: ${elapsed("confirm_booking_clicked", "system_dialog_opened")}")
            appendLine("system confirm → booking success: ${elapsed("system_confirm_clicked", "booking_success_detected")}")
            append("FAST PATH TOTAL: ${elapsed("target_doctor_available_detected", "booking_success_detected")}")
        }
    }

    @Synchronized
    fun saveBookingResult(result: BookingResult, summary: String) {
        val json = JSONObject()
            .put("doctor", result.doctor)
            .put("department", result.department)
            .put("patient", result.patient)
            .put("date", result.date)
            .put("time", result.time)
            .put("campus", result.campus)
            .put("success_wall_time", result.successWallTime)
            .put("performance", summary)
        File(context.filesDir, "booking_result.json").writeText(json.toString(2), Charsets.UTF_8)
        info(summary)
    }

    fun readAll(): String = if (logFile.exists()) logFile.readText(Charsets.UTF_8) else "暂无日志"

    private fun rotateIfNeeded() {
        if (logFile.exists() && logFile.length() > 2_000_000L) {
            val backup = File(context.filesDir, "automation.previous.log")
            if (backup.exists()) backup.delete()
            logFile.renameTo(backup)
        }
    }

    private fun singleLine(value: String): String = value.replace('\n', ' ').replace('\t', ' ')

    companion object {
        private val FAST_PATH_MARKS = setOf(
            "target_doctor_available_detected",
            "target_doctor_clicked",
            "time_dialog_opened",
            "time_clicked",
            "booking_confirm_page_opened",
            "confirm_booking_clicked",
            "system_dialog_opened",
            "system_confirm_clicked",
            "booking_success_detected"
        )
    }
}

