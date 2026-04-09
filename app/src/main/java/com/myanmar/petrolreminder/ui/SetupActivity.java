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
import com.myanmar.petrolreminder.utils.CarStore;
import com.myanmar.petrolreminder.utils.NotificationHelper;
import com.myanmar.petrolreminder.utils.QuotaManager;

public class SetupActivity extends AppCompatActivity {

    private String editCarId = null; // non-null = editing existing car

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        editCarId = getIntent().getStringExtra("edit_car_id");

        EditText etVehicle = findViewById(R.id.etVehicleName);
        EditText etQuota   = findViewById(R.id.etTotalQuota);
        TextView tvHint    = findViewById(R.id.tvOddEvenHint);
        TextView tvTitle   = findViewById(R.id.tvSetupTitle);
        Button   btnSave   = findViewById(R.id.btnSaveSetup);

        CarStore cs = new CarStore(this);

        if (editCarId != null) {
            // Edit existing car
            CarStore.Car car = cs.getCarById(editCarId);
            if (car != null) {
                etVehicle.setText(car.name);
                etQuota.setText(String.format("%.1f", car.totalQuota));
                tvTitle.setText("ကား ပြင်ဆင်မည်");
                btnSave.setText("သိမ်းဆည်းမည်");
                updateHint(tvHint, car.name);
            }
        } else {
            tvTitle.setText(cs.hasCars() ? "ကား အသစ် ထည့်မည်" : "ဆီကိုတာ Setup");
        }

        etVehicle.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){ updateHint(tvHint, s.toString()); }
            public void afterTextChanged(Editable s){}
        });

        btnSave.setOnClickListener(v -> {
            String name = etVehicle.getText().toString().trim();
            String qStr = etQuota.getText().toString().trim();
            if (name.isEmpty()) { etVehicle.setError("ကားနံပတ် ထည့်သွင်းပါ"); return; }
            if (qStr.isEmpty()) { etQuota.setError("ကိုတာ လီတာ ထည့်သွင်းပါ"); return; }
            float quota;
            try { quota=Float.parseFloat(qStr); if(quota<=0) throw new NumberFormatException(); }
            catch(Exception e) { etQuota.setError("ကိန်းဂဏန်း မှားနေသည်"); return; }

            if (editCarId != null) {
                // Update existing
                cs.updateCar(editCarId, name, quota);
                Toast.makeText(this, "✅ "+name+" ပြင်ဆင်ပြီး", Toast.LENGTH_SHORT).show();
            } else {
                // Add new car
                CarStore.Car car = cs.addCar(name, quota);
                QuotaManager qm = new QuotaManager(this, car.id);
                qm.saveSetup(name, quota);
                cs.setActiveCarId(car.id);
                NotificationHelper.createNotificationChannel(this);
                AlarmScheduler.scheduleAllAlarms(this);

                boolean isEven = car.isEven();
                String vType = isEven ? "စုံကား (စုံနေ့ ဖြည့်ရမည်)" : "မကား (မနေ့ ဖြည့်ရမည်)";
                Toast.makeText(this, "✅ "+name+" ထည့်ပြီး!\n"+vType, Toast.LENGTH_LONG).show();
            }

            startActivity(new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
    }

    private void updateHint(TextView tv, String name) {
        if (name.isEmpty()) { tv.setText(""); return; }
        for (char c : name.toCharArray()) {
            if (Character.isDigit(c)) {
                int d = Character.getNumericValue(c);
                tv.setText((d%2==0)
                    ? "➡️ ဂဏန်း "+d+" (စုံ) — စုံကား | စုံနေ့မှသာ ဆီဖြည့်နိုင်"
                    : "➡️ ဂဏန်း "+d+" (မ) — မကား | မနေ့မှသာ ဆီဖြည့်နိုင်");
                return;
            }
        }
        tv.setText("ကားနံပတ်တွင် ဂဏန်း ထည့်ပါ");
    }
}
