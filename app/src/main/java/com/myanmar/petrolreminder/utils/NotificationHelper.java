package com.myanmar.petrolreminder.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.myanmar.petrolreminder.R;
import com.myanmar.petrolreminder.ui.MainActivity;

public class NotificationHelper {

    public static final String CHANNEL_ID       = "petrol_reminder_channel";
    public static final String CHANNEL_NAME     = "Petrol Quota Reminders";
    public static final int    NOTIF_ID_WITHIN  = 2001;  // refill within current window
    public static final int    NOTIF_ID_NEW     = 2002;  // new quota available tomorrow

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminds you about your 7-day petrol quota.");
            channel.enableVibration(true);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    /**
     * Dispatches the correct notification based on ReminderType.
     * Called by ReminderReceiver every evening at 7 PM.
     */
    public static void showReminderNotification(Context context, QuotaManager qm) {
        QuotaManager.ReminderType type = qm.getReminderTypeForTonight();
        switch (type) {
            case NEW_QUOTA_AVAILABLE:
                showNewQuotaNotification(context, qm);
                break;
            case REFILL_WITHIN_WINDOW:
                showWithinWindowNotification(context, qm);
                break;
            default:
                break;
        }
    }

    /**
     * "Tomorrow your new quota is available — အသစ် စထည့် လို့ရပါပီ"
     * Fires when the current 7-day window expires tomorrow.
     */
    private static void showNewQuotaNotification(Context context, QuotaManager qm) {
        String vehicle = qm.getVehicleName();
        float  total   = qm.getTotalQuota();

        String title = "🆕 New Quota Tomorrow! – " + vehicle;
        String body  = String.format(
                "မနက်ဖြန် ဆီကိုတာ အသစ် စတင်ပါပြီ။\n" +
                "Tomorrow your new %.0f L quota is available.\n" +
                "အသစ် စထည့် လို့ရပါပီ ✅\n\n" +
                "Ready for 2 refills in the new window!",
                total
        );
        postNotification(context, title, body, NOTIF_ID_NEW);
    }

    /**
     * Reminder that the window resets tomorrow and quota/refills still remain.
     */
    private static void showWithinWindowNotification(Context context, QuotaManager qm) {
        float  remaining   = qm.getRemainingLitres();
        int    refillsLeft = qm.getRemainingRefills();
        String vehicle     = qm.getVehicleName();

        String title = "⛽ Refuel Tomorrow! – " + vehicle;
        String body  = String.format(
                "Your 7-day quota resets tomorrow.\n" +
                "Remaining quota: %.0f L  |  Refills left: %d\n" +
                "Don't miss your quota!",
                remaining, refillsLeft
        );
        postNotification(context, title, body, NOTIF_ID_WITHIN);
    }

    private static void postNotification(Context context, String title, String body, int notifId) {
        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(
                context, notifId, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_fuel)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 300, 200, 300})
                .build();

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification);
        } catch (SecurityException e) {
            // Notification permission not granted
        }
    }

    // ─── Test helpers (called directly from UI) ───────────────────────────────

    public static void fireTestNewQuotaNotification(Context context, QuotaManager qm) {
        showNewQuotaNotification(context, qm);
    }

    public static void fireTestWithinWindowNotification(Context context, QuotaManager qm) {
        showWithinWindowNotification(context, qm);
    }
}
