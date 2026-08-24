package com.azkar.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AdhanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val soundRes = intent.getStringExtra("sound_res_name") ?: "azan"
        val title = intent.getStringExtra("title") ?: "حان الآن وقت الصلاة"
        val body = intent.getStringExtra("body") ?: ""

        val serviceIntent = Intent(context, AdhanForegroundService::class.java).apply {
            putExtra(AdhanForegroundService.EXTRA_SOUND_RES, soundRes)
            putExtra(AdhanForegroundService.EXTRA_TITLE, title)
            putExtra(AdhanForegroundService.EXTRA_BODY, body)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
