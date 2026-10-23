package com.ronnie.teslakey.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ronnie.teslakey.ble.TeslaBle
import com.ronnie.teslakey.protocol.*
import com.ronnie.teslakey.protocol.Proto.b
import com.ronnie.teslakey.protocol.Proto.u

class VehicleController(context:Context) {
 val keys=KeyStoreRepository(context)
 val log=com.ronnie.teslakey.diagnostics.DiagnosticLog(context)
 val ble=TeslaBle(context,log)
 private val handler=Handler(Looper.getMainLooper())
 var onChange:()->Unit={}
 var onNotice:(String)->Unit={}
 var vin=keys.prefs.getString("vin","")?:"";private set
 var paired=keys.prefs.getBoolean("paired",false);private set
 var demo=false;private set
 var demoConnected=true;private set
 var connected=false;private set
 var busy=false;private set
 var scanning=false;private set
 var status="未连接";private set
 var error="";private set
 var observation:VehicleObservation?=null;private set
 val found=mutableListOf<TeslaBle.Found>()
 var diagnosing=false;private set
 val diagnosticFound=mutableListOf<TeslaBle.Found>()
 private var session:TeslaSession?=null
 private var pending:VehicleCommand?=null
 private var pairing=false
 private var waitingHello=false
 private var sequence=0
 private var wasBackground=false
 private var reconnecting=false
 private val refresh = Runnable {
  if(canControl&&!demo) command(VehicleCommand.STATUS)
 }
 private fun scheduleRefresh(){
  handler.removeCallbacks(refresh)
  if(connected&&!demo) handler.postDelayed(refresh,2000)
 }
 val canControl get()=if(demo)demoConnected else connected&&!busy&&session?.ready==true
 init {
  ble.onFound={f->if(reconnecting){reconnecting=false;connect(f,false)}else if(found.none{it.device.address==f.device.address}){found.add(f);onChange()}}
  ble.onScanEnd={reconnecting=false;scanning=false
   if(found.isEmpty()){
    val devices=ble.scanDevices.values
    diagnosticFound.clear();diagnosticFound.addAll(devices)
    error=if(devices.isEmpty())"30 秒内未收到任何附近设备广播。请确认附近设备权限已允许，保持 App 在前台，靠近并唤醒车辆后重试。"
     else "已收到 ${devices.size} 个附近设备广播，但未匹配到此 VIN 的车辆。请核对 VIN，靠近并唤醒车辆后重试。"
   }
   status=if(found.isEmpty())"未找到车辆" else "请选择车辆";onChange()}
  ble.onDiagnostic={f->if(diagnosticFound.none{it.device.address==f.device.address}){diagnosticFound.add(f);onChange()}}
  ble.onDiagnosticEnd={diagnosing=false;onChange()}
  ble.onError={fail(it)}
  ble.onDisconnected={handler.removeCallbacks(refresh);session=null;connected=false;busy=false;waitingHello=false;pending=null;observation=null;sequence++;status="蓝牙连接已断开";onChange()}
  ble.onReady={busy=false;status="蓝牙已连接，请验证钥匙";if(!pairing)verify() else onChange()}
  ble.onMessage={m->receive(m)}
 }
 fun setDemo(value:Boolean){disconnect();demo=value;demoConnected=true;observation=if(value)VehicleObservation(1,0,0)else null;error="";status=if(value)"演示模式" else "未连接";onChange()}
 fun setDemoConnection(value:Boolean){if(demo){demoConnected=value;onChange()}}
 fun scan(input:String){log.begin("vehicle.scan");val v=input.trim().uppercase();require(TeslaProtocol.validVin(v)){"请输入有效的 17 位 VIN（不含 I、O、Q）"};disconnect();demo=false;if(v!=vin){vin=v;paired=false};keys.load(v);keys.prefs.edit().putString("vin",v).putBoolean("paired",paired).apply();found.clear();error="";scanning=true;status="正在搜索你的 Model Y";try{ble.scan(v)}catch(e:Exception){fail(e.message?:"搜索失败")};onChange()}
 fun connect(f:TeslaBle.Found,forPair:Boolean){scanning=false;ble.stopScan();pairing=forPair;busy=true;error="";status="正在连接 Model Y";observation=null;try{ble.connect(f.device)}catch(e:Exception){fail(e.message?:"连接失败")};onChange()}
 fun startDiagnostic(){diagnosticFound.clear();diagnosing=true;error="";try{ble.scanAll()}catch(e:Exception){fail(e.message?:"诊断扫描失败")};onChange()}
 fun stopDiagnostic(){ble.stopScan();diagnosing=false;onChange()}
 fun requestPair(){log.event("pair.request");check(!demo&&ble.ready&&!busy){"请先搜索并连接车辆"};busy=true;error="";status="正在发送添加钥匙请求";try{ble.send(TeslaProtocol.pairRequest(keys.load(vin).publicBytes));status="请使用实体卡并按车机提示确认";val s=++sequence;handler.postDelayed({if(sequence==s){busy=false;onChange()}},1200)}catch(e:Exception){fail(e.message?:"无法发送配对请求")};onChange()}
 fun verify(){log.event("session.verify","busy=$busy ready=${ble.ready}");if(demo||!ble.ready||busy)return;error="";busy=true;waitingHello=true;status="正在验证车辆钥匙";session=TeslaSession(vin,keys.load(vin));try{ble.send(session!!.hello());deadline(12000,"钥匙验证超时。请确认车机已添加此手机钥匙，再重试。 ")}catch(e:Exception){fail(e.message?:"无法验证钥匙")};onChange()}
 fun command(cmd:VehicleCommand){log.event("command.request","command=$cmd demo=$demo allowed=$canControl");if(!canControl)return
  if(demo){val old=observation?:VehicleObservation(1,0,0);observation=when(cmd){VehicleCommand.UNLOCK->old.copy(lock=0);VehicleCommand.LOCK->old.copy(lock=1);VehicleCommand.FRUNK->old.copy(frunk=1);VehicleCommand.TRUNK_OPEN->old.copy(trunk=1);VehicleCommand.TRUNK_CLOSE->old.copy(trunk=0);else->old};onNotice("演示操作完成，不会控制真实车辆");onChange();return}
  handler.removeCallbacks(refresh);busy=true;error="";pending=cmd;status=if(cmd==VehicleCommand.STATUS)"正在读取车辆状态" else "正在等待车辆响应";try{ble.send(session!!.command(cmd));deadline(12000,"未收到确认，执行结果未知。请检查车辆实际状态后再操作。") }catch(e:Exception){fail(e.message?:"命令未能发送")};onChange()
 }
 private fun deadline(ms:Long,message:String){val s=++sequence;handler.postDelayed({if(sequence==s&&busy){log.event("operation.timeout",message);pending=null;waitingHello=false;busy=false;session=null;connected=false;observation=null;error=message;status="请重新验证钥匙";onChange()}},ms)}
 private fun receive(m:ByteArray){if(waitingHello){try{session!!.acceptHello(m);log.event("session.accepted");sequence++;waitingHello=false;busy=false;connected=true;if(pairing){paired=true;pairing=false;keys.prefs.edit().putBoolean("paired",true).apply();onNotice("钥匙已通过车辆认证")};status="蓝牙已连接";onChange();command(VehicleCommand.STATUS)}catch(e:Exception){ // Ignore unrelated VCSEC broadcasts; explicit matching hello errors are surfaced.
    log.event("session.rejected",e.message?:e.javaClass.simpleName);val f=runCatching{Proto.parse(m)}.getOrNull();if(f?.b(15)!=null||f?.b(12)!=null){fail(e.message?:"钥匙验证失败")}
   };return}
  val cmd=pending?:return
  try{val plain=session!!.decryptReply(m);val obs=VehicleReply.observation(plain);val result=VehicleReply.commandStatus(plain);log.event("command.reply","command=$cmd result=$result observation=$obs")
   if(result==2){fail("车辆拒绝了此操作，请检查权限及车辆状态");return}
   if(obs!=null){observation=obs;complete();if(cmd!=VehicleCommand.STATUS)onNotice("已收到车辆状态，请核对实际状态");return}
   if(result==0){complete();if(cmd!=VehicleCommand.STATUS){onNotice("车辆已确认命令，正在读取状态");handler.postDelayed({if(canControl&&!demo)command(VehicleCommand.STATUS)},300)}}
  }catch(e:Exception){fail(e.message?:"无法认证车辆响应，未确认执行结果")}
 }
 private fun complete(){sequence++;pending=null;busy=false;status="蓝牙已连接";error="";scheduleRefresh();onChange()}
 private fun fail(message:String){handler.removeCallbacks(refresh);reconnecting=false;log.event("controller.error",message);sequence++;busy=false;scanning=false;diagnosing=false;waitingHello=false;pending=null;session=null;connected=false;observation=null;error=message;status="连接或验证未完成";ble.disconnect();onChange()}
 fun disconnect(){handler.removeCallbacks(refresh);reconnecting=false;sequence++;ble.disconnect();session=null;connected=false;busy=false;scanning=false;diagnosing=false;waitingHello=false;pending=null;pairing=false;observation=null;status="未连接";error=""}
 fun forget(){disconnect();keys.forget();vin="";paired=false;onChange()}
 fun background(){log.event("app.background");wasBackground=!demo&&(connected||ble.ready||scanning||busy);disconnect()}
 fun foreground(){log.event("app.foreground");if(wasBackground){wasBackground=false;if(paired&&TeslaProtocol.validVin(vin)){val savedVin=vin;scan(savedVin);reconnecting=scanning;status=if(scanning)"正在重新连接车辆" else status}else status="已暂停连接，请重新连接车辆"};onChange()}
 fun close(){disconnect();onChange={};onNotice={}}
}
