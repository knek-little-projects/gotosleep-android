package com.example.myapplication;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;

public class RepeatSmartlockAlarm extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Kernel kernel = new Kernel(context);
        kernel.log("AlarmReceiver.onReceive: " + intent.toString());

        Intent i = new Intent(context, RepeatSmartlockService.class);
        i.putExtra("caller", "AlarmManager");
        context.startService(i);
    }

    static public void setAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, RepeatSmartlockAlarm.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

        Calendar now = Calendar.getInstance();
        long criticalTimeInMillis = (new Kernel(context)).getNextCriticalTimeInMillis(now);
        Log.w("Alarm will fire after (millis)", Long.toString(criticalTimeInMillis - now.getTimeInMillis()));
        alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                criticalTimeInMillis,
                AlarmManager.INTERVAL_DAY,
                alarmIntent
        );
    }

    static public boolean isSomeAlarmSet(Context context) {
        Intent intent = new Intent(context, RepeatSmartlockAlarm.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE);
        return  pendingIntent != null;
    }

    static public void cancelAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, RepeatSmartlockAlarm.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(sender);
        sender.cancel();
    }

}
