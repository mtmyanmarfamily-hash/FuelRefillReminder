package com.myanmar.petrolreminder.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Core quota logic:
 * - User sets a total quota (litres) for their vehicle.
 * - Within any 7-day window (starting from first refill), user may refill at most 2 times.
 * - Each refill records the litres added; remaining quota = total - sum of refills.
 * - After 2 refills OR after 7 days from first refill → window resets automatically.
 * - Evening reminder fires when there is still quota/refill remaining and reset day is tomorrow.
 * - Reminders stop when both refills are used or quota is exhausted within the window.
 */
public class QuotaManager {

    private static final String PREFS_NAME = "petrol_prefs";

    // Setup keys
    static final String KEY_TOTAL_QUOTA      = "total_quota_litres";
    static final String KEY_VEHICLE_NAME     = "vehicle_name";
    static final String KEY_SETUP_DONE       = "setup_done";

    // Current window keys
    static final String KEY_WINDOW_START_MS  = "window_start_ms";   // epoch ms of first refill
    static final String KEY_REFILL_COUNT     = "refill_count";      // 0, 1, or 2
    static final String KEY_REFILL_1_LITRES  = "refill_1_litres";
    static final String KEY_REFILL_2_LITRES  = "refill_2_litres";

    public static final int MAX_REFILLS_PER_WINDOW = 2;
    public static final int WINDOW_DAYS            = 7;
    private static final long WINDOW_MS            = (long) WINDOW_DAYS * 24 * 60 * 60 * 1000;

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

    public boolean isSetupDone() {
        return prefs.getBoolean(KEY_SETUP_DONE, false);
    }

    public String getVehicleName() {
        return prefs.getString(KEY_VEHICLE_NAME, "My Vehicle");
    }

    public float getTotalQuota() {
        return prefs.getFloat(KEY_TOTAL_QUOTA, 0f);
    }

    // ─── Window helpers ──────────────────────────────────────────────────────

