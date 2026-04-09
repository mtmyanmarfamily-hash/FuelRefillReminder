package com.myanmar.petrolreminder.ui;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.myanmar.petrolreminder.R;
import com.myanmar.petrolreminder.utils.AlarmScheduler;
import com.myanmar.petrolreminder.utils.CarStore;
import com.myanmar.petrolreminder.utils.NotificationHelper;
import com.myanmar.petrolreminder.utils.QuotaManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private CarStore   cs;
    private QuotaManager qm;

    private Spinner  spinnerCars;
    private TextView tvVehicleType;
    private TextView tvTotalQuota, tvUsedQuota, tvRemainingQuota;
    private TextView tvRefillsUsed, tvRefillsLeft, tvWindowStatus;
    private TextView tvDate1stRefill, tvDate2ndRefill, tvDateNewQuota;
    private TextView tvChatAnswer;
    private Button   btnRecordRefill, btnEditRefill, btnCheckStatus;
    private Button   btnNotifSettings, btnTestNotif, btnSettings, btnAddCar;

    private List<CarStore.Car> carList = new ArrayList<>();

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) Toast.makeText(this, "Notification ခွင့်ပြုချက် လိုအပ်သည်", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cs = new CarStore(this);
        cs.migrateFromLegacyIfNeeded();

        if (!cs.hasCars()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish(); return;
        }

        setContentView(R.layout.activity_main);
        requestNotifPermission();
        bindViews();
        setListeners();
        loadActiveCar();
    }

    @Override protected void onResume() {
        super.onResume();
        if (cs.hasCars()) { loadCarSpinner(); refreshUI(); }
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void bindViews() {
        spinnerCars      = findViewById(R.id.spinnerCars);
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
        btnAddCar        = findViewById(R.id.btnAddCar);
    }

    private void setListeners() {
        spinnerCars.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos < carList.size()) {
                    cs.setActiveCarId(carList.get(pos).id);
                    qm = new QuotaManager(MainActivity.this, carList.get(pos).id);
                    refreshUI();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        btnAddCar.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        btnRecordRefill.setOnClickListener(v -> showRefillDialog());
        btnEditRefill.setOnClickListener(v -> showEditRefillDialog());
        btnCheckStatus.setOnClickListener(v -> { tvChatAnswer.setVisibility(View.VISIBLE); tvChatAnswer.setText(qm.getRefuelStatusMessage()); });
        btnNotifSettings.setOnClickListener(v -> showNotifSettingsDialog());
        btnTestNotif.setOnClickListener(v -> { NotificationHelper.createNotificationChannel(this); showTestNotifMenu(); });
        btnSettings.setOnClickListener(v -> showCarOptionsDialog());
    }

    private void loadActiveCar() {
        loadCarSpinner();
        String activeId = cs.getActiveCarId();
        if (activeId != null) {
            qm = new QuotaManager(this, activeId);
        } else {
            qm = new QuotaManager(this, carList.get(0).id);
        }
        refreshUI();
    }

    private void loadCarSpinner() {
        carList = cs.getAllCars();
        List<String> names = new ArrayList<>();
        int activePos = 0;
        String activeId = cs.getActiveCarId();
        for (int i = 0; i < carList.size(); i++) {
            CarStore.Car c = carList.get(i);
            names.add(c.typeLabel() + "  " + c.name);
            if (c.id.equals(activeId)) activePos = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCars.setAdapter(adapter);
        spinnerCars.setSelection(activePos, false);
    }

    private void refreshUI() {
        if (qm == null) return;
        qm.checkAndResetExpiredWindow();

        CarStore.Car car = cs.getActiveCar();
        if (car != null) tvVehicleType.setText(car.typeLabel());

        tvTotalQuota.setText(String.format("%.1f L", qm.getTotalQuota()));
        tvUsedQuota.setText(String.format("%.1f L", qm.getUsedLitres()));
        tvRemainingQuota.setText(String.format("%.1f L", qm.getRemainingLitres()));
        tvRefillsUsed.setText(String.valueOf(qm.getRefillCount()));
        tvRefillsLeft.setText(String.valueOf(qm.getRemainingRefills()));

        if (!qm.isWindowActive() && qm.getRefillCount()==0) {
            tvWindowStatus.setText("Window မစသေးပါ — ဆီဖြည့်ဖို့ အဆင်သင့်");
            tvWindowStatus.setTextColor(getColor(R.color.status_green));
        } else if (qm.isWindowActive()) {
            int d = qm.getDaysUntilReset();
            if (qm.getRemainingRefills()<=0 || qm.getRemainingLitres()<=0.01f) {
                tvWindowStatus.setText("ကိုတာကုန်ပါပြီ — "+d+" ရက်နောက် ကိုတာ အသစ်");
                tvWindowStatus.setTextColor(getColor(R.color.status_red));
            } else if (d==1) {
                tvWindowStatus.setText("⚠️ မနက်ဖြန် Window ပြန်သစ်မည်!");
                tvWindowStatus.setTextColor(getColor(R.color.status_orange));
            } else {
                tvWindowStatus.setText("Window ကျန် "+d+" ရက်");
                tvWindowStatus.setTextColor(getColor(R.color.status_green));
            }
        }

        tvDate1stRefill.setText(qm.getFirstRefillDateStr());
        tvDate2ndRefill.setText(qm.getRemainingEligibleDaysStr());
        tvDateNewQuota.setText(qm.getNewQuotaDateStr());

        boolean canRecord = qm.getRemainingRefills()>0 && qm.getRemainingLitres()>0.01f;
        btnRecordRefill.setEnabled(canRecord);
        btnRecordRefill.setAlpha(canRecord ? 1f : 0.4f);
        btnEditRefill.setVisibility(qm.getRefillCount()>0 ? View.VISIBLE : View.GONE);
    }

    // ─── Record refill ────────────────────────────────────────────────────────

    private void showRefillDialog() {
        boolean isFirst = (qm.getRefillCount() == 0);
        View dv = getLayoutInflater().inflate(R.layout.dialog_refill, null);
        EditText etLitres = dv.findViewById(R.id.etRefillLitres);
        TextView tvHint   = dv.findViewById(R.id.tvRefillHint);
        Button   btnDate  = dv.findViewById(R.id.btnPickDate);
        TextView tvDate   = dv.findViewById(R.id.tvDateChosen);

        tvHint.setText(String.format("ကျန်ကိုတာ: %.1f L  |  ဖြည့်ခွင့်ကျန်: %d / 2 ကြိမ်",
                qm.getRemainingLitres(), qm.getRemainingRefills()));
        btnDate.setVisibility(View.VISIBLE);
        tvDate.setVisibility(View.VISIBLE);
        tvDate.setText("ရက်: ဒီနေ့ (default)");

        final long[] chosenMs = {0L};
        btnDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, android.R.style.Theme_Holo_Light_Dialog,
                (dp,y,m,d) -> {
                    Calendar p=Calendar.getInstance(); p.set(y,m,d,0,0,0); p.set(Calendar.MILLISECOND,0);
                    chosenMs[0]=p.getTimeInMillis();
                    tvDate.setText("ရက်: "+QuotaManager.DATE_FMT.format(p.getTime()));
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        new AlertDialog.Builder(this)
                .setTitle(isFirst ? "ပထမ ဆီဖြည့်မှတ်တမ်း" : "ဒုတိယ ဆီဖြည့်မှတ်တမ်း")
                .setView(dv)
                .setPositiveButton("သိမ်းမည်", (d,w) -> {
                    String s = etLitres.getText().toString().trim();
                    if (s.isEmpty()) { Toast.makeText(this,"လီတာ ထည့်ပါ",Toast.LENGTH_SHORT).show(); return; }
                    float litres;
                    try { litres=Float.parseFloat(s); if(litres<=0) throw new NumberFormatException(); }
                    catch(Exception e) { Toast.makeText(this,"ကိန်းဂဏန်း မှားသည်",Toast.LENGTH_SHORT).show(); return; }

                    if (isFirst && chosenMs[0]>0) qm.setWindowStartDate(chosenMs[0]);
                    if (!isFirst && chosenMs[0]>0) qm.setRefill2Date(chosenMs[0]);

                    switch (qm.recordRefill(litres)) {
                        case SUCCESS:
                            AlarmScheduler.scheduleAllAlarms(this);
                            Toast.makeText(this, String.format("✅ အကြိမ် %d — %.1f L မှတ်တမ်းတင်ပြီး\nကျန်: %.1f L",
                                qm.getRefillCount(), litres, qm.getRemainingLitres()), Toast.LENGTH_LONG).show();
                            refreshUI(); break;
                        case ALREADY_USED_MAX:
                            Toast.makeText(this,"2 ကြိမ် ပြည့်သွားပါပြီ",Toast.LENGTH_LONG).show(); break;
                        case EXCEEDS_QUOTA:
                            Toast.makeText(this,String.format("ကိုတာ %.1f L ကျော်နေသည်",qm.getRemainingLitres()),Toast.LENGTH_LONG).show(); break;
                    }
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    // ─── Edit refill ──────────────────────────────────────────────────────────

    private void showEditRefillDialog() {
        int num=qm.getLastRefillNumber(); float cur=qm.getLastRefillLitres();
        String[] opts={"✏️  အကြိမ် "+num+" လီတာ ပြင်မည် ("+String.format("%.1f",cur)+" L)",
                       "🗑️  အကြိမ် "+num+" ဖျက်မည်"};
        new AlertDialog.Builder(this).setTitle("မှတ်တမ်း ပြင်ဆင်မည်")
                .setItems(opts,(d,w)->{if(w==0)showCorrectDialog(num,cur);else confirmDelete(num);})
                .setNegativeButton("မလုပ်တော့ပါ",null).show();
    }

    private void showCorrectDialog(int num, float cur) {
        View dv=getLayoutInflater().inflate(R.layout.dialog_refill,null);
        EditText et=dv.findViewById(R.id.etRefillLitres);
        TextView th=dv.findViewById(R.id.tvRefillHint);
        dv.findViewById(R.id.btnPickDate).setVisibility(View.GONE);
        dv.findViewById(R.id.tvDateChosen).setVisibility(View.GONE);
        et.setText(String.format("%.1f",cur)); et.selectAll();
        th.setText("အကြိမ် "+num+" — မှန်ကန်သော လီတာ ထည့်ပါ");
        new AlertDialog.Builder(this).setTitle("လီတာ ပြင်မည်").setView(dv)
                .setPositiveButton("သိမ်းမည်",(d,w)->{
                    String s=et.getText().toString().trim(); if(s.isEmpty())return;
                    float nl; try{nl=Float.parseFloat(s);}catch(Exception e){return;}
                    if(qm.editLastRefill(nl)){Toast.makeText(this,"✅ "+String.format("%.1f",nl)+" L သို့ ပြင်ပြီး",Toast.LENGTH_SHORT).show();refreshUI();}
                    else Toast.makeText(this,"ကိုတာ ကျော်သည်",Toast.LENGTH_SHORT).show();
                }).setNegativeButton("မလုပ်တော့ပါ",null).show();
    }

    private void confirmDelete(int num) {
        new AlertDialog.Builder(this).setTitle("ဖျက်မည်လား?")
                .setMessage(num==1?"အကြိမ် 1 ဖျက်ပါမည်။ Window လည်း ပျောက်မည်။":"အကြိမ် 2 ဖျက်ပါမည်။")
                .setPositiveButton("ဖျက်မည်",(d,w)->{qm.deleteLastRefill();refreshUI();
                    Toast.makeText(this,"ဖျက်ပြီး",Toast.LENGTH_SHORT).show();})
                .setNegativeButton("မလုပ်တော့ပါ",null).show();
    }

    // ─── Car options ──────────────────────────────────────────────────────────

    private void showCarOptionsDialog() {
        CarStore.Car car = cs.getActiveCar();
        if (car == null) return;
        String[] opts = {
            "✏️  ကားနာမည် / ကိုတာ ပြင်ဆင်မည်",
            "🗑️  ဤကားကို ဖျက်မည်"
        };
        new AlertDialog.Builder(this).setTitle(car.name)
                .setItems(opts, (d,w) -> {
                    if (w==0) {
                        Intent i = new Intent(this, SetupActivity.class);
                        i.putExtra("edit_car_id", car.id);
                        startActivity(i);
                    } else {
                        confirmDeleteCar(car);
                    }
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    private void confirmDeleteCar(CarStore.Car car) {
        new AlertDialog.Builder(this)
                .setTitle("ကား ဖျက်မည်လား?")
                .setMessage(car.name + " နှင့် ၎င်း၏ quota data အားလုံး ဖျက်မည်။")
                .setPositiveButton("ဖျက်မည်", (d,w) -> {
                    cs.deleteCar(car.id);
                    if (!cs.hasCars()) {
                        startActivity(new Intent(this, SetupActivity.class));
                        finish();
                    } else {
                        loadActiveCar();
                    }
                })
                .setNegativeButton("မလုပ်တော့ပါ", null).show();
    }

    // ─── Notification settings ────────────────────────────────────────────────

    private void showNotifSettingsDialog() {
        boolean[] states = {qm.isNotifDayBeforeNoonEnabled(), qm.isNotifDayBeforeEveningEnabled(), qm.isNotifRefillDayMorningEnabled()};
        String[] labels  = {"☀️ မနေ့ 12:00  (တစ်ရက်အလို)","🌙 မနေ့ 18:00  (တစ်ရက်အလို)","⛽ ဖြည့်နိုင်သောနေ့ မနက် 7:00"};
        new AlertDialog.Builder(this).setTitle("🔔 Notification On/Off")
                .setMultiChoiceItems(labels, states,(d,i,c)->states[i]=c)
                .setPositiveButton("သိမ်းမည်",(d,w)->{
                    qm.setNotifDayBeforeNoon(states[0]); qm.setNotifDayBeforeEvening(states[1]); qm.setNotifRefillDayMorning(states[2]);
                    AlarmScheduler.scheduleAllAlarms(this);
                    Toast.makeText(this,"သိမ်းပြီး",Toast.LENGTH_SHORT).show();
                }).setNegativeButton("မလုပ်တော့ပါ",null).show();
    }

    private void showTestNotifMenu() {
        String[] opts={"☀️ မနေ့ မွန်းတည့် (တစ်ရက်အလို)","🌙 မနေ့ ညနေ (တစ်ရက်အလို)","⛽ ဖြည့်နိုင်သောနေ့ မနက်","🆕 ကိုတာ အသစ် မနက်ဖြန်"};
        new AlertDialog.Builder(this).setTitle("Notification စမ်းသပ်မည်")
                .setItems(opts,(d,w)->{
                    switch(w){
                        case 0: NotificationHelper.showDayBeforeNotification(this,qm,false); break;
                        case 1: NotificationHelper.showDayBeforeNotification(this,qm,true);  break;
                        case 2: NotificationHelper.showRefillDayMorningNotification(this,qm); break;
                        case 3: NotificationHelper.fireTestNewQuotaNotification(this,qm); break;
                    }
                    Toast.makeText(this,"Notification ပို့ပြီး!",Toast.LENGTH_SHORT).show();
                }).setNegativeButton("မလုပ်တော့ပါ",null).show();
    }
}
