package com.myanmar.petrolreminder.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.myanmar.petrolreminder.utils.NotificationHelper;
import com.myanmar.petrolreminder.utils.QuotaManager;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_DAILY_CHECK = "com.myanmar.petrolreminder.DAILY_CHECK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_DAILY_CHECK.equals(intent.getAction())) {
            QuotaManager qm = new QuotaManager(context);
            // Only notify if there is still quota/refill available and reset is tomorrow
            if (qm.isSetupDone() && qm.shouldRemindTonight()) {
                NotificationHelper.createNotificationChannel(context);
                NotificationHelper.showReminderNotification(context, qm);
            }
        }
    }
}
