package com.myanmar.petrolreminder.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.myanmar.petrolreminder.utils.AlarmScheduler;
import com.myanmar.petrolreminder.utils.QuotaManager;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            QuotaManager qm = new QuotaManager(context);
            if (qm.isSetupDone()) {
                AlarmScheduler.scheduleDailyReminder(context);
            }
        }
    }
}
