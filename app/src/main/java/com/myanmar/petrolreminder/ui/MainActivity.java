package com.myanmar.petrolreminder.ui;

import android.Manifest;
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
import com.myanmar.petrolreminder.utils.NotificationHelper;
import com.myanmar.petrolreminder.utils.QuotaManager;

public class MainActivity extends AppCompatActivity {

    private QuotaManager qm;
    private TextView tvVehicleName, tvVehicleType;
    private TextView tvTotalQuota, tvUsedQuota, tvRemainingQuota;
    private TextView tvRefillsUsed, tvRefillsLeft, tvWindowStatus;
    private TextView tvDate1stRefill, tvDate2ndRefill, tvDateNewQuota;
    private TextView tvChatAnswer;
    private Button   btnRecordRefill, btnEditRefill, btnCheckStatus, btnTestNotif, btnSettings;

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) Toast.makeText(this, "Notification ခွင့်ပြုချက် လိုအပ်သည်။", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        qm = new QuotaManager(this);
        if (!qm.isSetupDone()) { startActivity(new Intent(this, SetupActivity.class)); finish(); return; }
        setContentView(R.layout.activity_main);
        requestNotificationPermission();
        bindViews();
        setListeners();
        refreshUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (qm.isSetupDone()) refreshUI();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
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
        btnTestNotif     = findViewById(R.id.btnTestNotif);
        btnSettings      = findViewById(R.id.btnSettings);
    }

    private void setListeners() {
        btnRecordRefill.setOnClickListener(v -> showRefillDialog());
        btnEditRefill.setOnClickListener(v -> showEditRefillDialog());
        btnCheckStatus.setOnClickListener(v -> {
            tvChatAnswer.setVisibility(View.VISIBLE);
            tvChatAnswer.setText(qm.getRefuelStatusMessage());
        });
        btnTestNotif.setOnClickListener(v -> {
            NotificationHelper.createNotificationChannel(this);
            showTestNotificationMenu();
        });
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
    }

    private void refreshUI() {
        qm.checkAndResetExpiredWindow();

        tvVehicleName.setText(qm.getVehicleName());
        tvVehicleType.setText(qm.getVehicleTypeLabel());
        tvTotalQuota.setText(String.format("%.0f L", qm.getTotalQuota()));
        tvUsedQuota.setText(String.format("%.0f L", qm.getUsedLitres()));
        tvRemainingQuota.setText(String.format("%.0f L", qm.getRemainingLitres()));
        tvRefillsUsed.setText(String.valueOf(qm.getRefillCount()));
        tvRefillsLeft.setText(String.valueOf(qm.getRemainingRefills()));

        // Window status
        if (!qm.isWindowActive() && qm.getRefillCount() == 0) {
            tvWindowStatus.setText("Window မစသေးပါ — ဆီဖြည့်ဖို့ အဆင်သင့်ဖြစ်ပြီ!");
            tvWindowStatus.setTextColor(getColor(R.color.status_green));
        } else if (qm.isWindowActive()) {
            int daysLeft = qm.getDaysUntilReset();
            if (qm.getRemainingRefills() <= 0 || qm.getRemainingLitres() <= 0.01f) {
                tvWindowStatus.setText("ကိုတာကုန်ပါပြီ — " + daysLeft + " ရက်နောက် ကိုတာ အသစ်");
                tvWindowStatus.setTextColor(getColor(R.color.status_red));
            } else if (daysLeft == 1) {
                tvWindowStatus.setText("⚠️ မနက်ဖြန် Window ပြန်သစ်မည် — ဒီနေ့ ဖြည့်ပါ!");
                tvWindowStatus.setTextColor(getColor(R.color.status_orange));
            } else {
                tvWindowStatus.setText("Window ကျန် " + daysLeft + " ရက်");
                tvWindowStatus.setTextColor(getColor(R.color.status_green));
            }
        }

        // Dates
        tvDate1stRefill.setText(qm.getFirstRefillDateStr());
        tvDate2ndRefill.setText(qm.getSecondRefillDeadlineDateStr());
        tvDateNewQuota.setText(qm.getNewQuotaDateStr());

        // Buttons
        boolean canRecord = qm.getRemainingRefills() > 0 && qm.getRemainingLitres() > 0.01f;
        btnRecordRefill.setEnabled(canRecord);
        btnRecordRefill.setAlpha(canRecord ? 1f : 0.4f);
        btnEditRefill.setVisibility(qm.getRefillCount() > 0 ? View.VISIBLE : View.GONE);
    }

    // ─── Record refill ────────────────────────────────────────────────────────

    private void showRefillDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_refill, null);
        EditText etLitres = dialogView.findViewById(R.id.etRefillLitres);
        TextView tvHint   = dialogView.findViewById(R.id.tvRefillHint);
        tvHint.setText(String.format("ကျန်ကိုတာ: %.0f L  |  ဖြည့်ခွင့်ကျန်: %d / 2 ကြိမ်",
                qm.getRemainingLitres(), qm.getRemainingRefills()));

        new AlertDialog.Builder(this)
                .setTitle("ဆီဖြည့်မှတ်တမ်းတင်မည်")
                .setView(dialogView)
                .setPositiveButton("သိမ်းမည်", (d, w) -> {
                    String s = etLitres.getText().toString().trim();
                    if (s.isEmpty()) { Toast.makeText(this, "လီတာ ထည့်သွင်းပါ", Toast.LENGTH_SHORT).show(); return; }
                    float litres;
                    try { litres = Float.parseFloat(s); if (litres <= 0) throw new NumberFormatException(); }
                    catch (NumberFormatException e) { Toast.makeText(this, "ကိန်းဂဏန်း မှားနေသည်", Toast.LENGTH_SHORT).show(); return; }
                    switch (qm.recordRefill(litres)) {
                        case SUCCESS:
                            Toast.makeText(this, String.format(
                                "✅ အကြိမ် %d မှတ်တမ်းတင်ပြီး: %.0f L\nကျန်ဆီ: %.0f L  |  ဖြည့်ခွင့်ကျန်: %d ကြိမ်",
                                qm.getRefillCount(), litres, qm.getRemainingLitres(), qm.getRemainingRefills()),
                                Toast.LENGTH_LONG).show();
                            refreshUI(); break;
                        case ALREADY_USED_MAX:
                            Toast.makeText(this, "ဤ window တွင် 2 ကြိမ် ပြည့်သွားပါပြီ။", Toast.LENGTH_LONG).show(); break;
                        case EXCEEDS_QUOTA:
                            Toast.makeText(this, String.format("ကိုတာ %.0f L ကျော်နေသည်", qm.getRemainingLitres()), Toast.LENGTH_LONG).show(); break;
                    }
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    // ─── Edit refill ──────────────────────────────────────────────────────────

    private void showEditRefillDialog() {
        int   refillNum     = qm.getLastRefillNumber();
        float currentLitres = qm.getLastRefillLitres();
        String[] options = {
            "✏️  အကြိမ် " + refillNum + " — လီတာ ပြင်ဆင်မည် (လက်ရှိ: " + String.format("%.0f", currentLitres) + " L)",
            "🗑️  အကြိမ် " + refillNum + " — ဖျက်မည် (undo)"
        };
        new AlertDialog.Builder(this).setTitle("မှတ်တမ်း ပြင်ဆင်မည်")
                .setItems(options, (d, w) -> {
                    if (w == 0) showCorrectLitresDialog(refillNum, currentLitres);
                    else showConfirmDeleteDialog(refillNum);
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    private void showCorrectLitresDialog(int refillNum, float current) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_refill, null);
        EditText etLitres = dialogView.findViewById(R.id.etRefillLitres);
        TextView tvHint   = dialogView.findViewById(R.id.tvRefillHint);
        etLitres.setText(String.format("%.0f", current));
        etLitres.selectAll();
        tvHint.setText("အကြိမ် " + refillNum + " — မှန်ကန်သော လီတာ ထည့်ပါ");
        new AlertDialog.Builder(this).setTitle("လီတာ ပြင်ဆင်မည်")
                .setView(dialogView)
                .setPositiveButton("သိမ်းမည်", (d, w) -> {
                    String s = etLitres.getText().toString().trim();
                    if (s.isEmpty()) return;
                    float newL;
                    try { newL = Float.parseFloat(s); if (newL <= 0) throw new NumberFormatException(); }
                    catch (NumberFormatException e) { Toast.makeText(this, "ကိန်းဂဏန်း မှားနေသည်", Toast.LENGTH_SHORT).show(); return; }
                    if (qm.editLastRefill(newL)) {
                        Toast.makeText(this, "✅ အကြိမ် " + refillNum + " → " + String.format("%.0f", newL) + " L သို့ ပြင်ပြီး", Toast.LENGTH_SHORT).show();
                        refreshUI();
                    } else {
                        Toast.makeText(this, "ကိုတာ ကျော်သွားသည်", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    private void showConfirmDeleteDialog(int refillNum) {
        String msg = refillNum == 1
                ? "အကြိမ် 1 ဖျက်ပါမည်။ လက်ရှိ 7-ရက် window လည်း ပျောက်သွားမည်။"
                : "အကြိမ် 2 ဖျက်ပါမည်။ Window ထဲ အကြိမ် 1 သာ ကျန်မည်။";
        new AlertDialog.Builder(this).setTitle("အကြိမ် " + refillNum + " ဖျက်မည်လား?")
                .setMessage(msg)
                .setPositiveButton("ဖျက်မည်", (d, w) -> {
                    qm.deleteLastRefill();
                    Toast.makeText(this, "အကြိမ် " + refillNum + " ဖျက်ပြီး", Toast.LENGTH_SHORT).show();
                    refreshUI();
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    // ─── Test notification ────────────────────────────────────────────────────

    private void showTestNotificationMenu() {
        String[] options = {
            "🆕  ကိုတာ အသစ် မနက်ဖြန် (မနက်ဖြန် ဆီကိုတာ အသစ် စတင်ပါပြီ)",
            "⛽  ဆီဖြည့်ဖို့ သတိပေးချက်"
        };
        new AlertDialog.Builder(this).setTitle("Notification စမ်းသပ်မည်")
                .setItems(options, (d, w) -> {
                    if (w == 0) NotificationHelper.fireTestNewQuotaNotification(this, qm);
                    else NotificationHelper.fireTestWithinWindowNotification(this, qm);
                    Toast.makeText(this, "Notification ပို့ပြီး! Notification bar စစ်ဆေးပါ", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }
}
