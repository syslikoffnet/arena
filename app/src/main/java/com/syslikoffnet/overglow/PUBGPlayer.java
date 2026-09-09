package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * Игрок PUBG Mobile (Unreal Engine 4 Architecture):
 * - USpringArmComponent & Orbit Camera System
 * - Полет на самолете C-130, свободное падение (Freefall), парашют
 * - Вид от 3-го лица (TPP) и 1-го лица (FPP), кнопка свободного обзора ("Глаз")
 * - Вождение транспорта, открытие дверей, сбор лута на полу
 * - Рюкзак, шлем, бронежилет, аптечки, энергетики, сковорода на пояснице
 */
public final class PUBGPlayer {

    public static final int MODE_IN_PLANE = 0;
    public static final int MODE_FREEFALL = 1;
    public static final int MODE_PARACHUTE = 2;
    public static final int MODE_ON_FOOT = 3;
    public static final int MODE_DRIVING = 4;

    public int moveMode = MODE_IN_PLANE;

    public final Math3D.Vec3 pos = new Math3D.Vec3();
    public final Math3D.Vec3 vel = new Math3D.Vec3();

    public float yaw = 0;
    public float pitch = 0;

    public float eyeYawOffset = 0;
    public float eyePitchOffset = 0;
    public boolean isFreeLooking = false;

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
        pitch = 30f;
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

    public void update(float dt, PUBGMap map, float moveX, float moveZ) {
        if (isDead) return;

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

        float targetAds = isAiming ? 1.0f : 0f;
        adsFactor = Math3D.lerp(adsFactor, targetAds, dt * 14f);

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
                updateOnFoot(dt, map, moveX, moveZ);
                break;
            case MODE_DRIVING:
                updateDriving(dt, map, moveX, moveZ);
                break;
        }

        float distToZone = Math3D.dist(pos.x, 0, pos.z, map.blueZoneX, 0, map.blueZoneZ);
        if (distToZone > map.blueZoneRadius && moveMode == MODE_ON_FOOT) {
            takeDamage(5.5f * dt * map.zonePhase, null);
        }
    }

    private void updateFreefall(float dt, PUBGMap map, float moveX, float moveZ) {
        float speed = (pitch > 45f) ? 220f : 160f;
        fallSpeed = speed;

        float rad = yaw * Math3D.TO_RAD;
        vel.x = -(float) Math.sin(rad) * (moveZ * 20f);
        vel.z = (float) Math.cos(rad) * (moveZ * 20f);
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
        fallSpeed = 25f;

        float rad = yaw * Math3D.TO_RAD;
        vel.x = -(float) Math.sin(rad) * 12f;
        vel.z = (float) Math.cos(rad) * 12f;
        vel.y = -7.5f;

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

    private void updateOnFoot(float dt, PUBGMap map, float moveX, float moveZ) {
        float speed = isProning ? 1.8f : (isCrouching ? 3.5f : (isSprinting ? 8.2f : 5.4f));
        if (isAiming) speed *= 0.6f;

        float rad = yaw * Math3D.TO_RAD;
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);

        float forwardX = -sin * moveZ;
        float forwardZ = cos * moveZ;
        float strafeX = cos * moveX;
        float strafeZ = sin * moveX;

        vel.x = Math3D.lerp(vel.x, (forwardX + strafeX) * speed, dt * 14f);
        vel.z = Math3D.lerp(vel.z, (forwardZ + strafeZ) * speed, dt * 14f);
        vel.y -= 18.0f * dt;

        pos.x += vel.x * dt;
        pos.z += vel.z * dt;
        pos.y += vel.y * dt;

        float gHeight = map.getTerrainHeight(pos.x, pos.z);
        if (pos.y <= gHeight) {
            pos.y = gHeight;
            vel.y = 0;
        }

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
            yaw = currentVehicle.yaw;
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
            damageAngle = angleToAttacker - yaw;
        }

        SoundSynth3D.play2D(SoundSynth3D.SOUND_BODY_HIT, 0.8f);

        if (health <= 0) {
            health = 0;
            isDead = true;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_DEFEAT, 1.0f);
        }
    }
}
