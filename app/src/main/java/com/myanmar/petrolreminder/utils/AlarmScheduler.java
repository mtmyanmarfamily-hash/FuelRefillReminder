package com.myanmar.petrolreminder.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.myanmar.petrolreminder.receiver.ReminderReceiver;

import java.util.Calendar;

public class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";

    // Request codes for each alarm slot
    public static final int RC_DAY_BEFORE_NOON    = 1001; // မနေ့/မနက်တစ်ခါ  12:00 pm
    public static final int RC_DAY_BEFORE_EVENING = 1002; // မနေ့/မနက်တစ်ခါ  18:00 pm
    public static final int RC_REFILL_DAY_MORNING = 1003; // ဖြည့်နိုင်သောနေ့  07:00 am

    public static void scheduleAllAlarms(Context context) {
        QuotaManager qm = new QuotaManager(context);

        // All 3 fire daily; ReminderReceiver decides whether to show notification
        scheduleDailyAt(context, RC_DAY_BEFORE_NOON,    12, 0, ReminderReceiver.ACTION_NOON);
        scheduleDailyAt(context, RC_DAY_BEFORE_EVENING, 18, 0, ReminderReceiver.ACTION_EVENING);
        scheduleDailyAt(context, RC_REFILL_DAY_MORNING,  7, 0, ReminderReceiver.ACTION_MORNING);
        Log.d(TAG, "All 3 alarms scheduled");
    }

    private static void scheduleDailyAt(Context context, int requestCode,
                                         int hour, int minute, String action) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis())
            cal.add(Calendar.DAY_OF_YEAR, 1);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY, pi);
            } else {
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY, pi);
            }
        } catch (Exception e) {
            Log.e(TAG, "Alarm schedule failed: " + e.getMessage());
        }
    }

    public static void cancelAll(Context context) {
        cancelAlarm(context, RC_DAY_BEFORE_NOON,    ReminderReceiver.ACTION_NOON);
        cancelAlarm(context, RC_DAY_BEFORE_EVENING, ReminderReceiver.ACTION_EVENING);
        cancelAlarm(context, RC_REFILL_DAY_MORNING, ReminderReceiver.ACTION_MORNING);
    }

    private static void cancelAlarm(Context context, int requestCode, String action) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }
    public static void scheduleDailyReminder(android.content.Context context) {
        // TODO: implement reminder logic
    }

}
