package com.syslikoffnet.overglow;

/**
 * Фаза 2: Контроллер отдачи и спрей-паттернов (Recoil Controller):
 * - Процедурный расчет вертикального подброса ствола и горизонтального увода
 * - Модификаторы стоек: Stand (1.0x), Crouch (0.75x), Prone (0.50x)
 * - Модификаторы движения и прицеливания (ADS: 0.65x, Moving: 1.35x)
 * - Плавное гашение отдачи и визуальный Screen Punch
 * - 0 GC Alloc
 */
public final class RecoilController {

    public float currentPitchOffset = 0f;
    public float currentYawOffset = 0f;
    public float screenShake = 0f;

    private int shotSequence = 0;
    private float fireCooldown = 0f;

    public void applyShot(Weapon weapon, int stance, boolean isMoving, boolean isAiming) {
        if (weapon == null) return;

        shotSequence++;
        fireCooldown = 0.25f;

        // 1. Множители позы
        float stanceMultiplier = 1.0f;
        if (stance == CharacterMotor.STANCE_PRONE) stanceMultiplier = 0.50f;
        else if (stance == CharacterMotor.STANCE_CROUCH) stanceMultiplier = 0.75f;

        // 2. Множители движения и прицела
        float moveMultiplier = isMoving ? 1.35f : 1.0f;
        float aimMultiplier = isAiming ? 0.65f : 1.0f;

        float totalModifier = stanceMultiplier * moveMultiplier * aimMultiplier;

        // 3. Вертикальный подброс с учетом насадок
        float vertKick = weapon.getRecoilPitch() * totalModifier;
        if (shotSequence <= 3) vertKick *= 0.85f; // Первые пули более кучные
        currentPitchOffset += vertKick;

        // 4. Горизонтальный увод (S-образный спрей-паттерн)
        float horizBase = weapon.getRecoilYaw() * totalModifier;
        float sprayDirection = (float) Math.sin(shotSequence * 0.9f);
        currentYawOffset += (sprayDirection * horizBase) + (float) ((Math.random() - 0.5) * horizBase * 0.4f);

        // 5. Визуальная тряска экрана
        screenShake = Math.min(2.5f, screenShake + 0.8f);
    }

    public void update(float dt, Weapon weapon) {
        if (fireCooldown > 0) {
            fireCooldown -= dt;
            if (fireCooldown <= 0) {
                shotSequence = 0; // Сброс спрей-паттерна
            }
        }

        float recoverySpeed = (weapon != null) ? weapon.getRecoilRecovery() : 6.0f;

        // Плавный возврат прицела в исходную точку
        currentPitchOffset = Math.max(0f, currentPitchOffset - recoverySpeed * dt);
        currentYawOffset = Math3D.lerp(currentYawOffset, 0f, recoverySpeed * dt);
        screenShake = Math.max(0f, screenShake - dt * 8f);
    }

    public void reset() {
        currentPitchOffset = 0f;
        currentYawOffset = 0f;
        screenShake = 0f;
        shotSequence = 0;
        fireCooldown = 0f;
    }
}
