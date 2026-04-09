package com.myanmar.petrolreminder.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages multiple cars. Each car has:
 *  - id (unique string)
 *  - name / plate number
 *  - totalQuota (litres)
 * Per-car quota data is stored in a separate SharedPreferences file named "car_<id>".
 */
public class CarStore {

    private static final String PREFS_NAME  = "car_store";
    private static final String KEY_CARS    = "cars_json";
    private static final String KEY_ACTIVE  = "active_car_id";

    private final SharedPreferences prefs;
    private final Context ctx;

    public CarStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        prefs = this.ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Car model ────────────────────────────────────────────────────────────

    public static class Car {
        public String id;
        public String name;
        public float  totalQuota;

        public Car(String id, String name, float totalQuota) {
            this.id = id; this.name = name; this.totalQuota = totalQuota;
        }

        public boolean isEven() {
            for (char c : name.toCharArray())
                if (Character.isDigit(c)) return Character.getNumericValue(c) % 2 == 0;
            return true;
        }

        public String typeLabel() { return isEven() ? "စုံကား" : "မကား"; }
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    public List<Car> getAllCars() {
        List<Car> list = new ArrayList<>();
        try {
            String json = prefs.getString(KEY_CARS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Car(o.getString("id"), o.getString("name"), (float) o.getDouble("quota")));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void saveCars(List<Car> cars) {
        try {
            JSONArray arr = new JSONArray();
            for (Car c : cars) {
                JSONObject o = new JSONObject();
                o.put("id", c.id); o.put("name", c.name); o.put("quota", c.totalQuota);
                arr.put(o);
            }
            prefs.edit().putString(KEY_CARS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public Car addCar(String name, float quota) {
        String id = "car_" + System.currentTimeMillis();
        Car car = new Car(id, name, quota);
        List<Car> cars = getAllCars();
        cars.add(car);
        saveCars(cars);
        if (getActiveCarId() == null) setActiveCarId(id);
        return car;
    }

    public void updateCar(String id, String name, float quota) {
        List<Car> cars = getAllCars();
        for (Car c : cars) {
            if (c.id.equals(id)) { c.name = name; c.totalQuota = quota; break; }
        }
        saveCars(cars);
        // Update quota in QuotaManager for this car too
        QuotaManager qm = new QuotaManager(ctx, id);
        qm.updateTotalQuota(name, quota);
    }

    public void deleteCar(String id) {
        List<Car> cars = getAllCars();
        cars.removeIf(c -> c.id.equals(id));
        saveCars(cars);
        // Clear car prefs
        ctx.getSharedPreferences("car_" + id, Context.MODE_PRIVATE).edit().clear().apply();
        // If deleted car was active, switch to first available
        if (id.equals(getActiveCarId())) {
            setActiveCarId(cars.isEmpty() ? null : cars.get(0).id);
        }
    }

    public Car getCarById(String id) {
        for (Car c : getAllCars()) if (c.id.equals(id)) return c;
        return null;
    }

    // ─── Active car ───────────────────────────────────────────────────────────

    public String getActiveCarId() { return prefs.getString(KEY_ACTIVE, null); }

    public void setActiveCarId(String id) { prefs.edit().putString(KEY_ACTIVE, id).apply(); }

    public Car getActiveCar() {
        String id = getActiveCarId();
        if (id == null) return null;
        return getCarById(id);
    }

    public boolean hasCars() { return !getAllCars().isEmpty(); }

    // ─── Migration from old single-car setup ──────────────────────────────────

    /**
     * If user had old single-car data, migrate it into the new multi-car system.
     */
    public void migrateFromLegacyIfNeeded() {
        if (hasCars()) return; // already migrated
        SharedPreferences old = ctx.getSharedPreferences("petrol_prefs", Context.MODE_PRIVATE);
        if (!old.getBoolean("setup_done", false)) return;
        String name  = old.getString("vehicle_name", "My Vehicle");
        float  quota = old.getFloat("total_quota_litres", 0f);
        if (quota <= 0) return;

        // Create car with legacy id
        String legacyId = "car_legacy";
        Car car = new Car(legacyId, name, quota);
        List<Car> cars = new ArrayList<>();
        cars.add(car);
        saveCars(cars);
        setActiveCarId(legacyId);

        // Copy old quota data into new car prefs
        SharedPreferences newPrefs = ctx.getSharedPreferences("car_" + legacyId, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = newPrefs.edit();
        ed.putString("vehicle_name", name);
        ed.putFloat("total_quota_litres", quota);
        ed.putBoolean("setup_done", true);
        if (old.contains("window_start_ms")) ed.putLong("window_start_ms", old.getLong("window_start_ms", 0));
        if (old.contains("refill_count"))    ed.putInt("refill_count",     old.getInt("refill_count", 0));
        if (old.contains("refill_1_litres")) ed.putFloat("refill_1_litres", old.getFloat("refill_1_litres", 0));
        if (old.contains("refill_2_litres")) ed.putFloat("refill_2_litres", old.getFloat("refill_2_litres", 0));
        ed.apply();
    }
}
