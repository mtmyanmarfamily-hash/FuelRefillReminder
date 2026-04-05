package com.myanmar.petrolreminder.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * ကိုတာ Logic:
 * - User သည် ကားနံပတ် နှင့် စုစုပေါင်း quota (L) သတ်မှတ်သည်
 * - စုံကား = စုံနေ့ (2,4,6,8...) မှသာ ဖြည့်နိုင်
 * - မကား  = မနေ့  (1,3,5,7...) မှသာ ဖြည့်နိုင်
 * - 7 ရက်တွင်း အများဆုံး 2 ကြိမ် ဖြည့်နိုင်
 * - Window = ပထမ ဖြည့်သောနေ့မှ 7 ရက်
 * - New quota = window ကျော်ပြီး ကိုယ့် odd/even နေ့ ကျမှ
 */
public class QuotaManager {

    private static final String PREFS_NAME = "petrol_prefs";

    static final String KEY_TOTAL_QUOTA     = "total_quota_litres";
    static final String KEY_VEHICLE_NAME    = "vehicle_name";
    static final String KEY_SETUP_DONE      = "setup_done";
    static final String KEY_WINDOW_START_MS = "window_start_ms";
    static final String KEY_REFILL_COUNT    = "refill_count";
    static final String KEY_REFILL_1_LITRES = "refill_1_litres";
    static final String KEY_REFILL_2_LITRES = "refill_2_litres";

    public static final int  MAX_REFILLS_PER_WINDOW = 2;
    public static final int  WINDOW_DAYS            = 7;
    private static final long WINDOW_MS             = (long) WINDOW_DAYS * 24 * 60 * 60 * 1000;

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM yyyy (EEE)", Locale.ENGLISH);

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

    public boolean isSetupDone()      { return prefs.getBoolean(KEY_SETUP_DONE, false); }
    public String  getVehicleName()   { return prefs.getString(KEY_VEHICLE_NAME, "My Vehicle"); }
    public float   getTotalQuota()    { return prefs.getFloat(KEY_TOTAL_QUOTA, 0f); }

    // ─── Odd / Even vehicle ──────────────────────────────────────────────────

    /**
     * ကားနံပတ် ပထမဆုံး ဂဏန်းကို စစ်သည်။
     * 8A/1234 → 8 စုံ → true (စုံကား)
     * 7B/1234 → 7 မ  → false (မကား)
     */
    public boolean isEvenVehicle() {
        String name = getVehicleName();
        for (char c : name.toCharArray()) {
            if (Character.isDigit(c)) {
                return (Character.getNumericValue(c) % 2 == 0);
            }
        }
        return true; // digit မတွေ့ရင် even default
    }

    public String getVehicleTypeLabel() {
        return isEvenVehicle() ? "စုံကား" : "မကား";
    }

    // ─── Window helpers ──────────────────────────────────────────────────────