    /** True if a 7-day window is currently active (has not yet expired). */
    public boolean isWindowActive() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start == 0L) return false;
        return (System.currentTimeMillis() - start) < WINDOW_MS;
    }

    /** Expire old window if 7 days have passed. */
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

    /**
     * Record a refill of `litres` today.
     * Returns a RefillResult describing what happened.
     */
    public RefillResult recordRefill(float litres) {
        checkAndResetExpiredWindow();

        int count = prefs.getInt(KEY_REFILL_COUNT, 0);

        // Already used both refills in this window
        if (count >= MAX_REFILLS_PER_WINDOW) {
            return RefillResult.ALREADY_USED_MAX;
        }

        // Check remaining quota
        float used = getUsedLitres();
        float total = getTotalQuota();
        if (litres > (total - used) + 0.01f) {
            return RefillResult.EXCEEDS_QUOTA;
        }

        SharedPreferences.Editor ed = prefs.edit();

        if (count == 0) {
            // First refill in a new window — start the clock
            ed.putLong(KEY_WINDOW_START_MS, System.currentTimeMillis());
            ed.putFloat(KEY_REFILL_1_LITRES, litres);
        } else {
            // Second refill
            ed.putFloat(KEY_REFILL_2_LITRES, litres);
        }
        ed.putInt(KEY_REFILL_COUNT, count + 1);
        ed.apply();

        return RefillResult.SUCCESS;
    }

    // ─── Status getters ──────────────────────────────────────────────────────

    public int getRefillCount() {
        checkAndResetExpiredWindow();
        return prefs.getInt(KEY_REFILL_COUNT, 0);
    }

    public float getUsedLitres() {
        float r1 = prefs.getFloat(KEY_REFILL_1_LITRES, 0f);
        float r2 = prefs.getFloat(KEY_REFILL_2_LITRES, 0f);
        return r1 + r2;
    }

    public float getRemainingLitres() {
        return getTotalQuota() - getUsedLitres();
    }

    public int getRemainingRefills() {
        return MAX_REFILLS_PER_WINDOW - getRefillCount();
    }

    /**
     * Returns epoch ms when the current window expires (= start + 7 days).
     * Returns 0 if no active window.
     */
    public long getWindowResetTimeMs() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start == 0L) return 0L;
        return start + WINDOW_MS;
    }

    /**
     * Days remaining until window reset. Returns -1 if no active window.
     */
    public int getDaysUntilReset() {
        long resetMs = getWindowResetTimeMs();
        if (resetMs == 0L) return -1;
        long diff = resetMs - System.currentTimeMillis();
        if (diff <= 0) return 0;
        return (int) Math.ceil((double) diff / (24.0 * 60 * 60 * 1000));
    }

    /**
     * Whether the user can still refuel: window is active (or not started),
     * quota remaining > 0, and refill count < 2.
     */
    public boolean canRefuel() {
        checkAndResetExpiredWindow();
        if (!isWindowActive() && getRefillCount() == 0) return true; // new window possible
        return getRemainingRefills() > 0 && getRemainingLitres() > 0.01f;
    }

    /**
     * Human-readable status for "when can I refuel?" query.
     */
    public String getRefuelStatusMessage() {
        checkAndResetExpiredWindow();

        float total    = getTotalQuota();
        float used     = getUsedLitres();
        float remaining = total - used;
        int   refillsLeft = getRemainingRefills();
        int   daysLeft    = getDaysUntilReset();

        if (!isWindowActive() && getRefillCount() == 0) {
            return String.format(
                "✅ Ready to refuel!\n\nYou have not started a quota window yet.\n" +
                "Your full quota of %.0f L is available.\n" +
                "You can refuel up to 2 times within 7 days once you start.",
                total);
        }

        if (refillsLeft <= 0 || remaining <= 0.01f) {
            if (daysLeft > 0) {
                return String.format(
                    "⛽ Quota used up.\n\nYou have used both refills (%.0f / %.0f L).\n" +
                    "Your quota resets in %d day%s.\n" +
                    "You can refuel again after the reset.",
                    used, total, daysLeft, daysLeft == 1 ? "" : "s");
            } else {
                return "✅ Quota has reset! You can refuel now with a fresh quota.";
            }
        }

        if (daysLeft == 0) {
            return String.format(
                "✅ Your quota resets TODAY!\n\nRemaining this window: %.0f L (%d refill%s left).\n" +
                "After today, your full %.0f L quota restores.",
                remaining, refillsLeft, refillsLeft == 1 ? "" : "s", total);
        }

        return String.format(
            "⛽ Refuel Status\n\n" +
            "Quota used: %.0f / %.0f L\n" +
            "Remaining quota: %.0f L\n" +
            "Refills used: %d / 2\n" +
            "Refills remaining: %d\n" +
            "Window resets in: %d day%s\n\n" +
            "%s",
            used, total,
            remaining,
            getRefillCount(),
            refillsLeft,
            daysLeft, daysLeft == 1 ? "" : "s",
            refillsLeft > 0
                ? "You CAN still refuel " + refillsLeft + " more time" + (refillsLeft == 1 ? "." : "s.")
                : "No more refills available until reset.");
    }

    /**
     * Reminder type enum so the receiver knows which notification to show.
     */
    public enum ReminderType {
        NONE,
        REFILL_WITHIN_WINDOW,   // Still have quota/refills left, reset tomorrow
        NEW_QUOTA_AVAILABLE     // Window expires tomorrow → fresh quota starts
    }

    /**
     * Decide which reminder (if any) should fire tonight at 7 PM.
     *
     * Priority:
     *  1. NEW_QUOTA_AVAILABLE  – window expires tomorrow (user can start fresh)
     *  2. REFILL_WITHIN_WINDOW – reset tomorrow AND still has remaining quota/refills
     *  3. NONE
     */
    public ReminderType getReminderTypeForTonight() {
        checkAndResetExpiredWindow();

        // No window ever started → nothing to remind about yet
        if (!isWindowActive() && getRefillCount() == 0) return ReminderType.NONE;

        int daysLeft = getDaysUntilReset();

        // Window expires tomorrow → new quota available day after tomorrow
        if (isWindowActive() && daysLeft == 1) {
            return ReminderType.NEW_QUOTA_AVAILABLE;
        }

        // Shouldn't normally hit this, but guard
        return ReminderType.NONE;
    }

    /** Convenience — kept for any callers that just need a boolean. */
    public boolean shouldRemindTonight() {
        return getReminderTypeForTonight() != ReminderType.NONE;
    }

    // ─── Date helpers ─────────────────────────────────────────────────────────

    private static final java.text.SimpleDateFormat DATE_FMT =
            new java.text.SimpleDateFormat("dd MMM yyyy (EEE)", java.util.Locale.ENGLISH);

    /** Date of 1st refill, or "—" if none yet. */
    public String getFirstRefillDateStr() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start == 0L) return "—";
        return DATE_FMT.format(new java.util.Date(start));
    }

    /**
     * Last day to use 2nd refill = day 7 of window (= reset day).
     * Returns "—" if no window active or 2nd refill already used.
     */
    public String getSecondRefillDeadlineDateStr() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start == 0L) return "—";
        if (prefs.getInt(KEY_REFILL_COUNT, 0) >= 2) return "✅ Done";
        // Last day is day 7 = start + 6 days (inclusive)
        long lastDay = start + 6L * 24 * 60 * 60 * 1000;
        return DATE_FMT.format(new java.util.Date(lastDay));
    }

    /** Date when the new quota window starts = start + 7 days. */
    public String getNewQuotaDateStr() {
        long start = prefs.getLong(KEY_WINDOW_START_MS, 0L);
        if (start == 0L) return "—";
        long newQuotaDay = start + WINDOW_MS;
        return DATE_FMT.format(new java.util.Date(newQuotaDay));
    }

    // ─── Edit / correct last refill ──────────────────────────────────────────

    /** Returns the litres of the most recent refill, or 0 if none. */
    public float getLastRefillLitres() {
        int count = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (count == 2) return prefs.getFloat(KEY_REFILL_2_LITRES, 0f);
        if (count == 1) return prefs.getFloat(KEY_REFILL_1_LITRES, 0f);
        return 0f;
    }

    /** Returns which refill number (1 or 2) was last recorded, or 0 if none. */
    public int getLastRefillNumber() {
        return prefs.getInt(KEY_REFILL_COUNT, 0);
    }

    /**
     * Correct the last recorded refill with a new litre amount.
     * Returns false if no refill exists or new amount exceeds quota.
     */
    public boolean editLastRefill(float newLitres) {
        int count = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (count == 0) return false;

        float total = getTotalQuota();
        // Calculate used excluding the last refill
        float otherRefill = (count == 2)
                ? prefs.getFloat(KEY_REFILL_1_LITRES, 0f)
                : 0f;
        if (newLitres > (total - otherRefill) + 0.01f) return false;

        SharedPreferences.Editor ed = prefs.edit();
        if (count == 1) {
            ed.putFloat(KEY_REFILL_1_LITRES, newLitres);
        } else {
            ed.putFloat(KEY_REFILL_2_LITRES, newLitres);
        }
        ed.apply();
        return true;
    }

    /**
     * Delete (undo) the last recorded refill entirely.
     * If it was refill #1, the whole window is cleared.
     * If it was refill #2, window stays active with just refill #1.
     */
    public void deleteLastRefill() {
        int count = prefs.getInt(KEY_REFILL_COUNT, 0);
        if (count == 0) return;
        SharedPreferences.Editor ed = prefs.edit();
        if (count == 1) {
            // Remove entire window
            ed.remove(KEY_WINDOW_START_MS)
              .remove(KEY_REFILL_COUNT)
              .remove(KEY_REFILL_1_LITRES)
              .remove(KEY_REFILL_2_LITRES);
        } else {
            // Just remove refill #2
            ed.remove(KEY_REFILL_2_LITRES)
              .putInt(KEY_REFILL_COUNT, 1);
        }
        ed.apply();
    }

    public enum RefillResult {
        SUCCESS,
        ALREADY_USED_MAX,
        EXCEEDS_QUOTA
    }
}
