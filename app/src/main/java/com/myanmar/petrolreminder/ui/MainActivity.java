package com.myanmar.petrolreminder.ui;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.myanmar.petrolreminder.R;
import com.myanmar.petrolreminder.utils.AlarmScheduler;
import com.myanmar.petrolreminder.utils.NotificationHelper;
import com.myanmar.petrolreminder.utils.QuotaManager;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private QuotaManager qm;
    private TextView tvVehicleName, tvVehicleType;
    private TextView tvTotalQuota, tvUsedQuota, tvRemainingQuota;
    private TextView tvRefillsUsed, tvRefillsLeft, tvWindowStatus;
    private TextView tvDate1stRefill, tvDate2ndRefill, tvDateNewQuota;
    private TextView tvChatAnswer;
    private Button   btnRecordRefill, btnEditRefill, btnCheckStatus;
    private Button   btnNotifSettings, btnTestNotif, btnSettings;

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) Toast.makeText(this, "Notification ခွင့်ပြုချက် လိုအပ်သည်", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        qm = new QuotaManager(this);
        if (!qm.isSetupDone()) { startActivity(new Intent(this, SetupActivity.class)); finish(); return; }
        setContentView(R.layout.activity_main);
        requestNotifPermission();
        bindViews();
        setListeners();
        refreshUI();
    }

    @Override protected void onResume() { super.onResume(); if (qm.isSetupDone()) refreshUI(); }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void bindViews() {
        tvVehicleName    = findViewById(R.id.tvVehicleName);
        tvVehicleType    = findViewById(R.id.tvVehicleType);
        tvTotalQuota     = findViewById(R.id.tvTotalQuota);
        tvUsedQuota      = findViewById(R.id.tvUsedQuota);
        tvRemainingQuota = findViewById(R.id.tvRemainingQuota);
        tvRefillsUsed    = findViewById(R.id.tvRefillsUsed);
        tvRefillsLeft    = findViewById(R.id.tvRefillsLeft);
        tvWindowStatus   = findViewById(R.id.tvWindowStatus);
        tvDate1stRefill  = findViewById(R.id.tvDate1stRefill);
        tvDate2ndRefill  = findViewById(R.id.tvDate2ndRefill);
        tvDateNewQuota   = findViewById(R.id.tvDateNewQuota);
        tvChatAnswer     = findViewById(R.id.tvChatAnswer);
        btnRecordRefill  = findViewById(R.id.btnRecordRefill);
        btnEditRefill    = findViewById(R.id.btnEditRefill);
        btnCheckStatus   = findViewById(R.id.btnCheckStatus);
        btnNotifSettings = findViewById(R.id.btnNotifSettings);
        btnTestNotif     = findViewById(R.id.btnTestNotif);
        btnSettings      = findViewById(R.id.btnSettings);
    }

    private void setListeners() {
        btnRecordRefill.setOnClickListener(v -> showRefillDialog());
        btnEditRefill.setOnClickListener(v -> showEditRefillDialog());
        btnCheckStatus.setOnClickListener(v -> { tvChatAnswer.setVisibility(View.VISIBLE); tvChatAnswer.setText(qm.getRefuelStatusMessage()); });
        btnNotifSettings.setOnClickListener(v -> showNotifSettingsDialog());
        btnTestNotif.setOnClickListener(v -> { NotificationHelper.createNotificationChannel(this); showTestNotifMenu(); });
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
    }

    private void refreshUI() {
        qm.checkAndResetExpiredWindow();
        tvVehicleName.setText(qm.getVehicleName());
        tvVehicleType.setText(qm.getVehicleTypeLabel());
        tvTotalQuota.setText(String.format("%.1f L", qm.getTotalQuota()));
        tvUsedQuota.setText(String.format("%.1f L", qm.getUsedLitres()));
        tvRemainingQuota.setText(String.format("%.1f L", qm.getRemainingLitres()));
        tvRefillsUsed.setText(String.valueOf(qm.getRefillCount()));
        tvRefillsLeft.setText(String.valueOf(qm.getRemainingRefills()));

        if (!qm.isWindowActive() && qm.getRefillCount() == 0) {
            tvWindowStatus.setText("Window မစသေးပါ — ဆီဖြည့်ဖို့ အဆင်သင့်");
            tvWindowStatus.setTextColor(getColor(R.color.status_green));
        } else if (qm.isWindowActive()) {
            int d = qm.getDaysUntilReset();
            if (qm.getRemainingRefills() <= 0 || qm.getRemainingLitres() <= 0.01f) {
                tvWindowStatus.setText("ကိုတာကုန်ပါပြီ — " + d + " ရက်နောက် ကိုတာ အသစ်");
                tvWindowStatus.setTextColor(getColor(R.color.status_red));
            } else if (d == 1) {
                tvWindowStatus.setText("⚠️ မနက်ဖြန် Window ပြန်သစ်မည်!");
                tvWindowStatus.setTextColor(getColor(R.color.status_orange));
            } else {
                tvWindowStatus.setText("Window ကျန် " + d + " ရက်");
                tvWindowStatus.setTextColor(getColor(R.color.status_green));
            }
        }

        tvDate1stRefill.setText(qm.getFirstRefillDateStr());
        tvDate2ndRefill.setText(qm.getRemainingEligibleDaysStr());
        tvDateNewQuota.setText(qm.getNewQuotaDateStr());

        boolean canRecord = qm.getRemainingRefills() > 0 && qm.getRemainingLitres() > 0.01f;
        btnRecordRefill.setEnabled(canRecord);
        btnRecordRefill.setAlpha(canRecord ? 1f : 0.4f);
        btnEditRefill.setVisibility(qm.getRefillCount() > 0 ? View.VISIBLE : View.GONE);
    }

    // ─── Record refill with date picker ──────────────────────────────────────

    private void showRefillDialog() {
        boolean isFirstRefill = (qm.getRefillCount() == 0);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_refill, null);
        EditText etLitres   = dialogView.findViewById(R.id.etRefillLitres);
        TextView tvHint     = dialogView.findViewById(R.id.tvRefillHint);
        Button   btnPickDate = dialogView.findViewById(R.id.btnPickDate);
        TextView tvDateChosen = dialogView.findViewById(R.id.tvDateChosen);

        tvHint.setText(String.format("ကျန်ကိုတာ: %.1f L  |  ဖြည့်ခွင့်ကျန်: %d / 2 ကြိမ်",
                qm.getRemainingLitres(), qm.getRemainingRefills()));

        // Date picker only for first refill
        final long[] chosenDateMs = {0L};
        if (isFirstRefill) {
            btnPickDate.setVisibility(View.VISIBLE);
            tvDateChosen.setVisibility(View.VISIBLE);
            tvDateChosen.setText("ရက်: ဒီနေ့ (default)");
            btnPickDate.setOnClickListener(v -> {
                Calendar cal = Calendar.getInstance();
                new DatePickerDialog(this,
                    android.R.style.Theme_Holo_Light_Dialog,
                    (dp, y, m, d) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(y, m, d, 0, 0, 0);
                    picked.set(Calendar.MILLISECOND, 0);
                    chosenDateMs[0] = picked.getTimeInMillis();
                    tvDateChosen.setText("ရက်: " + QuotaManager.DATE_FMT.format(picked.getTime()));
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                .show();
            });
        } else {
            btnPickDate.setVisibility(View.GONE);
            tvDateChosen.setVisibility(View.GONE);
        }

        new AlertDialog.Builder(this)
                .setTitle(isFirstRefill ? "ပထမ ဆီဖြည့်မှတ်တမ်း" : "ဒုတိယ ဆီဖြည့်မှတ်တမ်း")
                .setView(dialogView)
                .setPositiveButton("သိမ်းမည်", (d, w) -> {
                    String s = etLitres.getText().toString().trim();
                    if (s.isEmpty()) { Toast.makeText(this, "လီတာ ထည့်ပါ", Toast.LENGTH_SHORT).show(); return; }
                    float litres;
                    try { litres = Float.parseFloat(s); if (litres <= 0) throw new NumberFormatException(); }
                    catch (NumberFormatException e) { Toast.makeText(this, "ကိန်းဂဏန်း မှားသည်", Toast.LENGTH_SHORT).show(); return; }

                    // Set chosen date before recording
                    if (isFirstRefill && chosenDateMs[0] > 0) {
                        qm.setWindowStartDate(chosenDateMs[0]);
                    }

                    switch (qm.recordRefill(litres)) {
                        case SUCCESS:
                            AlarmScheduler.scheduleAllAlarms(this);
                            Toast.makeText(this, String.format(
                                "✅ အကြိမ် %d — %.1f L မှတ်တမ်းတင်ပြီး\nကျန်: %.1f L | ဖြည့်ခွင့်: %d ကြိမ်",
                                qm.getRefillCount(), litres, qm.getRemainingLitres(), qm.getRemainingRefills()),
                                Toast.LENGTH_LONG).show();
                            refreshUI(); break;
                        case ALREADY_USED_MAX:
                            Toast.makeText(this, "2 ကြိမ် ပြည့်သွားပါပြီ", Toast.LENGTH_LONG).show(); break;
                        case EXCEEDS_QUOTA:
                            Toast.makeText(this, String.format("ကိုတာ %.1f L ကျော်နေသည်", qm.getRemainingLitres()), Toast.LENGTH_LONG).show(); break;
                    }
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    // ─── Edit refill ──────────────────────────────────────────────────────────

    private void showEditRefillDialog() {
        int refillNum = qm.getLastRefillNumber();
        float cur     = qm.getLastRefillLitres();
        String[] opts = {
            "✏️  အကြိမ် " + refillNum + " လီတာ ပြင်မည် (လက်ရှိ: " + String.format("%.1f", cur) + " L)",
            "🗑️  အကြိမ် " + refillNum + " ဖျက်မည် (undo)"
        };
        new AlertDialog.Builder(this).setTitle("မှတ်တမ်း ပြင်ဆင်မည်")
                .setItems(opts, (d, w) -> { if (w == 0) showCorrectDialog(refillNum, cur); else confirmDelete(refillNum); })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    private void showCorrectDialog(int refillNum, float cur) {
        View v2 = getLayoutInflater().inflate(R.layout.dialog_refill, null);
        EditText et = v2.findViewById(R.id.etRefillLitres);
        TextView th = v2.findViewById(R.id.tvRefillHint);
        v2.findViewById(R.id.btnPickDate).setVisibility(View.GONE);
        v2.findViewById(R.id.tvDateChosen).setVisibility(View.GONE);
        et.setText(String.format("%.1f", cur)); et.selectAll();
        th.setText("အကြိမ် " + refillNum + " — မှန်ကန်သော လီတာ ထည့်ပါ");
        new AlertDialog.Builder(this).setTitle("လီတာ ပြင်မည်").setView(v2)
                .setPositiveButton("သိမ်းမည်", (d, w) -> {
                    String s = et.getText().toString().trim();
                    if (s.isEmpty()) return;
                    float nl; try { nl = Float.parseFloat(s); } catch (Exception e) { return; }
                    if (qm.editLastRefill(nl)) { Toast.makeText(this, "✅ " + String.format("%.1f", nl) + " L သို့ ပြင်ပြီး", Toast.LENGTH_SHORT).show(); refreshUI(); }
                    else Toast.makeText(this, "ကိုတာ ကျော်သည်", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    private void confirmDelete(int refillNum) {
        String msg = refillNum == 1 ? "အကြိမ် 1 ဖျက်ပါမည်။ Window လည်း ပျောက်မည်။" : "အကြိမ် 2 ဖျက်ပါမည်။";
        new AlertDialog.Builder(this).setTitle("ဖျက်မည်လား?").setMessage(msg)
                .setPositiveButton("ဖျက်မည်", (d, w) -> { qm.deleteLastRefill(); refreshUI(); Toast.makeText(this, "ဖျက်ပြီး", Toast.LENGTH_SHORT).show(); })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    // ─── Notification settings ───────────────────────────────────────────────

    private void showNotifSettingsDialog() {
        boolean[] states = {
            qm.isNotifDayBeforeNoonEnabled(),
            qm.isNotifDayBeforeEveningEnabled(),
            qm.isNotifRefillDayMorningEnabled()
        };
        String[] labels = {
            "☀️ မနေ့ မွန်းတည့် 12:00  (မနက်ဖြန် ဖြည့်နိုင်မည်)",
            "🌙 မနေ့ ညနေ 18:00  (မနက်ဖြန် ဖြည့်နိုင်မည်)",
            "⛽ ဖြည့်နိုင်သောနေ့ မနက် 7:00  (ဒီနေ့ ဖြည့်နိုင်သည်)"
        };
        new AlertDialog.Builder(this)
                .setTitle("🔔 Notification အချိန် On/Off")
                .setMultiChoiceItems(labels, states, (d, which, isChecked) -> states[which] = isChecked)
                .setPositiveButton("သိမ်းမည်", (d, w) -> {
                    qm.setNotifDayBeforeNoon(states[0]);
                    qm.setNotifDayBeforeEvening(states[1]);
                    qm.setNotifRefillDayMorning(states[2]);
                    AlarmScheduler.scheduleAllAlarms(this);
                    Toast.makeText(this, "Notification ဆက်တင် သိမ်းပြီး", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    // ─── Test notification ───────────────────────────────────────────────────

    private void showTestNotifMenu() {
        String[] opts = {
            "☀️ မနေ့ မွန်းတည့် 12:00\n   (မနက်ဖြန် ဆီဖြည့်နိုင်ကြောင်း သတိပေးချိန်)",
            "🌙 မနေ့ ညနေ 18:00\n   (မနက်ဖြန် ဆီဖြည့်နိုင်ကြောင်း သတိပေးချိန်)",
            "⛽ ဖြည့်နိုင်သောနေ့ မနက် 7:00\n   (ဒီနေ့ ဆီဖြည့်ရမည့်နေ့ သတိပေးချိန်)",
            "🆕 ကိုတာ အသစ် မနက်ဖြန် ရောက်မည်\n   (ကိုတာ အသစ် သတိပေးချိန်)"
        };
        new AlertDialog.Builder(this).setTitle("Notification စမ်းသပ်မည်")
                .setItems(opts, (d, w) -> {
                    switch (w) {
                        case 0: NotificationHelper.showDayBeforeNotification(this, qm, false); break;
                        case 1: NotificationHelper.fireTestWithinWindowNotification(this, qm); break;
                        case 2: NotificationHelper.fireTestMorningNotification(this, qm); break;
                        case 3: NotificationHelper.fireTestNewQuotaNotification(this, qm); break;
                    }
                    Toast.makeText(this, "Notification ပို့ပြီး!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }
}
