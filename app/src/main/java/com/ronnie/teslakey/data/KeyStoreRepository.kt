package com.ronnie.teslakey.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.ronnie.teslakey.protocol.TeslaProtocol
import com.ronnie.teslakey.protocol.VehicleKey
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.KeyAgreement

class KeyStoreRepository(context:Context) {
 val prefs=context.getSharedPreferences("vehicle",Context.MODE_PRIVATE)
 private val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
 private fun alias(vin:String)="tesla_"+TeslaProtocol.digest("SHA-256",vin.toByteArray()).take(12).joinToString(""){"%02x".format(it)}
 fun load(vin:String):VehicleKey {require(TeslaProtocol.validVin(vin));val name=alias(vin);if(!store.containsAlias(name)){val gen=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");gen.initialize(KeyGenParameterSpec.Builder(name,KeyProperties.PURPOSE_AGREE_KEY).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setUserAuthenticationRequired(false).build());gen.generateKeyPair()};val private=store.getKey(name,null) as PrivateKey;val public=store.getCertificate(name).publicKey as ECPublicKey
  return object:VehicleKey{override val publicBytes=byteArrayOf(4)+coord(public.w.affineX)+coord(public.w.affineY)
   override fun exchange(publicKey:ByteArray):ByteArray{require(publicKey.size==65&&publicKey[0]==4.toByte());val point=ECPoint(BigInteger(1,publicKey.copyOfRange(1,33)),BigInteger(1,publicKey.copyOfRange(33,65)));val remote=KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point,public.params));val secret=KeyAgreement.getInstance("ECDH").run{init(private);doPhase(remote,true);generateSecret()};return TeslaProtocol.digest("SHA-1",secret).copyOf(16)}
  }
 }
 private fun coord(x:BigInteger)=x.toByteArray().let{if(it.size>32)it.takeLast(32).toByteArray() else ByteArray(32-it.size)+it}
 fun forget(){val vin=prefs.getString("vin","")?:"";if(vin.isNotEmpty())store.deleteEntry(alias(vin));prefs.edit().remove("vin").remove("paired").apply()}
}
