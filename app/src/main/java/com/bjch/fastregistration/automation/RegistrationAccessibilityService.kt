package com.bjch.fastregistration.automation
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/** Thin Android adapter. RegistrationStateMachine is the only workflow authority. */
class RegistrationAccessibilityService:AccessibilityService(){
 private val handler=Handler(Looper.getMainLooper())
 private lateinit var preferences:AppPreferences;private lateinit var logger:PerformanceLogger;private lateinit var actions:AccessibilityActions;private lateinit var ocr:ScreenOcrEngine
 private var machine:RegistrationStateMachine?=null;private var running=false;private var generation=0L
 private val poll=object:Runnable{override fun run(){if(running)inspect();if(running)handler.postDelayed(this,700)}}
 override fun onServiceConnected(){activeInstance=this;preferences=AppPreferences(this);logger=PerformanceLogger(this);actions=AccessibilityActions(this,logger);ocr=ScreenOcrEngine(this,logger);if(preferences.isRunning())start()}
 override fun onAccessibilityEvent(e:AccessibilityEvent?){if(running&&e?.packageName?.toString()==TargetConfig.WECHAT_PACKAGE){handler.removeCallbacks(poll);handler.postDelayed(poll,180)}}
 override fun onInterrupt(){stop("辅助服务中断")}
 override fun onDestroy(){stop("服务销毁");if(::ocr.isInitialized)ocr.close();if(activeInstance===this)activeInstance=null;super.onDestroy()}
 fun handleCommand(c:ServiceCommand){if(c==ServiceCommand.START_GRAB)start()else stop("用户停止")}
 private fun start(){stop("重新开始");running=true;generation++;preferences.setRunning(true);machine=RegistrationStateMachine(GrabConfig(preferences.doctorName,preferences.patientName,preferences.targetAppointmentDate));execute(machine!!.start(SystemClock.elapsedRealtime()),null);handler.postDelayed(poll,500)}
 private fun stop(reason:String){running=false;generation++;handler.removeCallbacksAndMessages(null);if(::ocr.isInitialized)ocr.cancelPending();machine?.stop();preferences.setRunning(false);status(EngineState.STOPPED,PageType.UNKNOWN,reason)}
 private fun inspect(){val root=rootInActiveWindow;if(root==null||root.packageName?.toString()!=TargetConfig.WECHAT_PACKAGE){execute(machine?.observe(PageSnapshot(PageType.UNKNOWN,screenWidth(),screenHeight(),emptyList(),"outside"),SystemClock.elapsedRealtime()),null);return};val tree=NodeTree.from(root,screenWidth(),screenHeight());if(tree.nodes.count{it.combinedText.isNotBlank()}>=3)process(tree)else{val token=generation;ocr.capture(tree.screenWidth,tree.screenHeight){r->if(running&&token==generation)r.onSuccess(::process)}}}
 private fun process(tree:NodeTree){execute(machine?.observe(tree.toSnapshot(),SystemClock.elapsedRealtime()),tree)}
 private fun execute(o:MachineOutput?,tree:NodeTree?){o?:return;status(o.state,tree?.toSnapshot()?.page?:PageType.UNKNOWN,o.message);val accepted=when(val a=o.action){MachineAction.LaunchWechat->{packageManager.getLaunchIntentForPackage(TargetConfig.WECHAT_PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.also(::startActivity)!=null};MachineAction.Back->performGlobalAction(GLOBAL_ACTION_BACK);MachineAction.PullRecent->tree?.let(actions::pullDownMiniPrograms)?:false;MachineAction.SwipeUp->tree?.let(actions::swipeUp)?:false;is MachineAction.Click->actions.tap(a.x.toFloat(),a.y.toFloat());is MachineAction.Fail->{running=false;preferences.setRunning(false);false};MachineAction.None->false};if(o.action !is MachineAction.None)logger.event("machine_action","state=${o.state}; evidence=${tree?.allText?.take(180)}; action=${o.action}; submitted=${accepted}; retry=${o.retry}")}
 private fun status(s:EngineState,p:PageType,m:String)=preferences.setRuntimeStatus(s,p,SlotStatus.UNKNOWN,message=m)
 private fun NodeTree.toSnapshot():PageSnapshot{val list=nodes.map{UiText(it.text,it.contentDescription,Box(it.bounds.left,it.bounds.top,it.bounds.right,it.bounds.bottom),it.clickable,it.selected)};return PageSnapshot(classify(list),screenWidth,screenHeight,list)}
 private fun classify(i:List<UiText>):PageType{val labels=i.flatMap{it.labels}.toSet();val all=labels.joinToString("|");return when{all.contains("预约成功")||all.contains("挂号成功")->PageType.SUCCESS_DIALOG;all.contains("系统提示")->PageType.SYSTEM_DIALOG;all.contains("确认挂号信息")->PageType.BOOKING_CONFIRM;all.contains("请选择就诊时间")->PageType.TIME_PICKER;all.contains("按日期预约")&&all.contains(TargetConfig.TARGET_DEPARTMENT)->PageType.DEPARTMENT_SLOTS;all.contains("科室列表")||all.contains(TargetConfig.TARGET_CATEGORY)->PageType.DEPARTMENT_LIST;all.contains("选择就诊人")->PageType.PATIENT_PICKER;all.contains("预约挂号")&&(all.contains("预约记录")||all.contains("门诊号源"))->PageType.REGISTRATION_ENTRY;all.contains("门诊服务")&&all.contains("预约挂号")->PageType.HOME;all.contains("最近使用")||all.contains("搜索小程序")->PageType.RECENT_MINIPROGRAMS;listOf("微信","通讯录","发现","我").all{labels.contains(it)}->PageType.WECHAT_HOME;else->PageType.UNKNOWN}}
 private fun screenWidth()=resources.displayMetrics.widthPixels;private fun screenHeight()=resources.displayMetrics.heightPixels
 companion object{@Volatile private var activeInstance:RegistrationAccessibilityService?=null;fun sendCommand(context:Context,command:ServiceCommand):Boolean{val s=activeInstance?:return false;s.handler.post{s.handleCommand(command)};return true};fun isEnabled(context:Context)=activeInstance!=null}}
