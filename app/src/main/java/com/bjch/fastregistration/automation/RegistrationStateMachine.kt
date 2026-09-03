package com.bjch.fastregistration.automation
import java.time.LocalDate
import kotlin.math.abs
data class Box(val left:Int,val top:Int,val right:Int,val bottom:Int){val width get()=right-left;val height get()=bottom-top;val centerX get()=(left+right)/2;val centerY get()=(top+bottom)/2;fun valid(w:Int,h:Int)=width in 2 until w&&height in 2 until h&&left>=0&&top>=0&&right<=w&&bottom<=h;fun sameRow(o:Box)=abs(centerY-o.centerY)<=maxOf(height,o.height)*2}
data class UiText(val text:String,val description:String="",val bounds:Box,val clickable:Boolean=false,val selected:Boolean=false){val labels get()=sequenceOf(text,description).map(::norm).filter{it.isNotEmpty()}.toSet();val merged get()=labels.joinToString("")}
data class PageSnapshot(val page:PageType,val width:Int,val height:Int,val items:List<UiText>,val signature:String=page.name+":"+items.joinToString("|"){it.merged}){fun exact(v:String)=items.filter{i->i.labels.any{it==norm(v)}};fun contains(v:String)=items.filter{it.merged.contains(norm(v))}}
data class GrabConfig(val doctor:String,val patient:String,val date:LocalDate)
sealed class MachineAction{data object LaunchWechat:MachineAction();data object Back:MachineAction();data object PullRecent:MachineAction();data object SwipeUp:MachineAction();data class Click(val target:UiText,val x:Int=target.bounds.centerX,val y:Int=target.bounds.centerY,val reason:String):MachineAction();data class Fail(val reason:String):MachineAction();data object None:MachineAction()}
data class StateSpec(val requiredPage:PageType?,val expectedNext:Set<PageType>,val timeoutMs:Long,val maxRetries:Int,val fallback:String)
data class MachineOutput(val state:EngineState,val action:MachineAction,val message:String,val retry:Int)
class RegistrationStateMachine(private val config:GrabConfig){
 var state=EngineState.IDLE;private set
 var retry=0;private set
 private var submittedAt=0L;private var submittedSignature:String?=null;private var oncologySelected=false
 val specs=mapOf(
 EngineState.OPEN_WECHAT to StateSpec(null,setOf(PageType.WECHAT_HOME,PageType.RECENT_MINIPROGRAMS,PageType.HOME),8000,2,"重新显式启动微信"),
 EngineState.FIND_WECHAT_HOME to StateSpec(null,setOf(PageType.WECHAT_HOME),3000,6,"逐层返回"),
 EngineState.PULL_RECENT_MINIPROGRAMS to StateSpec(PageType.WECHAT_HOME,setOf(PageType.RECENT_MINIPROGRAMS),3500,3,"重取树后重新下拉"),
 EngineState.OPEN_HOSPITAL_MINIPROGRAM to StateSpec(PageType.RECENT_MINIPROGRAMS,setOf(PageType.HOME),8000,3,"文字、截断名、首格中心"),
 EngineState.OPEN_REGISTRATION to StateSpec(PageType.HOME,setOf(PageType.REGISTRATION_ENTRY),6000,3,"节点、OCR、粉色卡片中心"),
 EngineState.OPEN_OUTPATIENT_SOURCE to StateSpec(PageType.REGISTRATION_ENTRY,setOf(PageType.PATIENT_PICKER,PageType.DEPARTMENT_LIST),5000,3,"门诊号源卡片中心"),
 EngineState.SELECT_PATIENT to StateSpec(PageType.PATIENT_PICKER,setOf(PageType.DEPARTMENT_LIST),5000,2,"仅精确姓名"),
 EngineState.SELECT_ONCOLOGY_CATEGORY to StateSpec(PageType.DEPARTMENT_LIST,setOf(PageType.DEPARTMENT_LIST),3000,3,"左栏精确文字"),
 EngineState.SELECT_DEPARTMENT to StateSpec(PageType.DEPARTMENT_LIST,setOf(PageType.DEPARTMENT_SLOTS),6000,3,"等待右栏更新"),
 EngineState.SELECT_DATE to StateSpec(PageType.DEPARTMENT_SLOTS,setOf(PageType.DEPARTMENT_SLOTS),1200,120,"按配置日期有限重试"),
 EngineState.FIND_DOCTOR to StateSpec(PageType.DEPARTMENT_SLOTS,setOf(PageType.DEPARTMENT_SLOTS),2000,8,"下滑医生列表"),
 EngineState.CLICK_DOCTOR_REMAINING to StateSpec(PageType.DEPARTMENT_SLOTS,setOf(PageType.TIME_PICKER),2000,20,"重新绑定同排按钮"),
 EngineState.SELECT_TIME to StateSpec(PageType.TIME_PICKER,setOf(PageType.BOOKING_CONFIRM),4000,3,"最早时间中心"),
 EngineState.VERIFY_CONFIRMATION to StateSpec(PageType.BOOKING_CONFIRM,setOf(PageType.SYSTEM_DIALOG),5000,3,"核验后确认"),
 EngineState.CONFIRM_BOOKING to StateSpec(PageType.SYSTEM_DIALOG,setOf(PageType.SUCCESS_DIALOG),7000,3,"系统红色确认"))
 fun start(now:Long=0):MachineOutput{state=EngineState.OPEN_WECHAT;retry=0;return submit(MachineAction.LaunchWechat,"显式启动微信",now,"")}
 fun stop():MachineOutput{state=EngineState.STOPPED;submittedSignature=null;return out(MachineAction.None,"用户停止：已取消全部任务")}
 fun observe(s:PageSnapshot,now:Long):MachineOutput{if(state in setOf(EngineState.STOPPED,EngineState.ERROR,EngineState.SUCCESS))return out(MachineAction.None,"终止状态");val changed=submittedSignature!=null&&s.signature!=submittedSignature;advance(s);if(changed&&state==EngineState.SELECT_ONCOLOGY_CATEGORY&&oncologySelected)state=EngineState.SELECT_DEPARTMENT;if(submittedSignature!=null){val spec=specs[state]?:return out(MachineAction.None,"等待页面证据");if(changed||(s.page in spec.expectedNext&&s.page!=spec.requiredPage)){submittedSignature=null;retry=0}else if(now-submittedAt<spec.timeoutMs)return out(MachineAction.None,"动作已提交，等待下一页面")else{submittedSignature=null;retry++;if(retry>spec.maxRetries)return fail("${state.name} 超过最大重试次数")}};return decide(s,now)}
 private fun advance(s:PageSnapshot){state=when{s.page==PageType.SUCCESS_DIALOG->EngineState.SUCCESS;s.page==PageType.SYSTEM_DIALOG->EngineState.CONFIRM_BOOKING;s.page==PageType.BOOKING_CONFIRM->EngineState.VERIFY_CONFIRMATION;s.page==PageType.TIME_PICKER->EngineState.SELECT_TIME;s.page==PageType.DEPARTMENT_SLOTS&&state.ordinal<EngineState.SELECT_DATE.ordinal->EngineState.SELECT_DATE;s.page==PageType.DEPARTMENT_LIST&&state.ordinal<EngineState.SELECT_ONCOLOGY_CATEGORY.ordinal->EngineState.SELECT_ONCOLOGY_CATEGORY;s.page==PageType.PATIENT_PICKER->EngineState.SELECT_PATIENT;s.page==PageType.REGISTRATION_ENTRY->EngineState.OPEN_OUTPATIENT_SOURCE;s.page==PageType.HOME->EngineState.OPEN_REGISTRATION;s.page==PageType.RECENT_MINIPROGRAMS->EngineState.OPEN_HOSPITAL_MINIPROGRAM;s.page==PageType.WECHAT_HOME->EngineState.PULL_RECENT_MINIPROGRAMS;else->state}}
 private fun decide(s:PageSnapshot,now:Long):MachineOutput=when(state){
  EngineState.OPEN_WECHAT,EngineState.FIND_WECHAT_HOME->submit(MachineAction.Back,"返回微信主页",now,s.signature)
  EngineState.PULL_RECENT_MINIPROGRAMS->submit(MachineAction.PullRecent,"下拉最近使用",now,s.signature)
  EngineState.OPEN_HOSPITAL_MINIPROGRAM->hospitalEntry(s)?.let{submit(MachineAction.Click(it,reason="打开北肿小程序"),"点击北肿小程序",now,s.signature)}?:fail("最近使用页面未找到北京大学肿瘤医院")
  EngineState.OPEN_REGISTRATION->click(s,listOf("预约挂号","患者到院就诊"),"粉色预约挂号卡片",now)
  EngineState.OPEN_OUTPATIENT_SOURCE->click(s,listOf("门诊号源"),"门诊号源",now)
  EngineState.SELECT_PATIENT->{val m=s.exact(config.patient);if(m.size!=1)fail("就诊人必须精确且唯一匹配：${config.patient}")else submit(MachineAction.Click(m.single(),reason="精确就诊人"),"精确选择${config.patient}",now,s.signature)}
  EngineState.SELECT_ONCOLOGY_CATEGORY->click(s,listOf(TargetConfig.TARGET_CATEGORY),"左侧肿瘤内科",now).also{if(it.action is MachineAction.Click)oncologySelected=true}
  EngineState.SELECT_DEPARTMENT->if(!oncologySelected)fail("安全门阻止：尚未选择肿瘤内科")else click(s,listOf(TargetConfig.TARGET_DEPARTMENT),"右侧目标科室",now)
  EngineState.SELECT_DATE->{val day=config.date.dayOfMonth.toString();val c=s.exact(day).filter{it.bounds.top<s.height*.42&&it.bounds.valid(s.width,s.height)};val chosen=c.singleOrNull{it.selected};if(chosen!=null){state=EngineState.FIND_DOCTOR;retry=0;out(MachineAction.None,"配置日期${day}已选中")}else if(c.size!=1)fail("日期${day}未唯一匹配")else submit(MachineAction.Click(c.single(),reason="配置日期${day}"),"点击配置日期${day}；即将放号不改变目标",now,s.signature)}
  EngineState.FIND_DOCTOR->if(s.exact(config.doctor).isNotEmpty()){state=EngineState.CLICK_DOCTOR_REMAINING;decide(s,now)}else submit(MachineAction.SwipeUp,"查找医生${config.doctor}",now,s.signature)
  EngineState.CLICK_DOCTOR_REMAINING->{val doctor=s.exact(config.doctor).singleOrNull()?:run{state=EngineState.FIND_DOCTOR;return decide(s,now)};val a=s.items.filter{it.bounds.valid(s.width,s.height)&&it.bounds.sameRow(doctor.bounds)&&Regex("^剩余[1-9]\\d*$").matches(it.merged)};if(a.size==1)submit(MachineAction.Click(a.single(),reason="${config.doctor}同排非零剩余"),"点击目标医生同排${a.single().merged}",now,s.signature)else out(MachineAction.None,"目标医生无可点击非零剩余；约满/候补/即将放号/剩余0均忽略")}
  EngineState.SELECT_TIME->{val t=s.items.filter{Regex("^\\d{1,2}:\\d{2}[-~]\\d{1,2}:\\d{2}$").matches(it.merged)}.sortedWith(compareBy({it.bounds.top},{it.bounds.left}));if(t.isEmpty())fail("没有可用就诊时间")else submit(MachineAction.Click(t.first(),reason="最早时间"),"选择最早时间${t.first().merged}",now,s.signature)}
  EngineState.VERIFY_CONFIRMATION->{val e=s.signature;if(!e.contains(norm(config.doctor))||!e.contains(norm(config.patient))||!e.contains(config.date.dayOfMonth.toString()))fail("确认页信息与配置不一致")else click(s,listOf("确认挂号"),"核验后确认挂号",now)}
  EngineState.CONFIRM_BOOKING->click(s,listOf("确认"),"系统提示确认",now)
  else->out(MachineAction.None,state.name)}
 private fun hospitalEntry(s:PageSnapshot)=s.items.filter{it.bounds.top<s.height*.55&&it.bounds.valid(s.width,s.height)}.firstOrNull{it.merged.contains("北京大学肿瘤医院")||it.merged.contains("北大肿")||it.merged.contains("北肿")}?:s.items.filter{it.bounds.top in (s.height*.10).toInt()..(s.height*.55).toInt()}.minWithOrNull(compareBy({it.bounds.top},{it.bounds.left}))
 private fun click(s:PageSnapshot,labels:List<String>,reason:String,now:Long):MachineOutput{val t=labels.asSequence().flatMap{s.contains(it).asSequence()}.filter{it.bounds.valid(s.width,s.height)}.minByOrNull{it.bounds.width*it.bounds.height}?:return fail("${state.name} 未找到${reason}");return submit(MachineAction.Click(t,reason=reason),"点击${reason}",now,s.signature)}
 private fun submit(a:MachineAction,m:String,n:Long,s:String):MachineOutput{submittedAt=n;submittedSignature=s;return out(a,m)}
 private fun fail(r:String):MachineOutput{state=EngineState.ERROR;return out(MachineAction.Fail(r),r)}
 private fun out(a:MachineAction,m:String)=MachineOutput(state,a,m,retry)}
internal fun norm(v:String)=v.replace(Regex("\\s+"),"").replace("微信微信","微信").replace('—','-').replace('–','-').trim()
