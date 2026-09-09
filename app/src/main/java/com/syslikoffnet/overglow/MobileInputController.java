package com.syslikoffnet.overglow;

import android.view.MotionEvent;

/**
 * Фаза 1: Мобильный мультитач-контроллер (Mobile Input Controller):
 * - Плавающий виртуальный джойстик слева (Dynamic Floating Joystick) с фиксацией спринта
 * - Непрерывный 360° тачпад обзора справа (Right Touch Area) с мгновенным откликом
 * - Поддержка одновременного нажатия 4-6 пальцев (4-Finger Claw Grip)
 * - 0 GC Alloc во время игры
 */
public final class MobileInputController {

    // Чувствительность
    public float lookSensitivityX = 0.22f;
    public float lookSensitivityY = 0.20f;

    // Ввод перемещения (-1.0 .. +1.0)
    public float moveX = 0f;
    public float moveZ = 0f;
    public boolean sprintLocked = false;

    // Дельты вращения камеры
    public float lookDeltaYaw = 0f;
    public float lookDeltaPitch = 0f;

    // Кнопки действий
    public boolean isFiring = false;
    public boolean isLeftFiring = false;
    public boolean isAiming = false;
    public boolean isJumping = false;
    public boolean isCrouching = false;
    public boolean isProning = false;
    public boolean isReloading = false;
    public float leanAngle = 0f; // -15 (Left) .. 0 .. +15 (Right)

    // Идентификаторы пальцев
    private int stickPointerId = -1;
    private float stickStartX, stickStartY;
    private float stickCurX, stickCurY;
    private float stickRadius = 110f;

    private int lookPointerId = -1;
    private float lastLookX, lastLookY;

    public int screenWidth = 1920;
    public int screenHeight = 1080;

    public void setScreenSize(int w, int h) {
        this.screenWidth = w;
        this.screenHeight = h;
        this.stickRadius = h * 0.13f;
    }

    public boolean onTouchEvent(MotionEvent event, SpringArmCamera camera, PUBGPlayer player, PUBGMap map) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointerId = event.getPointerId(index);
        float x = event.getX(index);
        float y = event.getY(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handlePointerDown(pointerId, x, y, camera, player, map);
                break;

            case MotionEvent.ACTION_MOVE:
                int count = event.getPointerCount();
                for (int i = 0; i < count; i++) {
                    int pId = event.getPointerId(i);
                    handlePointerMove(pId, event.getX(i), event.getY(i), camera);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                handlePointerUp(pointerId, x, y, player);
                break;
        }
        return true;
    }

