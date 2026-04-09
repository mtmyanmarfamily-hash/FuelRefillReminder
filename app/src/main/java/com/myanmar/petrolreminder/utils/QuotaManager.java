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

<<<<<<< HEAD
    /** ------------------- Refill Day Logic ------------------- */
=======
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

    // ─── Eligible days ────────────────────────────────────────────────────────

    public List<Long> getRemainingEligibleDaysInWindow() {
        List<Long> result = new ArrayList<>();
        checkAndResetExpiredWindow();
        if (getRefillCount() >= MAX_REFILLS) return result;
        if (getRemainingLitres() <= 0.01f)   return result;

        long start     = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        long windowEnd = start == 0L ? Long.MAX_VALUE : start + WINDOW_MS;
        boolean needEven = isEvenVehicle();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);

        if (start != 0L) {
            Calendar startCal = Calendar.getInstance();
            startCal.setTimeInMillis(start);
            boolean startedToday = startCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
                                && startCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);
            if (startedToday) cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        for (int i = 0; i < 8; i++) {
            long t = cal.getTimeInMillis();
            if (t >= windowEnd) break;
            if ((cal.get(Calendar.DAY_OF_MONTH) % 2 == 0) == needEven) result.add(t);
            cal.add(Calendar.DAY_OF_YEAR, 1);
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

    public long getNextEligibleDayMs() {
        checkAndResetExpiredWindow();
        if (getRefillCount() >= MAX_REFILLS || getRemainingLitres() <= 0.01f) {
            long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
            return findNextEligibleDay(start == 0L ? System.currentTimeMillis() : start + WINDOW_MS, Long.MAX_VALUE);
        }
        List<Long> days = getRemainingEligibleDaysInWindow();
        if (!days.isEmpty()) return days.get(0);
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        return findNextEligibleDay(start == 0L ? System.currentTimeMillis() : start + WINDOW_MS, Long.MAX_VALUE);
    }

    private long findNextEligibleDay(long fromMs, long maxMs) {
        boolean needEven = isEvenVehicle();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fromMs);
        cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
        if (cal.getTimeInMillis() < fromMs) cal.add(Calendar.DAY_OF_YEAR,1);
        for (int i=0; i<30; i++) {
            if (cal.getTimeInMillis() > maxMs) return -1L;
            if ((cal.get(Calendar.DAY_OF_MONTH)%2==0) == needEven) return cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR,1);
        }
        return -1L;
    }

    // ─── Status message ───────────────────────────────────────────────────────

    public String getRefuelStatusMessage() {
        checkAndResetExpiredWindow();
        float total=getTotalQuota(), used=getUsedLitres(), rem=total-used;
        int refLeft=getRemainingRefills(), daysLeft=getDaysUntilReset();

        if (!isWindowActive() && getRefillCount()==0) {
            return "✅ ဆီဖြည့်ဖို့ အဆင်သင့်ဖြစ်ပြီ!\n\n"
                + "ကား: "+getVehicleTypeLabel()+"  |  ကိုတာ: "+String.format("%.1f",total)+" L\n"
                + "🆕 ဖြည့်နိုင်သောနေ့: "+getNewQuotaDateStr();
        }
        if (refLeft<=0 || rem<=0.01f) {
            return "⛽ ကိုတာ / ဖြည့်ခွင့် ကုန်ပြီ\n\n"
                + "သုံးပြီး: "+String.format("%.1f / %.1f",used,total)+" L\n"
                + "Window ကျန်: "+daysLeft+" ရက်\n"
                + "🆕 နောက်ကိုတာ: "+getNewQuotaDateStr();
        }
        return "⛽ ကိုတာ အခြေအနေ\n\n"
            + "ကျန်ဆီ: "+String.format("%.1f",rem)+" L  (သုံးပြီး: "+String.format("%.1f/%.1f",used,total)+" L)\n"
            + "ဖြည့်ခွင့်ကျန်: "+refLeft+" ကြိမ်  |  Window ကျန်: "+daysLeft+" ရက်\n\n"
            + "⛽ ဖြည့်နိုင်သောနေ့:\n"+getRemainingEligibleDaysStr()+"\n\n"
            + "📅 ပထမဖြည့်ခဲ့: "+getFirstRefillDateStr()+"\n"
            + "🆕 နောက်ကိုတာ: "+getNewQuotaDateStr();
    }

    // ─── Reminder ─────────────────────────────────────────────────────────────

    public enum ReminderType { NONE, NEW_QUOTA_AVAILABLE, REFILL_DAY }

    public ReminderType getReminderTypeForTonight() {
        checkAndResetExpiredWindow();

        // Both refills done or quota used up — check if new quota day is tomorrow
        if (getRefillCount() >= MAX_REFILLS || getRemainingLitres() <= 0.01f) {
            long nextQuota = getNextEligibleDayMs();
            return isTomorrow(nextQuota) ? ReminderType.NEW_QUOTA_AVAILABLE : ReminderType.NONE;
        }

        // Window active, still has refills — check eligible days list
        if (isWindowActive()) {
            for (long d : getRemainingEligibleDaysInWindow())
                if (isTomorrow(d)) return ReminderType.REFILL_DAY;
            return ReminderType.NONE;
        }

        // No window yet (never filled) — find next eligible day from tomorrow
        // e.g. today is odd day, odd car → tomorrow is even → no notify
        //      today is even day, odd car → tomorrow is odd → notify!
        long nextDay = findNextEligibleDay(
                System.currentTimeMillis() + 24L * 60 * 60 * 1000,
                Long.MAX_VALUE);
        return isTomorrow(nextDay) ? ReminderType.REFILL_DAY : ReminderType.NONE;
    }

    public boolean shouldRemindTonight()    { return getReminderTypeForTonight() != ReminderType.NONE; }
>>>>>>> a9c4ddf (Fix notification: correct odd/even day reminder logic)

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
