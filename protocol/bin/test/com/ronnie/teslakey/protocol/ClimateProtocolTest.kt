package com.ronnie.teslakey.protocol

import org.junit.Assert.*
import org.junit.Test
import com.ronnie.teslakey.protocol.Proto.b
import com.ronnie.teslakey.protocol.Proto.u

class ClimateProtocolTest {
 private fun hex(b:ByteArray)=b.joinToString(""){"%02x".format(it.toInt() and 255)}
 @Test fun commandsMatchOfficialCarServerSchema() {
  // Action.vehicleAction(2) -> VehicleAction.hvacAutoAction(10) -> power_on(1).
  assertEquals("120452020801",hex(TeslaProtocol.payload(VehicleCommand.CLIMATE_ON)))
  assertEquals("12025200",hex(TeslaProtocol.payload(VehicleCommand.CLIMATE_OFF)))
  // VehicleAction.getVehicleData(1) -> GetVehicleData.getClimateState(3).
  assertEquals("12040a021a00",hex(TeslaProtocol.payload(VehicleCommand.CLIMATE_STATUS)))
 }
 @Test fun climatePowerIsReadFromVehicleDataNotAcknowledgement() {
  fun response(on:Boolean)=Proto.bytes(1,byteArrayOf())+Proto.bytes(2,Proto.bytes(4,Proto.uint(110,if(on)1 else 0)))
  assertEquals(true,ClimateProtocol.reply(response(true)).on)
  assertEquals(false,ClimateProtocol.reply(response(false)).on)
  val ack=ClimateProtocol.reply(Proto.bytes(1,byteArrayOf()))
  assertEquals(true,ack.success)
  assertNull(ack.on)
  assertNull(ClimateProtocol.reply(byteArrayOf()).success)
 }
 @Test fun vehicleRejectionIsPreserved() {
  val payload=Proto.bytes(1,Proto.uint(1,1)+Proto.bytes(2,Proto.bytes(1,"not_allowed".toByteArray())))
  val reply=ClimateProtocol.reply(payload)
  assertEquals(false,reply.success)
  assertEquals("not_allowed",reply.reason)
  assertNull(reply.on)
 }
 @Test fun infotainmentHasSeparateDestinationAndRoute() {
  val key=object:VehicleKey {
   override val publicBytes=byteArrayOf(4)+ByteArray(64)
   override fun exchange(publicKey:ByteArray)=ByteArray(16)
  }
  val body=TeslaSession("5YJS0000000000000",key)
  val climate=TeslaSession("5YJS0000000000000",key,domain=3)
  val hello=Proto.parse(climate.hello())
  assertEquals(3L,Proto.parse(hello.b(6)!!).u(1))
  val reply=Proto.bytes(6,Proto.bytes(2,climate.route))+Proto.bytes(7,Proto.uint(1,3))
  assertTrue(climate.accepts(reply))
  assertFalse(body.accepts(reply))
 }

 @Test fun authenticatedClimateRoundTripUsesInfotainmentDomain() {
  val vin="5YJS0000000000000"
  val secret=ByteArray(16){it.toByte()}
  val key=object:VehicleKey {
   override val publicBytes=byteArrayOf(4)+ByteArray(64)
   override fun exchange(publicKey:ByteArray)=secret
  }
  for(domain in listOf(2,3)) {
   val session=TeslaSession(vin,key,now={1000L},domain=domain)
   val info=Proto.uint(1,4)+Proto.bytes(2,key.publicBytes)+Proto.bytes(3,ByteArray(16))+Proto.fixed32(4,100)
   val mac=TeslaProtocol.hmac(TeslaProtocol.hmac(secret,"session info".toByteArray()),
    TeslaProtocol.metadata(0 to byteArrayOf(6),2 to vin.toByteArray(),6 to session.challenge)+info)
   val route=Proto.bytes(6,Proto.bytes(2,session.route))+Proto.bytes(7,Proto.uint(1,domain.toLong()))
   session.acceptHello(route+Proto.bytes(50,session.challenge)+Proto.bytes(15,info)+Proto.bytes(13,Proto.bytes(6,Proto.bytes(1,mac))))
   val request=Proto.parse(session.command(if(domain==3)VehicleCommand.CLIMATE_ON else VehicleCommand.LOCK))
   assertEquals(domain.toLong(),Proto.parse(request.b(6)!!).u(1))
   val sig=Proto.parse(Proto.parse(request.b(13)!!).b(5)!!)
   val commandAad=TeslaProtocol.digest("SHA-256",TeslaProtocol.metadata(
    0 to byteArrayOf(5),1 to byteArrayOf(domain.toByte()),2 to vin.toByteArray(),3 to sig.b(1)!!,
    4 to TeslaProtocol.be(sig.u(4)),5 to TeslaProtocol.be(sig.u(3)),7 to TeslaProtocol.be(1)))
   val decrypt=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
   decrypt.init(javax.crypto.Cipher.DECRYPT_MODE,javax.crypto.spec.SecretKeySpec(secret,"AES"),javax.crypto.spec.GCMParameterSpec(128,sig.b(2)!!))
   decrypt.updateAAD(commandAad)
   assertArrayEquals(TeslaProtocol.payload(if(domain==3)VehicleCommand.CLIMATE_ON else VehicleCommand.LOCK),decrypt.doFinal(request.b(10)!!+sig.b(5)!!))
   val nonce=ByteArray(12){(it+1).toByte()}
   val aad=TeslaProtocol.digest("SHA-256",TeslaProtocol.metadata(
    0 to byteArrayOf(9),1 to byteArrayOf(domain.toByte()),2 to vin.toByteArray(),5 to TeslaProtocol.be(1),
    7 to TeslaProtocol.be(0),8 to (byteArrayOf(5)+sig.b(5)!!),9 to TeslaProtocol.be(0)))
   val plain=Proto.bytes(1,byteArrayOf())
   val encrypt=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
   encrypt.init(javax.crypto.Cipher.ENCRYPT_MODE,javax.crypto.spec.SecretKeySpec(secret,"AES"),javax.crypto.spec.GCMParameterSpec(128,nonce))
   encrypt.updateAAD(aad)
   val encrypted=encrypt.doFinal(plain)
   val reply=route+Proto.bytes(50,request.b(51)!!)+Proto.bytes(10,encrypted.dropLast(16).toByteArray())+
    Proto.bytes(13,Proto.bytes(9,Proto.bytes(1,nonce)+Proto.uint(2,1)+Proto.bytes(3,encrypted.takeLast(16).toByteArray())))
   assertArrayEquals(plain,session.decryptReply(reply))
   try { session.decryptReply(reply);fail("Replay must be rejected") } catch(_:IllegalArgumentException) {}
  }
 }
}
