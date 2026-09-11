package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * 3D Трейсеры пуль, частицы крови, искры, вспышки выстрела, взрывы гранат и RPG.
 */
public final class ParticleSystem {

    public static final class Particle {
        public float x, y, z;
        public float vx, vy, vz;
        public float r, g, b, a;
        public float size;
        public float life;
        public float maxLife;
        public boolean gravity;

        public boolean update(float dt) {
            life += dt;
            if (life >= maxLife) return false;
            x += vx * dt;
            y += vy * dt;
            z += vz * dt;
            if (gravity) vy -= 9.8f * dt;
            a = 1f - (life / maxLife);
            return true;
        }
    }

    public static final class Tracer {
        public float x0, y0, z0;
        public float x1, y1, z1;
        public float life;
        public float maxLife = 0.08f;

        public Tracer(float x0, float y0, float z0, float x1, float y1, float z1) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.life = 0;
        }

        public boolean update(float dt) {
            life += dt;
            return life < maxLife;
        }
    }

    public static final class Projectile {
        public int type; // 0 = RPG, 1 = Grenade
        public float x, y, z;
        public float vx, vy, vz;
        public float life = 0;
        public float maxLife = 3.5f;
        public float damage = 160f;
        public float radius = 8.5f;
        public boolean exploded = false;
        public int ownerId = 0;

        public Projectile(int type, float x, float y, float z, float vx, float vy, float vz, float damage, float radius, int ownerId) {
            this.type = type;
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.damage = damage;
            this.radius = radius;
            this.ownerId = ownerId;
            if (type == 0) maxLife = 4.0f;
            else maxLife = 2.4f;
        }

        public boolean update(float dt, PUBGMap map) {
            life += dt;
            if (life >= maxLife) {
                exploded = true;
                return false;
            }

            x += vx * dt;
            y += vy * dt;
            z += vz * dt;

            if (type == 1) {
                vy -= 14.0f * dt;
            } else {
                vy -= 1.5f * dt;
            }

            if (y < 0.2f) {
                y = 0.2f;
                if (type == 1) {
                    vy = -vy * 0.45f;
                    vx *= 0.65f;
                    vz *= 0.65f;
                } else {
                    exploded = true;
                    return false;
                }
            }

            if (map != null) {
                for (PUBGMap.Obstacle obs : map.obstacles) {
                    if (obs.box.contains(x, y, z)) {
                        if (type == 1) {
                            vx = -vx * 0.5f;
                            vz = -vz * 0.5f;
                        } else {
                            exploded = true;
                            return false;
                        }
                    }
                }
            }
            return true;
        }
    }

    public final ArrayList<Particle> particles = new ArrayList<>();
    public final ArrayList<Tracer> tracers = new ArrayList<>();
    public final ArrayList<Projectile> projectiles = new ArrayList<>();

    public final Math3D.Vec3 muzzleFlashPos = new Math3D.Vec3();
    public float muzzleFlashIntensity = 0f;

    /** Потребляемое событие взрыва (гранаты): игра снимает урон по площади */
    public static final class ExplosionEvent {
        public float x, y, z, damage, radius;
        public int ownerId;

        void set(float x, float y, float z, float damage, float radius, int ownerId) {
            this.x = x; this.y = y; this.z = z;
            this.damage = damage; this.radius = radius; this.ownerId = ownerId;
        }
    }
    private final ExplosionEvent pendingExplosion = new ExplosionEvent();
    private boolean explosionPending = false;

    public boolean consumeExplosion(ExplosionEvent out) {
        if (!explosionPending) return false;
        explosionPending = false;
        out.x = pendingExplosion.x; out.y = pendingExplosion.y; out.z = pendingExplosion.z;
        out.damage = pendingExplosion.damage; out.radius = pendingExplosion.radius; out.ownerId = pendingExplosion.ownerId;
        return true;
    }

    public void update(float dt, PUBGMap map) {
        if (muzzleFlashIntensity > 0) {
            muzzleFlashIntensity = Math.max(0, muzzleFlashIntensity - dt * 14f);
        }

        for (int i = particles.size() - 1; i >= 0; i--) {
            if (!particles.get(i).update(dt)) {
                particles.remove(i);
            }
        }

        for (int i = tracers.size() - 1; i >= 0; i--) {
            if (!tracers.get(i).update(dt)) {
                tracers.remove(i);
            }
        }

        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            if (!p.update(dt, map)) {
                if (p.exploded) {
                    spawnExplosion(p.x, p.y, p.z);
                    pendingExplosion.set(p.x, p.y, p.z, p.damage, p.radius, p.ownerId);
                    explosionPending = true;
                    SoundSynth3D.play2D(SoundSynth3D.SOUND_EXPLOSION, 0.9f);
                }
                projectiles.remove(i);
            }
        }
    }

    public void addTracer(float x0, float y0, float z0, float x1, float y1, float z1) {
        tracers.add(new Tracer(x0, y0, z0, x1, y1, z1));
    }

    public void triggerMuzzleFlash(float x, float y, float z) {
        muzzleFlashPos.set(x, y, z);
        muzzleFlashIntensity = 1.4f;
    }

    public void spawnSparks(float x, float y, float z, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = x; p.y = y; p.z = z;
            p.vx = (float) ((Math.random() - 0.5) * 8);
            p.vy = (float) (Math.random() * 6 + 1);
            p.vz = (float) ((Math.random() - 0.5) * 8);
            p.r = 1.0f; p.g = 0.85f; p.b = 0.2f; p.a = 1.0f;
            p.size = 0.12f;
            p.life = 0;
            p.maxLife = (float) (Math.random() * 0.25 + 0.15);
            p.gravity = true;
            particles.add(p);
        }
    }

    public void spawnBlood(float x, float y, float z, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = x; p.y = y; p.z = z;
            p.vx = (float) ((Math.random() - 0.5) * 4);
            p.vy = (float) (Math.random() * 4 + 0.5);
            p.vz = (float) ((Math.random() - 0.5) * 4);
            p.r = 0.85f; p.g = 0.05f; p.b = 0.05f; p.a = 1.0f;
            p.size = 0.16f;
            p.life = 0;
            p.maxLife = (float) (Math.random() * 0.35 + 0.2);
            p.gravity = true;
            particles.add(p);
        }
    }

    public void spawnExplosion(float x, float y, float z) {
        for (int i = 0; i < 40; i++) {
            Particle p = new Particle();
            p.x = x; p.y = y; p.z = z;
            p.vx = (float) ((Math.random() - 0.5) * 22);
            p.vy = (float) (Math.random() * 16 + 2);
            p.vz = (float) ((Math.random() - 0.5) * 22);
            p.r = 1.0f; p.g = (float) (Math.random() * 0.6 + 0.2); p.b = 0.05f; p.a = 1.0f;
            p.size = 0.55f;
            p.life = 0;
            p.maxLife = (float) (Math.random() * 0.5 + 0.3);
            p.gravity = false;
            particles.add(p);
        }
        for (int i = 0; i < 25; i++) {
            Particle p = new Particle();
            p.x = x; p.y = y; p.z = z;
            p.vx = (float) ((Math.random() - 0.5) * 6);
            p.vy = (float) (Math.random() * 4 + 1);
            p.vz = (float) ((Math.random() - 0.5) * 6);
            p.r = 0.25f; p.g = 0.25f; p.b = 0.25f; p.a = 0.8f;
            p.size = 0.8f;
            p.life = 0;
            p.maxLife = (float) (Math.random() * 1.2 + 0.8);
            p.gravity = false;
            particles.add(p);
        }
    }
}
