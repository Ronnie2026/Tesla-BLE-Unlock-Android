package com.ronnie.teslakey.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.ronnie.teslakey.protocol.BleFrames
import com.ronnie.teslakey.protocol.TeslaProtocol
import java.util.UUID
import java.util.ArrayDeque

/** Foreground BLE transport; callbacks and writes are serialized on the main thread. */
@SuppressLint("MissingPermission")
class TeslaBle(private val context:Context, private val log:com.ronnie.teslakey.diagnostics.DiagnosticLog) {
 companion object {
  val SERVICE=UUID.fromString("00000211-b2d1-43f0-9b88-960cebf8b91e")
  val WRITE=UUID.fromString("00000212-b2d1-43f0-9b88-960cebf8b91e")
  val READ=UUID.fromString("00000213-b2d1-43f0-9b88-960cebf8b91e")
  val CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
 }
 data class Found(val device:BluetoothDevice,val name:String,val rssi:Int)
 private val handler=Handler(Looper.getMainLooper())
 private val adapter=(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
 var onFound:(Found)->Unit={};var onReady:()->Unit={};var onMessage:(ByteArray)->Unit={};var onDisconnected:()->Unit={};var onError:(String)->Unit={};var onScanEnd:()->Unit={}
 var onDiagnostic:(Found)->Unit={};var onDiagnosticEnd:()->Unit={}
 private var gatt:BluetoothGatt?=null
 private var tx:BluetoothGattCharacteristic?=null
 private var mtu=23
 private var scan:ScanCallback?=null
 val scanDevices=mutableMapOf<String,Found>()
 private var generation=0
 private val frames=BleFrames()
 private val writes=ArrayDeque<ByteArray>()
 private var writing=false
 var ready=false;private set
 val enabled get()=permitted()&&adapter?.isEnabled==true
 fun permitted()=context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
 private fun later(ms:Long,token:Int,block:()->Unit){handler.postDelayed({if(token==generation)block()},ms)}
 private fun advHex(result:ScanResult)=result.scanRecord?.bytes?.joinToString(""){"%02x".format(it.toInt()and 255)}.orEmpty()
 fun scan(vin:String){stopScan();check(permitted()){ "请允许附近设备权限" };check(enabled){"请先开启手机蓝牙"};scanDevices.clear();val expected=TeslaProtocol.advertisementPrefix(vin);log.event("scan.start","expectedPrefix=$expected permission=${permitted()} enabled=$enabled durationMs=30000");val token=++generation;val cb=object:ScanCallback(){override fun onScanResult(type:Int,result:ScanResult){val name=result.scanRecord?.deviceName?:result.device.name?:"";handler.post{if(scan===this){val f=Found(result.device,name,result.rssi);val old=scanDevices[result.device.address];if(old==null||old.name!=name)log.event("scan.device","id=${log.deviceId(result.device.address)} name=${if(TeslaProtocol.isTeslaAdvertisement(name))name else if(name.isEmpty())"unnamed" else "other"} rssi=${result.rssi} connectable=${result.isConnectable} matched=${name.startsWith(expected)} services=${result.scanRecord?.serviceUuids.orEmpty()} adv=${advHex(result)}");if(old==null||name.isNotEmpty())scanDevices[result.device.address]=f;
  // Report every Tesla-format device, not just the VIN match; the user picks from the list.
  if(TeslaProtocol.isTeslaAdvertisement(name))onFound(f)}}}
  override fun onScanFailed(code:Int){handler.post{if(scan===this){log.event("scan.failed","code=$code");stopScan();onError("蓝牙搜索失败（$code），请稍后重试")}}}}
  scan=cb;adapter.bluetoothLeScanner.startScan(null,ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),cb);later(30000,token){log.event("scan.end","devices=${scanDevices.size}");stopScan();onScanEnd()}
 }
 /** Diagnostic scan reports every nearby BLE advertisement for ~8s. Not used for pairing. */
 fun scanAll(){log.event("diagnostic.start");stopScan();check(permitted()){ "请允许附近设备权限" };check(enabled){"请先开启手机蓝牙"};val token=++generation;val cb=object:ScanCallback(){override fun onScanResult(type:Int,result:ScanResult){val name=result.scanRecord?.deviceName?:result.device.name?:"";handler.post{if(scan===this){log.event("diagnostic.device","id=${log.deviceId(result.device.address)} teslaName=${if(TeslaProtocol.isTeslaAdvertisement(name))name else "other"} rssi=${result.rssi} connectable=${result.isConnectable} adv=${advHex(result)}");onDiagnostic(Found(result.device,name,result.rssi))}}}
  override fun onScanFailed(code:Int){handler.post{if(scan===this){log.event("scan.failed","code=$code");stopScan();onError("蓝牙搜索失败（$code），请稍后重试")}}}}
  scan=cb;adapter.bluetoothLeScanner.startScan(null,ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),cb);later(8000,token){stopScan();onDiagnosticEnd()}
 }
 fun stopScan(){val s=scan;scan=null;if(s!=null&&permitted())try{adapter?.bluetoothLeScanner?.stopScan(s)}catch(_:Exception){} }
 fun connect(device:BluetoothDevice){log.event("gatt.connect","id=${log.deviceId(device.address)}");disconnect();check(enabled){"请先开启手机蓝牙"};val token=generation
  val cb=object:BluetoothGattCallback(){
   private fun current(g:BluetoothGatt,block:()->Unit){handler.post{if(gatt===g)block()}}
   override fun onConnectionStateChange(g:BluetoothGatt,status:Int,newState:Int)=current(g){log.event("gatt.state","status=$status state=$newState");if(status!=BluetoothGatt.GATT_SUCCESS||newState==BluetoothProfile.STATE_DISCONNECTED){disconnect();onDisconnected();if(status!=0)onError("车辆蓝牙连接中断（$status）")}else if(newState==BluetoothProfile.STATE_CONNECTED){if(!g.requestMtu(185)&&!g.discoverServices())fail("无法发现车辆蓝牙服务")}}
   override fun onMtuChanged(g:BluetoothGatt,m:Int,status:Int)=current(g){log.event("gatt.mtu","status=$status mtu=$m");if(status==0)mtu=m;if(!g.discoverServices())fail("无法发现车辆蓝牙服务")}
   override fun onServicesDiscovered(g:BluetoothGatt,status:Int)=current(g){log.event("gatt.services","status=$status servicePresent=${g.getService(SERVICE)!=null}");if(status!=0){fail("读取车辆蓝牙服务失败");return@current};val service=g.getService(SERVICE);tx=service?.getCharacteristic(WRITE);val rx=service?.getCharacteristic(READ);if(tx==null||rx==null){fail("未找到特斯拉钥匙服务");return@current};val d=rx.getDescriptor(CCCD);if(d==null||!g.setCharacteristicNotification(rx,true)){fail("无法订阅车辆响应");return@current};val value=if(rx.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY!=0)BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
    @Suppress("DEPRECATION") val ok=if(android.os.Build.VERSION.SDK_INT>=33)g.writeDescriptor(d,value)==BluetoothStatusCodes.SUCCESS else{d.value=value;g.writeDescriptor(d)};if(!ok)fail("无法启用车辆响应")}
   override fun onDescriptorWrite(g:BluetoothGatt,d:BluetoothGattDescriptor,status:Int)=current(g){log.event("gatt.subscription","status=$status");if(d.uuid!=CCCD)return@current;if(status!=0){fail("车辆响应订阅失败");return@current};ready=true;onReady()}
   override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic,value:ByteArray)=current(g){if(c.uuid==READ)receive(value)}
   @Deprecated("Legacy Android callback") override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic){if(android.os.Build.VERSION.SDK_INT<33){@Suppress("DEPRECATION") val value=c.value?.clone()?:return;current(g){if(c.uuid==READ)receive(value)}}}
   override fun onCharacteristicWrite(g:BluetoothGatt,c:BluetoothGattCharacteristic,status:Int)=current(g){log.event("gatt.write","status=$status remaining=${writes.size}");if(c.uuid!=WRITE)return@current;if(status!=0){fail("命令传输失败，请检查车辆实际状态");return@current};writing=false;pump()}
  }
  gatt=device.connectGatt(context,false,cb,BluetoothDevice.TRANSPORT_LE);later(22000,token){if(!ready)fail("连接超时，请靠近车辆并重试")}
 }
 private fun receive(b:ByteArray){log.event("rx.chunk","bytes=${b.size}");try{frames.feed(b).forEach{log.event("rx.message","bytes=${it.size}");onMessage(it)}}catch(_:IllegalArgumentException){fail("车辆返回了无效的数据帧")}catch(_:Exception){fail("无法读取车辆响应")}}
 fun send(message:ByteArray){log.event("tx.request","bytes=${message.size} ready=$ready writing=$writing queued=${writes.size}");check(ready){"蓝牙连接尚未就绪"};check(writes.isEmpty()&&!writing){"上一条消息仍在传输"};val frame=BleFrames.encode(message);frame.asList().chunked((mtu-3).coerceAtLeast(20)).forEach{writes.add(it.toByteArray())};pump()}
 private fun pump(){if(writing||writes.isEmpty())return;val g=gatt?:return;val c=tx?:return;val data=writes.removeFirst();writing=true
  @Suppress("DEPRECATION") val ok=if(android.os.Build.VERSION.SDK_INT>=33)g.writeCharacteristic(c,data,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS else{c.writeType=BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;c.value=data;g.writeCharacteristic(c)};if(!ok)fail("蓝牙发送失败，请重新连接")
 }
 private fun fail(message:String){log.event("ble.error",message);disconnect();onError(message)}
 fun disconnect(){log.event("ble.disconnect","ready=$ready writing=$writing queued=${writes.size}");generation++;stopScan();ready=false;tx=null;mtu=23;writes.clear();writing=false;frames.clear();val old=gatt;gatt=null;if(old!=null&&permitted()){try{old.disconnect()}catch(_:Exception){};try{old.close()}catch(_:Exception){}}}
}