    private void handlePointerDown(int pId, float x, float y, SpringArmCamera camera, PUBGPlayer player, PUBGMap map) {
        float rx = x / screenWidth;
        float ry = y / screenHeight;

        // 1. Полет и прыжок из самолета
        if (player.moveMode == PUBGPlayer.MODE_IN_PLANE) {
            if (rx > 0.35f && rx < 0.65f && ry > 0.70f) {
                player.jumpFromPlane(map.planePos, 0);
                return;
            }
        }

        // 2. Раскрытие парашюта
        if (player.moveMode == PUBGPlayer.MODE_FREEFALL) {
            if (rx > 0.35f && rx < 0.65f && ry > 0.70f) {
                player.openParachute();
                return;
            }
        }

        // 3. Тактические кнопки интерфейса

        // Огонь справа (Правый указательный/большой палец)
        if (rx > 0.78f && rx < 0.94f && ry > 0.55f && ry < 0.85f) {
            isFiring = true;
            return;
        }

        // Огонь слева (Левый указательный палец)
        if (rx < 0.22f && ry > 0.20f && ry < 0.50f) {
            isLeftFiring = true;
            return;
        }

        // Кнопка Прицела (ADS 🎯)
        if (rx > 0.82f && ry > 0.32f && ry < 0.52f) {
            isAiming = !isAiming;
            player.isAiming = isAiming;
            return;
        }

        // Смена плеча (Shoulder Switch 🔄)
        if (rx > 0.88f && ry > 0.18f && ry < 0.32f) {
            camera.switchShoulder();
            return;
        }

        // Наклоны Peek Q / E
        if (rx > 0.74f && rx < 0.84f && ry > 0.30f && ry < 0.40f) {
            leanAngle = (leanAngle < -5f) ? 0f : -15f;
            player.leanAngle = leanAngle;
            return;
        }
        if (rx > 0.74f && rx < 0.84f && ry > 0.40f && ry < 0.50f) {
            leanAngle = (leanAngle > 5f) ? 0f : 15f;
            player.leanAngle = leanAngle;
            return;
        }

        // Стойки (Прыжок, Присед, Лечь)
        if (rx > 0.86f && ry > 0.80f) {
            isJumping = true;
            player.motor.jump();
            return;
        }
        if (rx > 0.74f && rx < 0.86f && ry > 0.80f) {
            isCrouching = !isCrouching;
            isProning = false;
            player.motor.setStance(isCrouching ? CharacterMotor.STANCE_CROUCH : CharacterMotor.STANCE_STAND);
            return;
        }
        if (rx > 0.62f && rx < 0.74f && ry > 0.80f) {
            isProning = !isProning;
            isCrouching = false;
            player.motor.setStance(isProning ? CharacterMotor.STANCE_PRONE : CharacterMotor.STANCE_STAND);
            return;
        }

        // Перезарядка
        if (rx > 0.68f && rx < 0.78f && ry > 0.50f && ry < 0.62f) {
            isReloading = true;
            Weapon w = player.getActiveWeapon();
            if (w != null) w.startReload();
            return;
        }

        // Интерактив (Двери / Транспорт)
        if (rx > 0.70f && rx < 0.82f && ry > 0.22f && ry < 0.35f) {
            player.interactDoors(map);
            player.enterExitVehicle(map);
            return;
        }

        // Быстрое лечение
        if (rx > 0.20f && rx < 0.30f && ry > 0.80f) {
            player.useMedkit();
            return;
        }

        // Смена слотов оружия
        if (rx > 0.35f && rx < 0.50f && ry > 0.80f) { player.activeSlot = 0; return; }
        if (rx > 0.50f && rx < 0.65f && ry > 0.80f) { player.activeSlot = 1; return; }

        // 4. Левая половина: Плавающий виртуальный джойстик (Left Thumb)
        if (x < screenWidth * 0.48f && stickPointerId == -1) {
            stickPointerId = pId;
            stickStartX = x;
            stickStartY = y;
            stickCurX = x;
            stickCurY = y;
            return;
        }

        // 5. Правая половина: Сенсорное поле камеры (Right Thumb)
        if (x >= screenWidth * 0.48f && lookPointerId == -1) {
            lookPointerId = pId;
            lastLookX = x;
            lastLookY = y;
        }
    }

    private void handlePointerMove(int pId, float x, float y, SpringArmCamera camera) {
        if (pId == stickPointerId) {
            stickCurX = x;
            stickCurY = y;
            float dx = stickCurX - stickStartX;
            float dy = stickCurY - stickStartY;
            float len = (float) Math.sqrt(dx * dx + dy * dy);

            if (len > stickRadius) {
                dx = (dx / len) * stickRadius;
                dy = (dy / len) * stickRadius;
            }

            // Мертвая зона 10%
            if (len < stickRadius * 0.10f) {
                moveX = 0;
                moveZ = 0;
            } else {
                moveX = dx / stickRadius;
                moveZ = -dy / stickRadius; // Вверх по экрану = вперед (+moveZ)
            }

            // Авто-спринт при сильной тяге вверх
            sprintLocked = (moveZ > 0.75f && Math.abs(moveX) < 0.5f);

        } else if (pId == lookPointerId) {
            float dx = x - lastLookX;
            float dy = y - lastLookY;
            lastLookX = x;
            lastLookY = y;

            // Плавное вращение камеры без скачков и инверсий
            float dYaw = dx * lookSensitivityX;
            float dPitch = -dy * lookSensitivityY;

            camera.yaw += dYaw;
            camera.pitch += dPitch;
            camera.pitch = Math3D.clamp(camera.pitch, -85f, 85f);
        }
    }

    private void handlePointerUp(int pId, float x, float y, PUBGPlayer player) {
        isFiring = false;
        isLeftFiring = false;
        isReloading = false;
        isJumping = false;

        if (pId == stickPointerId) {
            stickPointerId = -1;
            moveX = 0;
            moveZ = 0;
            sprintLocked = false;
        } else if (pId == lookPointerId) {
            lookPointerId = -1;
        }
    }

    public boolean hasActiveStick() {
        return stickPointerId != -1;
    }

    public float getStickStartX() { return stickStartX; }
    public float getStickStartY() { return stickStartY; }
    public float getStickCurX() { return stickCurX; }
    public float getStickCurY() { return stickCurY; }
}
