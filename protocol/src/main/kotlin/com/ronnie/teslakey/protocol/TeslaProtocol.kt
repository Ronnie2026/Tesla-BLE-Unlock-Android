package com.ronnie.teslakey.protocol

import com.ronnie.teslakey.protocol.Proto.b
import com.ronnie.teslakey.protocol.Proto.u
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface VehicleKey { val publicBytes:ByteArray; fun exchange(publicKey:ByteArray):ByteArray }
enum class VehicleCommand { STATUS, UNLOCK, LOCK, FRUNK, TRUNK_OPEN, TRUNK_CLOSE }
object TeslaProtocol {
 fun validVin(v:String)=Regex("[A-HJ-NPR-Z0-9]{17}").matches(v)
 fun advertisementName(vin:String):String {require(validVin(vin));return "S"+digest("SHA-1",vin.toByteArray()).take(8).joinToString(""){"%02x".format(it.toInt()and 255)}+"C"}
 fun digest(type:String,b:ByteArray)=MessageDigest.getInstance(type).digest(b)
 fun random(n:Int)=ByteArray(n).also{SecureRandom().nextBytes(it)}
 fun be(v:Long)=ByteBuffer.allocate(4).putInt(v.toInt()).array()
 fun hmac(k:ByteArray,b:ByteArray):ByteArray=Mac.getInstance("HmacSHA256").run{init(SecretKeySpec(k,"HmacSHA256"));doFinal(b)}
 fun metadata(vararg pairs:Pair<Int,ByteArray>):ByteArray {var b=byteArrayOf();var last=-1;for((tag,v) in pairs){require(tag>last&&v.size<=255);last=tag;b+=byteArrayOf(tag.toByte(),v.size.toByte())+v};return b+byteArrayOf(255.toByte())}
 fun pairRequest(publicKey:ByteArray):ByteArray {require(publicKey.size==65&&publicKey[0]==4.toByte());val permission=Proto.bytes(1,Proto.bytes(1,publicKey))+Proto.uint(4,3);val whitelist=Proto.bytes(5,permission)+Proto.bytes(6,Proto.uint(1,7));val unsigned=Proto.bytes(16,whitelist);return Proto.bytes(1,Proto.bytes(2,unsigned)+Proto.uint(3,2))}
 fun payload(cmd:VehicleCommand):ByteArray=when(cmd){VehicleCommand.STATUS->Proto.bytes(1,byteArrayOf());VehicleCommand.UNLOCK->Proto.uint(2,0);VehicleCommand.LOCK->Proto.uint(2,1);VehicleCommand.FRUNK->Proto.bytes(4,Proto.uint(6,3));VehicleCommand.TRUNK_OPEN->Proto.bytes(4,Proto.uint(5,3));VehicleCommand.TRUNK_CLOSE->Proto.bytes(4,Proto.uint(5,4))}
 fun envelope(route:ByteArray,uuid:ByteArray,payloadField:Int,payload:ByteArray,signature:ByteArray?=null,flags:Long=0):ByteArray {var msg=Proto.bytes(6,Proto.uint(1,2))+Proto.bytes(7,Proto.bytes(2,route))+Proto.bytes(payloadField,payload)+Proto.bytes(51,uuid);if(signature!=null)msg+=Proto.bytes(13,signature);if(flags!=0L)msg+=Proto.uint(52,flags);return msg}
}

