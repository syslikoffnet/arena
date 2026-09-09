package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * Игрок от 1-го лица: физика движения (ходьба, спринт, присед, прыжок),
 * отдача оружия, прицеливание (ADS), рейкасты стрельбы и хедшоты.
 */
public final class Player {

    public final Math3D.Vec3 pos = new Math3D.Vec3();
    public final Math3D.Vec3 vel = new Math3D.Vec3();

    public float yaw = 0;       // поворот по горизонтали (градусы)
    public float pitch = 0;     // наклон вверх/вниз (-89..89)

    public float health = 100f;
    public float armor = 100f;
    public int team = 0;        // 0 = T (Красные), 1 = CT (Синие)
    public boolean isDead = false;
    public int kills = 0;
    public int deaths = 0;
    public int score = 0;

    // Стойка и движение
    public boolean isCrouching = false;
    public boolean isSprinting = false;
    public boolean isGrounded = true;
    public float eyeHeight = 1.7f;
    public float targetEyeHeight = 1.7f;

    // Оружие и инвентарь (4 слота)
    public final Weapon[] inventory = new Weapon[4];
    public int activeSlot = 0;

    // Прицеливание (ADS)
    public boolean isAiming = false;
    public float adsFactor = 0f; // 0..1 плавный переход

    // Отдача ствола (смещение камеры и viewmodel)
    public float recoilPitchOffset = 0;
    public float recoilYawOffset = 0;

    // Анимация шага / покачивания оружия (bobbing)
    public float walkCycle = 0;
    public float bobX = 0, bobY = 0;

    // Таймер попадания по нам (для красного индикатора урона)
    public float damageIndicatorTimer = 0;
    public float damageAttackerAngle = 0;

    // Хитмаркер (попадание по врагу)
    public float hitMarkerTimer = 0;
    public boolean hitMarkerHeadshot = false;

    public Player() {
        resetInventory();
    }

    public void resetInventory() {
        inventory[0] = Weapon.create(Weapon.ID_AKR);     // Основное
        inventory[1] = Weapon.create(Weapon.ID_DEAGLE);  // Вторичное
        inventory[2] = Weapon.create(Weapon.ID_KNIFE);   // Нож
        inventory[3] = Weapon.create(Weapon.ID_GRENADE); // Граната
        activeSlot = 0;
    }

    public Weapon getActiveWeapon() {
        return inventory[activeSlot];
    }

    public void switchSlot(int slot) {
        if (slot >= 0 && slot < inventory.length && inventory[slot] != null) {
            activeSlot = slot;
            getActiveWeapon().fireTimer = 0.25f; // небольшая задержка доставания
            SoundSynth3D.play2D(SoundSynth3D.SOUND_RELOAD, 0.4f);
        }
    }

    public void spawn(float x, float y, float z, float startYaw) {
        pos.set(x, y, z);
        vel.set(0, 0, 0);
        yaw = startYaw;
        pitch = 0;
        health = 100f;
        armor = 100f;
        isDead = false;
        recoilPitchOffset = 0;
        recoilYawOffset = 0;
        for (Weapon w : inventory) {
            if (w != null) {
                w.ammoInMag = w.maxMag;
                w.ammoReserve = w.maxReserve;
                w.reloadTimer = 0;
            }
        }
    }

    public void update(float dt, Map3D map, float moveX, float moveZ) {
        if (isDead) return;

        // Плавный переход приседа
        targetEyeHeight = isCrouching ? 1.0f : 1.7f;
        eyeHeight = Math3D.lerp(eyeHeight, targetEyeHeight, dt * 12f);

        // Плавный ADS
        float targetAds = isAiming ? 1.0f : 0f;
        adsFactor = Math3D.lerp(adsFactor, targetAds, dt * 14f);

        // Стабилизация отдачи
        Weapon current = getActiveWeapon();
        if (current != null) {
            current.update(dt);
            recoilPitchOffset = Math.max(0, recoilPitchOffset - current.recoilRecovery * dt);
            recoilYawOffset = Math3D.lerp(recoilYawOffset, 0, current.recoilRecovery * dt);
        }

        // Движение относительно угла поворота камеры
        float rad = yaw * Math3D.TO_RAD;
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);

        float speed = isCrouching ? 3.5f : (isSprinting ? 9.5f : 6.2f);
        if (isAiming) speed *= 0.6f;

        float forwardX = -sin * moveZ;
        float forwardZ = cos * moveZ;
        float strafeX = cos * moveX;
        float strafeZ = sin * moveX;

        float targetVx = (forwardX + strafeX) * speed;
        float targetVz = (forwardZ + strafeZ) * speed;

