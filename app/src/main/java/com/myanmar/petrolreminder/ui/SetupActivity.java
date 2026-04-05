package com.myanmar.petrolreminder.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
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
        Button   btnSave   = findViewById(R.id.btnSaveSetup);

        btnSave.setOnClickListener(v -> {
            String vehicleName = etVehicle.getText().toString().trim();
            String quotaStr    = etQuota.getText().toString().trim();

            if (vehicleName.isEmpty()) {
                etVehicle.setError("Please enter vehicle name");
                return;
            }
            if (quotaStr.isEmpty()) {
                etQuota.setError("Please enter your quota in litres");
                return;
            }

            float quota;
            try {
                quota = Float.parseFloat(quotaStr);
                if (quota <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                etQuota.setError("Enter a valid quota amount");
                return;
            }

            QuotaManager qm = new QuotaManager(this);
            qm.saveSetup(vehicleName, quota);

            NotificationHelper.createNotificationChannel(this);
            AlarmScheduler.scheduleDailyReminder(this);

            Toast.makeText(this, "Setup saved! Reminders scheduled at 7 PM daily.", Toast.LENGTH_LONG).show();

            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
