package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * Игрок PUBG Mobile (Фаза 1: Мобильное управление и TPP-камера):
 * - Интеграция CharacterMotor (независимое вращение тела и расчет физики)
 * - Интеграция SpringArmCamera (орбитальная камера с коллизиями и сменой плеча)
 * - Полет на самолете C-130, свободное падение (Freefall), парашют
 * - Сбор лута, экипировка, аптечки, энергетики, вождение транспорта
 */
public final class PUBGPlayer {

    public static final int MODE_IN_PLANE = 0;
    public static final int MODE_FREEFALL = 1;
    public static final int MODE_PARACHUTE = 2;
    public static final int MODE_ON_FOOT = 3;
    public static final int MODE_DRIVING = 4;

    public int moveMode = MODE_IN_PLANE;

    // Мотор персонажа и TPP Камера
    public final CharacterMotor motor = new CharacterMotor();
    public final SpringArmCamera camera = new SpringArmCamera();

    // Быстрый доступ к координатам для совместимости
    public final Math3D.Vec3 pos = motor.position;
    public final Math3D.Vec3 vel = motor.velocity;

    public float yaw = 0;
    public float pitch = 0;

    public boolean isTPP = true;

    public float health = 100f;
    public float boost = 0f;
    public int helmetLevel = 0;
    public int vestLevel = 0;
    public int backpackLevel = 1;

    public int kills = 0;
    public boolean isDead = false;

    public final Weapon[] weapons = new Weapon[5];
    public int activeSlot = 0;

    public int firstAidCount = 0;
    public int energyDrinkCount = 0;
    public int ammo556 = 0;
    public int ammo762 = 0;
    public boolean hasPan = false;

    public float healTimer = 0f;
    public float maxHealTimer = 0f;

    public boolean isCrouching = false;
    public boolean isProning = false;
    public boolean isSprinting = false;
    public boolean isAiming = false;
    public float adsFactor = 0f;

    public float leanAngle = 0f;

    public float fallSpeed = 0f;
    public float altitude = 180f;

    public Vehicle3D currentVehicle = null;

    public float recoilPitch = 0f;
    public float recoilYaw = 0f;

    public float hitMarkerTimer = 0f;
    public boolean hitMarkerHeadshot = false;
    public float damageIndicatorTimer = 0f;
    public float damageAngle = 0f;

    public PUBGPlayer() {}

    public void jumpFromPlane(Math3D.Vec3 planePos, float planeYaw) {
        moveMode = MODE_FREEFALL;
        pos.set(planePos);
        yaw = planeYaw;
        camera.yaw = planeYaw;
        camera.pitch = -25f;
        altitude = planePos.y;
        vel.set(0, -35f, 0);
        SoundSynth3D.play2D(SoundSynth3D.SOUND_EXPLOSION, 0.5f);
    }

