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
import com.myanmar.petrolreminder.utils.QuotaManager;

public class MainActivity extends AppCompatActivity {

    private QuotaManager qm;

    private TextView tvVehicleName;
    private TextView tvTotalQuota;
    private TextView tvUsedQuota;
    private TextView tvRemainingQuota;
    private TextView tvRefillsUsed;
    private TextView tvRefillsLeft;
    private TextView tvWindowStatus;
    private TextView tvChatAnswer;
    private Button   btnRecordRefill;
    private Button   btnCheckStatus;
    private Button   btnSettings;
    private EditText etChatQuestion;
    private Button   btnAsk;

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, "Notification permission needed for reminders.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        qm = new QuotaManager(this);

        // Redirect to setup if not done
        if (!qm.isSetupDone()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

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
        tvTotalQuota     = findViewById(R.id.tvTotalQuota);
        tvUsedQuota      = findViewById(R.id.tvUsedQuota);
        tvRemainingQuota = findViewById(R.id.tvRemainingQuota);
        tvRefillsUsed    = findViewById(R.id.tvRefillsUsed);
        tvRefillsLeft    = findViewById(R.id.tvRefillsLeft);
        tvWindowStatus   = findViewById(R.id.tvWindowStatus);
        tvChatAnswer     = findViewById(R.id.tvChatAnswer);
        btnRecordRefill  = findViewById(R.id.btnRecordRefill);
        btnCheckStatus   = findViewById(R.id.btnCheckStatus);
        btnSettings      = findViewById(R.id.btnSettings);
        etChatQuestion   = findViewById(R.id.etChatQuestion);
        btnAsk           = findViewById(R.id.btnAsk);
    }

    private void setListeners() {
        btnRecordRefill.setOnClickListener(v -> showRefillDialog());
        btnCheckStatus.setOnClickListener(v -> {
            tvChatAnswer.setVisibility(View.VISIBLE);
            tvChatAnswer.setText(qm.getRefuelStatusMessage());
        });
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SetupActivity.class));
        });
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

        // Window status line
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

        // Disable record button if no refills left
        boolean canRecord = qm.getRemainingRefills() > 0 && qm.getRemainingLitres() > 0.01f;
        btnRecordRefill.setEnabled(canRecord);
        btnRecordRefill.setAlpha(canRecord ? 1f : 0.4f);
    }

    private void showRefillDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_refill, null);
        EditText etLitres = dialogView.findViewById(R.id.etRefillLitres);

        // Show remaining quota hint
        TextView tvHint = dialogView.findViewById(R.id.tvRefillHint);
        float remaining = qm.getRemainingLitres();
        int refillsLeft = qm.getRemainingRefills();
        tvHint.setText(String.format(
                "Remaining quota: %.0f L  |  Refills left: %d / 2", remaining, refillsLeft));

        new AlertDialog.Builder(this)
                .setTitle("Record Today's Refill")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String litresStr = etLitres.getText().toString().trim();
                    if (litresStr.isEmpty()) {
                        Toast.makeText(this, "Please enter litres filled", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    float litres;
                    try {
                        litres = Float.parseFloat(litresStr);
                        if (litres <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    QuotaManager.RefillResult result = qm.recordRefill(litres);
                    switch (result) {
                        case SUCCESS:
                            String msg = String.format(
                                    "✅ Refill recorded: %.0f L\nRemaining: %.0f L  |  Refills left: %d",
                                    litres, qm.getRemainingLitres(), qm.getRemainingRefills());
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                            refreshUI();
                            break;
                        case ALREADY_USED_MAX:
                            Toast.makeText(this,
                                    "You have already used both refills this 7-day window.",
                                    Toast.LENGTH_LONG).show();
                            break;
                        case EXCEEDS_QUOTA:
                            Toast.makeText(this,
                                    String.format("Amount exceeds remaining quota of %.0f L", remaining),
                                    Toast.LENGTH_LONG).show();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleChatQuestion() {
        String question = etChatQuestion.getText().toString().trim().toLowerCase();
        tvChatAnswer.setVisibility(View.VISIBLE);

        if (question.isEmpty()) {
            tvChatAnswer.setText("Please type a question first.");
            return;
        }

        // Keyword-based answers
        if (question.contains("when") || question.contains("refuel") || question.contains("refill")
                || question.contains("quota") || question.contains("status")) {
            tvChatAnswer.setText(qm.getRefuelStatusMessage());
        } else if (question.contains("how much") || question.contains("litre") || question.contains("liter")
                || question.contains("remaining") || question.contains("left")) {
            tvChatAnswer.setText(String.format(
                    "Remaining quota: %.0f L\nUsed so far: %.0f L\nTotal quota: %.0f L",
                    qm.getRemainingLitres(), qm.getUsedLitres(), qm.getTotalQuota()));
        } else if (question.contains("day") || question.contains("reset") || question.contains("window")) {
            int days = qm.getDaysUntilReset();
            if (days < 0) {
                tvChatAnswer.setText("No active 7-day window yet. Refuel once to start your window.");
            } else if (days == 0) {
                tvChatAnswer.setText("Your quota window resets TODAY!");
            } else {
                tvChatAnswer.setText("Your 7-day quota window resets in " + days + " day(s).");
            }
        } else if (question.contains("vehicle") || question.contains("car") || question.contains("name")) {
            tvChatAnswer.setText("Vehicle: " + qm.getVehicleName()
                    + "\nTotal quota: " + String.format("%.0f L", qm.getTotalQuota()));
        } else if (question.contains("remind") || question.contains("notification")) {
            tvChatAnswer.setText("Reminders are set for 7:00 PM every evening.\n"
                    + "You will be reminded the evening before your 7-day window resets,\n"
                    + "as long as you still have quota and refills remaining.");
        } else {
            // Default: show full status
            tvChatAnswer.setText(qm.getRefuelStatusMessage());
        }

        etChatQuestion.setText("");
    }
}
