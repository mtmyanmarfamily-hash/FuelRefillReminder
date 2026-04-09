package com.myanmar.petrolreminder;

import android.content.Context;
import java.util.Calendar;

public class QuotaManager {

    public enum ReminderType {
        MORNING,
        EVENING,
        TONIGHT
    }

    private Context context;
    private boolean notifRefillDayMorning = true;   // user preference
    private boolean notifRefillDayEvening = true;   // user preference
    private boolean notifTonight = true;            // user preference
    private int refillDayOfMonth = 10;             // example: 10th of each month
    private int estimatedQueueMinutes = 30;        // example

    public QuotaManager(Context context) {
        this.context = context;
    }

    /** ------------------- Preference Checks ------------------- */

    public boolean isNotifRefillDayMorningEnabled() {
        return notifRefillDayMorning;
    }

    public boolean isNotifRefillDayEveningEnabled() {
        return notifRefillDayEvening;
    }

    public boolean isNotifTonightEnabled() {
        return notifTonight;
    }

    /** ------------------- Refill Day Logic ------------------- */

    public boolean isTodayEligibleRefillDay() {
        Calendar c = Calendar.getInstance();
        int today = c.get(Calendar.DAY_OF_MONTH);
        return today == refillDayOfMonth;
    }

    public boolean isTomorrowEligibleRefillDay() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, 1);
        int tomorrow = c.get(Calendar.DAY_OF_MONTH);
        return tomorrow == refillDayOfMonth;
    }

    public ReminderType getReminderTypeForTonight() {
        return ReminderType.TONIGHT;
    }

    /** ------------------- Refuel Status Message ------------------- */

    public String getRefuelStatusMessage() {
        if (isTodayEligibleRefillDay()) {
            return "Today is your refill day! Estimated queue: " + estimatedQueueMinutes + " mins.";
        } else if (isTomorrowEligibleRefillDay()) {
            return "Tomorrow is your refill day. Prepare your vehicle.";
        } else {
            return "Next refill day is on " + refillDayOfMonth + " of the month.";
        }
    }

    /** ------------------- Estimated Queue ------------------- */

    public int getEstimatedQueueMinutes() {
        return estimatedQueueMinutes;
    }

    public void setEstimatedQueueMinutes(int minutes) {
        estimatedQueueMinutes = minutes;
    }

    /** ------------------- Refill Day Setter (Optional) ------------------- */

    public void setRefillDayOfMonth(int day) {
        refillDayOfMonth = day;
    }

    /** ------------------- Notification Preference Setter ------------------- */

    public void setNotifRefillDayMorning(boolean enabled) {
        notifRefillDayMorning = enabled;
    }

    public void setNotifRefillDayEvening(boolean enabled) {
        notifRefillDayEvening = enabled;
    }

    public void setNotifTonight(boolean enabled) {
        notifTonight = enabled;
    }
}
