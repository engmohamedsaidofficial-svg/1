package com.azkar.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * بيشغل الأذان كامل باستخدام MediaPlayer + AudioAttributes(USAGE_ALARM).
 * الفرق عن صوت الإشعار العادي: ده بيتجاوز Doze / توفير البطارية / قيود MIUI-Samsung
 * وبيكمل لحد ما يخلص الملف، تمامًا زي منبه الموبايل.
 */
class AdhanForegroundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "adhan-playback-channel"
        const val NOTIF_ID = 5501
        const val EXTRA_SOUND_RES = "sound_res_name"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val ACTION_STOP = "com.azkar.app.STOP_ADHAN"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        val soundResName = intent?.getStringExtra(EXTRA_SOUND_RES) ?: "azan"
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "حان الآن وقت الصلاة"
        val body = intent?.getStringExtra(EXTRA_BODY) ?: ""

        acquireWakeLock()
        startForeground(NOTIF_ID, buildNotification(title, body))
        playAdhan(soundResName)

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "azkar:adhan_wakelock")
        // حد أقصى ٦ دقائق حماية من أي خطأ يخلي الـ wakelock شغال للأبد
        wakeLock?.acquire(6 * 60 * 1000L)
    }

    private fun playAdhan(soundResName: String) {
        try {
            val resId = resources.getIdentifier(soundResName, "raw", packageName)
            if (resId == 0) {
                stopSelfSafely()
                return
            }
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(
                    this@AdhanForegroundService,
                    Uri.parse("android.resource://$packageName/$resId")
                )
                isLooping = false
                setOnCompletionListener { stopSelfSafely() }
                setOnErrorListener { _, _, _ -> stopSelfSafely(); true }
                prepare()
                start()
            }
        } catch (e: Exception) {
            stopSelfSafely()
        }
    }

    private fun buildNotification(title: String, body: String): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "تشغيل الأذان", NotificationManager.IMPORTANCE_LOW
                )
                // الصوت هنا متعمد إنه يبقى null: التشغيل بيتم يدويًا عن طريق MediaPlayer فوق
                channel.setSound(null, null)
                mgr.createNotificationChannel(channel)
            }
        }

        val stopIntent = Intent(this, AdhanForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .addAction(0, "إيقاف", stopPending)
            .build()
    }

    private fun stopSelfSafely() {
        try { mediaPlayer?.stop() } catch (e: Exception) {}
        try { mediaPlayer?.release() } catch (e: Exception) {}
        mediaPlayer = null
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (e: Exception) {}
        try { stopForeground(true) } catch (e: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        stopSelfSafely()
        super.onDestroy()
    }
}
