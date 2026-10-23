package com.ronnie.teslakey.protocol

import java.io.ByteArrayOutputStream

/** Minimal bounded protobuf wire codec for the official VCSEC/universal-message subset. */
object Proto {
 data class Field(val number:Int,val wire:Int,val data:ByteArray=byteArrayOf(),val value:Long=0)
 fun varint(v:Long):ByteArray { require(v>=0); var x=v; val out=ByteArrayOutputStream(); while(x>127){out.write(((x and 127) or 128).toInt());x=x ushr 7};out.write(x.toInt());return out.toByteArray() }
 fun uint(n:Int,v:Long)=varint((n.toLong() shl 3))+varint(v)
 fun bytes(n:Int,b:ByteArray)=varint((n.toLong() shl 3) or 2)+varint(b.size.toLong())+b
 fun fixed32(n:Int,v:Long)=varint((n.toLong() shl 3) or 5)+ByteArray(4){(v ushr (it*8)).toByte()}
 fun parse(b:ByteArray):List<Field>{require(b.size<=65535);val fields=mutableListOf<Field>();var p=0
  fun readVar():Long{var v=0L;for(i in 0..9){require(p<b.size){"Truncated protobuf"};val x=b[p++].toInt() and 255;require(i!=9||x<=1);v=v or ((x and 127).toLong() shl (7*i));if(x<128)return v};error("Invalid varint")}
  while(p<b.size){require(fields.size<4096);val key=readVar();val n=(key ushr 3).toInt();require(n>0);val wire=(key and 7).toInt();when(wire){0->fields.add(Field(n,wire,value=readVar()));2->{val length=readVar();require(length>=0&&length<=b.size-p);val size=length.toInt();fields.add(Field(n,wire,b.copyOfRange(p,p+size)));p+=size};5->{require(p+4<=b.size);var v=0L;repeat(4){v=v or ((b[p++].toLong() and 255) shl(it*8))};fields.add(Field(n,wire,value=v))};1->{require(p+8<=b.size);p+=8};else->error("Unsupported wire type")}}
  return fields
 }
 fun List<Field>.b(n:Int)=lastOrNull{it.number==n&&it.wire==2}?.data
 fun List<Field>.u(n:Int)=lastOrNull{it.number==n&&(it.wire==0||it.wire==5)}?.value?:0L
}

class BleFrames {
 private var buffer=byteArrayOf()
 fun clear(){buffer=byteArrayOf()}
 fun feed(chunk:ByteArray):List<ByteArray>{require(buffer.size+chunk.size<=131074);buffer+=chunk;val out=mutableListOf<ByteArray>();while(buffer.size>=2){val n=((buffer[0].toInt()and 255)shl 8)or(buffer[1].toInt()and 255);require(n in 1..4096){"Invalid BLE frame length"};if(buffer.size<2+n)break;out.add(buffer.copyOfRange(2,2+n));buffer=buffer.copyOfRange(2+n,buffer.size)};return out}
 companion object {fun encode(b:ByteArray):ByteArray{require(b.size in 1..4096);return byteArrayOf((b.size ushr 8).toByte(),b.size.toByte())+b}}
}
