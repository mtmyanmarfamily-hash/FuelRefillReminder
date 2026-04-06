package com.myanmar.petrolreminder.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.myanmar.petrolreminder.utils.NotificationHelper;
import com.myanmar.petrolreminder.utils.QuotaManager;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_NOON    = "com.myanmar.petrolreminder.ALARM_NOON";
    public static final String ACTION_EVENING = "com.myanmar.petrolreminder.ALARM_EVENING";
    public static final String ACTION_MORNING = "com.myanmar.petrolreminder.ALARM_MORNING";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        QuotaManager qm = new QuotaManager(context);
        if (!qm.isSetupDone()) return;
        String action = intent.getAction();
        if (action == null) return;

        NotificationHelper.createNotificationChannel(context);
        QuotaManager.ReminderType type = qm.getReminderTypeForTonight();

        switch (action) {
            case ACTION_NOON:
                // မနေ့ noon — မနက်ဖြန် ဖြည့်နိုင်မည် သတိပေး
                if (qm.isNotifDayBeforeNoonEnabled() && type != QuotaManager.ReminderType.NONE) {
                    NotificationHelper.showDayBeforeNotification(context, qm, false);
                }
                break;

            case ACTION_EVENING:
                // မနေ့ ညနေ — မနက်ဖြန် ဖြည့်နိုင်မည် သတိပေး
                if (qm.isNotifDayBeforeEveningEnabled() && type != QuotaManager.ReminderType.NONE) {
                    NotificationHelper.showDayBeforeNotification(context, qm, true);
                }
                break;

            case ACTION_MORNING:
                // မနက် ၇ နာရီ — ဒီနေ့ ဖြည့်နိုင်သောနေ့ ဆိုလျှင် သတိပေး
                if (qm.isNotifRefillDayMorningEnabled() && qm.isTodayEligibleRefillDay()) {
                    NotificationHelper.showRefillDayMorningNotification(context, qm);
                }
                break;
        }
    }
}
