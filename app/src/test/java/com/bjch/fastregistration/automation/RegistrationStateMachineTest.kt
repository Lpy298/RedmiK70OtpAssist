package com.bjch.fastregistration.automation
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RegistrationStateMachineTest {
 private val cfg=GrabConfig("目标医生甲","测试患者甲",LocalDate.of(2026,9,10))
 private fun u(t:String,l:Int,top:Int,r:Int,b:Int,desc:String="",selected:Boolean=false)=UiText(t,desc,Box(l,top,r,b),selected=selected)
 private fun page(type:PageType,vararg i:UiText,signature:String=i.joinToString("|"){it.merged})=PageSnapshot(type,706,1568,i.toList(),signature)
 @Test fun duplicateTextDescriptionIsNotConcatenated(){assertEquals(setOf("微信"),u("微信",0,0,20,20,"微信").labels)}
 @Test fun hospitalFullTruncatedAndCoordinateFallback(){
  listOf(u("北京大学肿瘤医院",20,250,150,330),u("北大肿…",20,250,150,330),u("北肿",20,250,150,330),u("",20,220,150,330)).forEach{
   val m=RegistrationStateMachine(cfg);m.start();val o=m.observe(page(PageType.RECENT_MINIPROGRAMS,it),1);assertTrue(o.action is MachineAction.Click);val c=o.action as MachineAction.Click;assertTrue(c.y in 1..1567)
  }}
 @Test fun unchangedPageRetriesThenStopsSafely(){val m=RegistrationStateMachine(cfg);m.start();m.observe(page(PageType.UNKNOWN,signature="x"),1);var now=9002L;repeat(4){m.observe(page(PageType.UNKNOWN,signature="x"),now);now+=9000};assertEquals(EngineState.ERROR,m.state)}
 @Test fun oncologyMustPrecedeDepartment(){val m=RegistrationStateMachine(cfg);m.start();val first=m.observe(page(PageType.DEPARTMENT_LIST,u("肿瘤内科",10,400,120,450),signature="category"),1);assertEquals(EngineState.SELECT_ONCOLOGY_CATEGORY,first.state);val second=m.observe(page(PageType.DEPARTMENT_LIST,u("肿瘤内科",10,400,120,450),u(TargetConfig.TARGET_DEPARTMENT,180,400,600,450),signature="department"),2);assertEquals(EngineState.SELECT_DEPARTMENT,second.state);assertTrue(second.action is MachineAction.Click)}
 @Test fun date10And11NeverClickCurrentDay(){for(day in listOf(10,11)){val m=RegistrationStateMachine(cfg.copy(date=LocalDate.of(2026,9,day)));m.start();val o=m.observe(page(PageType.DEPARTMENT_SLOTS,u("9",40,300,80,350),u(day.toString(),100,300,140,350)),1);val c=o.action as MachineAction.Click;assertEquals(day.toString(),c.target.text)}}
 @Test fun onlyTargetDoctorSameRowPositiveRemaining(){val m=RegistrationStateMachine(cfg);m.start();val selected=u("10",100,300,140,350,selected=true);m.observe(page(PageType.DEPARTMENT_SLOTS,selected,u("目标医生甲",100,700,180,740),u("剩余 0",550,700,640,740),u("其他医生乙",100,800,180,840),u("剩余 9",550,800,640,840),u("剩余 7",550,700,640,740)),1);val o=m.observe(page(PageType.DEPARTMENT_SLOTS,selected,u("目标医生甲",100,700,180,740),u("剩余 0",450,700,530,740),u("剩余 7",550,700,640,740),u("其他医生乙",100,800,180,840),u("剩余 9",550,800,640,840)),2);val c=o.action as MachineAction.Click;assertEquals("剩余 7",c.target.text)}
 @Test fun forbiddenStatusesNeverClicked(){for(v in listOf("约满","候补","即将放号","剩余 0")){val m=RegistrationStateMachine(cfg);m.start();val d=u("10",100,300,140,350,selected=true);m.observe(page(PageType.DEPARTMENT_SLOTS,d,u("目标医生甲",100,700,180,740),u(v,550,700,640,740)),1);val o=m.observe(page(PageType.DEPARTMENT_SLOTS,d,u("目标医生甲",100,700,180,740),u(v,550,700,640,740)),2);assertFalse(o.action is MachineAction.Click)}}
 @Test fun patientMustMatchExactly(){val m=RegistrationStateMachine(cfg);m.start();val o=m.observe(page(PageType.PATIENT_PICKER,u("测试患者乙",100,500,200,550)),1);assertEquals(EngineState.ERROR,o.state)}
 @Test fun fixtureClickPointsWithinBitmap(){val points=listOf(80 to 280,155 to 313,105 to 240,276 to 621,60 to 1308,404 to 750,141 to 365,141 to 365,628 to 1028,131 to 837,353 to 1346,558 to 847);assertTrue(points.all{it.first in 0 until 706&&it.second in 0 until 1568})}
 @Test fun stopIsImmediateTerminal(){val m=RegistrationStateMachine(cfg);m.start();assertEquals(EngineState.STOPPED,m.stop().state);assertTrue(m.observe(page(PageType.HOME),999).action is MachineAction.None)}
 @Test fun completeScreenshotReplay(){
  val m=RegistrationStateMachine(cfg);m.start()
  var now=1L
  fun feed(p:PageSnapshot)=m.observe(p,now++)
  assertTrue(feed(page(PageType.WECHAT_HOME,u("微信",0,1450,100,1560,"微信"),u("通讯录",150,1450,250,1560),u("发现",300,1450,400,1560),u("我",500,1450,600,1560))).action is MachineAction.PullRecent)
  assertTrue(feed(page(PageType.RECENT_MINIPROGRAMS,u("北大肿…",20,220,150,330))).action is MachineAction.Click)
  assertTrue(feed(page(PageType.HOME,u("预约挂号",110,280,200,340),u("患者到院就诊",110,310,220,345))).action is MachineAction.Click)
  assertTrue(feed(page(PageType.REGISTRATION_ENTRY,u("门诊号源",50,200,170,270))).action is MachineAction.Click)
  assertTrue(feed(page(PageType.PATIENT_PICKER,u("测试患者甲",200,580,330,650))).action is MachineAction.Click)
  assertTrue(feed(page(PageType.DEPARTMENT_LIST,u("肿瘤内科",10,1200,120,1350),signature="category")).action is MachineAction.Click)
  assertTrue(feed(page(PageType.DEPARTMENT_LIST,u("肿瘤内科",10,1200,120,1350),u(TargetConfig.TARGET_DEPARTMENT,180,700,600,780),signature="dept")).action is MachineAction.Click)
  assertTrue(feed(page(PageType.DEPARTMENT_SLOTS,u("9",40,300,80,350),u("10",100,300,140,350))).action is MachineAction.Click)
  feed(page(PageType.DEPARTMENT_SLOTS,u("10",100,300,140,350,selected=true),u("目标医生甲",100,700,180,740),u("剩余 7",550,700,640,740),signature="selected"))
  assertTrue(feed(page(PageType.DEPARTMENT_SLOTS,u("10",100,300,140,350,selected=true),u("目标医生甲",100,700,180,740),u("剩余 7",550,700,640,740),signature="selected")).action is MachineAction.Click)
  assertTrue(feed(page(PageType.TIME_PICKER,u("15:00~15:30",60,820,200,860),u("15:30~16:00",260,820,420,860))).action is MachineAction.Click)
  assertTrue(feed(page(PageType.BOOKING_CONFIRM,u("目标医生甲",20,300,100,340),u("测试患者甲",20,350,100,390),u("2026年09月10日",20,400,200,440),u("确认挂号",250,1300,460,1380))).action is MachineAction.Click)
  assertTrue(feed(page(PageType.SYSTEM_DIALOG,u("确认",530,830,590,870))).action is MachineAction.Click)
  feed(page(PageType.SUCCESS_DIALOG,u("预约成功",250,500,450,560)))
  assertEquals(EngineState.SUCCESS,m.state)
 }
}
