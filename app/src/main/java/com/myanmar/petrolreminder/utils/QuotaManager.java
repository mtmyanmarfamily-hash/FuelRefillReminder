package com.myanmar.petrolreminder.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
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

    public static final String KEY_NOTIF_DAY_BEFORE_NOON    = "notif_day_before_noon";
    public static final String KEY_NOTIF_DAY_BEFORE_EVENING = "notif_day_before_evening";
    public static final String KEY_NOTIF_REFILL_DAY_MORNING = "notif_refill_day_morning";

    public static final int  MAX_REFILLS = 2;
    public static final int  WINDOW_DAYS = 7;
    public static final long WINDOW_MS   = (long) WINDOW_DAYS * 24 * 60 * 60 * 1000;

    public static final SimpleDateFormat DATE_FMT   = new SimpleDateFormat("dd MMM yyyy (EEE)", Locale.ENGLISH);
    public static final SimpleDateFormat DATE_SHORT  = new SimpleDateFormat("dd MMM (EEE)", Locale.ENGLISH);

    private final SharedPreferences prefs;

    public QuotaManager(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    public void saveSetup(String name, float quota) {
        prefs.edit().putString(KEY_VEHICLE_NAME, name)
             .putFloat(KEY_TOTAL_QUOTA, quota)
             .putBoolean(KEY_SETUP_DONE, true).apply();
    }

    public boolean isSetupDone()    { return prefs.getBoolean(KEY_SETUP_DONE, false); }
    public String  getVehicleName() { return prefs.getString(KEY_VEHICLE_NAME, "My Vehicle"); }
    public float   getTotalQuota()  { return prefs.getFloat(KEY_TOTAL_QUOTA, 0f); }

    // ─── Notification toggles ─────────────────────────────────────────────────

    public boolean isNotifDayBeforeNoonEnabled()    { return prefs.getBoolean(KEY_NOTIF_DAY_BEFORE_NOON, true); }
    public boolean isNotifDayBeforeEveningEnabled() { return prefs.getBoolean(KEY_NOTIF_DAY_BEFORE_EVENING, true); }
    public boolean isNotifRefillDayMorningEnabled() { return prefs.getBoolean(KEY_NOTIF_REFILL_DAY_MORNING, true); }
    public void setNotifDayBeforeNoon(boolean v)    { prefs.edit().putBoolean(KEY_NOTIF_DAY_BEFORE_NOON, v).apply(); }
    public void setNotifDayBeforeEvening(boolean v) { prefs.edit().putBoolean(KEY_NOTIF_DAY_BEFORE_EVENING, v).apply(); }
    public void setNotifRefillDayMorning(boolean v) { prefs.edit().putBoolean(KEY_NOTIF_REFILL_DAY_MORNING, v).apply(); }

    // ─── Odd / Even ───────────────────────────────────────────────────────────

    public boolean isEvenVehicle() {
        for (char c : getVehicleName().toCharArray())
            if (Character.isDigit(c)) return (Character.getNumericValue(c) % 2 == 0);
        return true;
    }

    public String getVehicleTypeLabel() { return isEvenVehicle() ? "စုံကား" : "မကား"; }

    // ─── Window ───────────────────────────────────────────────────────────────

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
             .remove(KEY_REFILL_1_LITRES).remove(KEY_REFILL_2_LITRES).apply();
    }

    public void setWindowStartDate(long ms) {
        prefs.edit().putLong(KEY_WINDOW_START_MS, ms).apply();
    }

    // ─── Refill recording ─────────────────────────────────────────────────────

    public RefillResult recordRefill(float litres) {
        checkAndResetExpiredWindow();
        int count = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (count >= MAX_REFILLS)               return RefillResult.ALREADY_USED_MAX;
        if (litres > getRemainingLitres()+0.05f) return RefillResult.EXCEEDS_QUOTA;
        SharedPreferences.Editor ed = prefs.edit();
        if (count == 0) {
            if (prefs.getLong(KEY_WINDOW_START_MS, 0L) == 0L)
                ed.putLong(KEY_WINDOW_START_MS, System.currentTimeMillis());
            ed.putFloat(KEY_REFILL_1_LITRES, litres);
        } else {
            ed.putFloat(KEY_REFILL_2_LITRES, litres);
        }
        ed.putInt(KEY_REFILL_COUNT, count + 1).apply();
        return RefillResult.SUCCESS;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public int   getRefillCount()      { checkAndResetExpiredWindow(); return prefs.getInt(KEY_REFILL_COUNT, 0); }
    public float getUsedLitres()       { return prefs.getFloat(KEY_REFILL_1_LITRES,0f)+prefs.getFloat(KEY_REFILL_2_LITRES,0f); }
    public float getRemainingLitres()  { return getTotalQuota() - getUsedLitres(); }
    public int   getRemainingRefills() { return MAX_REFILLS - getRefillCount(); }
    public long  getWindowStartMs()    { return prefs.getLong(KEY_WINDOW_START_MS, 0L); }
    public long  getWindowEndMs()      { long s=getWindowStartMs(); return s==0L?0L:s+WINDOW_MS; }

    public int getDaysUntilReset() {
        long end = getWindowEndMs();
        if (end == 0L) return -1;
        long diff = end - System.currentTimeMillis();
        return diff <= 0 ? 0 : (int) Math.ceil((double)diff / (24.0*60*60*1000));
    }

    // ─── Eligible days in window ──────────────────────────────────────────────

    /**
     * Window ထဲ ကျန်ရှိသော ဒုတိယ ဖြည့်နိုင်သောနေ့များ (ယနေ့ + နောက်ပိုင်း)
     * 2 ကြိမ် ပြည့်သွားပြီဆို empty list ပြန်သည်
     */
    public List<Long> getRemainingEligibleDaysInWindow() {
        List<Long> result = new ArrayList<>();
        checkAndResetExpiredWindow();

        if (getRefillCount() >= MAX_REFILLS) return result;
        if (getRemainingLitres() <= 0.01f)   return result;

        long start     = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        long windowEnd = start == 0L ? Long.MAX_VALUE : start + WINDOW_MS;
        boolean needEven = isEvenVehicle();

        // Start from TODAY midnight — include today if it is an eligible day
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // If window started TODAY (first refill was today), skip today for 2nd refill
        // because you cannot fill the same day twice
        if (start != 0L) {
            Calendar startCal = Calendar.getInstance();
            startCal.setTimeInMillis(start);
            boolean startedToday = (startCal.get(Calendar.YEAR)        == cal.get(Calendar.YEAR)
                                 && startCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR));
            if (startedToday) cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        for (int i = 0; i < 8; i++) {
            long t = cal.getTimeInMillis();
            if (t >= windowEnd) break;
            if ((cal.get(Calendar.DAY_OF_MONTH) % 2 == 0) == needEven) {
                result.add(t);
            }
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return result;
    }

    /**
     * ဒုတိယ ဖြည့်နိုင်သောနေ့များ ပြသရန် string
     * ဥပမာ: "08 Apr (Wed),  10 Apr (Fri),  12 Apr (Sun)"
     */
    public String getRemainingEligibleDaysStr() {
        if (getRefillCount() >= MAX_REFILLS)     return "✅ ၂ ကြိမ် ပြည့်ပြီ — နောက်ကိုတာ " + getNewQuotaDateStr();
        if (getRemainingLitres() <= 0.01f)        return "ကိုတာ ကုန်ပြီ — " + getNewQuotaDateStr();
        List<Long> days = getRemainingEligibleDaysInWindow();
        if (days.isEmpty())                       return "—  (window ထဲ ကျန်နေ့ မရှိ)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < days.size(); i++) {
            if (i > 0) sb.append(",   ");
            sb.append(DATE_SHORT.format(new Date(days.get(i))));
        }
        return sb.toString();
    }

    // ─── Date strings ─────────────────────────────────────────────────────────

    public String getFirstRefillDateStr() {
        long s = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        return s == 0L ? "—" : DATE_FMT.format(new Date(s));
    }

    public String getNewQuotaDateStr() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        long from  = start == 0L ? System.currentTimeMillis() : start + WINDOW_MS;
        long next  = findNextEligibleDay(from, Long.MAX_VALUE);
        return next < 0 ? "—" : DATE_FMT.format(new Date(next));
    }

    /**
     * Next single eligible day ms — for alarm/notification logic
     */
    public long getNextEligibleDayMs() {
        checkAndResetExpiredWindow();
        if (getRefillCount() >= MAX_REFILLS || getRemainingLitres() <= 0.01f) {
            // Both refills done → next new quota day
            long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
            long from  = start == 0L ? System.currentTimeMillis() : start + WINDOW_MS;
            return findNextEligibleDay(from, Long.MAX_VALUE);
        }
        List<Long> days = getRemainingEligibleDaysInWindow();
        if (!days.isEmpty()) return days.get(0);
        // Fallback: new quota
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        long from  = start == 0L ? System.currentTimeMillis() : start + WINDOW_MS;
        return findNextEligibleDay(from, Long.MAX_VALUE);
    }

    private long findNextEligibleDay(long fromMs, long maxMs) {
        boolean needEven = isEvenVehicle();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fromMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() < fromMs) cal.add(Calendar.DAY_OF_YEAR, 1);
        for (int i = 0; i < 30; i++) {
            if (cal.getTimeInMillis() > maxMs) return -1L;
            if ((cal.get(Calendar.DAY_OF_MONTH) % 2 == 0) == needEven) return cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return -1L;
    }

    // ─── Status message ───────────────────────────────────────────────────────

    public String getRefuelStatusMessage() {
        checkAndResetExpiredWindow();
        float total = getTotalQuota(), used = getUsedLitres(), rem = total - used;
        int   refLeft = getRemainingRefills(), daysLeft = getDaysUntilReset();

        if (!isWindowActive() && getRefillCount() == 0) {
            return "✅ ဆီဖြည့်ဖို့ အဆင်သင့်ဖြစ်ပြီ!\n\n"
                + "ကား: " + getVehicleTypeLabel() + "  |  ကိုတာ: " + String.format("%.1f", total) + " L\n"
                + "🆕 ဖြည့်နိုင်သောနေ့: " + getNewQuotaDateStr();
        }
        if (refLeft <= 0 || rem <= 0.01f) {
            return "⛽ ကိုတာ / ဖြည့်ခွင့် ကုန်ပြီ\n\n"
                + "သုံးပြီး: " + String.format("%.1f / %.1f", used, total) + " L\n"
                + "Window ကျန်: " + daysLeft + " ရက်\n"
                + "🆕 နောက်ကိုတာ: " + getNewQuotaDateStr();
        }
        return "⛽ ကိုတာ အခြေအနေ\n\n"
            + "ကျန်ဆီ: " + String.format("%.1f", rem) + " L  (သုံးပြီး: " + String.format("%.1f / %.1f", used, total) + " L)\n"
            + "ဖြည့်ခွင့်ကျန်: " + refLeft + " ကြိမ်  |  Window ကျန်: " + daysLeft + " ရက်\n\n"
            + "⛽ ဖြည့်နိုင်သောနေ့:\n" + getRemainingEligibleDaysStr() + "\n\n"
            + "📅 ပထမဖြည့်ခဲ့: " + getFirstRefillDateStr() + "\n"
            + "🆕 နောက်ကိုတာ: " + getNewQuotaDateStr();
    }

    // ─── Reminder type ────────────────────────────────────────────────────────

    public enum ReminderType { NONE, NEW_QUOTA_AVAILABLE, REFILL_DAY }

    public ReminderType getReminderTypeForTonight() {
        checkAndResetExpiredWindow();
        // Both refills done → check if new quota is tomorrow
        if (getRefillCount() >= MAX_REFILLS || getRemainingLitres() <= 0.01f) {
            long nextQuota = getNextEligibleDayMs();
            return isTomorrow(nextQuota) ? ReminderType.NEW_QUOTA_AVAILABLE : ReminderType.NONE;
        }
        // Has remaining refills → is any eligible day tomorrow?
        List<Long> days = getRemainingEligibleDaysInWindow();
        for (long d : days) {
            if (isTomorrow(d)) return ReminderType.REFILL_DAY;
        }
        return ReminderType.NONE;
    }

    public boolean shouldRemindTonight() { return getReminderTypeForTonight() != ReminderType.NONE; }

    /** True if today IS an eligible refill day */
    public boolean isTodayEligibleRefillDay() {
        if (getRefillCount() >= MAX_REFILLS || getRemainingLitres() <= 0.01f) return false;
        boolean needEven = isEvenVehicle();
        int today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        return (today % 2 == 0) == needEven;
    }

    private boolean isTomorrow(long ms) {
        if (ms < 0) return false;
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(ms);
        return tomorrow.get(Calendar.YEAR)        == target.get(Calendar.YEAR)
            && tomorrow.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR);
    }

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
        float other = count == 2 ? prefs.getFloat(KEY_REFILL_1_LITRES, 0f) : 0f;
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

// Note: setRefill2Date added via append - must be before last 
    public static final String KEY_REFILL_2_DATE_MS = "refill_2_date_ms";

    /** Store the date user chose for 2nd refill (for display purposes) */
    public void setRefill2Date(long ms) {
        prefs.edit().putLong(KEY_REFILL_2_DATE_MS, ms).apply();
    }

    public String getRefill2DateStr() {
        long ms = prefs.getLong(KEY_REFILL_2_DATE_MS, 0L);
        return ms == 0L ? "" : DATE_FMT.format(new java.util.Date(ms));
    }
}
