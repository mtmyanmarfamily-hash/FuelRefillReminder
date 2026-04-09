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
 * Per-car quota manager with 7-day quota, max 2 refills, odd/even day logic, and notifications:
 * - 2 reminders on day before eligible refill (12:00 & 18:00)
 * - 1 reminder on actual eligible refill day (06:00)
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

    public static final int  MAX_REFILLS = 2;
    public static final int  WINDOW_DAYS = 7;
    public static final long WINDOW_MS   = (long) WINDOW_DAYS * 24 * 60 * 60 * 1000;

    public static final SimpleDateFormat DATE_FMT   = new SimpleDateFormat("dd MMM yyyy (EEE)", Locale.ENGLISH);
    public static final SimpleDateFormat DATE_SHORT  = new SimpleDateFormat("dd MMM (EEE)", Locale.ENGLISH);

    private final SharedPreferences prefs;

    /** Legacy constructor — uses old single prefs file */
    public QuotaManager(Context ctx) {
        prefs = ctx.getApplicationContext()
                   .getSharedPreferences("petrol_prefs", Context.MODE_PRIVATE);
    }

    /** Multi-car constructor — uses per-car prefs file */
    public QuotaManager(Context ctx, String carId) {
        prefs = ctx.getApplicationContext()
                   .getSharedPreferences("car_" + carId, Context.MODE_PRIVATE);
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    public void saveSetup(String name, float quota) {
        prefs.edit().putString(KEY_VEHICLE_NAME, name)
             .putFloat(KEY_TOTAL_QUOTA, quota)
             .putBoolean(KEY_SETUP_DONE, true).apply();
    }

    public void updateTotalQuota(String name, float quota) {
        prefs.edit().putString(KEY_VEHICLE_NAME, name)
             .putFloat(KEY_TOTAL_QUOTA, quota).apply();
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
            if (Character.isDigit(c)) return Character.getNumericValue(c) % 2 == 0;
        return true; // default even
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
             .remove(KEY_REFILL_1_LITRES).remove(KEY_REFILL_2_LITRES)
             .remove(KEY_REFILL_2_DATE_MS).apply();
    }

    public void setWindowStartDate(long ms) { prefs.edit().putLong(KEY_WINDOW_START_MS, ms).apply(); }
    public void setRefill2Date(long ms)     { prefs.edit().putLong(KEY_REFILL_2_DATE_MS, ms).apply(); }

    // ─── Refill recording ─────────────────────────────────────────────────────

    public RefillResult recordRefill(float litres) {
        checkAndResetExpiredWindow();
        int count = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (count >= MAX_REFILLS)                return RefillResult.ALREADY_USED_MAX;
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

    // ─── Reminder ─────────────────────────────────────────────────────────────

    public enum ReminderType { NONE, DAY_BEFORE_NOON, DAY_BEFORE_EVENING, REFILL_DAY_MORNING }

    /**
     * Returns the ReminderType for 12 PM or 6 PM on the day before refill, or 6 AM on refill day
     */
    public ReminderType getReminderForHour(int hour24) {
        checkAndResetExpiredWindow();
        boolean even = isEvenVehicle();
        Calendar today = Calendar.getInstance();
        int todayDay = today.get(Calendar.DAY_OF_MONTH);
        int tomorrowDay = todayDay + 1;

        // First refill eligibility
        long windowStart = getWindowStartMs();
        boolean firstRefillDone = getRefillCount() > 0;
        List<Long> eligibleDays = getRemainingEligibleDaysInWindow();

        if (hour24 == 6) {
            // morning of actual eligible refill day
            for (long d : eligibleDays) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(d);
                if (c.get(Calendar.YEAR)==today.get(Calendar.YEAR) &&
                    c.get(Calendar.DAY_OF_YEAR)==today.get(Calendar.DAY_OF_YEAR)) {
                    return ReminderType.REFILL_DAY_MORNING;
                }
            }
        } else if (hour24 == 12) {
            // day before refill 12 PM
            for (long d : eligibleDays) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(d);
                c.add(Calendar.DAY_OF_YEAR, -1);
                if (c.get(Calendar.YEAR)==today.get(Calendar.YEAR) &&
                    c.get(Calendar.DAY_OF_YEAR)==today.get(Calendar.DAY_OF_YEAR)) {
                    return ReminderType.DAY_BEFORE_NOON;
                }
            }
        } else if (hour24 == 18) {
            // day before refill 6 PM
            for (long d : eligibleDays) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(d);
                c.add(Calendar.DAY_OF_YEAR, -1);
                if (c.get(Calendar.YEAR)==today.get(Calendar.YEAR) &&
                    c.get(Calendar.DAY_OF_YEAR)==today.get(Calendar.DAY_OF_YEAR)) {
                    return ReminderType.DAY_BEFORE_EVENING;
                }
            }
        }

        return ReminderType.NONE;
    }

    /** Returns the ReminderType for tonight (12 PM or 6 PM) */
    public ReminderType getReminderTypeForTonight() {
        ReminderType type = getReminderForHour(12);
        if (type != ReminderType.NONE) return type;
        type = getReminderForHour(18);
        return type;
    }

    public boolean shouldRemindTonight() { return getReminderTypeForTonight() != ReminderType.NONE; }

    // ─── Eligible days ────────────────────────────────────────────────────────

    public List<Long> getRemainingEligibleDaysInWindow() {
        List<Long> result = new ArrayList<>();
        checkAndResetExpiredWindow();
        if (getRefillCount() >= MAX_REFILLS) return result;
        if (getRemainingLitres() <= 0.01f) return result;

        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        boolean needEven = isEvenVehicle();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);

        if (start != 0L) {
            Calendar startCal = Calendar.getInstance();
            startCal.setTimeInMillis(start);
            if (startCal.get(Calendar.YEAR)==cal.get(Calendar.YEAR) &&
                startCal.get(Calendar.DAY_OF_YEAR)==cal.get(Calendar.DAY_OF_YEAR)) {
                cal.add(Calendar.DAY_OF_YEAR,1);
            }
        }

        for (int i=0;i<8;i++) {
            if ((cal.get(Calendar.DAY_OF_MONTH)%2==0)==needEven)
                result.add(cal.getTimeInMillis());
            cal.add(Calendar.DAY_OF_YEAR,1);
        }
        return result;
    }

    public String getRemainingEligibleDaysStr() {
        if (getRefillCount() >= MAX_REFILLS) return "✅ ၂ ကြိမ် ပြည့်ပြီ — နောက်ကိုတာ " + getNewQuotaDateStr();
        if (getRemainingLitres() <= 0.01f)   return "ကိုတာ ကုန်ပြီ — " + getNewQuotaDateStr();
        List<Long> days = getRemainingEligibleDaysInWindow();
        if (days.isEmpty()) return "—  (window ထဲ ကျန်နေ့ မရှိ)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < days.size(); i++) {
            if (i>0) sb.append(",   ");
            sb.append(DATE_SHORT.format(new Date(days.get(i))));
        }
        return sb.toString();
    }

    // ─── Date strings ─────────────────────────────────────────────────────────

    public String getFirstRefillDateStr() {
        long s = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        return s==0L?"—":DATE_FMT.format(new Date(s));
    }

    public String getNewQuotaDateStr() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        long from  = start==0L?System.currentTimeMillis():start + WINDOW_MS;
        long next  = findNextEligibleDay(from, Long.MAX_VALUE);
        return next<0?"—":DATE_FMT.format(new Date(next));
    }

    private long findNextEligibleDay(long fromMs, long maxMs) {
        boolean needEven = isEvenVehicle();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fromMs);
        cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
        if (cal.getTimeInMillis()<fromMs) cal.add(Calendar.DAY_OF_YEAR,1);
        for(int i=0;i<30;i++){
            if(cal.getTimeInMillis()>maxMs) return -1;
            if((cal.get(Calendar.DAY_OF_MONTH)%2==0)==needEven) return cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR,1);
        }
        return -1;
    }

    // ─── Edit / Delete last refill ────────────────────────────────────────────

    public float getLastRefillLitres() {
        int c=prefs.getInt(KEY_REFILL_COUNT,0);
        if(c==2) return prefs.getFloat(KEY_REFILL_2_LITRES,0f);
        if(c==1) return prefs.getFloat(KEY_REFILL_1_LITRES,0f);
        return 0f;
    }

    public int getLastRefillNumber() { return prefs.getInt(KEY_REFILL_COUNT,0); }

    public boolean editLastRefill(float newLitres) {
        int count=prefs.getInt(KEY_REFILL_COUNT,0);
        if(count==0) return false;
        float other = count==2 ? prefs.getFloat(KEY_REFILL_1_LITRES,0f) : 0f;
        if(newLitres>(getTotalQuota()-other)+0.05f) return false;
        SharedPreferences.Editor ed=prefs.edit();
        if(count==1) ed.putFloat(KEY_REFILL_1_LITRES,newLitres);
        else ed.putFloat(KEY_REFILL_2_LITRES,newLitres);
        ed.apply(); return true;
    }

    public void deleteLastRefill() {
        int count=prefs.getInt(KEY_REFILL_COUNT,0);
        if(count==0) return;
        SharedPreferences.Editor ed=prefs.edit();
        if(count==1) ed.remove(KEY_WINDOW_START_MS).remove(KEY_REFILL_COUNT)
                       .remove(KEY_REFILL_1_LITRES).remove(KEY_REFILL_2_LITRES).remove(KEY_REFILL_2_DATE_MS);
        else ed.remove(KEY_REFILL_2_LITRES).remove(KEY_REFILL_2_DATE_MS).putInt(KEY_REFILL_COUNT,1);
        ed.apply();
    }

    // ─── Refill result ───────────────────────────────────────────────────────

    public enum RefillResult { SUCCESS, ALREADY_USED_MAX, EXCEEDS_QUOTA }
}
