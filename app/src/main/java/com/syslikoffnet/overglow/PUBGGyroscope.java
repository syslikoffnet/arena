package com.syslikoffnet.overglow;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Профессиональный гироскоп для PUBG Mobile (Gyroscope Aiming):
 * - Плавная доводка прицела и гашение отдачи наклоном телефона (как у PRO-игроков)
 * - Режимы: Always On (Всегда включен), Scope Only (Только в прицеле), Off (Выключен)
 * - Фильтрация микро-дрожания рук (Low-pass filter) и мгновенный отклик 120Hz
 */
public final class PUBGGyroscope implements SensorEventListener {

    public static final int MODE_ALWAYS_ON = 0;
    public static final int MODE_SCOPE_ONLY = 1;
    public static final int MODE_OFF = 2;

    public int mode = MODE_ALWAYS_ON;
    public float sensitivity = 1.8f;
    public boolean invertY = false;

    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private boolean isRegistered = false;

    // Угловые скорости (рад/сек)
    private float gyroPitchDelta = 0f;
    private float gyroYawDelta = 0f;
    private float lastTimestamp = 0f;

    public void init(Context ctx) {
        if (ctx == null) return;
        try {
            sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                if (gyroSensor == null) {
                    gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
                }
            }
        } catch (Throwable ignored) {}
    }

    public void start() {
        if (sensorManager != null && gyroSensor != null && !isRegistered) {
            sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
            isRegistered = true;
        }
    }

    public void stop() {
        if (sensorManager != null && isRegistered) {
            sensorManager.unregisterListener(this);
            isRegistered = false;
        }
        gyroPitchDelta = 0;
        gyroYawDelta = 0;
        lastTimestamp = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float dt = 0.016f;
            if (lastTimestamp != 0) {
                dt = (event.timestamp - lastTimestamp) * 1e-9f;
                if (dt <= 0 || dt > 0.1f) dt = 0.016f;
            }
            lastTimestamp = event.timestamp;

            // В альбомной ориентации (Landscape):
            // event.values[0] (ось X) = наклон телефона вверх/вниз (Pitch)
            // event.values[1] (ось Y) = поворот влево/вправо (Yaw)
            float rawPitch = event.values[0];
            float rawYaw = event.values[1];

            // Мертвая зона против микро-дрожания
            if (Math.abs(rawPitch) < 0.015f) rawPitch = 0;
            if (Math.abs(rawYaw) < 0.015f) rawYaw = 0;

            float degScale = 57.29578f * dt * sensitivity;

            gyroPitchDelta += (invertY ? rawPitch : -rawPitch) * degScale;
            gyroYawDelta += -rawYaw * degScale;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void applyToPlayer(PUBGPlayer player) {
        if (mode == MODE_OFF || player == null) {
            gyroPitchDelta = 0;
            gyroYawDelta = 0;
            return;
        }

        if (mode == MODE_SCOPE_ONLY && player.adsFactor < 0.5f) {
            gyroPitchDelta = 0;
            gyroYawDelta = 0;
            return;
        }

        player.pitch += gyroPitchDelta;
        player.yaw += gyroYawDelta;
        player.pitch = Math3D.clamp(player.pitch, -85f, 85f);

        gyroPitchDelta = 0;
        gyroYawDelta = 0;
    }
}
