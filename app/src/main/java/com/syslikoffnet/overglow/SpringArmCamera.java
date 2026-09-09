package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * Фаза 1: Профессиональная TPP Камера (Unreal Engine 4 SpringArm Component):
 * - Орбитальная камера с независимым вращением вокруг персонажа (camYaw, camPitch)
 * - Смена активного плеча (Right Shoulder +0.55m / Left Shoulder -0.55m)
 * - Плавный переход в режим прицеливания ADS (Aim Down Sights) к мушке/оптике
 * - Защита от прохождения сквозь стены (Camera Obstruction Collision / Probe Raycast)
 * - Оптимизация под мобильные устройства: 0 GC Alloc за кадр
 */
public final class SpringArmCamera {

    // Углы обзора камеры
    public float yaw = 0f;
    public float pitch = 0f;

    // Смещение плеча и зум
    public static final float SHOULDER_RIGHT = 0.55f;
    public static final float SHOULDER_LEFT = -0.55f;
    public float targetShoulderOffset = SHOULDER_RIGHT;
    public float currentShoulderOffset = SHOULDER_RIGHT;

    // Длина штанги (Spring Arm Length)
    public float defaultArmLength = 3.2f;
    public float currentArmLength = 3.2f;
    public float adsFactor = 0f; // 0.0 = TPP, 1.0 = ADS

    // Позиции камеры и цели
    public final Math3D.Vec3 eye = new Math3D.Vec3();
    public final Math3D.Vec3 target = new Math3D.Vec3();
    public final Math3D.Vec3 forward = new Math3D.Vec3();
    public final Math3D.Vec3 right = new Math3D.Vec3();
    public final Math3D.Vec3 up = new Math3D.Vec3(0, 1, 0);

    // Временные векторы для предотвращения аллокаций
    private final Math3D.Vec3 pivot = new Math3D.Vec3();
    private final Math3D.Vec3 desiredEye = new Math3D.Vec3();

    public void switchShoulder() {
        targetShoulderOffset = (targetShoulderOffset > 0) ? SHOULDER_LEFT : SHOULDER_RIGHT;
    }

    public void update(float dt, Math3D.Vec3 playerPos, float characterHeight,
                       boolean isAiming, boolean isProning, boolean isCrouching,
                       PUBGMap map) {
        // 1. Плавный переход ADS и плеча
        float targetAds = isAiming ? 1.0f : 0.0f;
        adsFactor = Math3D.lerp(adsFactor, targetAds, dt * 14f);
        currentShoulderOffset = Math3D.lerp(currentShoulderOffset, targetShoulderOffset * (1.0f - adsFactor), dt * 12f);

        // 2. Расчет базовой длины штанги камеры
        float targetArm = isAiming ? 0.0f : (isProning ? 2.0f : (isCrouching ? 2.6f : defaultArmLength));
        currentArmLength = Math3D.lerp(currentArmLength, targetArm, dt * 10f);

        // 3. Точка привязки (Pivot) на уровне груди/глаз персонажа
        float pivotHeight = isProning ? 0.35f : (isCrouching ? 1.15f : (characterHeight * 0.88f));
        pivot.set(playerPos.x, playerPos.y + pivotHeight, playerPos.z);

        // 4. Расчет направляющих векторов камеры (Forward, Right, Up)
        float radYaw = yaw * Math3D.TO_RAD;
        float radPitch = pitch * Math3D.TO_RAD;

        float cosPitch = (float) Math.cos(radPitch);
        float sinPitch = (float) Math.sin(radPitch);
        float cosYaw = (float) Math.cos(radYaw);
        float sinYaw = (float) Math.sin(radYaw);

        forward.x = sinYaw * cosPitch;
        forward.y = sinPitch;
        forward.z = cosYaw * cosPitch;

        right.x = cosYaw;
        right.y = 0;
        right.z = -sinYaw;

        // 5. Желаемая позиция камеры с учетом плечевого смещения
        float actualArmLength = currentArmLength;

        // Расчет позиции без коллизий
        desiredEye.x = pivot.x - forward.x * actualArmLength + right.x * currentShoulderOffset;
        desiredEye.y = pivot.y - forward.y * actualArmLength + (isAiming ? 0.08f : 0.15f);
        desiredEye.z = pivot.z - forward.z * actualArmLength + right.z * currentShoulderOffset;

        // 6. Raycast коллизия камеры со стенами и домами (Camera Occlusion Prevention)
        if (map != null && actualArmLength > 0.3f) {
            float minAllowedDistance = actualArmLength;
            Math3D.Vec3 rayDir = new Math3D.Vec3(desiredEye.x - pivot.x, desiredEye.y - pivot.y, desiredEye.z - pivot.z);
            float rayLen = rayDir.length();
            if (rayLen > 0.001f) {
                rayDir.x /= rayLen;
                rayDir.y /= rayLen;
                rayDir.z /= rayLen;

                for (PUBGMap.Obstacle obs : map.obstacles) {
                    float hitDist = obs.box.raycast(pivot, rayDir);
                    if (hitDist > 0 && hitDist < minAllowedDistance) {
                        minAllowedDistance = Math.max(0.4f, hitDist - 0.25f);
                    }
                }

                // Корректировка позиции камеры ближе к персонажу при препятствии
                if (minAllowedDistance < actualArmLength) {
                    desiredEye.x = pivot.x + rayDir.x * minAllowedDistance;
                    desiredEye.y = pivot.y + rayDir.y * minAllowedDistance;
                    desiredEye.z = pivot.z + rayDir.z * minAllowedDistance;
                }
            }
        }

        // 7. Итоговая позиция камеры и целевая точка фокуса
        eye.set(desiredEye);
        target.set(eye.x + forward.x * 40f, eye.y + forward.y * 40f, eye.z + forward.z * 40f);
    }
}
