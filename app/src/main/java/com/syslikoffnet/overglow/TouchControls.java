package com.syslikoffnet.overglow;

import android.view.MotionEvent;

/**
 * Мультитач-управление профессионального уровня (Standoff 2 & PUBG Mobile layout):
 * - Левый аналоговый джойстик (ходьба + лок спринта)
 * - Правая область прицеливания (Look swipe)
 * - Кнопки стрельбы под 3-4 пальца (Claw grip): левая стрельба + правая стрельба
 * - Кнопка прицела (ADS Zoom), прыжок, присед, перезарядка, переключение оружия.
 */
public final class TouchControls {

    // Чувствительность
    public float sensitivityX = 0.22f;
    public float sensitivityY = 0.20f;
    public float adsSensMultiplier = 0.65f;

    // Ввод движения
    public float moveX = 0;
    public float moveZ = 0;

    // Указатели касания
    private int movePointerId = -1;
    private float stickStartX, stickStartY;
    private float stickCurX, stickCurY;

    private int lookPointerId = -1;
    private float lastLookX, lastLookY;

    // Состояния кнопок
    public boolean btnFire = false;
    public boolean btnFireLeft = false;
    public boolean btnAds = false;
    public boolean btnJump = false;
    public boolean btnCrouch = false;
    public boolean btnReload = false;
    public int selectSlot = -1;
    public boolean btnPause = false;
    public boolean btnScoreboard = false;

    // Размеры экрана
    public int screenW = 1920;
    public int screenH = 1080;

    public void resize(int w, int h) {
        this.screenW = w;
        this.screenH = h;
    }

    public void update(Player player, float dt) {
        // Применение прыжка
        if (btnJump) {
            player.jump();
            btnJump = false;
        }

        // Применение приседа
        player.isCrouching = btnCrouch;

        // Переключение оружия
        if (selectSlot >= 0) {
            player.switchSlot(selectSlot);
            selectSlot = -1;
        }

        // Перезарядка
        if (btnReload) {
            Weapon w = player.getActiveWeapon();
            if (w != null) w.startReload();
            btnReload = false;
        }

        // Спринт при вытягивании джойстика вверх
        player.isSprinting = (moveZ > 0.85f);
    }

    public boolean onTouchEvent(MotionEvent event, Player player) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointerId = event.getPointerId(index);
        float x = event.getX(index);
        float y = event.getY(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleTouchDown(pointerId, x, y, player);
                break;

            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pId = event.getPointerId(i);
                    float px = event.getX(i);
                    float py = event.getY(i);
                    handleTouchMove(pId, px, py, player);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                handleTouchUp(pointerId, x, y, player);
                break;
        }
        return true;
    }

    private void handleTouchDown(int id, float x, float y, Player player) {
        // Проверка кнопок HUD
        float rx = x / screenW;
        float ry = y / screenH;

        // Кнопка Паузы (верхний правый угол)
        if (rx > 0.92f && ry < 0.12f) {
            btnPause = true;
            return;
        }

        // Таблица очков (верхний центр)
        if (rx > 0.40f && rx < 0.60f && ry < 0.14f) {
            btnScoreboard = !btnScoreboard;
            return;
        }

        // Левая кнопка стрельбы (для 3-4 пальцев, верхняя левая зона)
        if (rx < 0.22f && ry > 0.20f && ry < 0.50f) {
            btnFireLeft = true;
            return;
        }

        // Правая основная кнопка стрельбы
        if (rx > 0.76f && rx < 0.94f && ry > 0.52f && ry < 0.84f) {
            btnFire = true;
            return;
        }

        // Кнопка Прицела (ADS Scope)
        if (rx > 0.84f && ry > 0.30f && ry < 0.50f) {
            player.isAiming = !player.isAiming;
            return;
        }

        // Кнопка Прыжка
        if (rx > 0.86f && ry > 0.84f) {
            btnJump = true;
            return;
        }

        // Кнопка Приседа
        if (rx > 0.72f && rx < 0.86f && ry > 0.84f) {
            btnCrouch = !btnCrouch;
            return;
        }

        // Кнопка Перезарядки
        if (rx > 0.65f && rx < 0.78f && ry > 0.38f && ry < 0.56f) {
            btnReload = true;
            return;
        }

        // Слот 1 (Основное)
        if (rx > 0.36f && rx < 0.48f && ry > 0.86f) {
            selectSlot = 0;
            return;
        }
        // Слот 2 (Пистолет)
        if (rx > 0.48f && rx < 0.58f && ry > 0.86f) {
            selectSlot = 1;
            return;
        }
        // Слот 3 (Нож)
        if (rx > 0.58f && rx < 0.68f && ry > 0.86f) {
            selectSlot = 2;
            return;
        }
        // Слот 4 (Граната)
        if (rx > 0.68f && rx < 0.78f && ry > 0.86f) {
            selectSlot = 3;
            return;
        }

        // Левая половина экрана -> Виртуальный Джойстик
        if (x < screenW * 0.45f && movePointerId == -1) {
            movePointerId = id;
            stickStartX = x;
            stickStartY = y;
            stickCurX = x;
            stickCurY = y;
            return;
        }

        // Правая половина экрана -> Поворот камеры (Look Swipe)
        if (x >= screenW * 0.45f && lookPointerId == -1) {
            lookPointerId = id;
            lastLookX = x;
            lastLookY = y;
        }
    }

    private void handleTouchMove(int id, float x, float y, Player player) {
        if (id == movePointerId) {
            stickCurX = x;
            stickCurY = y;
            float dx = stickCurX - stickStartX;
            float dy = stickCurY - stickStartY;
            float maxR = screenH * 0.14f;

            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > maxR) {
                dx = (dx / len) * maxR;
                dy = (dy / len) * maxR;
            }
            moveX = dx / maxR;
            moveZ = -dy / maxR; // Вперед
        } else if (id == lookPointerId) {
            float dx = x - lastLookX;
            float dy = y - lastLookY;
            lastLookX = x;
            lastLookY = y;

            float mult = player.isAiming ? adsSensMultiplier : 1.0f;
            player.yaw -= dx * sensitivityX * mult;
            player.pitch -= dy * sensitivityY * mult;
            player.pitch = Math3D.clamp(player.pitch, -88f, 88f);
        }
    }

    private void handleTouchUp(int id, float x, float y, Player player) {
        float rx = x / screenW;
        float ry = y / screenH;

        if (rx < 0.22f && ry > 0.20f && ry < 0.50f) btnFireLeft = false;
        if (rx > 0.76f && rx < 0.94f && ry > 0.52f && ry < 0.84f) btnFire = false;

        if (id == movePointerId) {
            movePointerId = -1;
            moveX = 0;
            moveZ = 0;
        } else if (id == lookPointerId) {
            lookPointerId = -1;
        }
    }

    public boolean isShooting() {
        return btnFire || btnFireLeft;
    }

    public float getStickStartX() { return stickStartX; }
    public float getStickStartY() { return stickStartY; }
    public float getStickCurX() { return stickCurX; }
    public float getStickCurY() { return stickCurY; }
    public boolean isStickActive() { return movePointerId != -1; }
}