        vel.x = Math3D.lerp(vel.x, targetVx, dt * 14f);
        vel.z = Math3D.lerp(vel.z, targetVz, dt * 14f);

        // Гравитация
        vel.y -= 18.0f * dt;

        // Покачивание оружия при ходьбе
        float moveLen = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (moveLen > 0.1f && isGrounded) {
            walkCycle += dt * (isSprinting ? 14f : 9f);
            bobX = (float) Math.cos(walkCycle) * 0.025f * (1f - adsFactor * 0.8f);
            bobY = (float) Math.abs(Math.sin(walkCycle)) * 0.025f * (1f - adsFactor * 0.8f);
            if (Math.sin(walkCycle) < -0.95f) {
                SoundSynth3D.playSound(SoundSynth3D.SOUND_STEP, pos.x, pos.y, pos.z, pos, yaw);
            }
        } else {
            bobX = Math3D.lerp(bobX, 0, dt * 10f);
            bobY = Math3D.lerp(bobY, 0, dt * 10f);
        }

        // Физика и коллизии
        applyMovement(dt, map);

        // Таймеры HUD
        if (damageIndicatorTimer > 0) damageIndicatorTimer -= dt;
        if (hitMarkerTimer > 0) hitMarkerTimer -= dt;

        // Урон от зоны в Battle Royale
        if (map.id == Map3D.MAP_BATTLE_ISLAND) {
            float distFromCenter = Math3D.dist(pos.x, 0, pos.z, map.zoneCenterX, 0, map.zoneCenterZ);
            if (distFromCenter > map.zoneRadius) {
                takeDamage(8f * dt, null); // урон радиации
            }
        }
    }

    private void applyMovement(float dt, Map3D map) {
        float nextX = pos.x + vel.x * dt;
        float nextY = pos.y + vel.y * dt;
        float nextZ = pos.z + vel.z * dt;

        float radius = 0.45f;
        float height = isCrouching ? 1.2f : 1.8f;

        // Коллизии по X
        Math3D.Box boxX = new Math3D.Box(nextX - radius, pos.y, pos.z - radius,
                                         nextX + radius, pos.y + height, pos.z + radius);
        boolean collideX = false;
        for (Map3D.Obstacle obs : map.obstacles) {
            if (obs.box.intersects(boxX)) {
                collideX = true;
                break;
            }
        }
        if (!collideX) pos.x = nextX;
        else vel.x = 0;

        // Коллизии по Z
        Math3D.Box boxZ = new Math3D.Box(pos.x - radius, pos.y, nextZ - radius,
                                         pos.x + radius, pos.y + height, nextZ + radius);
        boolean collideZ = false;
        for (Map3D.Obstacle obs : map.obstacles) {
            if (obs.box.intersects(boxZ)) {
                collideZ = true;
                break;
            }
        }
        if (!collideZ) pos.z = nextZ;
        else vel.z = 0;

        // Коллизии по Y (земля и потолок)
        if (nextY <= 0) {
            pos.y = 0;
            vel.y = 0;
            isGrounded = true;
        } else {
            Math3D.Box boxY = new Math3D.Box(pos.x - radius, nextY, pos.z - radius,
                                             pos.x + radius, nextY + height, pos.z + radius);
            boolean collideY = false;
            for (Map3D.Obstacle obs : map.obstacles) {
                if (obs.box.intersects(boxY)) {
                    if (vel.y < 0) { // приземление на ящик
                        pos.y = obs.box.maxY;
                        vel.y = 0;
                        isGrounded = true;
                    } else { // удар головой
                        vel.y = 0;
                    }
                    collideY = true;
                    break;
                }
            }
            if (!collideY) {
                pos.y = nextY;
                isGrounded = false;
            }
        }
    }

    public void jump() {
        if (isGrounded) {
            vel.y = 6.8f;
            isGrounded = false;
        }
    }

    public void takeDamage(float dmg, Math3D.Vec3 attackerPos) {
        if (isDead) return;
        if (armor > 0) {
            float absorbed = dmg * 0.45f;
            armor = Math.max(0, armor - absorbed);
            dmg -= absorbed;
        }
        health -= dmg;
        damageIndicatorTimer = 0.6f;

        if (attackerPos != null) {
            float dx = attackerPos.x - pos.x;
            float dz = attackerPos.z - pos.z;
            float angleToAttacker = (float) Math.atan2(dx, dz) * Math3D.TO_DEG;
            damageAttackerAngle = angleToAttacker - yaw;
        }

        SoundSynth3D.play2D(SoundSynth3D.SOUND_HIT, 0.7f);

        if (health <= 0) {
            health = 0;
            isDead = true;
            deaths++;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_DEFEAT, 0.9f);
        }
    }
}
