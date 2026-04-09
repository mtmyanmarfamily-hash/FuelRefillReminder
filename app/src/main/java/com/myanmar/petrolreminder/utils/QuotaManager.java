package com.myanmar.petrolreminder.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * QuotaManager with full alternate-day refill logic
 * and multiple reminders per day
 */
public class QuotaManager {

    public static final String KEY_TOTAL_QUOTA      = "total_quota_litres";
    public static final String KEY_VEHICLE_NAME     = "vehicle_name";
    public static final String KEY_SETUP_DONE       = "setup_done";
    public static final String KEY_WINDOW_START_MS  = "window_start_ms";
    public static final String KEY_REFILL_COUNT     = "refill_count";
    public static final String KEY_REFILL_1_LITRES  = "refill_1_litres";
    public static final String KEY_REFILL_2_LITRES  = "refill_2_litres";
    public static final String KEY_REFILL_2_DATE_MS = "refill_2_date_ms";

    public static final String KEY_NOTIF_DAY_BEFORE_NOON    = "notif_day_before_noon";
    public static final String KEY_NOTIF_DAY_BEFORE_EVENING = "notif_day_before_evening";
    public static final String KEY_NOTIF_REFILL_DAY_MORNING = "notif_refill_day_morning";

    public static final int MAX_REFILLS = 2;
    public static final int WINDOW_DAYS = 7;
    public static final long WINDOW_MS = (long) WINDOW_DAYS * 24 * 60 * 60 * 1000;

    public static final SimpleDateFormat DATE_FMT   = new SimpleDateFormat("dd MMM yyyy (EEE)", Locale.ENGLISH);
    public static final SimpleDateFormat DATE_SHORT = new SimpleDateFormat("dd MMM (EEE)", Locale.ENGLISH);

    private final SharedPreferences prefs;

    /** Multi-car constructor — uses per-car prefs file */
    public QuotaManager(Context ctx, String carId) {
        prefs = ctx.getApplicationContext()
                   .getSharedPreferences("car_" + carId, Context.MODE_PRIVATE);
    }

    /** Legacy single-car constructor */
    public QuotaManager(Context ctx) {
        prefs = ctx.getApplicationContext()
                   .getSharedPreferences("petrol_prefs", Context.MODE_PRIVATE);
    }

    // ─── Setup ─────────────────────────────
    public void saveSetup(String name, float quota) {
        prefs.edit().putString(KEY_VEHICLE_NAME, name)
             .putFloat(KEY_TOTAL_QUOTA, quota)
             .putBoolean(KEY_SETUP_DONE, true).apply();
    }

    public boolean isSetupDone()    { return prefs.getBoolean(KEY_SETUP_DONE, false); }
    public String getVehicleName()  { return prefs.getString(KEY_VEHICLE_NAME, "My Vehicle"); }
    public float  getTotalQuota()   { return prefs.getFloat(KEY_TOTAL_QUOTA, 0f); }

    // ─── Notification toggles ─────────────
    public boolean isNotifDayBeforeNoonEnabled()    { return prefs.getBoolean(KEY_NOTIF_DAY_BEFORE_NOON, true); }
    public boolean isNotifDayBeforeEveningEnabled() { return prefs.getBoolean(KEY_NOTIF_DAY_BEFORE_EVENING, true); }
    public boolean isNotifRefillDayMorningEnabled(){ return prefs.getBoolean(KEY_NOTIF_REFILL_DAY_MORNING, true); }

    // ─── Odd / Even ─────────────
    public boolean isEvenVehicle() {
        for (char c : getVehicleName().toCharArray())
            if (Character.isDigit(c)) return Character.getNumericValue(c) % 2 == 0;
        return true; // default even if no digit found
    }

    public String getVehicleTypeLabel() { return isEvenVehicle() ? "စုံကား" : "မကား"; }

