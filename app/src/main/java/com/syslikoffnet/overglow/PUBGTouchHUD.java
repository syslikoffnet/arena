package com.syslikoffnet.overglow;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.ArrayList;

/**
 * 1-в-1 Интерфейс и HUD PUBG Mobile:
 * - Лобби: Желтая кнопка "НАЧАТЬ", выбор карт (Erangel, Miramar, Sanhok), TPP/FPP, валюта BP/UC
 * - Полет на самолете: Кнопка "ПРЫЖОК", спидометр (км/ч) и высотомер (м)
 * - HUD в игре: 360° Компас, Мини-карта с кругом зоны, список лута поблизости, 2 слота оружия
 * - Управление транспортом (Педали Газ/Тормоз, Руль, Спидометр)
 * - Экран победы "WINNER WINNER CHICKEN DINNER!"
 */
public final class PUBGTouchHUD {

    // Чувствительность
    public float sensitivityX = 0.22f;
    public float sensitivityY = 0.20f;

    // Ввод
    public float moveX = 0, moveZ = 0;
    public float vehicleThrottle = 0, vehicleSteer = 0;
    public boolean vehicleHandbrake = false;

    // Кнопки
    public boolean btnFire = false;
    public boolean btnFireLeft = false;
    public boolean btnJump = false;
    public boolean btnCrouch = false;
    public boolean btnProne = false;
    public boolean btnReload = false;
    public boolean btnInteract = false; // дверь / машина
    public boolean btnJumpPlane = false;
    public boolean btnOpenChute = false;

    // Указатели касания
    private int stickPointerId = -1;
    private float stickStartX, stickStartY, stickCurX, stickCurY;
    private int lookPointerId = -1;
    private float lastLookX, lastLookY;

    // Свободный обзор ("Глаз")
    private int eyePointerId = -1;
    private float eyeStartX, eyeStartY;

    public int screenW = 1920, screenH = 1080;

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public void resize(int w, int h) {
        this.screenW = w;
        this.screenH = h;
    }

    // =========================================================================
    // Отрисовка HUD в зависимости от состояния
    // =========================================================================
    public void render(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map,
                       ArrayList<PUBGBot> bots, int aliveCount, float matchTimer) {
        if (player.moveMode == PUBGPlayer.MODE_IN_PLANE) {
            renderPlaneFlightHUD(c, w, h, map);
        } else if (player.moveMode == PUBGPlayer.MODE_FREEFALL || player.moveMode == PUBGPlayer.MODE_PARACHUTE) {
            renderSkydivingHUD(c, w, h, player);
        } else if (player.moveMode == PUBGPlayer.MODE_DRIVING) {
            renderDrivingHUD(c, w, h, player);
        } else {
            renderOnFootHUD(c, w, h, player, map, bots, aliveCount);
        }
    }

    private void renderOnFootHUD(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map,
                                 ArrayList<PUBGBot> bots, int aliveCount) {
        // 1. Верхняя панель: ALIVE, KILLED, Компас
        renderTopHeader(c, w, h, player, aliveCount);

        // 2. Радар и Мини-карта
        renderMiniMap(c, w, h, player, map, bots);

        // 3. Список лута поблизости (Proximity Loot)
        renderNearbyLoot(c, w, h, player, map);

        // 4. Полоса здоровья и буста
        renderHealthAndBoost(c, w, h, player);

        // 5. Оружие (2 слота + пистолет + сковорода)
        renderWeaponSlots(c, w, h, player);

        // 6. Кнопки тач-управления (Стрельба, Прицел, Прыжок, Присед, Дверь/Машина)
        renderTouchButtons(c, w, h, player, map);

        // 7. Снайперский прицел или перекрестие
        if (player.adsFactor > 0.85f && player.getActiveWeapon() != null && player.getActiveWeapon().adsZoom > 3f) {
            renderScope(c, w, h);
        } else {
            renderCrosshair(c, w, h, player);
        }
    }

