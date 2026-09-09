package com.syslikoffnet.overglow;

/**
 * Интерактивная открывающаяся дверь в домах PUBG Mobile.
 * Анимирует плавный поворот на петлях на 90 градусов и переключает коллизию.
 */
public final class InteractiveDoor {

    public final int id;
    public final float hingeX, hingeY, hingeZ; // точка петли
    public final float width = 1.6f;
    public final float height = 2.4f;
    public final float thickness = 0.15f;
    public final float baseAngle; // 0 или 90 градусов

    public boolean isOpen = false;
    public float currentAngle = 0f; // 0..90
    public final Math3D.Box collider = new Math3D.Box();

    public InteractiveDoor(int id, float x, float y, float z, float baseAngle) {
        this.id = id;
        this.hingeX = x;
        this.hingeY = y;
        this.hingeZ = z;
        this.baseAngle = baseAngle;
        updateCollider();
    }

    public void toggle() {
        isOpen = !isOpen;
        SoundSynth3D.playSound(SoundSynth3D.SOUND_RELOAD, hingeX, hingeY + 1f, hingeZ,
                new Math3D.Vec3(hingeX, hingeY, hingeZ), 0);
    }

    public void update(float dt) {
        float target = isOpen ? 90f : 0f;
        currentAngle = Math3D.lerp(currentAngle, target, dt * 10f);
        updateCollider();
    }

    public void updateCollider() {
        if (isOpen) {
            // При открытии дверь поворачивается к стене, проем свободен
            collider.set(hingeX - 0.2f, hingeY, hingeZ - 0.2f,
                         hingeX + 0.2f, hingeY + height, hingeZ + 0.2f);
        } else {
            // Дверь закрыта — блокирует проход
            float rad = (baseAngle + currentAngle) * Math3D.TO_RAD;
            float endX = hingeX + (float) Math.cos(rad) * width;
            float endZ = hingeZ + (float) Math.sin(rad) * width;

            collider.set(Math.min(hingeX, endX) - 0.2f, hingeY, Math.min(hingeZ, endZ) - 0.2f,
                         Math.max(hingeX, endX) + 0.2f, hingeY + height, Math.max(hingeZ, endZ) + 0.2f);
        }
    }
}
