package com.azkar.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "AdhanAlarm")
class AdhanAlarmPlugin : Plugin() {

    @PluginMethod
    fun schedule(call: PluginCall) {
        val atMillis = call.getLong("at")
        val requestCode = call.getInt("id")
        if (atMillis == null || requestCode == null) {
            call.reject("at and id are required")
            return
        }
        val soundRes = call.getString("soundRes") ?: "azan"
        val title = call.getString("title") ?: ""
        val body = call.getString("body") ?: ""

        val ctx = context
        val intent = Intent(ctx, AdhanAlarmReceiver::class.java).apply {
            putExtra("sound_res_name", soundRes)
            putExtra("title", title)
            putExtra("body", body)
        }
        val pending = PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            call.reject("SCHEDULE_EXACT_ALARM_NOT_GRANTED")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
        call.resolve()
    }

    @PluginMethod
    fun cancel(call: PluginCall) {
        val requestCode = call.getInt("id")
        if (requestCode == null) {
            call.reject("id is required")
            return
        }
        val ctx = context
        val intent = Intent(ctx, AdhanAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending)
        call.resolve()
    }

    @PluginMethod
    fun stopPlayback(call: PluginCall) {
        val ctx = context
        val stopIntent = Intent(ctx, AdhanForegroundService::class.java).apply {
            action = AdhanForegroundService.ACTION_STOP
        }
        ctx.startService(stopIntent)
        call.resolve()
    }
}