    private void renderTopHeader(Canvas c, int w, int h, PUBGPlayer player, int aliveCount) {
        // ALIVE & KILLS (Верхний левый угол)
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xAA111722);
        rect.set(20, 15, 230, 65);
        c.drawRoundRect(rect, 6, 6, pFill);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(20);
        pText.setFakeBoldText(true);
        c.drawText("ALIVE " + aliveCount, 35, 48, pText);

        pText.setColor(0xFFFF9800);
        c.drawText("KILLS " + player.kills, 140, 48, pText);

        // 360° Компас (Верхний центр)
        float cx = w / 2f;
        pFill.setColor(0x88000000);
        rect.set(cx - 200, 15, cx + 200, 55);
        c.drawRoundRect(rect, 4, 4, pFill);

        float yaw = (player.yaw % 360 + 360) % 360;
        pText.setColor(0xFFFFD700);
        pText.setTextSize(22);
        String degStr = (int) yaw + "°";
        c.drawText(degStr, cx - pText.measureText(degStr) / 2f, 44, pText);

        // Маркер сторон света (N, E, S, W)
        String heading = "N";
        if (yaw >= 45 && yaw < 135) heading = "E";
        else if (yaw >= 135 && yaw < 225) heading = "S";
        else if (yaw >= 225 && yaw < 315) heading = "W";
        pText.setColor(0xFF00E5FF);
        pText.setTextSize(16);
        c.drawText(heading, cx - 6, 30, pText);
    }

    private void renderMiniMap(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map, ArrayList<PUBGBot> bots) {
        float mx = w - 100;
        float my = 90;
        float r = 75;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xCC1A232E);
        c.drawCircle(mx, my, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0x88FFFFFF);
        pStroke.setStrokeWidth(2f);
        c.drawCircle(mx, my, r, pStroke);

        // Белый круг зоны
        pStroke.setColor(0xFFFFFFFF);
        c.drawCircle(mx, my, r * (map.whiteZoneRadius / 300f), pStroke);

        // Синий круг зоны
        pStroke.setColor(0xFF29B6F6);
        c.drawCircle(mx, my, r * (map.blueZoneRadius / 300f), pStroke);

        // Игрок в центре
        pFill.setColor(0xFFFFD700);
        c.drawCircle(mx, my, 5, pFill);
    }

    private void renderNearbyLoot(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map) {
        float startX = w - 280;
        float startY = 180;
        int drawn = 0;

        for (LootItem item : map.loot) {
            if (!item.isTaken && Math3D.dist(player.pos.x, player.pos.y, player.pos.z, item.x, item.y, item.z) < 3.5f) {
                pFill.setStyle(Paint.Style.FILL);
                pFill.setColor(0xAA1E2836);
                rect.set(startX, startY + drawn * 42, w - 20, startY + drawn * 42 + 38);
                c.drawRoundRect(rect, 4, 4, pFill);

                pText.setColor(0xFFFF9800);
                pText.setTextSize(18);
                c.drawText("📦 " + item.name, startX + 10, startY + drawn * 42 + 26, pText);
                drawn++;
                if (drawn >= 4) break;
            }
        }
    }

    private void renderHealthAndBoost(Canvas c, int w, int h, PUBGPlayer player) {
        float cx = w / 2f;
        float barW = 320f;
        float startX = cx - barW / 2f;
        float startY = h - 65;

        // Фон HP
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0x99000000);
        rect.set(startX - 10, startY - 25, startX + barW + 10, startY + 25);
        c.drawRoundRect(rect, 6, 6, pFill);

        // Полоса Буста (Энергетик - оранжевая)
        pFill.setColor(0xFFFF9800);
        rect.set(startX, startY - 20, startX + (barW * (player.boost / 100f)), startY - 8);
        c.drawRoundRect(rect, 2, 2, pFill);

        // Полоса Здоровья (HP - белая/красная)
        pFill.setColor(player.health > 25 ? 0xFFFFFFFF : 0xFFFF1744);
        rect.set(startX, startY - 4, startX + (barW * (player.health / 100f)), startY + 16);
        c.drawRoundRect(rect, 3, 3, pFill);
    }

    private void renderWeaponSlots(Canvas c, int w, int h, PUBGPlayer player) {
        float cx = w / 2f;
        float slotW = 150f;

        // Слот 1
        renderSingleSlot(c, cx - slotW - 8, h - 130, slotW, 55, player.weapons[0], player.activeSlot == 0, "1. ");
        // Слот 2
        renderSingleSlot(c, cx + 8, h - 130, slotW, 55, player.weapons[1], player.activeSlot == 1, "2. ");
    }

    private void renderSingleSlot(Canvas c, float x, float y, float w, float h, Weapon wep, boolean active, String prefix) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xCCFF9800 : 0x77111822);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, 4, 4, pFill);

        pText.setColor(active ? 0xFF000000 : 0xFFFFFFFF);
        pText.setTextSize(18);
        pText.setFakeBoldText(true);
        if (wep != null) {
            c.drawText(prefix + wep.name, x + 8, y + 24, pText);
            c.drawText(wep.ammoInMag + " / " + wep.ammoReserve, x + 8, y + 46, pText);
        } else {
            c.drawText(prefix + "EMPTY", x + 8, y + 34, pText);
        }
    }

    private void renderTouchButtons(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map) {
        // Джойстик
        if (stickPointerId != -1) {
            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setColor(0x55FFFFFF);
            pStroke.setStrokeWidth(3f);
            c.drawCircle(stickStartX, stickStartY, h * 0.14f, pStroke);

            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(0xCC00E5FF);
            c.drawCircle(stickCurX, stickCurY, 34, pFill);
        }

        // Кнопка Огня
        drawCircleBtn(c, w * 0.85f, h * 0.70f, 55, "🔥", btnFire);

        // Левая стрельба под палец (Claw)
        drawCircleBtn(c, w * 0.12f, h * 0.35f, 44, "🔥", btnFireLeft);

        // Кнопка Прицела (ADS)
        drawCircleBtn(c, w * 0.88f, h * 0.42f, 42, "🎯", player.isAiming);

        // Прыжок, Присед, Лечь (Prone)
        drawCircleBtn(c, w * 0.92f, h * 0.88f, 38, "⬆", btnJump);
        drawCircleBtn(c, w * 0.80f, h * 0.88f, 38, "🧎", player.isCrouching);
        drawCircleBtn(c, w * 0.68f, h * 0.88f, 38, "🛌", player.isProning);

        // Кнопка "Дверь / Войти в машину"
        drawCircleBtn(c, w * 0.75f, h * 0.50f, 40, "🚪 / 🚗", false);

        // Кнопка аптечки
        drawCircleBtn(c, w * 0.25f, h * 0.88f, 38, "🩹 " + player.firstAidCount, false);

        // Кнопка "Глаз" (Free Look)
        drawCircleBtn(c, w * 0.75f, h * 0.26f, 34, "👁", player.isFreeLooking);
    }

    private void renderPlaneFlightHUD(Canvas c, int w, int h, PUBGMap map) {
        float cx = w / 2f;
        // Кнопка ПРЫГНУТЬ
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFFFF9800);
        rect.set(cx - 140, h * 0.75f, cx + 140, h * 0.75f + 70);
        c.drawRoundRect(rect, 10, 10, pFill);

        pText.setColor(0xFF000000);
        pText.setTextSize(32);
        pText.setFakeBoldText(true);
        String jumpText = "JUMP (ПРЫЖОК)";
        c.drawText(jumpText, cx - pText.measureText(jumpText) / 2f, h * 0.75f + 46, pText);
    }

    private void renderSkydivingHUD(Canvas c, int w, int h, PUBGPlayer player) {
        // Высотомер слева
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xAA000000);
        rect.set(40, h * 0.30f, 180, h * 0.70f);
        c.drawRoundRect(rect, 6, 6, pFill);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(22);
        c.drawText("ALT: " + (int) player.altitude + "m", 55, h * 0.40f, pText);
        c.drawText("SPD: " + (int) player.fallSpeed + " km/h", 55, h * 0.48f, pText);

        if (player.moveMode == PUBGPlayer.MODE_FREEFALL) {
            // Кнопка открытия парашюта
            float cx = w / 2f;
            pFill.setColor(0xFF00E5FF);
            rect.set(cx - 150, h * 0.75f, cx + 150, h * 0.75f + 65);
            c.drawRoundRect(rect, 8, 8, pFill);

            pText.setColor(0xFF000000);
            pText.setTextSize(26);
            c.drawText("OPEN PARACHUTE", cx - 120, h * 0.75f + 42, pText);
        }
    }

    private void renderDrivingHUD(Canvas c, int w, int h, PUBGPlayer player) {
        // Педаль Газа
        drawCircleBtn(c, w * 0.88f, h * 0.70f, 55, "GAS", false);
        // Педаль Тормоза / Задний ход
        drawCircleBtn(c, w * 0.75f, h * 0.75f, 45, "BRAKE", false);
        // Руль Влево / Вправо
        drawCircleBtn(c, w * 0.10f, h * 0.70f, 48, "◀", false);
        drawCircleBtn(c, w * 0.22f, h * 0.70f, 48, "▶", false);
        // Выйти из машины
        drawCircleBtn(c, w * 0.85f, h * 0.35f, 45, "EXIT 🚪", false);

        // Спидометр
        if (player.currentVehicle != null) {
            float cx = w / 2f;
            pText.setColor(0xFFFFD700);
            pText.setTextSize(36);
            pText.setFakeBoldText(true);
            c.drawText((int) Math.abs(player.currentVehicle.speedKmH) + " km/h", cx - 60, h - 90, pText);
        }
    }

    private void renderCrosshair(Canvas c, int w, int h, PUBGPlayer player) {
        float cx = w / 2f;
        float cy = h / 2f;
        float spread = 12f + player.recoilPitch * 6f;

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xCC00FF88);
        pStroke.setStrokeWidth(2.5f);

        c.drawLine(cx, cy - spread - 12, cx, cy - spread, pStroke);
        c.drawLine(cx, cy + spread, cx, cy + spread + 12, pStroke);
        c.drawLine(cx - spread - 12, cy, cx - spread, cy, pStroke);
        c.drawLine(cx + spread, cy, cx + spread + 12, cy, pStroke);
    }

    private void renderScope(Canvas c, int w, int h) {
        float cx = w / 2f, cy = h / 2f;
        float r = h * 0.44f;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFF000000);
        c.drawRect(0, 0, cx - r, h, pFill);
        c.drawRect(cx + r, 0, w, h, pFill);
        c.drawRect(cx - r, 0, cx + r, cy - r, pFill);
        c.drawRect(cx - r, cy + r, cx + r, h, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xEEFF2222);
        pStroke.setStrokeWidth(2f);
        c.drawLine(cx - r, cy, cx + r, cy, pStroke);
        c.drawLine(cx, cy - r, cx, cy + r, pStroke);
    }

    private void drawCircleBtn(Canvas c, float cx, float cy, float r, String text, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xCCFF9800 : 0x66111722);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0x88FFFFFF);
        pStroke.setStrokeWidth(2f);
        c.drawCircle(cx, cy, r, pStroke);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(r * 0.75f);
        pText.setFakeBoldText(true);
        float tw = pText.measureText(text);
        c.drawText(text, cx - tw / 2f, cy + r * 0.35f, pText);
    }

    // =========================================================================
    // Обработка касаний
    // =========================================================================
    public boolean onTouchEvent(MotionEvent event, PUBGPlayer player, PUBGMap map) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointerId = event.getPointerId(index);
        float x = event.getX(index);
        float y = event.getY(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleDown(pointerId, x, y, player, map);
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pId = event.getPointerId(i);
                    handleMove(pId, event.getX(i), event.getY(i), player);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                handleUp(pointerId, x, y, player);
                break;
        }
        return true;
    }

    private void handleDown(int id, float x, float y, PUBGPlayer player, PUBGMap map) {
        float rx = x / screenW;
        float ry = y / screenH;

        if (player.moveMode == PUBGPlayer.MODE_IN_PLANE) {
            if (rx > 0.35f && rx < 0.65f && ry > 0.70f) {
                player.jumpFromPlane(map.planePos, 0);
                return;
            }
        }

        if (player.moveMode == PUBGPlayer.MODE_FREEFALL) {
            if (rx > 0.35f && rx < 0.65f && ry > 0.70f) {
                player.openParachute();
                return;
            }
        }

        if (player.moveMode == PUBGPlayer.MODE_DRIVING) {
            if (rx > 0.80f && ry > 0.60f) vehicleThrottle = 1f;
            else if (rx > 0.68f && rx < 0.80f && ry > 0.65f) vehicleThrottle = -1f;
            else if (rx < 0.16f && ry > 0.60f) vehicleSteer = -1f;
            else if (rx > 0.16f && rx < 0.30f && ry > 0.60f) vehicleSteer = 1f;
            else if (rx > 0.75f && ry < 0.45f) player.enterExitVehicle(map);
            return;
        }

        // Кнопка Огня
        if (rx > 0.78f && rx < 0.94f && ry > 0.55f && ry < 0.85f) { btnFire = true; return; }
        if (rx < 0.22f && ry > 0.20f && ry < 0.50f) { btnFireLeft = true; return; }

        // Прицел
        if (rx > 0.82f && ry > 0.32f && ry < 0.52f) { player.isAiming = !player.isAiming; return; }

        // Прыжок, Присед, Лечь
        if (rx > 0.86f && ry > 0.80f) { btnJump = true; return; }
        if (rx > 0.74f && rx < 0.86f && ry > 0.80f) { player.isCrouching = !player.isCrouching; player.isProning = false; return; }
        if (rx > 0.62f && rx < 0.74f && ry > 0.80f) { player.isProning = !player.isProning; player.isCrouching = false; return; }

        // Дверь / Машина
        if (rx > 0.70f && rx < 0.82f && ry > 0.42f && ry < 0.58f) {
            player.interactDoors(map);
            player.enterExitVehicle(map);
            return;
        }

        // Аптечка
        if (rx > 0.20f && rx < 0.32f && ry > 0.80f) { player.useMedkit(); return; }

        // Смена слотов оружия
        if (rx > 0.35f && rx < 0.50f && ry > 0.80f) { player.activeSlot = 0; return; }
        if (rx > 0.50f && rx < 0.65f && ry > 0.80f) { player.activeSlot = 1; return; }

        // Джойстик слева
        if (x < screenW * 0.45f && stickPointerId == -1) {
            stickPointerId = id;
            stickStartX = x; stickStartY = y;
            stickCurX = x; stickCurY = y;
            return;
        }

        // Вращение камеры справа
        if (x >= screenW * 0.45f && lookPointerId == -1) {
            lookPointerId = id;
            lastLookX = x;
            lastLookY = y;
        }
    }

    private void handleMove(int id, float x, float y, PUBGPlayer player) {
        if (id == stickPointerId) {
            stickCurX = x; stickCurY = y;
            float dx = stickCurX - stickStartX;
            float dy = stickCurY - stickStartY;
            float maxR = screenH * 0.14f;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > maxR) { dx = (dx / len) * maxR; dy = (dy / len) * maxR; }
            moveX = dx / maxR;
            moveZ = -dy / maxR;
        } else if (id == lookPointerId) {
            float dx = x - lastLookX;
            float dy = y - lastLookY;
            lastLookX = x; lastLookY = y;
            player.yaw -= dx * sensitivityX;
            player.pitch -= dy * sensitivityY;
            player.pitch = Math3D.clamp(player.pitch, -88f, 88f);
        }
    }

    private void handleUp(int id, float x, float y, PUBGPlayer player) {
        btnFire = false;
        btnFireLeft = false;
        vehicleThrottle = 0;
        vehicleSteer = 0;

        if (id == stickPointerId) {
            stickPointerId = -1;
            moveX = 0; moveZ = 0;
        } else if (id == lookPointerId) {
            lookPointerId = -1;
        }
    }

    public boolean isShooting() { return btnFire || btnFireLeft; }
}
