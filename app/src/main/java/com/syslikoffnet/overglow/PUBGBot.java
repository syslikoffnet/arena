package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * 3D Бот для PUBG Mobile (Фаза 2: Баллистика и перестрелки):
 * - Стрельба с физической баллистикой через BallisticsSystem
 * - Прыгает с парашютом, лутается, двигается в Белую Зону
 * - Использует укрытия, перезаряжается, оставляет ящик смерти
 */
public final class PUBGBot {

    public final int id;
    public final String name;
    public final Math3D.Vec3 pos = new Math3D.Vec3();
    public final Math3D.Vec3 vel = new Math3D.Vec3();

    public float yaw = 0f;
    public float pitch = 0f;

    public float health = 100f;
    public boolean isDead = false;
    public boolean isParachuting = true;

    public Weapon weapon;
    public int helmetLevel = 1;
    public int vestLevel = 1;

    public final Math3D.Vec3 targetPos = new Math3D.Vec3();
    public float stateTimer = 0f;
    public float strafeTimer = 0f;
    public float strafeDir = 1f;
    public float legAngle = 0f;

    public PUBGBot(int id, String name, float dropX, float dropZ) {
        this.id = id;
        this.name = name;
        this.pos.set(dropX, 150f, dropZ);
        this.targetPos.set(dropX + (float) ((Math.random() - 0.5) * 40), 0, dropZ + (float) ((Math.random() - 0.5) * 40));
        this.weapon = Weapon.create(id % 3 == 0 ? Weapon.ID_AWM : (id % 2 == 0 ? Weapon.ID_AKR : Weapon.ID_M4));
    }

    public Math3D.AABB getHeadBox() {
        return new Math3D.AABB(pos.x - 0.25f, pos.y + 1.45f, pos.z - 0.25f,
                pos.x + 0.25f, pos.y + 1.85f, pos.z + 0.25f);
    }

    public Math3D.AABB getBodyBox() {
        return new Math3D.AABB(pos.x - 0.35f, pos.y, pos.z - 0.35f,
                pos.x + 0.35f, pos.y + 1.45f, pos.z + 0.35f);
    }

    public void update(float dt, PUBGMap map, PUBGPlayer player, ArrayList<PUBGBot> allBots,
                       ParticleSystem particles, BallisticsSystem ballistics) {
        if (isDead) return;

        float gHeight = map.getTerrainHeight(pos.x, pos.z);

        if (isParachuting) {
            pos.y -= 14f * dt;
            if (pos.y <= gHeight) {
                pos.y = gHeight;
                isParachuting = false;
            }
            return;
        }

        weapon.update(dt);
        stateTimer -= dt;
        strafeTimer -= dt;

        // Поиск ближайшей цели (игрок или другие боты)
        Math3D.Vec3 enemyPos = null;
        boolean targetIsPlayer = false;

        if (!player.isDead && player.moveMode != PUBGPlayer.MODE_IN_PLANE) {
            float distToPlayer = Math3D.dist(pos.x, pos.y, pos.z, player.pos.x, player.pos.y, player.pos.z);
            if (distToPlayer < 75f) {
                enemyPos = player.pos;
                targetIsPlayer = true;
            }
        }

        if (enemyPos == null) {
            float closest = 60f;
            for (PUBGBot other : allBots) {
                if (other != this && !other.isDead && !other.isParachuting) {
                    float d = Math3D.dist(pos.x, pos.y, pos.z, other.pos.x, other.pos.y, other.pos.z);
                    if (d < closest) {
                        closest = d;
                        enemyPos = other.pos;
                        targetIsPlayer = false;
                    }
                }
            }
        }

        // Логика боя или движения в Зону
        if (enemyPos != null) {
            engage(dt, enemyPos, targetIsPlayer, player, allBots, particles, ballistics);
        } else {
            moveToSafeZone(dt, map);
        }

        // Гравитация и физика
        vel.y -= 18f * dt;
        pos.x += vel.x * dt;
        pos.z += vel.z * dt;
        pos.y += vel.y * dt;

        if (pos.y <= gHeight) {
            pos.y = gHeight;
            vel.y = 0;
        }

        WorldCollider.resolveCharacterCollision(pos, 0.38f, 1.8f, map);

        float hSpeed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (hSpeed > 0.1f) legAngle += dt * 10f;

        // Урон от Синей зоны
        float distToZone = Math3D.dist(pos.x, 0, pos.z, map.blueZoneX, 0, map.blueZoneZ);
        if (distToZone > map.blueZoneRadius) {
            takeDamage(5.0f * dt * map.zonePhase, null, player);
        }
    }

