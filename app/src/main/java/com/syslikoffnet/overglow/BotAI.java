package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * Искусственный Интеллект 3D ботов (Standoff 2 & PUBG style):
 * Тактическое перемещение, поиск укрытий, проверка прямой видимости (Raycast LoS),
 * прицеливание с разбросом, стрельба очередями, перезарядка.
 */
public final class BotAI {

    public static final int STATE_PATROL = 0;
    public static final int STATE_ALERT = 1;
    public static final int STATE_ENGAGE = 2;
    public static final int STATE_DEAD = 3;

    public final int id;
    public final String name;
    public final int team; // 0 = T, 1 = CT
    public final Math3D.Vec3 pos = new Math3D.Vec3();
    public final Math3D.Vec3 vel = new Math3D.Vec3();

    public float yaw = 0;
    public float pitch = 0;

    public float health = 100f;
    public float armor = 50f;
    public boolean isDead = false;
    public float respawnTimer = 0f;

    public int kills = 0;
    public int deaths = 0;
    public int score = 0;

    public int state = STATE_PATROL;
    public Weapon weapon;

    // Цель и навигация
    public final Math3D.Vec3 targetPos = new Math3D.Vec3();
    public float stateTimer = 0f;
    public float strafeDir = 1f;
    public float strafeTimer = 0f;
    public float reactionTimer = 0.2f;

    // Анимация шага
    public float legAngle = 0f;

    public BotAI(int id, String name, int team, int weaponId) {
        this.id = id;
        this.name = name;
        this.team = team;
        this.weapon = Weapon.create(weaponId);
    }

    public void spawn(float x, float y, float z, float startYaw) {
        pos.set(x, y, z);
        vel.set(0, 0, 0);
        yaw = startYaw;
        pitch = 0;
        health = 100f;
        armor = 50f;
        isDead = false;
        state = STATE_PATROL;
        stateTimer = (float) (Math.random() * 3 + 1);
        targetPos.set(x + (float) ((Math.random() - 0.5) * 30), 0, z + (float) ((Math.random() - 0.5) * 30));
        weapon.ammoInMag = weapon.maxMag;
        weapon.ammoReserve = weapon.maxReserve;
        weapon.reloadTimer = 0;
    }

    public void update(float dt, Map3D map, Player player, ArrayList<BotAI> allBots, ParticleSystem particles) {
        if (isDead) {
            respawnTimer -= dt;
            return;
        }

        weapon.update(dt);
        stateTimer -= dt;
        strafeTimer -= dt;

        // Поиск ближайшего видимого врага (игрок или боты противоположной команды)
        Math3D.Vec3 enemyPos = null;
        boolean enemyIsPlayer = false;

        if (player.team != this.team && !player.isDead) {
            if (hasLineOfSight(pos, player.pos, map)) {
                enemyPos = player.pos;
                enemyIsPlayer = true;
            }
        }

        if (enemyPos == null) {
            float closestDist = 80f;
            for (BotAI other : allBots) {
                if (other != this && other.team != this.team && !other.isDead) {
                    float d = Math3D.dist(pos.x, pos.y, pos.z, other.pos.x, other.pos.y, other.pos.z);
                    if (d < closestDist && hasLineOfSight(pos, other.pos, map)) {
                        closestDist = d;
                        enemyPos = other.pos;
                        enemyIsPlayer = false;
                    }
                }
            }
        }

        // Поведение стейт-машины
        if (enemyPos != null) {
            state = STATE_ENGAGE;
            engageEnemy(dt, enemyPos, enemyIsPlayer, player, allBots, map, particles);
        } else {
            patrolArea(dt, map);
        }

        // Гравитация и физика
        vel.y -= 18f * dt;
        applyMovement(dt, map);

        // Анимация шага
        float hSpeed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (hSpeed > 0.1f) {
            legAngle += dt * 10f;
        }
    }

    private void engageEnemy(float dt, Math3D.Vec3 enemyPos, boolean isPlayerTarget, Player player,
                             ArrayList<BotAI> allBots, Map3D map, ParticleSystem particles) {
        // Наведение на цель
        float dx = enemyPos.x - pos.x;
        float dy = (enemyPos.y + 1.2f) - (pos.y + 1.5f);
        float dz = enemyPos.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) Math.atan2(-dx, dz) * Math3D.TO_DEG;
        float targetPitch = (float) Math.atan2(dy, dist) * Math3D.TO_DEG;

        yaw = Math3D.lerp(yaw, targetYaw, dt * 8f);
        pitch = Math3D.lerp(pitch, targetPitch, dt * 8f);

        // Стрейф (движение влево-вправо при перестрелке)
        if (strafeTimer <= 0) {
            strafeDir = (Math.random() > 0.5) ? 1f : -1f;
            strafeTimer = (float) (Math.random() * 1.5 + 0.5);
        }

        float rad = yaw * Math3D.TO_RAD;
        float strafeX = (float) Math.cos(rad) * strafeDir * 3.5f;
        float strafeZ = (float) Math.sin(rad) * strafeDir * 3.5f;

        vel.x = Math3D.lerp(vel.x, strafeX, dt * 8f);
        vel.z = Math3D.lerp(vel.z, strafeZ, dt * 8f);

