package com.bjch.fastregistration.automation

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

class AppPreferences(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val doctorName: String
        get() = preferences.getString(KEY_DOCTOR, TargetConfig.DEFAULT_DOCTOR_NAME)
            ?.trim()?.takeIf { it.isNotEmpty() } ?: TargetConfig.DEFAULT_DOCTOR_NAME

    val patientName: String
        get() = preferences.getString(KEY_PATIENT, TargetConfig.DEFAULT_PATIENT_NAME)
            ?.trim()?.takeIf { it.isNotEmpty() } ?: TargetConfig.DEFAULT_PATIENT_NAME

    val targetAppointmentDate: LocalDate
        get() = preferences.getString(KEY_TARGET_APPOINTMENT_DATE, null)
            ?.let(AppointmentDate::parse) ?: AppointmentDate.default()

    fun saveConfiguration(doctor: String, patient: String, targetDate: LocalDate): Boolean {
        val normalizedDoctor = doctor.trim()
        val normalizedPatient = patient.trim()
        if (normalizedDoctor.isEmpty() || normalizedPatient.isEmpty()) return false

        val patientChanged = patientName != normalizedPatient
        return preferences.edit()
            .putString(KEY_DOCTOR, normalizedDoctor)
            .putString(KEY_PATIENT, normalizedPatient)
            .putString(KEY_TARGET_APPOINTMENT_DATE, AppointmentDate.format(targetDate))
            .apply {
                if (patientChanged) {
                    remove(KEY_VERIFIED_PATIENT)
                    remove(KEY_VERIFIED_PATIENT_AT)
                }
            }
            .commit()
    }

    fun setRunning(value: Boolean): Boolean = preferences.edit()
        .putBoolean(KEY_RUNNING, value)
        .commit()

    fun isRunning(): Boolean = preferences.getBoolean(KEY_RUNNING, false)

    fun setRuntimeStatus(
        state: EngineState,
        page: PageType,
        slotStatus: SlotStatus,
        nearestDate: String? = null,
        message: String? = null,
        ready: Boolean? = null
    ) {
        preferences.edit()
            .putString(KEY_STATE, state.name)
            .putString(KEY_PAGE, page.displayName)
            .putString(KEY_SLOT_STATUS, slotStatus.displayName)
            .apply {
                if (nearestDate != null) putString(KEY_NEAREST_DATE, nearestDate)
                if (message != null) putString(KEY_MESSAGE, message)
                if (ready != null) putBoolean(KEY_READY, ready)
            }
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun stateName(): String = preferences.getString(KEY_STATE, EngineState.IDLE.name)!!
    fun pageName(): String = preferences.getString(KEY_PAGE, PageType.UNKNOWN.displayName)!!
    fun slotStatusName(): String = preferences.getString(KEY_SLOT_STATUS, SlotStatus.UNKNOWN.displayName)!!
    fun message(): String = preferences.getString(KEY_MESSAGE, "等待开始")!!

    fun setPatientVerified(name: String) {
        preferences.edit()
            .putString(KEY_VERIFIED_PATIENT, name)
            .putLong(KEY_VERIFIED_PATIENT_AT, System.currentTimeMillis())
            .apply()
    }

    fun isPatientVerified(expected: String, maximumAgeMs: Long = 24 * 60 * 60_000L): Boolean {
        val stored = preferences.getString(KEY_VERIFIED_PATIENT, null) ?: return false
        val age = System.currentTimeMillis() - preferences.getLong(KEY_VERIFIED_PATIENT_AT, 0L)
        return norm(stored) == norm(expected) && age in 0..maximumAgeMs
    }

    companion object {
        private const val FILE_NAME = "registration_settings"
        private const val KEY_DOCTOR = "target_doctor"
        private const val KEY_PATIENT = "target_patient"
        private const val KEY_TARGET_APPOINTMENT_DATE = "target_appointment_date"
        private const val KEY_RUNNING = "running"
        private const val KEY_STATE = "runtime_state"
        private const val KEY_PAGE = "runtime_page"
        private const val KEY_SLOT_STATUS = "runtime_slot_status"
        private const val KEY_MESSAGE = "runtime_message"
        private const val KEY_NEAREST_DATE = "runtime_nearest_date"
        private const val KEY_READY = "runtime_ready"
        private const val KEY_UPDATED_AT = "runtime_updated_at"
        private const val KEY_VERIFIED_PATIENT = "verified_patient"
        private const val KEY_VERIFIED_PATIENT_AT = "verified_patient_at"
    }
}
