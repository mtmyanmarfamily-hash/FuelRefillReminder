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
    public static final int REMINDER_REQUEST_CODE = 1001;

    /**
     * Schedule a daily check at 7:00 PM.
     * The ReminderReceiver will decide whether to actually show a notification
     * based on QuotaManager.shouldRemindTonight().
     */
    public static void scheduleDailyReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_DAILY_CHECK);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REMINDER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set next 7:00 PM
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 19);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setRepeating(
                            AlarmManager.RTC_WAKEUP,
                            cal.getTimeInMillis(),
                            AlarmManager.INTERVAL_DAY,
                            pendingIntent
                    );
                } else {
                    alarmManager.setInexactRepeating(
                            AlarmManager.RTC_WAKEUP,
                            cal.getTimeInMillis(),
                            AlarmManager.INTERVAL_DAY,
                            pendingIntent
                    );
                }
            } else {
                alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        cal.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                );
            }
            Log.d(TAG, "Daily reminder scheduled for 7:00 PM");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule alarm: " + e.getMessage());
        }
    }

    public static void cancelDailyReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_DAILY_CHECK);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REMINDER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "Daily reminder cancelled");
    }
}
