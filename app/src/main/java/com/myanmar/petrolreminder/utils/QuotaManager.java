package com.myanmar.petrolreminder.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class QuotaManager {

    private static final String PREFS_NAME = "petrol_prefs";

    public static final String KEY_TOTAL_QUOTA      = "total_quota_litres";
    public static final String KEY_VEHICLE_NAME     = "vehicle_name";
    public static final String KEY_SETUP_DONE       = "setup_done";
    public static final String KEY_WINDOW_START_MS  = "window_start_ms";
    public static final String KEY_REFILL_COUNT     = "refill_count";
    public static final String KEY_REFILL_1_LITRES  = "refill_1_litres";
    public static final String KEY_REFILL_2_LITRES  = "refill_2_litres";

    // Notification toggle keys
    public static final String KEY_NOTIF_DAY_BEFORE_NOON    = "notif_day_before_noon";
    public static final String KEY_NOTIF_DAY_BEFORE_EVENING = "notif_day_before_evening";
    public static final String KEY_NOTIF_REFILL_DAY_MORNING = "notif_refill_day_morning";

    public static final int  MAX_REFILLS_PER_WINDOW = 2;
    public static final int  WINDOW_DAYS            = 7;
    public static final long WINDOW_MS              = (long) WINDOW_DAYS * 24 * 60 * 60 * 1000;

    public static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM yyyy (EEE)", Locale.ENGLISH);
    public static final SimpleDateFormat DATE_FMT_SHORT =
            new SimpleDateFormat("dd MMM (EEE)", Locale.ENGLISH);

    private final SharedPreferences prefs;

    public QuotaManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    public void saveSetup(String vehicleName, float totalQuota) {
        prefs.edit()
             .putString(KEY_VEHICLE_NAME, vehicleName)
             .putFloat(KEY_TOTAL_QUOTA, totalQuota)
             .putBoolean(KEY_SETUP_DONE, true)
             .apply();
    }

    public boolean isSetupDone()    { return prefs.getBoolean(KEY_SETUP_DONE, false); }
    public String  getVehicleName() { return prefs.getString(KEY_VEHICLE_NAME, "My Vehicle"); }
    public float   getTotalQuota()  { return prefs.getFloat(KEY_TOTAL_QUOTA, 0f); }

    // ─── Notification toggles ─────────────────────────────────────────────────

    public boolean isNotifDayBeforeNoonEnabled()    { return prefs.getBoolean(KEY_NOTIF_DAY_BEFORE_NOON, true); }
    public boolean isNotifDayBeforeEveningEnabled() { return prefs.getBoolean(KEY_NOTIF_DAY_BEFORE_EVENING, true); }
    public boolean isNotifRefillDayMorningEnabled() { return prefs.getBoolean(KEY_NOTIF_REFILL_DAY_MORNING, true); }

    public void setNotifDayBeforeNoon(boolean on)    { prefs.edit().putBoolean(KEY_NOTIF_DAY_BEFORE_NOON, on).apply(); }
    public void setNotifDayBeforeEvening(boolean on) { prefs.edit().putBoolean(KEY_NOTIF_DAY_BEFORE_EVENING, on).apply(); }
    public void setNotifRefillDayMorning(boolean on) { prefs.edit().putBoolean(KEY_NOTIF_REFILL_DAY_MORNING, on).apply(); }

    // ─── Odd / Even ───────────────────────────────────────────────────────────

    public boolean isEvenVehicle() {
        for (char c : getVehicleName().toCharArray()) {
            if (Character.isDigit(c)) return (Character.getNumericValue(c) % 2 == 0);
        }
        return true;
    }

    public String getVehicleTypeLabel() { return isEvenVehicle() ? "စုံကား" : "မကား"; }

    // ─── Window ───────────────────────────────────────────────────────────────

    public boolean isWindowActive() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start == 0L) return false;
        return (System.currentTimeMillis() - start) < WINDOW_MS;
    }

    public void checkAndResetExpiredWindow() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start != 0L && (System.currentTimeMillis() - start) >= WINDOW_MS) clearWindow();
    }

    private void clearWindow() {
        prefs.edit()
             .remove(KEY_WINDOW_START_MS).remove(KEY_REFILL_COUNT)
             .remove(KEY_REFILL_1_LITRES).remove(KEY_REFILL_2_LITRES)
             .apply();
    }

    /** Set window start to a specific date chosen by user (midnight of that day). */
    public void setWindowStartDate(long dayStartMs) {
        prefs.edit().putLong(KEY_WINDOW_START_MS, dayStartMs).apply();
    }

    // ─── Refill recording ─────────────────────────────────────────────────────

    public RefillResult recordRefill(float litres) {
        checkAndResetExpiredWindow();
        int count = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (count >= MAX_REFILLS_PER_WINDOW) return RefillResult.ALREADY_USED_MAX;
        if (litres > (getRemainingLitres()) + 0.05f) return RefillResult.EXCEEDS_QUOTA;

        SharedPreferences.Editor ed = prefs.edit();
        if (count == 0) {
            // Use existing window start (set by date picker) or now
            long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
            if (start == 0L) ed.putLong(KEY_WINDOW_START_MS, System.currentTimeMillis());
            ed.putFloat(KEY_REFILL_1_LITRES, litres);
        } else {
            ed.putFloat(KEY_REFILL_2_LITRES, litres);
        }
        ed.putInt(KEY_REFILL_COUNT, count + 1).apply();
        return RefillResult.SUCCESS;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public int   getRefillCount()      { checkAndResetExpiredWindow(); return prefs.getInt(KEY_REFILL_COUNT, 0); }
    public float getUsedLitres()       { return prefs.getFloat(KEY_REFILL_1_LITRES,0f) + prefs.getFloat(KEY_REFILL_2_LITRES,0f); }
    public float getRemainingLitres()  { return getTotalQuota() - getUsedLitres(); }
    public int   getRemainingRefills() { return MAX_REFILLS_PER_WINDOW - getRefillCount(); }

    public long getWindowStartMs()     { return prefs.getLong(KEY_WINDOW_START_MS, 0L); }

    public long getWindowResetTimeMs() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        return start == 0L ? 0L : start + WINDOW_MS;
    }

    public int getDaysUntilReset() {
        long resetMs = getWindowResetTimeMs();
        if (resetMs == 0L) return -1;
        long diff = resetMs - System.currentTimeMillis();
        if (diff <= 0) return 0;
        return (int) Math.ceil((double) diff / (24.0 * 60 * 60 * 1000));
    }

    // ─── Date helpers ─────────────────────────────────────────────────────────

    public String getFirstRefillDateStr() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        return start == 0L ? "—" : DATE_FMT.format(new Date(start));
    }

    public String getSecondRefillDeadlineDateStr() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start == 0L) return "—";
        if (prefs.getInt(KEY_REFILL_COUNT, 0) >= 2) return "✅ ပြီးပါပြီ";
        long next = findNextEligibleDay(start + 24L*60*60*1000, start + WINDOW_MS);
        return next < 0 ? "—" : DATE_FMT.format(new Date(next));
    }

    public String getNewQuotaDateStr() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        long from  = start == 0L ? System.currentTimeMillis() : start + WINDOW_MS;
        long next  = findNextEligibleDay(from, Long.MAX_VALUE);
        return next < 0 ? "—" : DATE_FMT.format(new Date(next));
    }

    /**
     * Returns midnight ms of the NEXT eligible refill day:
     *  - refillCount == 0 (no window): next odd/even day from tomorrow onwards
     *  - refillCount == 1 (window active): next odd/even day within remaining window
     *  - refillCount == 2 / quota used: next odd/even day after window ends (new quota)
     * Returns -1 if not found.
     */
    public long getNextEligibleDayMs() {
        checkAndResetExpiredWindow();
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        int count  = prefs.getInt(KEY_REFILL_COUNT, 0);

        // No window started yet — remind about the FIRST upcoming eligible day
        if (start == 0L || count == 0) {
            return findNextEligibleDay(
                    System.currentTimeMillis() + 24L * 60 * 60 * 1000,
                    Long.MAX_VALUE);
        }

        // Window active, 1 refill done — next eligible day within window for 2nd refill
        if (isWindowActive() && count == 1) {
            return findNextEligibleDay(
                    System.currentTimeMillis() + 24L * 60 * 60 * 1000,
                    start + WINDOW_MS);
        }

        // Both refills done or window expired — first eligible day after window ends
        return findNextEligibleDay(start + WINDOW_MS, Long.MAX_VALUE);
    }

    private long findNextEligibleDay(long fromMs, long maxMs) {
        boolean needEven = isEvenVehicle();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fromMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() < fromMs) cal.add(Calendar.DAY_OF_YEAR, 1);
        for (int i = 0; i < 30; i++) {
            if (cal.getTimeInMillis() > maxMs) return -1L;
            if ((cal.get(Calendar.DAY_OF_MONTH) % 2 == 0) == needEven)
                return cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return -1L;
    }

    // ─── Status message ───────────────────────────────────────────────────────

    public String getRefuelStatusMessage() {
        checkAndResetExpiredWindow();
        float total = getTotalQuota(), used = getUsedLitres(), remaining = total - used;
        int refillsLeft = getRemainingRefills(), daysLeft = getDaysUntilReset();

        if (!isWindowActive() && getRefillCount() == 0) {
            return "✅ ဆီဖြည့်ဖို့ အဆင်သင့်ဖြစ်ပြီ!\n\n"
                + "ကား: " + getVehicleTypeLabel() + " | ကိုတာ: " + String.format("%.1f", total) + " L\n"
                + "🆕 ဆီဖြည့်နိုင်သောနေ့: " + getNewQuotaDateStr();
        }
        if (refillsLeft <= 0 || remaining <= 0.01f) {
            return "⛽ ကိုတာကုန်ပါပြီ\n\nသုံးပြီး: " + String.format("%.1f / %.1f", used, total)
                + " L\nကျန် " + daysLeft + " ရက်\n🆕 နောက်ကိုတာ: " + getNewQuotaDateStr();
        }
        return "⛽ ကိုတာ အခြေအနေ\n\n"
            + "ကျန်ဆီ: " + String.format("%.1f / %.1f", remaining, total) + " L\n"
            + "ဖြည့်ခွင့်ကျန်: " + refillsLeft + " ကြိမ် | Window ကျန်: " + daysLeft + " ရက်\n\n"
            + "📅 ပထမဖြည့်ခဲ့: " + getFirstRefillDateStr() + "\n"
            + "⛽ ဒုတိယဖြည့်နိုင်: " + getSecondRefillDeadlineDateStr() + "\n"
            + "🆕 နောက်ကိုတာ: " + getNewQuotaDateStr();
    }

    // ─── Reminder type ────────────────────────────────────────────────────────

    public enum ReminderType { NONE, NEW_QUOTA_AVAILABLE }

    public ReminderType getReminderTypeForTonight() {
        checkAndResetExpiredWindow();
        if (!isWindowActive() && getRefillCount() == 0) return ReminderType.NONE;
        if (isWindowActive() && getDaysUntilReset() == 1) return ReminderType.NEW_QUOTA_AVAILABLE;
        return ReminderType.NONE;
    }

    public boolean shouldRemindTonight() { return getReminderTypeForTonight() != ReminderType.NONE; }

    // ─── Edit refill ──────────────────────────────────────────────────────────

    public float getLastRefillLitres() {
        int c = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (c == 2) return prefs.getFloat(KEY_REFILL_2_LITRES, 0f);
        if (c == 1) return prefs.getFloat(KEY_REFILL_1_LITRES, 0f);
        return 0f;
    }

    public int getLastRefillNumber() { return prefs.getInt(KEY_REFILL_COUNT, 0); }

    public boolean editLastRefill(float newLitres) {
        int count = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (count == 0) return false;
        float other = (count == 2) ? prefs.getFloat(KEY_REFILL_1_LITRES, 0f) : 0f;
        if (newLitres > (getTotalQuota() - other) + 0.05f) return false;
        SharedPreferences.Editor ed = prefs.edit();
        if (count == 1) ed.putFloat(KEY_REFILL_1_LITRES, newLitres);
        else ed.putFloat(KEY_REFILL_2_LITRES, newLitres);
        ed.apply();
        return true;
    }

    public void deleteLastRefill() {
        int count = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (count == 0) return;
        SharedPreferences.Editor ed = prefs.edit();
        if (count == 1) ed.remove(KEY_WINDOW_START_MS).remove(KEY_REFILL_COUNT)
                          .remove(KEY_REFILL_1_LITRES).remove(KEY_REFILL_2_LITRES);
        else ed.remove(KEY_REFILL_2_LITRES).putInt(KEY_REFILL_COUNT, 1);
        ed.apply();
    }

    public enum RefillResult { SUCCESS, ALREADY_USED_MAX, EXCEEDS_QUOTA }
}