    private void engage(float dt, Math3D.Vec3 enemyPos, boolean targetIsPlayer,
                        PUBGPlayer player, ArrayList<PUBGBot> allBots,
                        ParticleSystem particles, BallisticsSystem ballistics) {
        float dx = enemyPos.x - pos.x;
        float dz = enemyPos.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) Math.atan2(dx, dz) * Math3D.TO_DEG;
        yaw = Math3D.lerpAngle(yaw, targetYaw, dt * 7f);

        float dy = (enemyPos.y + 1.2f) - (pos.y + 1.4f);
        pitch = (float) Math.atan2(dy, dist) * Math3D.TO_DEG;

        if (strafeTimer <= 0) {
            strafeDir = (Math.random() > 0.5) ? 1f : -1f;
            strafeTimer = (float) (Math.random() * 1.5 + 0.5);
        }

        float rad = yaw * Math3D.TO_RAD;
        vel.x = (float) Math.cos(rad) * strafeDir * 2.8f;
        vel.z = (float) Math.sin(rad) * strafeDir * 2.8f;

        if (weapon.canFire()) {
            weapon.fireTimer = weapon.fireInterval + (float) (Math.random() * 0.12f);
            weapon.ammoInMag--;

            SoundSynth3D.play2D(SoundSynth3D.SOUND_AKM, 0.7f);

            float radY = yaw * Math3D.TO_RAD;
            float radP = pitch * Math3D.TO_RAD;

            float dirX = (float) Math.sin(radY) * (float) Math.cos(radP);
            float dirY = (float) Math.sin(radP);
            float dirZ = (float) Math.cos(radY) * (float) Math.cos(radP);

            if (!weapon.hideFlash()) {
                particles.triggerMuzzleFlash(pos.x + dirX * 0.7f, pos.y + 1.4f, pos.z + dirZ * 0.7f);
            }

            // Выстрел через физический баллистический движок
            if (ballistics != null) {
                ballistics.spawnBullet(pos.x, pos.y + 1.4f, pos.z, dirX, dirY, dirZ, weapon, false, this);
            }
        } else if (weapon.ammoInMag <= 0 && weapon.reloadTimer <= 0) {
            weapon.startReload();
        }
    }

    private void moveToSafeZone(float dt, PUBGMap map) {
        float dx = map.whiteZoneX - pos.x;
        float dz = map.whiteZoneZ - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        if (dist > 10f) {
            float targetYaw = (float) Math.atan2(dx, dz) * Math3D.TO_DEG;
            yaw = Math3D.lerpAngle(yaw, targetYaw, dt * 5f);

            float rad = yaw * Math3D.TO_RAD;
            vel.x = (float) Math.sin(rad) * 4.2f;
            vel.z = (float) Math.cos(rad) * 4.2f;
        } else {
            vel.x = 0;
            vel.z = 0;
        }
    }

    // Атрибуция убийства для килл-фида
    public boolean lastHitByPlayer = false;
    public Weapon lastHitWeapon = null;
    public boolean announced = false;
    public boolean killedByPlayer = false;

    public void takeDamage(float dmg, Object attacker, PUBGPlayer player) {
        if (isDead) return;

        float armorMul = 1.0f - (vestLevel * 0.18f);
        health -= dmg * armorMul;

        if (health <= 0) {
            health = 0;
            isDead = true;
            killedByPlayer = (attacker == player) || lastHitByPlayer;
            if (killedByPlayer) {
                player.kills++;
                SoundSynth3D.play2D(SoundSynth3D.SOUND_KILL, 0.7f);
            }
        }
    }
}