    // ─── Window & Refill ─────────────
    public boolean isWindowActive() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        return start != 0L && (System.currentTimeMillis() - start) < WINDOW_MS;
    }

    public void checkAndResetExpiredWindow() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start != 0L && (System.currentTimeMillis() - start) >= WINDOW_MS) clearWindow();
    }

    private void clearWindow() {
        prefs.edit().remove(KEY_WINDOW_START_MS).remove(KEY_REFILL_COUNT)
             .remove(KEY_REFILL_1_LITRES).remove(KEY_REFILL_2_LITRES)
             .remove(KEY_REFILL_2_DATE_MS).apply();
    }

    public void setWindowStartDate(long ms) { prefs.edit().putLong(KEY_WINDOW_START_MS, ms).apply(); }
    public void setRefill2Date(long ms)     { prefs.edit().putLong(KEY_REFILL_2_DATE_MS, ms).apply(); }

    public int getRefillCount() {
        checkAndResetExpiredWindow();
        return prefs.getInt(KEY_REFILL_COUNT, 0);
    }

    public float getUsedLitres() {
        return prefs.getFloat(KEY_REFILL_1_LITRES,0f) + prefs.getFloat(KEY_REFILL_2_LITRES,0f);
    }

    public float getRemainingLitres() { return getTotalQuota() - getUsedLitres(); }
    public int getRemainingRefills()   { return MAX_REFILLS - getRefillCount(); }

    public RefillResult recordRefill(float litres) {
        checkAndResetExpiredWindow();
        int count = getRefillCount();
        if (count >= MAX_REFILLS) return RefillResult.ALREADY_USED_MAX;
        if (litres > getRemainingLitres() + 0.05f) return RefillResult.EXCEEDS_QUOTA;

        SharedPreferences.Editor ed = prefs.edit();
        if (count == 0) {
            if (prefs.getLong(KEY_WINDOW_START_MS,0L) == 0L) ed.putLong(KEY_WINDOW_START_MS,System.currentTimeMillis());
            ed.putFloat(KEY_REFILL_1_LITRES, litres);
        } else {
            ed.putFloat(KEY_REFILL_2_LITRES, litres);
            ed.putLong(KEY_REFILL_2_DATE_MS,System.currentTimeMillis());
        }
        ed.putInt(KEY_REFILL_COUNT, count + 1).apply();
        return RefillResult.SUCCESS;
    }

    // ─── Eligible days calculation ─────────────
    public List<Long> getRemainingEligibleDaysInWindow() {
        List<Long> result = new ArrayList<>();
        checkAndResetExpiredWindow();
        int remRefills = getRemainingRefills();
        if (remRefills <= 0 || getRemainingLitres() <= 0.01f) return result;

        boolean needEven = isEvenVehicle();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);

        long windowStart = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        long windowEnd   = windowStart == 0L ? Long.MAX_VALUE : windowStart + WINDOW_MS;

        // If first refill done, skip days before next eligible
        if (getRefillCount() > 0 && windowStart != 0L) {
            cal.setTimeInMillis(windowStart);
        }

        int daysChecked = 0;
        while (cal.getTimeInMillis() < windowEnd && result.size() < remRefills && daysChecked < 30) {
            boolean isEligibleDay = (cal.get(Calendar.DAY_OF_MONTH) % 2 == 0) == needEven;
            if (isEligibleDay) result.add(cal.getTimeInMillis());
            cal.add(Calendar.DAY_OF_YEAR, 1);
            daysChecked++;
        }
        return result;
    }

    public enum RefillResult { SUCCESS, ALREADY_USED_MAX, EXCEEDS_QUOTA }

    // ─── Reminder logic ─────────────
    public enum ReminderType { NONE, DAY_BEFORE_NOON, DAY_BEFORE_EVENING, REFILL_DAY, NEW_QUOTA }

    /** Determine reminder type for a given action time */
    public ReminderType getReminderTypeForAction(String action) {
        checkAndResetExpiredWindow();

        List<Long> eligible = getRemainingEligibleDaysInWindow();
        Calendar now = Calendar.getInstance();
        Calendar dayBefore = Calendar.getInstance();
        dayBefore.add(Calendar.DAY_OF_YEAR, 1);

        for (long dayMs : eligible) {
            Calendar refillDay = Calendar.getInstance();
            refillDay.setTimeInMillis(dayMs);

            // Day-before reminders
            if (action.equals("NOON") || action.equals("EVENING")) {
                Calendar prev = (Calendar) refillDay.clone();
                prev.add(Calendar.DAY_OF_YEAR, -1);
                if (sameDay(prev, now)) return action.equals("NOON") ? ReminderType.DAY_BEFORE_NOON : ReminderType.DAY_BEFORE_EVENING;
            }

            // Morning refill day
            if (action.equals("MORNING")) {
                if (sameDay(refillDay, now)) return ReminderType.REFILL_DAY;
            }
        }

        // If no eligible refill left and window expired → NEW_QUOTA
        if (getRemainingRefills() <= 0 || getRemainingLitres() <= 0.01f) {
            Calendar nextWindow = Calendar.getInstance();
            nextWindow.setTimeInMillis(prefs.getLong(KEY_WINDOW_START_MS, System.currentTimeMillis()));
            nextWindow.add(Calendar.DAY_OF_YEAR, WINDOW_DAYS);
            if (sameDay(nextWindow, now) && action.equals("MORNING")) return ReminderType.NEW_QUOTA;
        }

        return ReminderType.NONE;
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
               a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

}
