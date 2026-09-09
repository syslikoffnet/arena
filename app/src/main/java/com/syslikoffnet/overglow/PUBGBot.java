package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * 3D Бот для PUBG Mobile:
 * - Прыгает с парашютом из самолета в города (Починки, Сосновка)
 * - Лутает оружие, бежит в безопасную Белую Зону, спасаясь от Синей Зоны
 * - Ведет позиционный бой, использует укрытия, оставляет ящик с лутом после смерти
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
        this.targetPos.set(dropX + (float)((Math.random() - 0.5) * 40), 0, dropZ + (float)((Math.random() - 0.5) * 40));
        this.weapon = Weapon.create(id % 3 == 0 ? Weapon.ID_AWM : (id % 2 == 0 ? Weapon.ID_AKR : Weapon.ID_M4));
    }

    public void update(float dt, PUBGMap map, PUBGPlayer player, ArrayList<PUBGBot> allBots, ParticleSystem particles) {
        if (isDead) return;

        float gHeight = map.getTerrainHeight(pos.x, pos.z);

        // Фаза парашюта
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
            if (distToPlayer < 65f) {
                enemyPos = player.pos;
                targetIsPlayer = true;
            }
        }

        if (enemyPos == null) {
            float closest = 50f;
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
            engage(dt, enemyPos, targetIsPlayer, player, allBots, particles);
        } else {
            moveToSafeZone(dt, map);
        }

        // Гравитация
        vel.y -= 18f * dt;
        pos.x += vel.x * dt;
        pos.z += vel.z * dt;
        pos.y += vel.y * dt;

        if (pos.y <= gHeight) {
            pos.y = gHeight;
            vel.y = 0;
        }

        float hSpeed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (hSpeed > 0.1f) legAngle += dt * 10f;

        // Урон от Синей зоны
        float distToZone = Math3D.dist(pos.x, 0, pos.z, map.blueZoneX, 0, map.blueZoneZ);
        if (distToZone > map.blueZoneRadius) {
            takeDamage(4f * dt * map.zonePhase, null, player);
        }
    }

    private void engage(float dt, Math3D.Vec3 enemyPos, boolean targetIsPlayer, PUBGPlayer player,
                        ArrayList<PUBGBot> allBots, ParticleSystem particles) {
        float dx = enemyPos.x - pos.x;
        float dz = enemyPos.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) Math.atan2(-dx, dz) * Math3D.TO_DEG;
        yaw = Math3D.lerp(yaw, targetYaw, dt * 7f);

        if (strafeTimer <= 0) {
            strafeDir = (Math.random() > 0.5) ? 1f : -1f;
            strafeTimer = (float) (Math.random() * 1.5 + 0.5);
        }

        float rad = yaw * Math3D.TO_RAD;
        vel.x = (float) Math.cos(rad) * strafeDir * 2.8f;
        vel.z = (float) Math.sin(rad) * strafeDir * 2.8f;

        if (weapon.canFire()) {
            weapon.fireTimer = weapon.fireInterval + (float) (Math.random() * 0.08f);
            weapon.ammoInMag--;

            SoundSynth3D.playSound(SoundSynth3D.SOUND_AKR, pos.x, pos.y + 1.4f, pos.z, player.pos, player.yaw);

            float spread = (float) ((Math.random() - 0.5) * 0.8f);
            float tx = enemyPos.x + spread;
            float ty = enemyPos.y + 1.2f + spread * 0.4f;
            float tz = enemyPos.z + spread;

            particles.addTracer(pos.x, pos.y + 1.4f, pos.z, tx, ty, tz);
            particles.triggerMuzzleFlash(pos.x, pos.y + 1.4f, pos.z);

            if (Math.random() < Math.max(0.20f, 1f - (dist / 60f))) {
                float dmg = weapon.damage * 0.75f;
                if (targetIsPlayer) {
                    player.takeDamage(dmg, pos);
                } else {
                    for (PUBGBot other : allBots) {
                        if (other != this && Math3D.dist(other.pos.x, other.pos.y, other.pos.z, tx, ty, tz) < 1.5f) {
                            other.takeDamage(dmg, this, player);
                        }
                    }
                }
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
            float targetYaw = (float) Math.atan2(-dx, dz) * Math3D.TO_DEG;
            yaw = Math3D.lerp(yaw, targetYaw, dt * 5f);

            float rad = yaw * Math3D.TO_RAD;
            vel.x = -(float) Math.sin(rad) * 4.5f;
            vel.z = (float) Math.cos(rad) * 4.5f;
        } else {
            vel.x = 0;
            vel.z = 0;
        }
    }

    public void takeDamage(float dmg, PUBGBot attacker, PUBGPlayer player) {
        if (isDead) return;
        health -= dmg;

        if (health <= 0) {
            health = 0;
            isDead = true;
            if (player != null && attacker == null) {
                player.kills++;
            }
        }
    }

    public Math3D.Box getHeadBox() {
        return new Math3D.Box(pos.x - 0.25f, pos.y + 1.45f, pos.z - 0.25f,
                              pos.x + 0.25f, pos.y + 1.9f, pos.z + 0.25f);
    }

    public Math3D.Box getBodyBox() {
        return new Math3D.Box(pos.x - 0.38f, pos.y, pos.z - 0.38f,
                              pos.x + 0.38f, pos.y + 1.45f, pos.z + 0.38f);
    }
}