        // Стрельба
        if (weapon.canFire()) {
            weapon.fireTimer = weapon.fireInterval + (float) (Math.random() * 0.05f);
            weapon.ammoInMag--;

            // Звук выстрела бота
            int soundId = SoundSynth3D.SOUND_AKR;
            if (weapon.id == Weapon.ID_M4) soundId = SoundSynth3D.SOUND_M4;
            else if (weapon.id == Weapon.ID_AWM) soundId = SoundSynth3D.SOUND_AWM;
            else if (weapon.id == Weapon.ID_DEAGLE) soundId = SoundSynth3D.SOUND_DEAGLE;

            SoundSynth3D.playSound(soundId, pos.x, pos.y + 1.5f, pos.z, player.pos, player.yaw);

            // Трейсер выстрела
            float spread = (float) ((Math.random() - 0.5) * 0.6);
            float targetX = enemyPos.x + spread;
            float targetY = enemyPos.y + 1.2f + spread * 0.5f;
            float targetZ = enemyPos.z + spread;

            particles.addTracer(pos.x, pos.y + 1.4f, pos.z, targetX, targetY, targetZ);
            particles.triggerMuzzleFlash(pos.x, pos.y + 1.4f, pos.z);

            // Проверка урона (вероятность попадания зависит от дистанции)
            float hitChance = Math.max(0.25f, 1f - (dist / 60f));
            if (Math.random() < hitChance) {
                float dmg = weapon.damage * 0.8f;
                if (isPlayerTarget) {
                    player.takeDamage(dmg, pos);
                } else {
                    for (BotAI other : allBots) {
                        if (other.team != this.team && Math3D.dist(other.pos.x, other.pos.y, other.pos.z, targetX, targetY, targetZ) < 1.5f) {
                            other.takeDamage(dmg, this, player);
                        }
                    }
                }
            }
        } else if (weapon.ammoInMag <= 0 && weapon.reloadTimer <= 0) {
            weapon.startReload();
        }
    }

    private void patrolArea(float dt, Map3D map) {
        if (stateTimer <= 0 || Math3D.dist(pos.x, 0, pos.z, targetPos.x, 0, targetPos.z) < 3f) {
            stateTimer = (float) (Math.random() * 6 + 3);
            targetPos.set((float) ((Math.random() - 0.5) * 80), 0, (float) ((Math.random() - 0.5) * 80));
        }

        float dx = targetPos.x - pos.x;
        float dz = targetPos.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        if (dist > 0.5f) {
            float targetYaw = (float) Math.atan2(-dx, dz) * Math3D.TO_DEG;
            yaw = Math3D.lerp(yaw, targetYaw, dt * 5f);

            float rad = yaw * Math3D.TO_RAD;
            vel.x = -(float) Math.sin(rad) * 4.2f;
            vel.z = (float) Math.cos(rad) * 4.2f;
        } else {
            vel.x = 0;
            vel.z = 0;
        }
    }

    private boolean hasLineOfSight(Math3D.Vec3 from, Math3D.Vec3 to, Map3D map) {
        Math3D.Vec3 dir = new Math3D.Vec3(to.x - from.x, to.y - from.y, to.z - from.z);
        float totalDist = dir.length();
        dir.normalize();

        for (Map3D.Obstacle obs : map.obstacles) {
            float t = obs.box.raycast(from, dir);
            if (t > 0 && t < totalDist - 1.0f) {
                return false; // Препятствие блокирует обзор
            }
        }
        return true;
    }

    private void applyMovement(float dt, Map3D map) {
        float nextX = pos.x + vel.x * dt;
        float nextY = pos.y + vel.y * dt;
        float nextZ = pos.z + vel.z * dt;

        float radius = 0.45f;
        float height = 1.8f;

        Math3D.Box boxX = new Math3D.Box(nextX - radius, pos.y, pos.z - radius,
                                         nextX + radius, pos.y + height, pos.z + radius);
        boolean collideX = false;
        for (Map3D.Obstacle obs : map.obstacles) {
            if (obs.box.intersects(boxX)) { collideX = true; break; }
        }
        if (!collideX) pos.x = nextX;
        else vel.x = -vel.x * 0.5f;

        Math3D.Box boxZ = new Math3D.Box(pos.x - radius, pos.y, nextZ - radius,
                                         pos.x + radius, pos.y + height, nextZ + radius);
        boolean collideZ = false;
        for (Map3D.Obstacle obs : map.obstacles) {
            if (obs.box.intersects(boxZ)) { collideZ = true; break; }
        }
        if (!collideZ) pos.z = nextZ;
        else vel.z = -vel.z * 0.5f;

        if (nextY <= 0) {
            pos.y = 0;
            vel.y = 0;
        } else {
            pos.y = nextY;
        }
    }

    public void takeDamage(float dmg, BotAI attacker, Player player) {
        if (isDead) return;
        if (armor > 0) {
            float absorbed = dmg * 0.4f;
            armor = Math.max(0, armor - absorbed);
            dmg -= absorbed;
        }
        health -= dmg;

        if (health <= 0) {
            health = 0;
            isDead = true;
            deaths++;
            respawnTimer = 4.0f; // возрождение через 4 секунды в TDM
            if (attacker != null) {
                attacker.kills++;
                attacker.score += 100;
            }
        }
    }

    public Math3D.Box getHeadBox() {
        return new Math3D.Box(pos.x - 0.22f, pos.y + 1.45f, pos.z - 0.22f,
                              pos.x + 0.22f, pos.y + 1.9f, pos.z + 0.22f);
    }

    public Math3D.Box getBodyBox() {
        return new Math3D.Box(pos.x - 0.35f, pos.y, pos.z - 0.35f,
                              pos.x + 0.35f, pos.y + 1.45f, pos.z + 0.35f);
    }
}
