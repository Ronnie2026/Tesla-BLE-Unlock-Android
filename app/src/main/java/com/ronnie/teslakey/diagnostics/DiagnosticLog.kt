package com.ronnie.teslakey.diagnostics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Private, bounded JSONL history. No packets, VINs, keys or raw Bluetooth addresses. */
class DiagnosticLog(context: Context) {
 private val directory=File(context.filesDir,"diagnostics")
 private val run=UUID.randomUUID().toString().take(8)
 private var attempt="startup"
 init {
  val version=context.packageManager.getPackageInfo(context.packageName,0).versionName
  event("app.start","version=$version android=${Build.VERSION.SDK_INT} model=${Build.MODEL}")
 }
 fun begin(kind:String){attempt=UUID.randomUUID().toString().take(8);event("attempt.start",kind)}
 fun deviceId(address:String)=MessageDigest.getInstance("SHA-256").digest(address.toByteArray()).take(6).joinToString(""){"%02x".format(it.toInt() and 255)}
 @Synchronized fun event(name:String,detail:String="") {
  try {
   directory.mkdirs()
   val current=File(directory,"events.jsonl")
   if(current.length()>512*1024){
    File(directory,"events.4.jsonl").delete()
    for(i in 3 downTo 1)File(directory,"events.$i.jsonl").renameTo(File(directory,"events.${i+1}.jsonl"))
    current.renameTo(File(directory,"events.1.jsonl"))
   }
   val row=JSONObject().put("time",SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(Date()))
    .put("elapsedMs",SystemClock.elapsedRealtime()).put("run",run).put("attempt",attempt)
    .put("event",name).put("detail",detail.take(800))
   current.appendText(row.toString()+"\n")
  }catch(e:Exception){Log.e("TeslaDiagnostics","Cannot persist diagnostic event: ${e.javaClass.simpleName}")}
 }
}
