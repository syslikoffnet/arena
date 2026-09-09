package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * Фаза 2: Высокоточный физический движок баллистики (Ballistics System):
 * - Интеграция траекторий пуль с реальной гравитацией (g = 9.81 м/с²) и аэродинамическим сопротивлением
 * - Пул из 128 предвыделенных пуль (Bullet Pool) для 0 GC Alloc
 * - Сегментная непрерывная коллизия (CCD) с защитой от пролета сквозь стены и препятствия
 * - Анатомический расчет урона через HitboxSystem (Head / Chest / Limbs / Armor)
 * - Звук пролета пули над ухом (Whiz-by / Supersonic Snap)
 */
public final class BallisticsSystem {

    public static final int POOL_SIZE = 128;
    public final BulletProjectile[] pool = new BulletProjectile[POOL_SIZE];

    public static final float GRAVITY = 9.81f;

    // Временные структуры для 0 GC Alloc
    private final Math3D.Vec3 segDir = new Math3D.Vec3();
    private final Math3D.Vec3 segOrigin = new Math3D.Vec3();

    public BallisticsSystem() {
        for (int i = 0; i < POOL_SIZE; i++) {
            pool[i] = new BulletProjectile();
        }
    }

    public void spawnBullet(float startX, float startY, float startZ,
                            float dirX, float dirY, float dirZ,
                            Weapon weapon, boolean fromPlayer, Object shooter) {
        if (weapon == null) return;

        float speed = weapon.muzzleVelocity;
        float drag = weapon.bulletDrag;
        float dmg = weapon.baseDamage;
        int count = weapon.pelletCount;
        float spread = weapon.spreadRadius;

        for (int p = 0; p < count; p++) {
            BulletProjectile b = obtainBullet();
            if (b == null) break;

            float fx = dirX, fy = dirY, fz = dirZ;
            if (spread > 0 || count > 1) {
                float rx = (float) ((Math.random() - 0.5) * spread);
                float ry = (float) ((Math.random() - 0.5) * spread);
                float rz = (float) ((Math.random() - 0.5) * spread);
                fx += rx; fy += ry; fz += rz;
                float len = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
                if (len > 0.0001f) { fx /= len; fy /= len; fz /= len; }
            }

            // Коррекция возвышения ствола по пристрелке (Zeroing Distance)
            if (weapon.zeroingDistance > 100) {
                float zeroElev = (weapon.zeroingDistance - 100) * 0.00015f;
                fy += zeroElev;
            }

            b.init(startX, startY, startZ, fx, fy, fz, speed, drag, dmg, fromPlayer, shooter);
        }
    }

    private BulletProjectile obtainBullet() {
        for (int i = 0; i < POOL_SIZE; i++) {
            if (!pool[i].active) {
                return pool[i];
            }
        }
        return null;
    }

