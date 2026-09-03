package com.bjch.fastregistration

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.bjch.fastregistration.automation.AppPreferences
import com.bjch.fastregistration.automation.AppointmentDate
import com.bjch.fastregistration.automation.RegistrationAccessibilityService
import com.bjch.fastregistration.automation.ServiceCommand
import com.bjch.fastregistration.automation.TargetConfig

@SuppressLint("SetTextI18n")
class MainActivity : Activity() {
    private lateinit var preferences: AppPreferences
    private lateinit var doctorInput: EditText
    private lateinit var patientInput: EditText
    private lateinit var targetDateInput: EditText
    private lateinit var runtimeText: TextView
    private lateinit var startButton: Button
    private val handler = Handler(Looper.getMainLooper())

    private val statusUpdater = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(this)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusUpdater)
    }

    override fun onPause() {
        saveConfiguration(showToast = false)
        handler.removeCallbacks(statusUpdater)
        super.onPause()
    }

    private fun buildContent(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(255, 249, 249)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(32))
        }
        scroll.addView(root)

        root.addView(text("北肿抢号助手", 27f, Color.rgb(32, 33, 36), true))

        root.addView(sectionTitle("医生"))
        doctorInput = EditText(this).apply {
            setText(preferences.doctorName)
            hint = TargetConfig.DEFAULT_DOCTOR_NAME
            isSingleLine = true
            textSize = 17f
        }
        root.addView(doctorInput, matchWrap())

        root.addView(sectionTitle("就诊人"))
        patientInput = EditText(this).apply {
            setText(preferences.patientName)
            hint = TargetConfig.DEFAULT_PATIENT_NAME
            isSingleLine = true
            textSize = 17f
        }
        root.addView(patientInput, matchWrap())

        root.addView(sectionTitle("就诊日期"))
        targetDateInput = EditText(this).apply {
            setText(AppointmentDate.format(preferences.targetAppointmentDate))
            hint = "2026-09-10"
            inputType = InputType.TYPE_CLASS_DATETIME
            isSingleLine = true
            textSize = 17f
        }
        root.addView(targetDateInput, matchWrap())

        startButton = Button(this).apply {
            text = "现在开始抢号"
            isAllCaps = false
            textSize = 18f
            setTextColor(Color.rgb(120, 0, 6))
            setOnClickListener { startGrab() }
        }
        root.addView(startButton, matchWrap(dp(58)))

        root.addView(Button(this).apply {
            text = "停止抢号"
            isAllCaps = false
            setOnClickListener {
                RegistrationAccessibilityService.sendCommand(this@MainActivity, ServiceCommand.STOP)
                preferences.setRunning(false)
                updateStatus()
            }
        }, matchWrap(dp(50)))

        root.addView(sectionTitle("当前状态"))
        runtimeText = text("", 15f, Color.rgb(32, 33, 36), false).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(runtimeText, matchWrap())

        return scroll
    }

    private fun startGrab() {
        if (!saveConfiguration(showToast = true)) return
        if (!RegistrationAccessibilityService.sendCommand(this, ServiceCommand.START_GRAB)) {
            Toast.makeText(this, "请启用“北肿预约辅助服务”，返回后再点开始", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            Toast.makeText(this, "已开始：微信主页下拉并打开第一个小程序", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveConfiguration(showToast: Boolean): Boolean {
        val doctor = doctorInput.text.toString().trim()
        val patient = patientInput.text.toString().trim()
        val targetDate = AppointmentDate.parse(targetDateInput.text.toString())
        if (doctor.isEmpty() || patient.isEmpty()) {
            if (showToast) Toast.makeText(this, "医生和就诊人不能为空", Toast.LENGTH_LONG).show()
            return false
        }
        if (targetDate == null) {
            if (showToast) Toast.makeText(this, "就诊日期格式应为 yyyy-MM-dd", Toast.LENGTH_LONG).show()
            return false
        }
        val saved = preferences.saveConfiguration(doctor, patient, targetDate)
        if (showToast && !saved) Toast.makeText(this, "设置保存失败", Toast.LENGTH_LONG).show()
        return saved
    }

    private fun updateStatus() {
        val accessibility = RegistrationAccessibilityService.isEnabled(this)
        startButton.isEnabled = true
        runtimeText.text = buildString {
            appendLine("辅助服务：${if (accessibility) "已启用" else "未启用"}")
            appendLine("运行状态：${if (preferences.isRunning()) "正在抢号" else "已停止"}")
            appendLine("目标：${preferences.doctorName} / ${preferences.patientName}")
            appendLine("日期：${AppointmentDate.format(preferences.targetAppointmentDate)}")
            appendLine("当前页面：${preferences.pageName()}")
            appendLine("号源状态：${preferences.slotStatusName()}")
            append("说明：${preferences.message()}")
        }
    }

    private fun sectionTitle(value: String): TextView = text(value, 18f, Color.rgb(120, 0, 6), true).apply {
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun matchWrap(height: Int = LinearLayout.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply {
            topMargin = dp(6)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
