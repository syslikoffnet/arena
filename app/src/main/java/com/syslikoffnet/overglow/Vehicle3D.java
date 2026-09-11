package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * Управляемый транспорт PUBG Mobile (Багги / УАЗ / Джип):
 * - Физика вождения (газ, тормоз, руление, занос, сбивание врагов)
 * - Звуки двигателя и клаксона, расход топлива, прочность автомобиля
 */
public final class Vehicle3D {

    public static final int TYPE_BUGGY = 0;
    public static final int TYPE_UAZ = 1;

    public final int id;
    public final int type;
    public final Math3D.Vec3 pos = new Math3D.Vec3();
    public final Math3D.Vec3 vel = new Math3D.Vec3();

    public float yaw = 0f;
    public float steering = 0f;      // -35..+35 градусов
    public float speedKmH = 0f;       // скорость в км/ч
    public float fuel = 100f;
    public float health = 800f;
    public boolean hasDriver = false;
    public boolean destroyed = false;
    private float prevSpeedMs = 0f;

    public float wheelSpin = 0f;
    public float engineSoundTimer = 0f;

    public final Math3D.Box collider = new Math3D.Box();

    public Vehicle3D(int id, int type, float x, float y, float z, float startYaw) {
        this.id = id;
        this.type = type;
        this.pos.set(x, y, z);
        this.yaw = startYaw;
        updateCollider();
    }

    public void update(float dt, PUBGMap map, ArrayList<PUBGBot> bots, PUBGPlayer player,
                       float throttle, float steerInput, boolean handbrake) {
        // Управление рулем
        float targetSteer = steerInput * 32f;
        steering = Math3D.lerp(steering, targetSteer, dt * 8f);

        // Расчет ускорения
        float maxSpeed = (type == TYPE_BUGGY) ? 95f : 80f; // км/ч
        float accelPower = (throttle > 0 ? 18f : (throttle < 0 ? -10f : 0f));

        if (fuel > 0 && throttle != 0) {
            fuel = Math.max(0, fuel - dt * 0.4f);
        } else if (fuel <= 0) {
            accelPower = 0;
        }

        if (handbrake) {
            accelPower = 0;
            speedKmH = Math3D.lerp(speedKmH, 0, dt * 5f);
        }

        speedKmH += accelPower * dt * 3.6f;
        speedKmH = Math3D.clamp(speedKmH, -25f, maxSpeed);

        // Трение качения
        if (throttle == 0 && !handbrake) {
            speedKmH = Math3D.lerp(speedKmH, 0, dt * 1.5f);
        }

        // Поворот автомобиля во время движения
        if (Math.abs(speedKmH) > 1f) {
            float turnFactor = (speedKmH / maxSpeed) * (speedKmH > 0 ? 1f : -1f);
            yaw -= steering * turnFactor * dt * 2.8f;
        }

        // Перемещение
        float speedMS = speedKmH / 3.6f;
        float rad = yaw * Math3D.TO_RAD;
            vel.x = (float) Math.sin(rad) * speedMS;
            vel.z = (float) Math.cos(rad) * speedMS;

        pos.x += vel.x * dt;
        pos.z += vel.z * dt;

        // Следование рельефу + коллизия со стенами (упрощённый выталкивающий цилиндр)
        if (map != null) {
            float gy = map.getTerrainHeight(pos.x, pos.z) + 0.02f;
            pos.y = Math3D.lerp(pos.y, gy, Math3D.clamp(dt * 12f, 0f, 1f));
            WorldCollider.resolveCharacterCollision(pos, 1.35f, 1.4f, map);
            float nowY = map.getTerrainHeight(pos.x, pos.z) + 0.02f;
            if (pos.y < nowY) pos.y = nowY;
        } else {
            pos.y = 0f;
        }

        // Урон от резких столкновений
        float impact = Math.abs(speedMS - prevSpeedMs) / Math.max(dt, 0.001f);
        if (impact > 26f) {
            health -= impact * 1.6f;
            SoundSynth3D.playSound(SoundSynth3D.SOUND_EXPLOSION, pos.x, pos.y, pos.z,
                    player.pos, player.yaw);
            if (health <= 0 && !destroyed) {
                destroyed = true;
                health = 0;
                hasDriver = false;
            }
        }
        prevSpeedMs = speedMS;

        wheelSpin += speedMS * dt * 6f;
        updateCollider();

        // Сбивание ботов на скорости
        if (Math.abs(speedKmH) > 25f) {
            for (PUBGBot bot : bots) {
                if (!bot.isDead && collider.intersects(bot.getBodyBox())) {
                    float hitDmg = Math.abs(speedKmH) * 2.5f;
                    bot.takeDamage(hitDmg, null, player);
                    SoundSynth3D.playSound(SoundSynth3D.SOUND_HIT, pos.x, pos.y, pos.z, player.pos, player.yaw);
                }
            }
        }

        // Звук двигателя
        engineSoundTimer += dt;
        if (hasDriver && engineSoundTimer > 0.18f && Math.abs(speedKmH) > 5f) {
            engineSoundTimer = 0;
            SoundSynth3D.playSound(SoundSynth3D.SOUND_STEP, pos.x, pos.y, pos.z, player.pos, player.yaw);
        }
    }

    public void updateCollider() {
        float hw = (type == TYPE_BUGGY) ? 1.1f : 1.3f;
        float hd = (type == TYPE_BUGGY) ? 2.0f : 2.4f;
        collider.set(pos.x - hw, pos.y, pos.z - hd, pos.x + hw, pos.y + 1.6f, pos.z + hd);
    }
}
