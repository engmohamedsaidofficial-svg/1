package com.azkar.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AdhanAlarm")
public class AdhanAlarmPlugin extends Plugin {

    @PluginMethod
    public void schedule(PluginCall call) {
        Long atMillis = call.getLong("at");
        Integer requestCode = call.getInt("id");
        if (atMillis == null || requestCode == null) {
            call.reject("at and id are required");
            return;
        }
        String soundRes = call.getString("soundRes", "azan");
        String title = call.getString("title", "");
        String body = call.getString("body", "");

        Context ctx = getContext();
        Intent intent = new Intent(ctx, AdhanAlarmReceiver.class);
        intent.putExtra("sound_res_name", soundRes);
        intent.putExtra("title", title);
        intent.putExtra("body", body);

        PendingIntent pending = PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            call.reject("SCHEDULE_EXACT_ALARM_NOT_GRANTED");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, atMillis, pending);
        }
        call.resolve();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        Integer requestCode = call.getInt("id");
        if (requestCode == null) {
            call.reject("id is required");
            return;
        }
        Context ctx = getContext();
        Intent intent = new Intent(ctx, AdhanAlarmReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending);
        call.resolve();
    }

    @PluginMethod
    public void stopPlayback(PluginCall call) {
        Context ctx = getContext();
        Intent stopIntent = new Intent(ctx, AdhanForegroundService.class);
        stopIntent.setAction(AdhanForegroundService.ACTION_STOP);
        ctx.startService(stopIntent);
        call.resolve();
    }
}
