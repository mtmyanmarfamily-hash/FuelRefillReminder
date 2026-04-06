package com.myanmar.petrolreminder.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.myanmar.petrolreminder.R;
import com.myanmar.petrolreminder.utils.AlarmScheduler;
import com.myanmar.petrolreminder.utils.NotificationHelper;
import com.myanmar.petrolreminder.utils.QuotaManager;

public class SetupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        EditText etVehicle = findViewById(R.id.etVehicleName);
        EditText etQuota   = findViewById(R.id.etTotalQuota);
        TextView tvHint    = findViewById(R.id.tvOddEvenHint);
        Button   btnSave   = findViewById(R.id.btnSaveSetup);

        // Pre-fill if already set up
        QuotaManager qm = new QuotaManager(this);
        if (qm.isSetupDone()) {
            etVehicle.setText(qm.getVehicleName());
            etQuota.setText(String.format("%.0f", qm.getTotalQuota()));
            updateHint(tvHint, qm.getVehicleName());
        }

        // Live odd/even hint as user types plate number
        etVehicle.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                updateHint(tvHint, s.toString());
            }
            public void afterTextChanged(Editable s) {}
        });

        btnSave.setOnClickListener(v -> {
            String vehicleName = etVehicle.getText().toString().trim();
            String quotaStr    = etQuota.getText().toString().trim();

            if (vehicleName.isEmpty()) { etVehicle.setError("ကားနံပတ် ထည့်သွင်းပါ"); return; }
            if (quotaStr.isEmpty())    { etQuota.setError("ကိုတာ လီတာ ထည့်သွင်းပါ"); return; }

            float quota;
            try { quota = Float.parseFloat(quotaStr); if (quota <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException e) { etQuota.setError("ကိန်းဂဏန်း မှားနေသည်"); return; }

            qm.saveSetup(vehicleName, quota);
            NotificationHelper.createNotificationChannel(this);
            AlarmScheduler.scheduleAllAlarms(this);

            // Detect odd/even for confirmation message
            boolean isEven = false;
            for (char c : vehicleName.toCharArray()) {
                if (Character.isDigit(c)) {
                    isEven = (Character.getNumericValue(c) % 2 == 0);
                    break;
                }
            }
            String vType = isEven ? "စုံကား (စုံနေ့ ဖြည့်ရမည်)" : "မကား (မနေ့ ဖြည့်ရမည်)";
            Toast.makeText(this,
                "✅ သိမ်းဆည်းပြီး!\n" + vType + "\nည ၇ နာရီ reminder တပ်ပြီးပါပြီ။",
                Toast.LENGTH_LONG).show();

            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void updateHint(TextView tvHint, String vehicleName) {
        if (vehicleName.isEmpty()) { tvHint.setText(""); return; }
        for (char c : vehicleName.toCharArray()) {
            if (Character.isDigit(c)) {
                int digit = Character.getNumericValue(c);
                boolean even = (digit % 2 == 0);
                tvHint.setText(even
                    ? "➡️ ဂဏန်း " + digit + " (စုံ) — စုံကား | စုံနေ့မှသာ ဆီဖြည့်နိုင်သည်"
                    : "➡️ ဂဏန်း " + digit + " (မ) — မကား | မနေ့မှသာ ဆီဖြည့်နိုင်သည်");
                return;
            }
        }
        tvHint.setText("ကားနံပတ်တွင် ဂဏန်း ထည့်ပါ");
    }
}