    public void update(float dt, PUBGMap map, PUBGPlayer player,
                       ArrayList<PUBGBot> bots, ParticleSystem particles) {
        for (int i = 0; i < POOL_SIZE; i++) {
            BulletProjectile b = pool[i];
            if (!b.active) continue;

            b.lifeTime += dt;
            if (b.lifeTime > b.maxLifeTime) {
                b.reset();
                continue;
            }

            // 1. Сохраняем предыдущую позицию
            b.prevPos.set(b.pos);

            // 2. Расчет сопротивления воздуха и гравитации
            float speed = b.velocity.length();
            float dragAccel = b.bulletDrag * speed * speed;

            float dragDirX = -b.velocity.x / Math.max(0.001f, speed);
            float dragDirY = -b.velocity.y / Math.max(0.001f, speed);
            float dragDirZ = -b.velocity.z / Math.max(0.001f, speed);

            b.velocity.x += dragDirX * dragAccel * dt;
            b.velocity.y += (dragDirY * dragAccel - GRAVITY) * dt;
            b.velocity.z += dragDirZ * dragAccel * dt;

            // 3. Перемещение
            b.pos.x += b.velocity.x * dt;
            b.pos.y += b.velocity.y * dt;
            b.pos.z += b.velocity.z * dt;

            float stepDist = Math3D.dist(b.prevPos.x, b.prevPos.y, b.prevPos.z, b.pos.x, b.pos.y, b.pos.z);
            b.travelDistance += stepDist;

            // 4. Трассер пули в воздухе
            if (b.travelDistance > 5.0f && (i % 2 == 0)) {
                particles.addTracer(b.prevPos.x, b.prevPos.y, b.prevPos.z, b.pos.x, b.pos.y, b.pos.z);
            }

            // 5. Звук пролета пули рядом с камерой (Whiz-by)
            if (!b.isPlayerShot) {
                float distToPlayer = Math3D.dist(b.pos.x, b.pos.y, b.pos.z, player.pos.x, player.pos.y + 1.5f, player.pos.z);
                if (distToPlayer < 2.5f) {
                    SoundSynth3D.play2D(SoundSynth3D.SOUND_STEP, 0.4f);
                }
            }

            // 6. Непрерывная проверка столкновения (CCD Segment Raycast)
            segOrigin.set(b.prevPos);
            segDir.set(b.pos.x - b.prevPos.x, b.pos.y - b.prevPos.y, b.pos.z - b.prevPos.z);
            float segLen = segDir.length();
            if (segLen > 0.0001f) {
                segDir.scale(1.0f / segLen);
            }

            // Проверка попадания в стены и препятствия
            float wallHit = WorldCollider.raycastWorld(segOrigin, segDir, segLen, map);
            boolean hitWall = (wallHit > 0 && wallHit <= segLen);
            float effectiveHitDist = hitWall ? wallHit : segLen;

            // Проверка попадания по ботам (если стрелял игрок)
            if (b.isPlayerShot) {
                PUBGBot hitBot = null;
                int hitZone = HitboxSystem.ZONE_CHEST;
                float closestBotHit = effectiveHitDist;

                for (PUBGBot bot : bots) {
                    if (bot.isDead || bot.isParachuting) continue;

                    // Голова
                    float tHead = bot.getHeadBox().raycast(segOrigin, segDir);
                    if (tHead > 0 && tHead < closestBotHit) {
                        closestBotHit = tHead;
                        hitBot = bot;
                        hitZone = HitboxSystem.ZONE_HEAD;
                    }

                    // Тело
                    float tBody = bot.getBodyBox().raycast(segOrigin, segDir);
                    if (tBody > 0 && tBody < closestBotHit) {
                        closestBotHit = tBody;
                        hitBot = bot;
                        hitZone = HitboxSystem.ZONE_CHEST;
                    }
                }

                if (hitBot != null) {
                    float finalDmg = HitboxSystem.calculateDamage(b.baseDamage, hitZone, b.travelDistance,
                            hitBot.helmetLevel, hitBot.vestLevel);
                    hitBot.takeDamage(finalDmg, null, player);

                    float hx = segOrigin.x + segDir.x * closestBotHit;
                    float hy = segOrigin.y + segDir.y * closestBotHit;
                    float hz = segOrigin.z + segDir.z * closestBotHit;

                    particles.spawnBlood(hx, hy, hz, (hitZone == HitboxSystem.ZONE_HEAD) ? 14 : 7);
                    if (hitZone == HitboxSystem.ZONE_HEAD) {
                        SoundSynth3D.play2D(SoundSynth3D.SOUND_HEADSHOT_HELMET, 1.0f);
                        player.hitMarkerHeadshot = true;
                    } else {
                        SoundSynth3D.play2D(SoundSynth3D.SOUND_BODY_HIT, 0.8f);
                        player.hitMarkerHeadshot = false;
                    }
                    player.hitMarkerTimer = 0.25f;

                    b.reset();
                    continue;
                }
            } else {
                // Если стрелял бот — проверка попадания по игроку
                if (!player.isDead && player.moveMode == PUBGPlayer.MODE_ON_FOOT) {
                    Math3D.AABB playerBox = new Math3D.AABB(
                            player.pos.x - 0.35f, player.pos.y, player.pos.z - 0.35f,
                            player.pos.x + 0.35f, player.pos.y + player.motor.currentHeight, player.pos.z + 0.35f);

                    float tPlayer = playerBox.raycast(segOrigin, segDir);
                    if (tPlayer > 0 && tPlayer <= effectiveHitDist) {
                        int hitZone = (segOrigin.y + segDir.y * tPlayer > player.pos.y + player.motor.currentHeight - 0.35f) ?
                                HitboxSystem.ZONE_HEAD : HitboxSystem.ZONE_CHEST;

                        float finalDmg = HitboxSystem.calculateDamage(b.baseDamage, hitZone, b.travelDistance,
                                player.helmetLevel, player.vestLevel);
                        player.takeDamage(finalDmg, segOrigin);

                        b.reset();
                        continue;
                    }
                }
            }

            // Попадание в стену или землю
            if (hitWall) {
                float hx = segOrigin.x + segDir.x * wallHit;
                float hy = segOrigin.y + segDir.y * wallHit;
                float hz = segOrigin.z + segDir.z * wallHit;
                particles.spawnSparks(hx, hy, hz, 5);
                b.reset();
                continue;
            }

            // Коллизия с поверхностью земли
            float groundY = map.getTerrainHeight(b.pos.x, b.pos.z);
            if (b.pos.y <= groundY) {
                particles.spawnSparks(b.pos.x, groundY + 0.1f, b.pos.z, 3);
                b.reset();
            }
        }
    }
}
