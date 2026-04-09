package com.myanmar.petrolreminder.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.myanmar.petrolreminder.utils.CarStore;
import com.myanmar.petrolreminder.utils.NotificationHelper;
import com.myanmar.petrolreminder.utils.QuotaManager;

import java.util.List;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_NOON    = "com.myanmar.petrolreminder.ALARM_NOON";
    public static final String ACTION_EVENING = "com.myanmar.petrolreminder.ALARM_EVENING";
    public static final String ACTION_MORNING = "com.myanmar.petrolreminder.ALARM_MORNING";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        CarStore cs = new CarStore(context);
        if (!cs.hasCars()) return;
        String action = intent.getAction();
        if (action == null) return;

        NotificationHelper.createNotificationChannel(context);

        // Check ALL cars — notify for each car that needs it
        List<CarStore.Car> cars = cs.getAllCars();
        for (CarStore.Car car : cars) {
            QuotaManager qm = new QuotaManager(context, car.id);
            QuotaManager.ReminderType type = qm.getReminderTypeForTonight();

            switch (action) {
                case ACTION_NOON:
                    if (qm.isNotifDayBeforeNoonEnabled() && type != QuotaManager.ReminderType.NONE)
                        NotificationHelper.showDayBeforeNotification(context, qm, false);
                    break;
                case ACTION_EVENING:
                    if (qm.isNotifDayBeforeEveningEnabled() && type != QuotaManager.ReminderType.NONE)
                        NotificationHelper.showDayBeforeNotification(context, qm, true);
                    break;
                case ACTION_MORNING:
                    if (qm.isNotifRefillDayMorningEnabled() && qm.isTodayEligibleRefillDay())
                        NotificationHelper.showRefillDayMorningNotification(context, qm);
                    break;
            }
        }
    }
}
