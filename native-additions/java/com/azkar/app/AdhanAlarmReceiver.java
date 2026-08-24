package com.azkar.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AdhanAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String soundRes = intent.getStringExtra("sound_res_name");
        if (soundRes == null) soundRes = "azan";
        String title = intent.getStringExtra("title");
        if (title == null) title = "حان الآن وقت الصلاة";
        String body = intent.getStringExtra("body");
        if (body == null) body = "";

        Intent serviceIntent = new Intent(context, AdhanForegroundService.class);
        serviceIntent.putExtra(AdhanForegroundService.EXTRA_SOUND_RES, soundRes);
        serviceIntent.putExtra(AdhanForegroundService.EXTRA_TITLE, title);
        serviceIntent.putExtra(AdhanForegroundService.EXTRA_BODY, body);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