    public boolean isWindowActive() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start == 0L) return false;
        return (System.currentTimeMillis() - start) < WINDOW_MS;
    }

    public void checkAndResetExpiredWindow() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start != 0L && (System.currentTimeMillis() - start) >= WINDOW_MS) {
            clearWindow();
        }
    }

    private void clearWindow() {
        prefs.edit()
             .remove(KEY_WINDOW_START_MS)
             .remove(KEY_REFILL_COUNT)
             .remove(KEY_REFILL_1_LITRES)
             .remove(KEY_REFILL_2_LITRES)
             .apply();
    }

    // ─── Refill recording ────────────────────────────────────────────────────

    public RefillResult recordRefill(float litres) {
        checkAndResetExpiredWindow();
        int count = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (count >= MAX_REFILLS_PER_WINDOW) return RefillResult.ALREADY_USED_MAX;
        float used  = getUsedLitres();
        float total = getTotalQuota();
        if (litres > (total - used) + 0.01f) return RefillResult.EXCEEDS_QUOTA;

        SharedPreferences.Editor ed = prefs.edit();
        if (count == 0) {
            ed.putLong(KEY_WINDOW_START_MS, System.currentTimeMillis());
            ed.putFloat(KEY_REFILL_1_LITRES, litres);
        } else {
            ed.putFloat(KEY_REFILL_2_LITRES, litres);
        }
        ed.putInt(KEY_REFILL_COUNT, count + 1).apply();
        return RefillResult.SUCCESS;
    }

    // ─── Status getters ──────────────────────────────────────────────────────

    public int   getRefillCount()      { checkAndResetExpiredWindow(); return prefs.getInt(KEY_REFILL_COUNT, 0); }
    public float getUsedLitres()       { return prefs.getFloat(KEY_REFILL_1_LITRES, 0f) + prefs.getFloat(KEY_REFILL_2_LITRES, 0f); }
    public float getRemainingLitres()  { return getTotalQuota() - getUsedLitres(); }
    public int   getRemainingRefills() { return MAX_REFILLS_PER_WINDOW - getRefillCount(); }

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

    public boolean canRefuel() {
        checkAndResetExpiredWindow();
        if (!isWindowActive() && getRefillCount() == 0) return true;
        return getRemainingRefills() > 0 && getRemainingLitres() > 0.01f;
    }

    // ─── Date helpers ─────────────────────────────────────────────────────────

    /** ပထမ ဖြည့်ခဲ့သောနေ့ */
    public String getFirstRefillDateStr() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start == 0L) return "—";
        return DATE_FMT.format(new Date(start));
    }

    /** ဒုတိယ ဖြည့်နိုင်သောနေ့ = window ထဲ ကိုယ့် odd/even နေ့ ကျတဲ့ ရက် */
    public String getSecondRefillDeadlineDateStr() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start == 0L) return "—";
        if (prefs.getInt(KEY_REFILL_COUNT, 0) >= 2) return "✅ ပြီးပါပြီ";

        // window ထဲ ကိုယ့် odd/even နေ့ ကျတဲ့ ရက် ရှာ (ပထမနေ့ မပါ)
        long nextDate = findNextEligibleDay(start + 24L * 60 * 60 * 1000, start + WINDOW_MS);
        if (nextDate < 0) return "—";
        return DATE_FMT.format(new Date(nextDate));
    }

    /**
     * ကိုတာ အသစ် စတင်နိုင်သောနေ့ = window ကျော်ပြီးနောက်
     * ကိုယ့် odd/even နေ့ ကျတဲ့ ပထမဆုံးရက်
     */
    public String getNewQuotaDateStr() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start == 0L) {
            // window မစသေးရင် ယနေ့မှ တွက်
            long nextDate = findNextEligibleDay(System.currentTimeMillis(), Long.MAX_VALUE);
            return nextDate < 0 ? "—" : DATE_FMT.format(new Date(nextDate));
        }
        long windowEnd = start + WINDOW_MS;
        long nextDate  = findNextEligibleDay(windowEnd, Long.MAX_VALUE);
        return nextDate < 0 ? "—" : DATE_FMT.format(new Date(nextDate));
    }

    /**
     * fromMs မှ maxMs အတွင်း ကိုယ့် odd/even နေ့ ပထမဆုံး ကျတဲ့ ms ကို ရှာသည်။
     * မတွေ့ရင် -1 ပြန်သည်။
     */
    private long findNextEligibleDay(long fromMs, long maxMs) {
        boolean needEven = isEvenVehicle();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fromMs);
        // အနည်းဆုံး fromMs ရောက်ပြီဖြစ်သော ရက်ဆီသို့ normalize
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() < fromMs) cal.add(Calendar.DAY_OF_YEAR, 1);

        for (int i = 0; i < 30; i++) { // 30 ရက်အတွင်း ရှာ
            if (cal.getTimeInMillis() > maxMs) return -1L;
            int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            boolean dayIsEven = (dayOfMonth % 2 == 0);
            if (dayIsEven == needEven) return cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return -1L;
    }

    // ─── Status message ───────────────────────────────────────────────────────

    public String getRefuelStatusMessage() {
        checkAndResetExpiredWindow();

        float total      = getTotalQuota();
        float used       = getUsedLitres();
        float remaining  = total - used;
        int   refillsLeft = getRemainingRefills();
        int   daysLeft    = getDaysUntilReset();
        String vType     = getVehicleTypeLabel();

        if (!isWindowActive() && getRefillCount() == 0) {
            return "✅ ဆီဖြည့်ဖို့ အဆင်သင့်ဖြစ်ပြီ!\n\n"
                + "ကား အမျိုးအစား: " + vType + "\n"
                + "ကိုတာ: " + String.format("%.0f", total) + " L\n"
                + "🆕 ဆီဖြည့်နိုင်တဲ့ ရက်: " + getNewQuotaDateStr() + "\n\n"
                + "ပထမအကြိမ် ဖြည့်လိုက်တာနဲ့ 7-ရက် window စပါမည်။";
        }

        if (refillsLeft <= 0 || remaining <= 0.01f) {
            return "⛽ ကိုတာကုန်ပါပြီ\n\n"
                + "သုံးပြီး: " + String.format("%.0f / %.0f", used, total) + " L\n"
                + "ကျန်ရှိ: " + daysLeft + " ရက်\n"
                + "🆕 နောက် ကိုတာ: " + getNewQuotaDateStr();
        }

        return "⛽ ကိုတာ အခြေအနေ\n\n"
            + "ကား: " + vType + "\n"
            + "သုံးပြီး: " + String.format("%.0f / %.0f", used, total) + " L\n"
            + "ကျန်ဆီ: " + String.format("%.0f", remaining) + " L\n"
            + "ဖြည့်ပြီး: " + getRefillCount() + " / 2 ကြိမ်\n"
            + "ကျန်ဖြည့်ခွင့်: " + refillsLeft + " ကြိမ်\n"
            + "Window ကျန်: " + daysLeft + " ရက်\n\n"
            + "📅 ပထမဖြည့်ခဲ့: " + getFirstRefillDateStr() + "\n"
            + "⛽ ဒုတိယဖြည့်ရမည်: " + getSecondRefillDeadlineDateStr() + "\n"
            + "🆕 နောက်ကိုတာ: " + getNewQuotaDateStr();
    }

    // ─── Reminder logic ──────────────────────────────────────────────────────

    public enum ReminderType {
        NONE,
        REFILL_WITHIN_WINDOW,
        NEW_QUOTA_AVAILABLE
    }

    public ReminderType getReminderTypeForTonight() {
        checkAndResetExpiredWindow();
        if (!isWindowActive() && getRefillCount() == 0) return ReminderType.NONE;
        int daysLeft = getDaysUntilReset();
        if (isWindowActive() && daysLeft == 1) return ReminderType.NEW_QUOTA_AVAILABLE;
        return ReminderType.NONE;
    }

    public boolean shouldRemindTonight() {
        return getReminderTypeForTonight() != ReminderType.NONE;
    }

    // ─── Edit / correct last refill ──────────────────────────────────────────

    public float getLastRefillLitres() {
        int count = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (count == 2) return prefs.getFloat(KEY_REFILL_2_LITRES, 0f);
        if (count == 1) return prefs.getFloat(KEY_REFILL_1_LITRES, 0f);
        return 0f;
    }

    public int getLastRefillNumber() {
        return prefs.getInt(KEY_REFILL_COUNT, 0);
    }

    public boolean editLastRefill(float newLitres) {
        int count = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (count == 0) return false;
        float total = getTotalQuota();
        float otherRefill = (count == 2) ? prefs.getFloat(KEY_REFILL_1_LITRES, 0f) : 0f;
        if (newLitres > (total - otherRefill) + 0.01f) return false;
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
        if (count == 1) {
            ed.remove(KEY_WINDOW_START_MS).remove(KEY_REFILL_COUNT)
              .remove(KEY_REFILL_1_LITRES).remove(KEY_REFILL_2_LITRES);
        } else {
            ed.remove(KEY_REFILL_2_LITRES).putInt(KEY_REFILL_COUNT, 1);
        }
        ed.apply();
    }

    public enum RefillResult {
        SUCCESS,
        ALREADY_USED_MAX,
        EXCEEDS_QUOTA
    }
}
