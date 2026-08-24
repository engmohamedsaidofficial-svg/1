package com.azkar.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * بيشغل الأذان كامل باستخدام MediaPlayer + AudioAttributes(USAGE_ALARM).
 * الفرق عن صوت الإشعار العادي: ده بيتجاوز Doze / توفير البطارية / قيود MIUI-Samsung
 * وبيكمل لحد ما يخلص الملف، تمامًا زي منبه الموبايل.
 * كمان بيوقف نفسه لو المستخدم دوس أي زرار صوت (رفع/خفض) وقت التشغيل.
 */
public class AdhanForegroundService extends Service {

    public static final String CHANNEL_ID = "adhan-playback-channel";
    public static final int NOTIF_ID = 5501;
    public static final String EXTRA_SOUND_RES = "sound_res_name";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BODY = "body";
    public static final String ACTION_STOP = "com.azkar.app.STOP_ADHAN";

    private MediaPlayer mediaPlayer;
    private PowerManager.WakeLock wakeLock;
    private BroadcastReceiver volumeKeyReceiver;
    private boolean volumeReceiverRegistered = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelfSafely();
            return START_NOT_STICKY;
        }

        String soundResName = intent != null ? intent.getStringExtra(EXTRA_SOUND_RES) : null;
        if (soundResName == null) soundResName = "azan";
        String title = intent != null ? intent.getStringExtra(EXTRA_TITLE) : null;
        if (title == null) title = "حان الآن وقت الصلاة";
        String body = intent != null ? intent.getStringExtra(EXTRA_BODY) : null;
        if (body == null) body = "";

        acquireWakeLock();
        startForeground(NOTIF_ID, buildNotification(title, body));
        registerVolumeKeyReceiver();
        playAdhan(soundResName);

        return START_NOT_STICKY;
    }

    /* ===== إيقاف الأذان بمجرد ما المستخدم يدوس زرار رفع أو خفض الصوت ===== */
    /* أندرويد بيبعث VOLUME_CHANGED_ACTION لما مستوى أي قناة صوت يتغيّر، حتى والشاشة مقفولة، */
    /* طالما التطبيق (الـ Service) شغال في الخلفية. بنفلتر بس على قناة الـ ALARM عشان مانوقفش */
    /* الأذان غلط لو حد غيّر صوت المكالمة أو الميديا لسبب تاني. */
    private void registerVolumeKeyReceiver() {
        if (volumeReceiverRegistered) return;
        volumeKeyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                if (streamType == AudioManager.STREAM_ALARM || streamType == -1) {
                    stopSelfSafely();
                }
            }
        };
        try {
            IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            // من أندرويد 13 (API 33) لازم تتحدد صراحة RECEIVER_EXPORTED/RECEIVER_NOT_EXPORTED
            // وإلا الـ registerReceiver بيرمي SecurityException وقت التشغيل.
            // ContextCompat.registerReceiver بتتعامل مع الفرق بين الإصدارات القديمة والحديثة تلقائيًا.
            ContextCompat.registerReceiver(
                this,
                volumeKeyReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            );
            volumeReceiverRegistered = true;
        } catch (Exception e) {
            Log.e("AdhanForegroundService", "فشل تسجيل مستقبل زرار الصوت", e);
        }
    }

    private void unregisterVolumeKeyReceiver() {
        if (!volumeReceiverRegistered) return;
        try { unregisterReceiver(volumeKeyReceiver); } catch (Exception e) {}
        volumeReceiverRegistered = false;
        volumeKeyReceiver = null;
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "azkar:adhan_wakelock");
        wakeLock.acquire(6 * 60 * 1000L); // حد أقصى ٦ دقائق حماية
    }

    private void playAdhan(String soundResName) {
        try {
            int resId = getResources().getIdentifier(soundResName, "raw", getPackageName());
            if (resId == 0) {
                stopSelfSafely();
                return;
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            );
            Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + resId);
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setLooping(false);
            mediaPlayer.setOnCompletionListener(mp -> stopSelfSafely());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> { stopSelfSafely(); return true; });
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            stopSelfSafely();
        }
    }

    private Notification buildNotification(String title, String body) {
        NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "تشغيل الأذان", NotificationManager.IMPORTANCE_LOW
                );
                channel.setSound(null, null); // الصوت بيتشغل يدويًا عن طريق MediaPlayer فوق
                mgr.createNotificationChannel(channel);
            }
        }

        Intent stopIntent = new Intent(this, AdhanForegroundService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body + " — دوس أي زرار صوت للإيقاف")
            .setSmallIcon(getApplicationInfo().icon)
            .setOngoing(true)
            .addAction(0, "إيقاف", stopPending)
            .build();
    }

    private void stopSelfSafely() {
        try { if (mediaPlayer != null) mediaPlayer.stop(); } catch (Exception e) {}
        try { if (mediaPlayer != null) mediaPlayer.release(); } catch (Exception e) {}
        mediaPlayer = null;
        unregisterVolumeKeyReceiver();
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception e) {}
        try { stopForeground(true); } catch (Exception e) {}
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopSelfSafely();
        super.onDestroy();
    }
}
