package com.syslikoffnet.overglow;

/**
 * Фаза 1: Кинематический контроллер персонажа (Character Motor):
 * - Разделение ориентации тела (bodyYaw) и направления взгляда камеры (camYaw)
 * - Стойки: Stand (Стоя), Crouch (Присед), Prone (Лёжа), Jump (Прыжок)
 * - Расчет скорости движения: Walk (Шаг), Run (Бег), Sprint (Спринт)
 * - Надежная коллизия со стенами, зданиями и закрытыми дверями
 * - Подготовка к сетевой синхронизации (State Snapshot)
 */
public final class CharacterMotor {

    public static final int STANCE_STAND = 0;
    public static final int STANCE_CROUCH = 1;
    public static final int STANCE_PRONE = 2;

    public int currentStance = STANCE_STAND;

    // Параметры движения
    public final Math3D.Vec3 position = new Math3D.Vec3();
    public final Math3D.Vec3 velocity = new Math3D.Vec3();

    // Ориентация
    public float bodyYaw = 0f;       // Угол поворота корпуса модели
    public float bodyPitch = 0f;     // Угол наклона корпуса
    public float turnSpeed = 12f;    // Скорость доворота корпуса к движению

    // Состояния
    public boolean isGrounded = true;
    public boolean isSprinting = false;
    public boolean isAiming = false;
    public boolean isMoving = false;

    // Высоты и радиус коллизии
    public static final float RADIUS = 0.38f;
    public static final float HEIGHT_STAND = 1.80f;
    public static final float HEIGHT_CROUCH = 1.25f;
    public static final float HEIGHT_PRONE = 0.45f;
    public float currentHeight = HEIGHT_STAND;

    // Скорости движения (м/с)
    public static final float SPEED_WALK = 3.2f;
    public static final float SPEED_RUN = 5.4f;
    public static final float SPEED_SPRINT = 7.8f;
    public static final float SPEED_CROUCH = 2.8f;
    public static final float SPEED_PRONE = 1.2f;

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }

    public void jump() {
        if (isGrounded && currentStance == STANCE_STAND) {
            velocity.y = 5.6f;
            isGrounded = false;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_STEP, 0.7f);
        }
    }

    public void setStance(int stance) {
        this.currentStance = stance;
    }

    public void update(float dt, float moveX, float moveZ, float camYaw, boolean sprintInput, boolean aimInput, PUBGMap map) {
        this.isAiming = aimInput;
        this.isSprinting = sprintInput && !aimInput && currentStance == STANCE_STAND && moveZ > 0.4f;

        // 1. Определение целевой высоты капсулы
        float targetHeight = (currentStance == STANCE_PRONE) ? HEIGHT_PRONE :
                ((currentStance == STANCE_CROUCH) ? HEIGHT_CROUCH : HEIGHT_STAND);
        currentHeight = Math3D.lerp(currentHeight, targetHeight, dt * 12f);

        // 2. Расчет базовой скорости
        float baseSpeed;
        if (currentStance == STANCE_PRONE) baseSpeed = SPEED_PRONE;
        else if (currentStance == STANCE_CROUCH) baseSpeed = SPEED_CROUCH;
        else if (isSprinting) baseSpeed = SPEED_SPRINT;
        else if (isAiming) baseSpeed = SPEED_WALK * 0.75f;
        else baseSpeed = SPEED_RUN;

        // 3. Расчет мирового направления движения относительно КАМЕРЫ (camYaw)
        float radCam = camYaw * Math3D.TO_RAD;
        float cosCam = (float) Math.cos(radCam);
        float sinCam = (float) Math.sin(radCam);

        float forwardX = sinCam * moveZ;
        float forwardZ = cosCam * moveZ;
        float strafeX = cosCam * moveX;
        float strafeZ = -sinCam * moveX;

        float targetVelX = (forwardX + strafeX) * baseSpeed;
        float targetVelZ = (forwardZ + strafeZ) * baseSpeed;

        float moveLen = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        this.isMoving = moveLen > 0.1f;

        // Плавный разгон / торможение
        velocity.x = Math3D.lerp(velocity.x, targetVelX, dt * 14f);
        velocity.z = Math3D.lerp(velocity.z, targetVelZ, dt * 14f);

        // 4. Поворот тела (Body Yaw):
        // Если целимся или стреляем — корпус выравнивается строго по камере
        // Если бежим свободно — корпус поворачивается по направлению движения
        if (isAiming || !isMoving) {
            bodyYaw = Math3D.lerpAngle(bodyYaw, camYaw, dt * 14f);
        } else {
            float moveAngle = (float) Math.atan2(velocity.x, velocity.z) * Math3D.TO_DEG;
            bodyYaw = Math3D.lerpAngle(bodyYaw, moveAngle, dt * turnSpeed);
        }

        // 5. Гравитация
        velocity.y -= 18.0f * dt;

        // 6. Интеграция позиции
        position.x += velocity.x * dt;
        position.z += velocity.z * dt;
        position.y += velocity.y * dt;

        // 7. Коллизия с ландшафтом карты
        float groundY = (map != null) ? map.getTerrainHeight(position.x, position.z) : 0f;
        if (position.y <= groundY) {
            position.y = groundY;
            velocity.y = 0;
            isGrounded = true;
        }

        // 8. Разрешение коллизий со стенами и домами (World Collider)
        WorldCollider.resolveCharacterCollision(position, RADIUS, currentHeight, map);
    }
}
