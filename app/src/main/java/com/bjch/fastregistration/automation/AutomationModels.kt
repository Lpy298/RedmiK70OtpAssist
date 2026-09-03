package com.bjch.fastregistration.automation

import java.time.LocalDate

object TargetConfig { const val WECHAT_PACKAGE="com.tencent.mm"; const val HOSPITAL="北京大学肿瘤医院"; const val DEFAULT_DOCTOR_NAME=""; const val TARGET_DEPARTMENT="胸部肿瘤内一科门诊"; const val TARGET_CATEGORY="肿瘤内科"; const val DEFAULT_PATIENT_NAME="" }
enum class EngineState { IDLE, OPEN_WECHAT, FIND_WECHAT_HOME, PULL_RECENT_MINIPROGRAMS, OPEN_HOSPITAL_MINIPROGRAM, OPEN_REGISTRATION, OPEN_OUTPATIENT_SOURCE, SELECT_PATIENT, SELECT_ONCOLOGY_CATEGORY, SELECT_DEPARTMENT, SELECT_DATE, FIND_DOCTOR, CLICK_DOCTOR_REMAINING, SELECT_TIME, VERIFY_CONFIRMATION, CONFIRM_BOOKING, SUCCESS, STOPPED, ERROR }
enum class PageType(val displayName:String) { UNKNOWN("未识别"), WECHAT_HOME("微信主页"), RECENT_MINIPROGRAMS("最近使用的小程序"), HOME("北肿首页"), REGISTRATION_ENTRY("预约挂号"), PATIENT_PICKER("选择就诊人"), DEPARTMENT_LIST("科室列表"), DEPARTMENT_SLOTS("号源"), TIME_PICKER("选择就诊时间"), BOOKING_CONFIRM("确认挂号信息"), SYSTEM_DIALOG("系统提示"), SUCCESS_DIALOG("预约成功") }
enum class SlotStatus(val displayName:String) { NO_SLOT("无号"), FULL("约满"), WAITLIST("候补"), UPCOMING("即将放号"), AVAILABLE("有号"), UNKNOWN("未识别") }
enum class ServiceCommand { START_GRAB, STOP }
data class SlotCandidate(val doctor:String,val date:LocalDate,val period:String?,val timeRange:String?,val campus:String?,val type:String?,val available:Boolean)
data class BookingResult(val doctor:String,val department:String,val patient:String,val date:String?,val time:String?,val campus:String?,val successWallTime:Long)
