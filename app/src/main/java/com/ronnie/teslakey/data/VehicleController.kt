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
 var climateOn:Boolean?=null;private set
 var climateError="";private set
 private var climateSession:TeslaSession?=null
 private var waitingClimateHello=false
 private var climateQueued:VehicleCommand?=null
 private var lastClimatePoll=0L
 private var pending:VehicleCommand?=null
 private var vcsecRoute=TeslaProtocol.random(16)
 private var pairing=false
 private var waitingHello=false
 private var sequence=0
 private var wasBackground=false
 private var reconnecting=false
 private var retryConnect=false
 private var inForeground=true
 private var statusTimeouts=0
 private val refresh = Runnable {
  if(canControl) {
   if(climateError.isEmpty()&&android.os.SystemClock.elapsedRealtime()-lastClimatePoll>=10000) command(VehicleCommand.CLIMATE_STATUS)
   else pollStatus()
  }
 }
 /** Background status poll: keeps the link alive without toggling busy/status, so the UI does not re-render. */
 private fun pollStatus(){
  if(!canControl||pending!=null)return
  handler.removeCallbacks(refresh)
  pending=VehicleCommand.STATUS
  try{ble.send(TeslaProtocol.plainStatusRequest(session?.route?:vcsecRoute));deadline(8000,"")}
  catch(e:Exception){pending=null;log.event("status.error",e.message?:"状态请求未能发送");scheduleRefresh()}
 }
 private fun scheduleRefresh(){
  handler.removeCallbacks(refresh)
  if(connected) handler.postDelayed(refresh,2000)
 }
 val canControl get()=connected&&!busy&&session?.ready==true
 init {
  ble.onFound={f->if(reconnecting){if(TeslaProtocol.validVin(vin)&&f.name.startsWith(TeslaProtocol.advertisementPrefix(vin))){reconnecting=false;connect(f,false)}}else if(found.none{it.device.address==f.device.address}){found.add(f);onChange()}}
  ble.onScanEnd={reconnecting=false;scanning=false
   if(found.isEmpty()){
    val devices=ble.scanDevices.values
    diagnosticFound.clear();diagnosticFound.addAll(devices)
    error=if(devices.isEmpty())"30 秒内未收到任何附近设备广播。请确认附近设备权限已允许，保持 App 在前台，靠近并唤醒车辆后重试。"
     else "已收到 ${devices.size} 个附近设备广播，但未匹配到此 VIN 的车辆。请核对 VIN，靠近并唤醒车辆后重试。"
   }
   status=if(found.isEmpty())"未找到车辆" else "请选择车辆";onChange()
   // Auto-reconnect keeps trying while the app is in the foreground; the car may have been asleep.
   if(retryConnect&&!connected&&paired&&inForeground)handler.postDelayed({autoConnectIfPaired()},15000) else retryConnect=false}
  ble.onDiagnostic={f->if(diagnosticFound.none{it.device.address==f.device.address}){diagnosticFound.add(f);onChange()}}
  ble.onDiagnosticEnd={diagnosing=false;onChange()}
  ble.onError={fail(it)}
  ble.onDisconnected={handler.removeCallbacks(refresh);session=null;resetClimate();connected=false;busy=false;waitingHello=false;pending=null;observation=null;sequence++;status="蓝牙连接已断开";onChange();
   // Unexpected drop while paired: quietly reconnect instead of leaving the user stranded.
   if(paired&&TeslaProtocol.validVin(vin))handler.postDelayed({autoConnectIfPaired()},3000)}
  ble.onReady={busy=false;status="蓝牙已连接，请验证钥匙";if(!pairing)verify() else onChange()}
  ble.onMessage={m->receive(m)}
 }
 fun scan(input:String){log.begin("vehicle.scan");val v=input.trim().uppercase();require(TeslaProtocol.validVin(v)){"请输入有效的 17 位 VIN（不含 I、O、Q）"};disconnect();vcsecRoute=TeslaProtocol.random(16);if(v!=vin){vin=v;paired=false};keys.load(v);keys.prefs.edit().putString("vin",v).putBoolean("paired",paired).apply();found.clear();error="";scanning=true;status="正在搜索你的 Model Y";try{ble.scan(v)}catch(e:Exception){fail(e.message?:"搜索失败")};onChange()}
 fun connect(f:TeslaBle.Found,forPair:Boolean){scanning=false;ble.stopScan();pairing=forPair;busy=true;error="";status="正在连接 Model Y";observation=null;vcsecRoute=TeslaProtocol.random(16);try{ble.connect(f.device)}catch(e:Exception){fail(e.message?:"连接失败")};onChange()}
 fun startDiagnostic(){diagnosticFound.clear();diagnosing=true;error="";try{ble.scanAll()}catch(e:Exception){fail(e.message?:"诊断扫描失败")};onChange()}
 fun stopDiagnostic(){ble.stopScan();diagnosing=false;onChange()}
 fun requestPair(){log.event("pair.request");check(ble.ready&&!busy){"请先搜索并连接车辆"};busy=true;error="";status="正在发送添加钥匙请求";try{ble.send(TeslaProtocol.pairRequest(keys.load(vin).publicBytes));status="请使用实体卡并按车机提示确认";val s=++sequence;handler.postDelayed({if(sequence==s){busy=false;onChange()}},1200)}catch(e:Exception){fail(e.message?:"无法发送配对请求")};onChange()}
 fun verify(){log.event("session.verify","busy=$busy ready=${ble.ready}");if(!ble.ready||busy)return;error="";busy=true;waitingHello=true;status="正在验证车辆钥匙";session=TeslaSession(vin,keys.load(vin));try{ble.send(session!!.hello());deadline(12000,"钥匙验证超时。请确认车机已添加此手机钥匙，再重试。 ")}catch(e:Exception){fail(e.message?:"无法验证钥匙")};onChange()}
 fun command(cmd:VehicleCommand){log.event("command.request","command=$cmd allowed=$canControl");if(!canControl)return
  if(cmd.domain==3){startClimate(cmd);return}
  handler.removeCallbacks(refresh);busy=true;error="";pending=cmd
  if(cmd==VehicleCommand.STATUS){
   // Official flow: status is an unsigned InformationRequest; the car answers with plaintext VehicleStatus.
   status="正在读取车辆状态";try{ble.send(TeslaProtocol.plainStatusRequest(session?.route?:vcsecRoute));deadline(8000,"读取车辆状态超时，请靠近车辆重试")}catch(e:Exception){fail(e.message?:"状态请求未能发送")};onChange();return}
  status="正在等待车辆响应";try{ble.send(session!!.command(cmd));deadline(12000,"未收到确认，执行结果未知。请检查车辆实际状态后再操作。") }catch(e:Exception){fail(e.message?:"命令未能发送")};onChange()
 }
 private fun deadline(ms:Long,message:String){val s=++sequence;handler.postDelayed({if(sequence==s&&(busy||pending!=null)){log.event("operation.timeout",message);
   if(waitingClimateHello||pending?.domain==3){climateFailure("空调连接或响应超时，执行结果未知。请唤醒车辆后重试。");return@postDelayed}
   // A missed status reply is not a session failure: keep the link and retry, unless it keeps happening.
   if(pending==VehicleCommand.STATUS){
    pending=null;busy=false
    if(++statusTimeouts>=3){statusTimeouts=0;fail("与车辆的通信中断，正在重新连接")}
    else{error="状态读取超时，稍后自动重试";status="蓝牙已连接";scheduleRefresh();onChange()}
    return@postDelayed}
   pending=null;waitingHello=false;busy=false;session=null;resetClimate();connected=false;observation=null;error=message;status="请重新验证钥匙";onChange()}},ms)}
 private fun receive(m:ByteArray){
  if(waitingClimateHello){
   val active=climateSession?:return
   if(!active.accepts(m))return
   try{active.acceptHello(m);sequence++;waitingClimateHello=false;busy=false
    val cmd=climateQueued?:VehicleCommand.CLIMATE_STATUS;climateQueued=null;startClimate(cmd)
   }catch(e:Exception){climateFailure(e.message?:"空调认证失败")};return
  }
  if(pending?.domain==3){
   val active=climateSession?:return
   if(!active.accepts(m))return
   try{
    val cmd=pending!!;
    // CarServer responses arrive plaintext as well; decrypt only when the car signs an encrypted response.
    val plain=try{active.decryptReply(m)}catch(e:Exception){
     val fields=runCatching{Proto.parse(m)}.getOrNull()?:throw e
     val hasResponseSig=fields.b(13)?.let{Proto.parse(it).b(9)}!=null
     if(hasResponseSig)throw e
     fields.b(10)?:throw e
    }
    val reply=ClimateProtocol.reply(plain)
    log.event("climate.reply","command=$cmd success=${reply.success} on=${reply.on}")
    if(reply.success==false){climateFailure("车辆拒绝空调操作：${reply.reason?:"请检查车辆状态及权限"}");return}
    if(reply.on!=null)climateOn=reply.on
    if(reply.success==true||reply.on!=null){
     if(cmd==VehicleCommand.CLIMATE_STATUS&&reply.on==null){climateFailure("车辆未返回空调状态，请稍后重试");return}
     complete()
     if(cmd!=VehicleCommand.CLIMATE_STATUS){
      onNotice("车辆已响应，正在核对空调状态")
      // Do not infer power state from command acknowledgement; read it from the car.
      command(VehicleCommand.CLIMATE_STATUS)
     }
    }
   }catch(e:Exception){climateFailure(e.message?:"空调响应验证失败")};return
  }
  if(waitingHello){if(session?.accepts(m)!=true)return;try{session!!.acceptHello(m);log.event("session.accepted");sequence++;waitingHello=false;busy=false;connected=true;if(pairing){paired=true;pairing=false;keys.prefs.edit().putBoolean("paired",true).apply();onNotice("钥匙已通过车辆认证")};status="蓝牙已连接";onChange();command(VehicleCommand.STATUS)}catch(e:Exception){ // Ignore unrelated VCSEC broadcasts; explicit matching hello errors are surfaced.
    log.event("session.rejected",e.message?:e.javaClass.simpleName);val f=runCatching{Proto.parse(m)}.getOrNull();if(f?.b(15)!=null||f?.b(12)!=null){fail(e.message?:"钥匙验证失败")}
   };return}
  val cmd=pending?:return
  if(session?.accepts(m)!=true)return
  try{
   // VCSEC replies are plaintext FromVCSECMessage; encrypted replies carry AES_GCM_Response_data (field 13/9).
   val plain=try{session!!.decryptReply(m)}catch(e:Exception){
    val fields=runCatching{Proto.parse(m)}.getOrNull()?:throw e
    val hasResponseSig=fields.b(13)?.let{Proto.parse(it).b(9)}!=null
    if(hasResponseSig)throw e
    fields.b(10)?:throw e
   }
   val obs=VehicleReply.observation(plain);val result=VehicleReply.commandStatus(plain);log.event("command.reply","command=$cmd result=$result observation=$obs")
   if(result==2){fail("车辆拒绝了此操作，请检查权限及车辆状态");return}
   if(obs!=null){observation=obs;statusTimeouts=0;if(error.startsWith("状态读取超时"))error="";complete();if(cmd!=VehicleCommand.STATUS)onNotice("已收到车辆状态，请核对实际状态");return}
   if(result==0){complete();if(cmd!=VehicleCommand.STATUS){onNotice("车辆已确认命令，正在读取状态");handler.postDelayed({if(canControl)command(VehicleCommand.STATUS)},300)}}
  }catch(e:Exception){fail(e.message?:"无法认证车辆响应，未确认执行结果")}
 }

 private fun resetClimate(){
  climateSession=null;waitingClimateHello=false;climateQueued=null;climateOn=null;climateError="";lastClimatePoll=0
 }
 private fun startClimate(cmd:VehicleCommand){
  handler.removeCallbacks(refresh);busy=true;error="";climateError="";if(cmd!=VehicleCommand.CLIMATE_STATUS)climateOn=null;lastClimatePoll=android.os.SystemClock.elapsedRealtime()
  try {
   if(climateSession?.ready!=true){
    climateSession=TeslaSession(vin,keys.load(vin),domain=3);waitingClimateHello=true;climateQueued=cmd
    status="正在连接空调控制";log.event("climate.hello");ble.send(climateSession!!.hello())
   }else{
    pending=cmd;status=when(cmd){VehicleCommand.CLIMATE_ON->"正在开启空调";VehicleCommand.CLIMATE_OFF->"正在关闭空调";else->"正在读取空调状态"}
    ble.send(climateSession!!.command(cmd))
   }
   deadline(12000,"空调响应超时")
  }catch(e:Exception){climateFailure(e.message?:"无法发送空调命令")}
  onChange()
 }
 private fun climateFailure(message:String){
  log.event("climate.error",message);sequence++;resetClimate();climateError=message
  pending=null;busy=false;status="蓝牙已连接";scheduleRefresh();onChange()
 }
 private fun complete(){sequence++;pending=null;busy=false;status="蓝牙已连接";error="";scheduleRefresh();onChange()}
 private fun fail(message:String){handler.removeCallbacks(refresh);reconnecting=false;log.event("controller.error",message);sequence++;busy=false;scanning=false;diagnosing=false;waitingHello=false;pending=null;session=null;resetClimate();connected=false;observation=null;error=message;status="连接或验证未完成";ble.disconnect();onChange()}
 fun disconnect(){handler.removeCallbacks(refresh);reconnecting=false;retryConnect=false;sequence++;ble.disconnect();session=null;resetClimate();connected=false;busy=false;scanning=false;diagnosing=false;waitingHello=false;pending=null;pairing=false;observation=null;status="未连接";error=""}
 fun forget(){disconnect();keys.forget();vin="";paired=false;onChange()}
 fun background(){log.event("app.background");inForeground=false;wasBackground=(connected||ble.ready||scanning||busy);disconnect()}
 fun foreground(){log.event("app.foreground");inForeground=true;wasBackground=false;
  if(paired&&TeslaProtocol.validVin(vin)){if(!connected&&!scanning&&!busy)autoConnectIfPaired()}
  else if(!paired)status="已暂停连接，请重新连接车辆";onChange()}
 /** Silently reconnect to a paired vehicle; retries while the app is in the foreground. */
 fun autoConnectIfPaired(){
  if(!paired||!TeslaProtocol.validVin(vin))return
  if(connected||scanning||busy||!inForeground)return
  if(!ble.permitted()||!ble.enabled){status="未连接";return}
  log.event("auto.connect");val savedVin=vin;scan(savedVin);retryConnect=true;reconnecting=scanning;status=if(scanning)"正在重新连接车辆" else status;onChange()
 }
 fun close(){disconnect();onChange={};onNotice={}}
}
