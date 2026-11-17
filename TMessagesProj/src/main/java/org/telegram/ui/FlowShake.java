package org.telegram.ui;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.widget.Toast;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.FlowConfig;

/** FlowGram: shake the phone to toggle full ghost mode. Active only while the app is in the foreground. */
public class FlowShake implements SensorEventListener {

    private static FlowShake instance;
    private static final float THRESHOLD_G = 2.6f;
    private static final long COOLDOWN_MS = 1500;

    private final Context context;
    private SensorManager sensorManager;
    private long lastShake;

    private FlowShake(Context context) {
        this.context = context.getApplicationContext();
    }

    public static void onResume(Context context) {
        if (!FlowConfig.shakeGhost || context == null) {
            onPause();
            return;
        }
        if (instance == null) {
            instance = new FlowShake(context);
        }
        instance.start();
    }

    public static void onPause() {
        if (instance != null) {
            instance.stop();
        }
    }

    private void start() {
        try {
            if (sensorManager == null) {
                sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            }
            Sensor sensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
            if (sensor != null) {
                sensorManager.unregisterListener(this);
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void stop() {
        try {
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float gx = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gy = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gz = event.values[2] / SensorManager.GRAVITY_EARTH;
        double g = Math.sqrt(gx * gx + gy * gy + gz * gz);
        long now = SystemClock.elapsedRealtime();
        if (g < THRESHOLD_G || now - lastShake < COOLDOWN_MS) {
            return;
        }
        lastShake = now;
        FlowConfig.toggleGhostModeFull();
        boolean on = FlowConfig.isGhostModeFull();
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(on ? 120 : 40);
            }
        } catch (Exception ignore) {
        }
        Toast.makeText(context, on ? "👻 Режим призрака включён" : "Режим призрака выключен", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
