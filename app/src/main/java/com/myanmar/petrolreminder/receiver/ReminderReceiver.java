package com.myanmar.petrolreminder.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.myanmar.petrolreminder.utils.NotificationHelper;
import com.myanmar.petrolreminder.utils.QuotaManager;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_NOON    = "com.myanmar.petrolreminder.ALARM_NOON";
    public static final String ACTION_EVENING = "com.myanmar.petrolreminder.ALARM_EVENING";
    public static final String ACTION_MORNING = "com.myanmar.petrolreminder.ALARM_MORNING";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !isSetupDone(context)) return;
        String action = intent.getAction();
        if (action == null) return;

        QuotaManager qm = new QuotaManager(context);
        NotificationHelper.createNotificationChannel(context);

        switch (action) {
            case ACTION_NOON:
                // မနက်တည်ဆိုင်: ဆီဖြည့်နိုင်သောနေ့ မနက်က နေ့ (day before) noon
                if (qm.isNotifDayBeforeNoonEnabled() && isDayBeforeEligibleDay(qm))
                    NotificationHelper.showDayBeforeNotification(context, qm, false);
                break;

            case ACTION_EVENING:
                // ညနေ: ဆီဖြည့်နိုင်သောနေ့ မနက်က နေ့ evening
                if (qm.isNotifDayBeforeEveningEnabled() && isDayBeforeEligibleDay(qm))
                    NotificationHelper.showDayBeforeNotification(context, qm, true);
                break;

            case ACTION_MORNING:
                // မနက် ၇ နာရီ: ဆီဖြည့်နိုင်သောနေ့ မနက်
                if (qm.isNotifRefillDayMorningEnabled() && isTodayEligibleDay(qm))
                    NotificationHelper.showRefillDayMorningNotification(context, qm);
                break;
        }
    }

    private boolean isSetupDone(Context context) {
        return new QuotaManager(context).isSetupDone();
    }

    /**
     * True if tomorrow is the next eligible refill day AND user still has quota/refills.
     *
     * Logic:
     *  - No window yet (refillCount==0): tomorrow is eligible if it matches odd/even day
     *  - Window active, refill 1 done: tomorrow is next odd/even day within window
     *  - Both refills done / quota used: false (nothing to remind about)
     */
    private boolean isDayBeforeEligibleDay(QuotaManager qm) {
        // If quota or refills exhausted, no point reminding
        if (qm.getRemainingRefills() <= 0 || qm.getRemainingLitres() <= 0.01f) return false;

        long nextEligible = qm.getNextEligibleDayMs();
        if (nextEligible < 0) return false;

        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        Calendar eligible = Calendar.getInstance();
        eligible.setTimeInMillis(nextEligible);
        return tomorrow.get(Calendar.YEAR)        == eligible.get(Calendar.YEAR)
            && tomorrow.get(Calendar.DAY_OF_YEAR) == eligible.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * True if today is the next eligible refill day.
     */
    private boolean isTodayEligibleDay(QuotaManager qm) {
        // Also check: user still has quota/refills
        if (qm.getRemainingRefills() <= 0 || qm.getRemainingLitres() <= 0.01f) return false;
        boolean needEven = qm.isEvenVehicle();
        int today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        return (today % 2 == 0) == needEven;
    }
}
