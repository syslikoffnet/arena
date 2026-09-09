package com.syslikoffnet.overglow;

/**
 * Фаза 2: Физический снаряд / пуля (Bullet Projectile):
 * - Траектория с учетом гравитации (9.81 м/с²), начальной скорости V0 и аэродинамического drag
 * - Сегментный Continuous Collision Detection (CCD)
 * - 0 GC Alloc
 */
public final class BulletProjectile {

    public boolean active = false;

    public final Math3D.Vec3 prevPos = new Math3D.Vec3();
    public final Math3D.Vec3 pos = new Math3D.Vec3();
    public final Math3D.Vec3 velocity = new Math3D.Vec3();

    public float bulletDrag = 0.0018f;
    public float baseDamage = 40f;
    public float maxLifeTime = 5.0f;
    public float lifeTime = 0f;
    public float travelDistance = 0f;
    public boolean isPlayerShot = true;
    public Object shooterRef = null;

    public void init(float x, float y, float z,
                     float dirX, float dirY, float dirZ,
                     float speed, float drag, float dmg,
                     boolean fromPlayer, Object shooter) {
        this.pos.set(x, y, z);
        this.prevPos.set(x, y, z);
        this.velocity.set(dirX * speed, dirY * speed, dirZ * speed);
        this.bulletDrag = drag;
        this.baseDamage = dmg;
        this.isPlayerShot = fromPlayer;
        this.shooterRef = shooter;
        this.lifeTime = 0f;
        this.travelDistance = 0f;
        this.active = true;
    }

    public void reset() {
        this.active = false;
        this.shooterRef = null;
    }
}