    public void openParachute() {
        if (moveMode == MODE_FREEFALL) {
            moveMode = MODE_PARACHUTE;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_PARACHUTE_DEPLOY, 1.0f);
        }
    }

    public void update(float dt, PUBGMap map, float moveX, float moveZ, boolean sprintInput) {
        if (isDead) return;

        // Синхронизация yaw/pitch камеры и игрока
        this.yaw = camera.yaw;
        this.pitch = camera.pitch;

        if (boost > 0) {
            boost = Math.max(0, boost - dt * 1.2f);
            health = Math.min(100f, health + dt * 2.5f);
        }

        if (healTimer > 0) {
            healTimer -= dt;
            if (healTimer <= 0) {
                health = Math.min(100f, health + 75f);
                SoundSynth3D.play2D(SoundSynth3D.SOUND_VICTORY, 0.4f);
            }
        }

        Weapon activeW = getActiveWeapon();
        if (activeW != null) {
            activeW.update(dt);
            recoilPitch = Math.max(0, recoilPitch - activeW.recoilRecovery * dt);
            recoilYaw = Math3D.lerp(recoilYaw, 0, activeW.recoilRecovery * dt);
        }

        if (damageIndicatorTimer > 0) damageIndicatorTimer -= dt;
        if (hitMarkerTimer > 0) hitMarkerTimer -= dt;

        switch (moveMode) {
            case MODE_IN_PLANE:
                pos.set(map.planePos);
                break;
            case MODE_FREEFALL:
                updateFreefall(dt, map, moveX, moveZ);
                break;
            case MODE_PARACHUTE:
                updateParachute(dt, map, moveX, moveZ);
                break;
            case MODE_ON_FOOT:
                updateOnFoot(dt, map, moveX, moveZ, sprintInput);
                break;
            case MODE_DRIVING:
                updateDriving(dt, map, moveX, moveZ);
                break;
        }

        // Обновление TPP SpringArm камеры с коллизиями
        isCrouching = (motor.currentStance == CharacterMotor.STANCE_CROUCH);
        isProning = (motor.currentStance == CharacterMotor.STANCE_PRONE);
        isSprinting = motor.isSprinting;

        camera.update(dt, pos, motor.currentHeight, isAiming, isProning, isCrouching, map);
        this.adsFactor = camera.adsFactor;

        float distToZone = Math3D.dist(pos.x, 0, pos.z, map.blueZoneX, 0, map.blueZoneZ);
        if (distToZone > map.blueZoneRadius && moveMode == MODE_ON_FOOT) {
            takeDamage(5.5f * dt * map.zonePhase, null);
        }
    }

    private void updateFreefall(float dt, PUBGMap map, float moveX, float moveZ) {
        float targetSpeed = (camera.pitch < -40f) ? 234f : ((camera.pitch < -15f) ? 180f : 135f);
        fallSpeed = Math3D.lerp(fallSpeed, targetSpeed, dt * 4f);

        float rad = camera.yaw * Math3D.TO_RAD;
        float forwardSpd = (camera.pitch > -35f) ? 42f : 18f;

        float targetVelX = (float) Math.sin(rad) * (moveZ * forwardSpd + 20f) + (float) Math.cos(rad) * (moveX * 28f);
        float targetVelZ = (float) Math.cos(rad) * (moveZ * forwardSpd + 20f) - (float) Math.sin(rad) * (moveX * 28f);

        vel.x = Math3D.lerp(vel.x, targetVelX, dt * 5f);
        vel.z = Math3D.lerp(vel.z, targetVelZ, dt * 5f);
        vel.y = -(fallSpeed / 3.6f);

        pos.x += vel.x * dt;
        pos.y += vel.y * dt;
        pos.z += vel.z * dt;

        float gHeight = map.getTerrainHeight(pos.x, pos.z);
        altitude = Math.max(0, pos.y - gHeight);

        if (pos.y <= gHeight + 50f) {
            openParachute();
        }
    }

    private void updateParachute(float dt, PUBGMap map, float moveX, float moveZ) {
        fallSpeed = 24f;

        float rad = camera.yaw * Math3D.TO_RAD;
        float targetVelX = (float) Math.sin(rad) * (moveZ * 14f + 8f) + (float) Math.cos(rad) * (moveX * 14f);
        float targetVelZ = (float) Math.cos(rad) * (moveZ * 14f + 8f) - (float) Math.sin(rad) * (moveX * 14f);

        vel.x = Math3D.lerp(vel.x, targetVelX, dt * 8f);
        vel.z = Math3D.lerp(vel.z, targetVelZ, dt * 8f);
        vel.y = -6.5f;

        pos.x += vel.x * dt;
        pos.y += vel.y * dt;
        pos.z += vel.z * dt;

        float gHeight = map.getTerrainHeight(pos.x, pos.z);
        altitude = Math.max(0, pos.y - gHeight);

        if (pos.y <= gHeight + 0.2f) {
            pos.y = gHeight;
            moveMode = MODE_ON_FOOT;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_STEP, 0.8f);
        }
    }

    private void updateOnFoot(float dt, PUBGMap map, float moveX, float moveZ, boolean sprintInput) {
        motor.update(dt, moveX, moveZ, camera.yaw, sprintInput, isAiming, map);

        // Сбор лута рядом
        for (LootItem item : map.loot) {
            if (!item.isTaken && Math3D.dist(pos.x, pos.y, pos.z, item.x, item.y, item.z) < 1.8f) {
                pickupItem(item);
            }
        }
    }

    private void updateDriving(float dt, PUBGMap map, float moveX, float moveZ) {
        if (currentVehicle != null) {
            currentVehicle.hasDriver = true;
            pos.set(currentVehicle.pos.x, currentVehicle.pos.y + 0.6f, currentVehicle.pos.z);
            motor.bodyYaw = currentVehicle.yaw;
        }
    }

    public void enterExitVehicle(PUBGMap map) {
        if (moveMode == MODE_DRIVING) {
            if (currentVehicle != null) currentVehicle.hasDriver = false;
            currentVehicle = null;
            moveMode = MODE_ON_FOOT;
            pos.x += 2.0f;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_DOOR_CLOSE, 0.7f);
        } else {
            for (Vehicle3D v : map.vehicles) {
                if (Math3D.dist(pos.x, pos.y, pos.z, v.pos.x, v.pos.y, v.pos.z) < 4.0f) {
                    currentVehicle = v;
                    moveMode = MODE_DRIVING;
                    SoundSynth3D.play2D(SoundSynth3D.SOUND_DOOR_OPEN, 0.8f);
                    break;
                }
            }
        }
    }

    public void interactDoors(PUBGMap map) {
        for (InteractiveDoor d : map.doors) {
            if (Math3D.dist(pos.x, pos.y, pos.z, d.hingeX, d.hingeY, d.hingeZ) < 2.5f) {
                d.toggle();
                break;
            }
        }
    }

    public void pickupItem(LootItem item) {
        item.isTaken = true;
        SoundSynth3D.play2D(SoundSynth3D.SOUND_RELOAD, 0.6f);

        switch (item.type) {
            case LootItem.TYPE_WEAPON:
                if (weapons[0] == null) weapons[0] = Weapon.create(item.subId);
                else if (weapons[1] == null) weapons[1] = Weapon.create(item.subId);
                else weapons[activeSlot] = Weapon.create(item.subId);
                break;
            case LootItem.TYPE_MEDKIT:
                firstAidCount++;
                break;
            case LootItem.TYPE_ENERGY_DRINK:
                energyDrinkCount++;
                break;
            case LootItem.TYPE_HELMET:
                helmetLevel = Math.max(helmetLevel, item.subId);
                break;
            case LootItem.TYPE_VEST:
                vestLevel = Math.max(vestLevel, item.subId);
                break;
            case LootItem.TYPE_BACKPACK:
                backpackLevel = Math.max(backpackLevel, item.subId);
                break;
            case LootItem.TYPE_PAN:
                hasPan = true;
                weapons[3] = Weapon.create(Weapon.ID_KNIFE);
                SoundSynth3D.play2D(SoundSynth3D.SOUND_PAN, 0.9f);
                break;
        }
    }

    public void useMedkit() {
        if (firstAidCount > 0 && health < 100f && healTimer <= 0) {
            firstAidCount--;
            healTimer = 5.0f;
            maxHealTimer = 5.0f;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_BANDAGE, 0.8f);
        }
    }

    public void useDrink() {
        if (energyDrinkCount > 0 && boost < 100f) {
            energyDrinkCount--;
            boost = Math.min(100f, boost + 40f);
            SoundSynth3D.play2D(SoundSynth3D.SOUND_DRINK_OPEN, 0.9f);
        }
    }

    public Weapon getActiveWeapon() {
        return weapons[activeSlot];
    }

    public void takeDamage(float dmg, Math3D.Vec3 attackerPos) {
        if (isDead) return;

        float armorMul = 1.0f - (vestLevel * 0.18f);
        dmg *= armorMul;

        health -= dmg;
        damageIndicatorTimer = 0.6f;

        if (attackerPos != null) {
            float dx = attackerPos.x - pos.x;
            float dz = attackerPos.z - pos.z;
            float angleToAttacker = (float) Math.atan2(dx, dz) * Math3D.TO_DEG;
            damageAngle = angleToAttacker - camera.yaw;
        }

        SoundSynth3D.play2D(SoundSynth3D.SOUND_BODY_HIT, 0.8f);

        if (health <= 0) {
            health = 0;
            isDead = true;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_DEFEAT, 1.0f);
        }
    }
}
