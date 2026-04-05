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

    public static final String CHANNEL_ID   = "petrol_reminder_channel";
    public static final String CHANNEL_NAME = "ဆီကိုတာ သတိပေးချက်";
    public static final int    NOTIF_ID_1   = 2001;
    public static final int    NOTIF_ID_2   = 2002;
    public static final int    NOTIF_ID_3   = 2003;

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("ဆီဖြည့်ရန် အချိန်မတိုင်မီ သတိပေးချက်များ");
            ch.enableVibration(true);
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    /** မနေ့ noon / evening — "မနက်ဖြန် ဆီဖြည့်နိုင်မည်" */
    public static void showDayBeforeNotification(Context context, QuotaManager qm, boolean isEvening) {
        String vehicle = qm.getVehicleName();
        String vType   = qm.getVehicleTypeLabel();
        float  total   = qm.getTotalQuota();
        int    refLeft = qm.getRemainingRefills();

        String title = isEvening
            ? "🌙 မနက်ဖြန် ဆီဖြည့်နိုင်မည် — " + vehicle
            : "☀️ မနက်ဖြန် ဆီဖြည့်နိုင်မည် — " + vehicle;
        String body = vType + " | မနက်ဖြန် ဆီဖြည့်ရမည့်နေ့\n"
            + String.format("ကိုတာ: %.1f L | ဖြည့်ခွင့်ကျန်: %d ကြိမ်\n", total, refLeft)
            + "🆕 ကိုတာ အသစ်: " + qm.getNewQuotaDateStr();
        post(context, title, body, isEvening ? NOTIF_ID_2 : NOTIF_ID_1);
    }

    /** ဆီဖြည့်နိုင်သောနေ့ မနက် ၇ နာရီ */
    public static void showRefillDayMorningNotification(Context context, QuotaManager qm) {
        String vehicle = qm.getVehicleName();
        String vType   = qm.getVehicleTypeLabel();
        float  rem     = qm.getRemainingLitres();
        int    refLeft = qm.getRemainingRefills();

        String title = "⛽ ဒီနေ့ ဆီဖြည့်နိုင်သည်! — " + vehicle;
        String body  = vType + " — ဒီနေ့ ဆီဖြည့်ရမည့်နေ့ဖြစ်သည်\n"
            + String.format("ကျန်ကိုတာ: %.1f L | ဖြည့်ခွင့်: %d ကြိမ်\n", rem, refLeft)
            + "🆕 ကိုတာ အသစ်: " + qm.getNewQuotaDateStr();
        post(context, title, body, NOTIF_ID_3);
    }

    /** New quota tomorrow (existing) */
    public static void showNewQuotaNotification(Context context, QuotaManager qm) {
        String title = "🆕 မနက်ဖြန် ကိုတာ အသစ်! — " + qm.getVehicleName();
        String body  = String.format("မနက်ဖြန် ဆီကိုတာ အသစ် စတင်ပါပြီ။\n%.1f L ပြည့်ဝပါသည်။\nအသစ် စထည့် လို့ရပါပီ ✅", qm.getTotalQuota());
        post(context, title, body, NOTIF_ID_1);
    }

    private static void post(Context context, String title, String body, int notifId) {
        Intent tap = new Intent(context, MainActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(context, notifId, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_fuel)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 300, 200, 300})
                .build();
        try { NotificationManagerCompat.from(context).notify(notifId, n); }
        catch (SecurityException ignored) {}
    }

    // Test helpers
    public static void fireTestNewQuotaNotification(Context context, QuotaManager qm)       { showNewQuotaNotification(context, qm); }
    public static void fireTestWithinWindowNotification(Context context, QuotaManager qm)    { showDayBeforeNotification(context, qm, true); }
    public static void fireTestMorningNotification(Context context, QuotaManager qm)         { showRefillDayMorningNotification(context, qm); }
}