/** Every session is authenticated against VIN + fresh request UUID before a command can be sent. */
class TeslaSession(private val vin:String,private val key:VehicleKey,private val now:()->Long={System.nanoTime()/1_000_000}) {
 val route=TeslaProtocol.random(16)
 val challenge=TeslaProtocol.random(16)
 private val started=now()
 private var secret:ByteArray?=null
 private var epoch=byteArrayOf()
 private var counter=0L
 private var clock=0L
 private var established=0L
 private var requestId=byteArrayOf()
 private var requestUuid=byteArrayOf()
 private var responseCounter=-1L
 private var sentAt=0L
 var ready=false;private set
 fun hello()=TeslaProtocol.envelope(route,challenge,14,Proto.bytes(1,key.publicBytes))
 fun acceptHello(message:ByteArray){require(now()-started in 0..30000){"会话验证超时"};val m=Proto.parse(message);require(m.b(6)?.let{Proto.parse(it).b(2)}?.contentEquals(route)==true){"会话路由不匹配"};require(m.b(50)?.contentEquals(challenge)==true){"会话挑战不匹配"};require(m.b(7)?.let{Proto.parse(it).u(1)}==2L){"车辆安全域不匹配"};val info=m.b(15)?:error("缺少会话信息");val f=Proto.parse(info);require(f.u(5)==0L){"此钥匙尚未获车辆授权，请先完成配对"};val pub=f.b(2)?:error("缺少车辆公钥");require(pub.size==65&&pub[0]==4.toByte());val ep=f.b(3)?:error("缺少会话标识");require(ep.size==16);val sessionKey=key.exchange(pub);require(sessionKey.size==16);val tag=m.b(13)?.let{Proto.parse(it).b(6)}?.let{Proto.parse(it).b(1)}?:error("缺少会话认证标签");val derived=TeslaProtocol.hmac(sessionKey,"session info".toByteArray());val expected=TeslaProtocol.hmac(derived,TeslaProtocol.metadata(0 to byteArrayOf(6),2 to vin.toByteArray(),6 to challenge)+info);require(MessageDigest.isEqual(expected,tag)){"车辆会话认证失败"};secret=sessionKey;epoch=ep;counter=f.u(1);clock=f.u(4);established=now();ready=true }
 fun command(cmd:VehicleCommand):ByteArray{check(ready);check(counter<0xffffffffL);counter++;val expires=clock+(now()-established)/1000+10;require(expires<=0xffffffffL);val nonce=TeslaProtocol.random(12);val aad=TeslaProtocol.digest("SHA-256",TeslaProtocol.metadata(0 to byteArrayOf(5),1 to byteArrayOf(2),2 to vin.toByteArray(),3 to epoch,4 to TeslaProtocol.be(expires),5 to TeslaProtocol.be(counter),7 to TeslaProtocol.be(1)));val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(secret!!,"AES"),GCMParameterSpec(128,nonce));cipher.updateAAD(aad);val encrypted=cipher.doFinal(TeslaProtocol.payload(cmd));val tag=encrypted.takeLast(16).toByteArray();requestId=byteArrayOf(5)+tag;requestUuid=TeslaProtocol.random(16);responseCounter=-1;sentAt=now();val sig=Proto.bytes(1,Proto.bytes(1,key.publicBytes))+Proto.bytes(5,Proto.bytes(1,epoch)+Proto.bytes(2,nonce)+Proto.uint(3,counter)+Proto.fixed32(4,expires)+Proto.bytes(5,tag));return TeslaProtocol.envelope(route,requestUuid,10,encrypted.dropLast(16).toByteArray(),sig,1)}
 fun decryptReply(message:ByteArray):ByteArray{check(ready&&requestId.isNotEmpty());require(now()-sentAt in 0..15000){"命令响应超时"};val m=Proto.parse(message);require(m.b(6)?.let{Proto.parse(it).b(2)}?.contentEquals(route)==true){"命令响应路由不匹配"};require(m.b(7)?.let{Proto.parse(it).u(1)}==2L);m.b(50)?.let{require(it.contentEquals(requestUuid)){"命令响应不匹配"}};val status=m.b(12)?.let(Proto::parse);val fault=status?.u(2)?:0;val sig=m.b(13)?.let{Proto.parse(it).b(9)}?.let(Proto::parse)?:error("车辆响应未加密，未确认执行结果");val n=sig.u(2);require(n>responseCounter){"重复的车辆响应"};val nonce=sig.b(1)?:error("缺少响应随机数");require(nonce.size==12);val tag=sig.b(3)?:error("缺少响应认证标签");require(tag.size==16);val aad=TeslaProtocol.digest("SHA-256",TeslaProtocol.metadata(0 to byteArrayOf(9),1 to byteArrayOf(2),2 to vin.toByteArray(),5 to TeslaProtocol.be(n),7 to TeslaProtocol.be(m.u(52)),8 to requestId,9 to TeslaProtocol.be(fault)));val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(secret!!,"AES"),GCMParameterSpec(128,nonce));cipher.updateAAD(aad);val plaintext=cipher.doFinal((m.b(10)?:error("缺少响应内容"))+tag);responseCounter=n;require(fault==0L){"车辆拒绝命令（代码 $fault）"};return plaintext}
}

data class VehicleObservation(val lock:Int?,val frunk:Int?,val trunk:Int?) {
 companion object {
  fun closureLabel(value:Int?)=when(value){0->"已关闭";1->"已打开";2->"未关严";4->"开启失败";5->"正在打开";6->"正在关闭";else->"状态未知"}
  fun lockLabel(value:Int?)=when(value){0->"已解锁";1,2->"已锁车";3->"部分解锁";else->"状态未知"}
 }
}
object VehicleReply {
 fun observation(payload:ByteArray):VehicleObservation?{val p=Proto.parse(payload);val raw=p.b(1)?:return null;val s=Proto.parse(raw);val closures=s.b(1)?.let(Proto::parse);return VehicleObservation(s.u(2).toInt(),closures?.u(6)?.toInt(),closures?.u(5)?.toInt())}
 /** null means keep waiting. No optimistic state changes based solely on write success. */
 fun commandStatus(payload:ByteArray):Int?{val s=Proto.parse(payload).b(4)?.let(Proto::parse)?:return null;val signed=s.b(2)?.let(Proto::parse);if(signed!=null&&signed.u(2)!=0L)return 2;return s.u(1).toInt()}
}
