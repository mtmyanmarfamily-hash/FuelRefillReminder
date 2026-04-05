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
    private TextView tvVehicleName, tvTotalQuota, tvUsedQuota, tvRemainingQuota;
    private TextView tvRefillsUsed, tvRefillsLeft, tvWindowStatus, tvChatAnswer;
    private TextView tvDate1stRefill, tvDate2ndRefill, tvDateNewQuota;
    private Button btnRecordRefill, btnEditRefill, btnCheckStatus, btnTestNotif, btnSettings;
    private EditText etChatQuestion;
    private Button btnAsk;

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) Toast.makeText(this, "Notification permission needed for reminders.", Toast.LENGTH_LONG).show();
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void bindViews() {
        tvVehicleName    = findViewById(R.id.tvVehicleName);
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
        etChatQuestion   = findViewById(R.id.etChatQuestion);
        btnAsk           = findViewById(R.id.btnAsk);
    }

    private void setListeners() {
        btnRecordRefill.setOnClickListener(v -> showRefillDialog());
        btnEditRefill.setOnClickListener(v -> showEditRefillDialog());
        btnCheckStatus.setOnClickListener(v -> { tvChatAnswer.setVisibility(View.VISIBLE); tvChatAnswer.setText(qm.getRefuelStatusMessage()); });
        btnTestNotif.setOnClickListener(v -> { NotificationHelper.createNotificationChannel(this); showTestNotificationMenu(); });
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        btnAsk.setOnClickListener(v -> handleChatQuestion());
    }

    private void refreshUI() {
        qm.checkAndResetExpiredWindow();
        tvVehicleName.setText(qm.getVehicleName());
        tvTotalQuota.setText(String.format("%.0f L", qm.getTotalQuota()));
        tvUsedQuota.setText(String.format("%.0f L", qm.getUsedLitres()));
        tvRemainingQuota.setText(String.format("%.0f L", qm.getRemainingLitres()));
        tvRefillsUsed.setText(String.valueOf(qm.getRefillCount()));
        tvRefillsLeft.setText(String.valueOf(qm.getRemainingRefills()));

        if (!qm.isWindowActive() && qm.getRefillCount() == 0) {
            tvWindowStatus.setText("No active window — Ready to start!");
            tvWindowStatus.setTextColor(getColor(R.color.status_green));
        } else if (qm.isWindowActive()) {
            int daysLeft = qm.getDaysUntilReset();
            if (qm.getRemainingRefills() <= 0 || qm.getRemainingLitres() <= 0.01f) {
                tvWindowStatus.setText("Quota exhausted — resets in " + daysLeft + " day(s)");
                tvWindowStatus.setTextColor(getColor(R.color.status_red));
            } else if (daysLeft == 1) {
                tvWindowStatus.setText("⚠️ Window resets TOMORROW — refuel today!");
                tvWindowStatus.setTextColor(getColor(R.color.status_orange));
            } else {
                tvWindowStatus.setText("Active window — " + daysLeft + " day(s) until reset");
                tvWindowStatus.setTextColor(getColor(R.color.status_green));
            }
        }

        boolean canRecord = qm.getRemainingRefills() > 0 && qm.getRemainingLitres() > 0.01f;
        btnRecordRefill.setEnabled(canRecord);
        btnRecordRefill.setAlpha(canRecord ? 1f : 0.4f);
        btnEditRefill.setVisibility(qm.getRefillCount() > 0 ? View.VISIBLE : View.GONE);

        // Dates
        tvDate1stRefill.setText(qm.getFirstRefillDateStr());
        tvDate2ndRefill.setText(qm.getSecondRefillDeadlineDateStr());
        tvDateNewQuota.setText(qm.getNewQuotaDateStr());
    }

    // ─── Record new refill ────────────────────────────────────────────────────

    private void showRefillDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_refill, null);
        EditText etLitres = dialogView.findViewById(R.id.etRefillLitres);
        TextView tvHint   = dialogView.findViewById(R.id.tvRefillHint);
        tvHint.setText(String.format("Remaining quota: %.0f L  |  Refills left: %d / 2",
                qm.getRemainingLitres(), qm.getRemainingRefills()));

        new AlertDialog.Builder(this)
                .setTitle("Record Today's Refill")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String s = etLitres.getText().toString().trim();
                    if (s.isEmpty()) { Toast.makeText(this, "Enter litres filled", Toast.LENGTH_SHORT).show(); return; }
                    float litres;
                    try { litres = Float.parseFloat(s); if (litres <= 0) throw new NumberFormatException(); }
                    catch (NumberFormatException e) { Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show(); return; }
                    QuotaManager.RefillResult result = qm.recordRefill(litres);
                    switch (result) {
                        case SUCCESS:
                            Toast.makeText(this, String.format("✅ Refill #%d: %.0f L recorded\nRemaining: %.0f L  |  Refills left: %d",
                                    qm.getRefillCount(), litres, qm.getRemainingLitres(), qm.getRemainingRefills()), Toast.LENGTH_LONG).show();
                            refreshUI(); break;
                        case ALREADY_USED_MAX:
                            Toast.makeText(this, "Both refills already used this window.", Toast.LENGTH_LONG).show(); break;
                        case EXCEEDS_QUOTA:
                            Toast.makeText(this, String.format("Exceeds remaining quota of %.0f L", qm.getRemainingLitres()), Toast.LENGTH_LONG).show(); break;
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ─── Edit / correct last refill ───────────────────────────────────────────

    private void showEditRefillDialog() {
        int   refillNum     = qm.getLastRefillNumber();
        float currentLitres = qm.getLastRefillLitres();
        String[] options = {
                "✏️  Correct Refill #" + refillNum + "  (currently " + String.format("%.0f", currentLitres) + " L)",
                "🗑️  Delete Refill #" + refillNum + "  (undo completely)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Edit Last Refill")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showCorrectLitresDialog(refillNum, currentLitres);
                    else showConfirmDeleteDialog(refillNum);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showCorrectLitresDialog(int refillNum, float current) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_refill, null);
        EditText etLitres = dialogView.findViewById(R.id.etRefillLitres);
        TextView tvHint   = dialogView.findViewById(R.id.tvRefillHint);
        etLitres.setText(String.format("%.0f", current));
        etLitres.selectAll();
        tvHint.setText("Correcting Refill #" + refillNum + ". Enter the correct amount.");

        new AlertDialog.Builder(this)
                .setTitle("Correct Refill #" + refillNum)
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String s = etLitres.getText().toString().trim();
                    if (s.isEmpty()) return;
                    float newLitres;
                    try { newLitres = Float.parseFloat(s); if (newLitres <= 0) throw new NumberFormatException(); }
                    catch (NumberFormatException e) { Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show(); return; }
                    if (qm.editLastRefill(newLitres)) {
                        Toast.makeText(this, String.format("✅ Refill #%d updated to %.0f L", refillNum, newLitres), Toast.LENGTH_SHORT).show();
                        refreshUI();
                    } else {
                        Toast.makeText(this, "Cannot update — exceeds quota.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showConfirmDeleteDialog(int refillNum) {
        String msg = refillNum == 1
                ? "This will delete Refill #1 and clear your current 7-day window."
                : "This will delete Refill #2. Your window stays active with Refill #1 only.";
        new AlertDialog.Builder(this)
                .setTitle("Delete Refill #" + refillNum + "?")
                .setMessage(msg)
                .setPositiveButton("Delete", (dialog, which) -> {
                    qm.deleteLastRefill();
                    Toast.makeText(this, "Refill #" + refillNum + " deleted.", Toast.LENGTH_SHORT).show();
                    refreshUI();
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ─── Test notification ────────────────────────────────────────────────────

    private void showTestNotificationMenu() {
        String[] options = {
                "🆕  New Quota Tomorrow\n    (မနက်ဖြန် ဆီကိုတာ အသစ် စတင်ပါပြီ)",
                "⛽  Refuel Reminder\n    (quota still remaining)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Test Notification")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        NotificationHelper.fireTestNewQuotaNotification(this, qm);
                    } else {
                        NotificationHelper.fireTestWithinWindowNotification(this, qm);
                    }
                    Toast.makeText(this, "Notification sent! Check your notification bar.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ─── Chat Q&A ─────────────────────────────────────────────────────────────

    private void handleChatQuestion() {
        String q = etChatQuestion.getText().toString().trim().toLowerCase();
        tvChatAnswer.setVisibility(View.VISIBLE);
        if (q.isEmpty()) { tvChatAnswer.setText("Please type a question first."); return; }

        if (q.contains("when") || q.contains("refuel") || q.contains("refill") || q.contains("quota") || q.contains("status")) {
            tvChatAnswer.setText(qm.getRefuelStatusMessage());
        } else if (q.contains("how much") || q.contains("litre") || q.contains("remaining") || q.contains("left")) {
            tvChatAnswer.setText(String.format("Remaining: %.0f L\nUsed: %.0f L\nTotal: %.0f L",
                    qm.getRemainingLitres(), qm.getUsedLitres(), qm.getTotalQuota()));
        } else if (q.contains("day") || q.contains("reset") || q.contains("window")) {
            int d = qm.getDaysUntilReset();
            tvChatAnswer.setText(d < 0 ? "No active window yet. Record a refill to start." :
                    d == 0 ? "Your quota window resets TODAY!" :
                    "Your 7-day window resets in " + d + " day(s).");
        } else if (q.contains("remind") || q.contains("notification")) {
            tvChatAnswer.setText("Reminders fire at 7:00 PM every evening.\n" +
                    "• Day 6 of window: reminded to use remaining quota\n" +
                    "• Day 7 evening: new quota starts tomorrow notification");
        } else {
            tvChatAnswer.setText(qm.getRefuelStatusMessage());
        }
        etChatQuestion.setText("");
    }
}
