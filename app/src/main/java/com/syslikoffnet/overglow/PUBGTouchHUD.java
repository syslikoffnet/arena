package com.syslikoffnet.overglow;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;

import java.util.ArrayList;

/**
 * Высокодетализированный 1-в-1 тактический интерфейс и отзывчивое управление PUBG Mobile:
 * - Плавный виртуальный джойстик ходьбы и свободного полета (Left Thumb)
 * - Естественный 360° обзор камеры и прицеливания (Right Thumb)
 * - Полноценное управление полетом при прыжке из самолета (пикирование, планирование)
 * - Тактические кнопки стрельбы, наклонов, прицела, смены стоек и транспорта
 */
public final class PUBGTouchHUD {

    // Чувствительность обзора
    public float sensitivityX = 0.24f;
    public float sensitivityY = 0.22f;

    // Ввод движения и транспорта
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
    public boolean btnInteract = false;

    // Указатели касаний
    private int stickPointerId = -1;
    private float stickStartX, stickStartY, stickCurX, stickCurY;
    private int lookPointerId = -1;
    private float lastLookX, lastLookY;

    public int screenW = 1920, screenH = 1080;

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    public void resize(int w, int h) {
        this.screenW = w;
        this.screenH = h;
    }

    public void render(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map,
                       ArrayList<PUBGBot> bots, int aliveCount, float matchTimer) {
        if (player.moveMode == PUBGPlayer.MODE_IN_PLANE) {
            renderPlaneFlightHUD(c, w, h, map);
        } else if (player.moveMode == PUBGPlayer.MODE_FREEFALL || player.moveMode == PUBGPlayer.MODE_PARACHUTE) {
            renderSkydivingHUD(c, w, h, player);
            renderJoystick(c);
        } else if (player.moveMode == PUBGPlayer.MODE_DRIVING) {
            renderDrivingHUD(c, w, h, player);
        } else {
            renderOnFootHUD(c, w, h, player, map, bots, aliveCount);
        }
    }

    // =========================================================================
    // HUD Пешком (On Foot)
    // =========================================================================
    private void renderOnFootHUD(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map,
                                 ArrayList<PUBGBot> bots, int aliveCount) {
        renderTopHeader(c, w, h, player, aliveCount);
        renderMiniMap(c, w, h, player, map, bots);
        renderNearbyLoot(c, w, h, player, map);
        renderHealthAndBoost(c, w, h, player);
        renderWeaponSlots(c, w, h, player);
        renderRealisticTouchButtons(c, w, h, player, map);
        renderJoystick(c);

        if (player.adsFactor > 0.85f && player.getActiveWeapon() != null && player.getActiveWeapon().adsZoom > 3f) {
            renderScope(c, w, h);
        } else {
            renderCrosshair(c, w, h, player);
        }
    }

    private void renderJoystick(Canvas c) {
        if (stickPointerId != -1) {
            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(0x4400E5FF);
            c.drawCircle(stickStartX, stickStartY, 70, pFill);

            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setColor(0x8800E5FF);
            pStroke.setStrokeWidth(2f);
            c.drawCircle(stickStartX, stickStartY, 70, pStroke);

            pFill.setColor(0xCC00E5FF);
            c.drawCircle(stickCurX, stickCurY, 32, pFill);
        }
    }

    // ------------------------------------------------------------- 1. Компас и Счётчики
    private void renderTopHeader(Canvas c, int w, int h, PUBGPlayer player, int aliveCount) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xCC0F1722);
        rect.set(24, 15, 240, 62);
        c.drawRoundRect(rect, 8, 8, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0x44FFFFFF);
        pStroke.setStrokeWidth(1.5f);
        c.drawRoundRect(rect, 8, 8, pStroke);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(18);
        pText.setFakeBoldText(true);
        c.drawText("ALIVE " + aliveCount, 38, 45, pText);

        pText.setColor(0xFFFF9800);
        c.drawText("KILLS " + player.kills, 145, 45, pText);

        // 360° Компас с делениями
        float cx = w / 2f;
        pFill.setColor(0xAA0A1118);
        rect.set(cx - 220, 15, cx + 220, 58);
        c.drawRoundRect(rect, 6, 6, pFill);

        pStroke.setColor(0x33FFFFFF);
        pStroke.setStrokeWidth(1f);
        c.drawRoundRect(rect, 6, 6, pStroke);

        float yaw = (player.yaw % 360 + 360) % 360;

        pStroke.setColor(0x88FFFFFF);
        for (int d = -60; d <= 60; d += 15) {
            float lineX = cx + (d * 3.2f);
            if (lineX >= cx - 210 && lineX <= cx + 210) {
                c.drawLine(lineX, 42, lineX, 52, pStroke);
            }
        }

        pText.setColor(0xFFFFD700);
        pText.setTextSize(22);
        pText.setFakeBoldText(true);
        String degStr = (int) yaw + "°";
        c.drawText(degStr, cx - pText.measureText(degStr) / 2f, 44, pText);

        String heading = "N";
        if (yaw >= 25 && yaw < 65) heading = "NE";
        else if (yaw >= 65 && yaw < 115) heading = "E";
        else if (yaw >= 115 && yaw < 155) heading = "SE";
        else if (yaw >= 155 && yaw < 205) heading = "S";
        else if (yaw >= 205 && yaw < 245) heading = "SW";
        else if (yaw >= 245 && yaw < 295) heading = "W";
        else if (yaw >= 295 && yaw < 335) heading = "NW";

        pText.setColor(0xFF00E5FF);
        pText.setTextSize(14);
        c.drawText(heading, cx - pText.measureText(heading) / 2f, 28, pText);
    }

    // ------------------------------------------------------------- 2. Мини-карта
    private void renderMiniMap(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map, ArrayList<PUBGBot> bots) {
        float mx = w - 105;
        float my = 95;
        float r = 78;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xDD111822);
        c.drawCircle(mx, my, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0x8800E5FF);
        pStroke.setStrokeWidth(2.5f);
        c.drawCircle(mx, my, r, pStroke);

        pStroke.setColor(0xFFFFFFFF);
        pStroke.setStrokeWidth(2f);
        c.drawCircle(mx, my, r * (map.whiteZoneRadius / 300f), pStroke);

        pStroke.setColor(0xFF00B0FF);
        pStroke.setStrokeWidth(3f);
        c.drawCircle(mx, my, r * (map.blueZoneRadius / 300f), pStroke);

        pFill.setColor(0xFFFFD700);
        c.drawCircle(mx, my, 5, pFill);

        float rad = player.yaw * Math3D.TO_RAD;
        c.drawLine(mx, my, mx + (float) Math.sin(rad) * 12, my - (float) Math.cos(rad) * 12, pStroke);
    }

    // ------------------------------------------------------------- 3. Лут рядом
    private void renderNearbyLoot(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map) {
        float startX = w - 290;
        float startY = 190;
        int drawn = 0;

        for (LootItem item : map.loot) {
            if (!item.isTaken && Math3D.dist(player.pos.x, player.pos.y, player.pos.z, item.x, item.y, item.z) < 3.5f) {
                pFill.setStyle(Paint.Style.FILL);
                pFill.setColor(0xDD162230);
                rect.set(startX, startY + drawn * 44, w - 24, startY + drawn * 44 + 40);
                c.drawRoundRect(rect, 6, 6, pFill);

                pStroke.setStyle(Paint.Style.STROKE);
                pStroke.setColor(0x44FFFFFF);
                pStroke.setStrokeWidth(1.5f);
                c.drawRoundRect(rect, 6, 6, pStroke);

                pText.setColor(0xFFFFB300);
                pText.setTextSize(17);
                pText.setFakeBoldText(true);
                c.drawText("📦 " + item.name, startX + 12, startY + drawn * 44 + 27, pText);

                drawn++;
                if (drawn >= 4) break;
            }
        }
    }

    // ------------------------------------------------------------- 4. HP и Буст
    private void renderHealthAndBoost(Canvas c, int w, int h, PUBGPlayer player) {
        float cx = w / 2f;
        float barW = 340f;
        float startX = cx - barW / 2f;
        float startY = h - 68;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xCC0A1018);
        rect.set(startX - 12, startY - 26, startX + barW + 12, startY + 26);
        c.drawRoundRect(rect, 8, 8, pFill);

        pFill.setColor(0xFFFF9800);
        rect.set(startX, startY - 20, startX + (barW * (player.boost / 100f)), startY - 8);
        c.drawRoundRect(rect, 3, 3, pFill);

        pFill.setColor(player.health > 25 ? 0xFFECEFF1 : 0xFFFF1744);
        rect.set(startX, startY - 4, startX + (barW * (player.health / 100f)), startY + 16);
        c.drawRoundRect(rect, 4, 4, pFill);

        pText.setColor(0xFF00E5FF);
        pText.setTextSize(14);
        pText.setFakeBoldText(true);
        c.drawText("🪖 Lv." + player.helmetLevel, startX - 70, startY + 10, pText);
        c.drawText("🛡 Lv." + player.vestLevel, startX + barW + 16, startY + 10, pText);
    }

    // ------------------------------------------------------------- 5. Оружие
    private void renderWeaponSlots(Canvas c, int w, int h, PUBGPlayer player) {
        float cx = w / 2f;
        float slotW = 160f, slotH = 58f;
        float startY = h - 138;

        renderSingleSlot(c, cx - slotW - 10, startY, slotW, slotH, player.weapons[0], player.activeSlot == 0, "1. ");
        renderSingleSlot(c, cx + 10, startY, slotW, slotH, player.weapons[1], player.activeSlot == 1, "2. ");
    }

    private void renderSingleSlot(Canvas c, float x, float y, float w, float h, Weapon wep, boolean active, String prefix) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xDD1E2F44 : 0x880A1018);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, 8, 8, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(active ? 0xFFFFB300 : 0x44FFFFFF);
        pStroke.setStrokeWidth(active ? 2.5f : 1.5f);
        c.drawRoundRect(rect, 8, 8, pStroke);

        if (wep != null) {
            pText.setColor(0xFFFFFFFF);
            pText.setTextSize(16);
            pText.setFakeBoldText(true);
            c.drawText(prefix + wep.name, x + 10, y + 24, pText);

            pText.setColor(0xFFFFD700);
            pText.setTextSize(18);
            c.drawText(wep.ammoInMag + " / " + wep.ammoReserve, x + 10, y + 48, pText);
        } else {
            pText.setColor(0x66FFFFFF);
            pText.setTextSize(14);
            c.drawText(prefix + "EMPTY", x + 12, y + 34, pText);
        }
    }

    // ------------------------------------------------------------- 6. Тактические кнопки
    private void renderRealisticTouchButtons(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map) {
        // Огонь справа (Правый палец)
        drawBulletFireButton(c, w * 0.86f, h * 0.70f, 62, btnFire);

        // Огонь слева (Левый указательный палец)
        drawBulletFireButton(c, w * 0.12f, h * 0.35f, 48, btnFireLeft);

        // Прицел (ADS Scope)
        drawOpticScopeButton(c, w * 0.88f, h * 0.42f, 48, player.isAiming);

        // Наклоны (Peek Q / E)
        drawPeekButton(c, w * 0.78f, h * 0.35f, 38, "◀ Q", player.leanAngle < -5f);
        drawPeekButton(c, w * 0.78f, h * 0.46f, 38, "▶ E", player.leanAngle > 5f);

        // Стойки: Прыжок, Присед, Лечь
        drawTacticalStanceBtn(c, w * 0.92f, h * 0.88f, 42, "⬆", btnJump);
        drawTacticalStanceBtn(c, w * 0.80f, h * 0.88f, 42, "🧎", player.isCrouching);
        drawTacticalStanceBtn(c, w * 0.68f, h * 0.88f, 42, "🛌", player.isProning);

        // Перезарядка
        drawTacticalStanceBtn(c, w * 0.74f, h * 0.55f, 38, "🔄", btnReload);

        // Взаимодействие (Двери / Машины)
        drawTacticalStanceBtn(c, w * 0.76f, h * 0.28f, 40, "🚪 / 🚗", false);

        // Быстрое лечение
        drawHealShortcut(c, w * 0.25f, h * 0.88f, player);
    }

    private void drawBulletFireButton(Canvas c, float cx, float cy, float r, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xEEFF5722 : 0xAA111822);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(active ? 0xFFFFEB3B : 0x88FFFFFF);
        pStroke.setStrokeWidth(3f);
        c.drawCircle(cx, cy, r, pStroke);

        pText.setColor(0xFFFFD700);
        pText.setTextSize(active ? 34 : 30);
        pText.setFakeBoldText(true);
        c.drawText("🔥", cx - 15, cy + 10, pText);
    }

    private void drawOpticScopeButton(Canvas c, float cx, float cy, float r, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xEE00E5FF : 0xAA111822);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(active ? 0xFFFFFFFF : 0x8800E5FF);
        pStroke.setStrokeWidth(3f);
        c.drawCircle(cx, cy, r, pStroke);

        pText.setColor(active ? 0xFF000000 : 0xFF00E5FF);
        pText.setTextSize(26);
        pText.setFakeBoldText(true);
        c.drawText("🎯", cx - 14, cy + 9, pText);
    }

    private void drawPeekButton(Canvas c, float cx, float cy, float r, String text, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xDD00E5FF : 0x88111822);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0x66FFFFFF);
        pStroke.setStrokeWidth(2f);
        c.drawCircle(cx, cy, r, pStroke);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(14);
        pText.setFakeBoldText(true);
        c.drawText(text, cx - 12, cy + 5, pText);
    }

    private void drawTacticalStanceBtn(Canvas c, float cx, float cy, float r, String icon, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xEE00E5FF : 0x88111822);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0x66FFFFFF);
        pStroke.setStrokeWidth(2f);
        c.drawCircle(cx, cy, r, pStroke);

        pText.setColor(active ? 0xFF000000 : 0xFFFFFFFF);
        pText.setTextSize(20);
        pText.setFakeBoldText(true);
        float tw = pText.measureText(icon);
        c.drawText(icon, cx - tw / 2f, cy + 7, pText);
    }

    private void drawHealShortcut(Canvas c, float cx, float cy, PUBGPlayer player) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xCC0F1722);
        c.drawCircle(cx, cy, 40, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xFF00E676);
        pStroke.setStrokeWidth(2.5f);
        c.drawCircle(cx, cy, 40, pStroke);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(18);
        pText.setFakeBoldText(true);
        c.drawText("🩹 x" + player.firstAidCount, cx - 22, cy + 6, pText);
    }

    // ------------------------------------------------------------- 7. Полет / Парашют
    private void renderPlaneFlightHUD(Canvas c, int w, int h, PUBGMap map) {
        float cx = w / 2f;
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFFFFB300);
        rect.set(cx - 150, h * 0.74f, cx + 150, h * 0.74f + 72);
        c.drawRoundRect(rect, 10, 10, pFill);

        pText.setColor(0xFF000000);
        pText.setTextSize(34);
        pText.setFakeBoldText(true);
        String jump = "▶ JUMP (ПРЫЖОК)";
        c.drawText(jump, cx - pText.measureText(jump) / 2f, h * 0.74f + 48, pText);
    }

    private void renderSkydivingHUD(Canvas c, int w, int h, PUBGPlayer player) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xCC0F1722);
        rect.set(35, h * 0.32f, 195, h * 0.68f);
        c.drawRoundRect(rect, 8, 8, pFill);

        pText.setColor(0xFF00E5FF);
        pText.setTextSize(20);
        pText.setFakeBoldText(true);
        c.drawText("ALT: " + (int) player.altitude + " m", 50, h * 0.44f, pText);

        pText.setColor(0xFFFF9800);
        c.drawText("SPD: " + (int) player.fallSpeed + " km/h", 50, h * 0.54f, pText);

        if (player.moveMode == PUBGPlayer.MODE_FREEFALL) {
            float cx = w / 2f;
            pFill.setColor(0xFF00E5FF);
            rect.set(cx - 160, h * 0.74f, cx + 160, h * 0.74f + 65);
            c.drawRoundRect(rect, 8, 8, pFill);

            pText.setColor(0xFF000000);
            pText.setTextSize(26);
            c.drawText("OPEN PARACHUTE", cx - 130, h * 0.74f + 42, pText);
        }
    }

    // ------------------------------------------------------------- 8. Вождение
    private void renderDrivingHUD(Canvas c, int w, int h, PUBGPlayer player) {
        drawPedalBtn(c, w * 0.88f, h * 0.70f, 60, "GAS", true);
        drawPedalBtn(c, w * 0.74f, h * 0.76f, 48, "BRAKE", false);

        drawSteerBtn(c, w * 0.10f, h * 0.70f, 52, "◀");
        drawSteerBtn(c, w * 0.24f, h * 0.70f, 52, "▶");

        drawTacticalStanceBtn(c, w * 0.86f, h * 0.34f, 46, "EXIT 🚪", false);

        if (player.currentVehicle != null) {
            float cx = w / 2f;
            pText.setColor(0xFFFFD700);
            pText.setTextSize(38);
            pText.setFakeBoldText(true);
            c.drawText((int) Math.abs(player.currentVehicle.speedKmH) + " km/h", cx - 65, h - 90, pText);
        }
    }

    private void drawPedalBtn(Canvas c, float cx, float cy, float r, String label, boolean isGas) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(isGas ? 0xDD2E7D32 : 0xDDC62828);
        rect.set(cx - r * 0.8f, cy - r, cx + r * 0.8f, cy + r);
        c.drawRoundRect(rect, 8, 8, pFill);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(20);
        pText.setFakeBoldText(true);
        float tw = pText.measureText(label);
        c.drawText(label, cx - tw / 2f, cy + 7, pText);
    }

    private void drawSteerBtn(Canvas c, float cx, float cy, float r, String arrow) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xDD162230);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0x88FFFFFF);
        pStroke.setStrokeWidth(2.5f);
        c.drawCircle(cx, cy, r, pStroke);

        pText.setColor(0xFF00E5FF);
        pText.setTextSize(32);
        pText.setFakeBoldText(true);
        c.drawText(arrow, cx - 14, cy + 12, pText);
    }

    private void renderCrosshair(Canvas c, int w, int h, PUBGPlayer player) {
        float cx = w / 2f, cy = h / 2f;
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

        // Кнопки стрельбы и прицела
        if (rx > 0.78f && rx < 0.94f && ry > 0.55f && ry < 0.85f) { btnFire = true; return; }
        if (rx < 0.22f && ry > 0.20f && ry < 0.50f) { btnFireLeft = true; return; }
        if (rx > 0.82f && ry > 0.32f && ry < 0.52f) { player.isAiming = !player.isAiming; return; }

        // Наклоны Peek Q/E
        if (rx > 0.74f && rx < 0.84f && ry > 0.30f && ry < 0.40f) { player.leanAngle = (player.leanAngle < -5f) ? 0f : -15f; return; }
        if (rx > 0.74f && rx < 0.84f && ry > 0.40f && ry < 0.50f) { player.leanAngle = (player.leanAngle > 5f) ? 0f : 15f; return; }

        // Стойки
        if (rx > 0.86f && ry > 0.80f) { btnJump = true; return; }
        if (rx > 0.74f && rx < 0.86f && ry > 0.80f) { player.isCrouching = !player.isCrouching; player.isProning = false; return; }
        if (rx > 0.62f && rx < 0.74f && ry > 0.80f) { player.isProning = !player.isProning; player.isCrouching = false; return; }

        // Перезарядка
        if (rx > 0.68f && rx < 0.78f && ry > 0.50f && ry < 0.62f) { btnReload = true; return; }

        // Интерактив
        if (rx > 0.70f && rx < 0.82f && ry > 0.22f && ry < 0.35f) {
            player.interactDoors(map);
            player.enterExitVehicle(map);
            return;
        }

        // Лечение
        if (rx > 0.20f && rx < 0.30f && ry > 0.80f) { player.useMedkit(); return; }

        // Смена слотов оружия
        if (rx > 0.35f && rx < 0.50f && ry > 0.80f) { player.activeSlot = 0; return; }
        if (rx > 0.50f && rx < 0.65f && ry > 0.80f) { player.activeSlot = 1; return; }

        // Левая половина экрана: Виртуальный джойстик (Left Thumb)
        if (x < screenW * 0.48f && stickPointerId == -1) {
            stickPointerId = id;
            stickStartX = x; stickStartY = y;
            stickCurX = x; stickCurY = y;
            return;
        }

        // Правая половина экрана: Обзор камеры (Right Thumb)
        if (x >= screenW * 0.48f && lookPointerId == -1) {
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
            float maxR = screenH * 0.12f;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > maxR) { dx = (dx / len) * maxR; dy = (dy / len) * maxR; }
            moveX = dx / maxR;
            moveZ = -dy / maxR; // Вверх по экрану = вперед (+moveZ)
        } else if (id == lookPointerId) {
            float dx = x - lastLookX;
            float dy = y - lastLookY;
            lastLookX = x; lastLookY = y;

            // Естественное панорамирование (вправо свайп = поворот вправо, вверх свайп = взгляд вверх)
            player.yaw += dx * sensitivityX;
            player.pitch -= dy * sensitivityY;
            player.pitch = Math3D.clamp(player.pitch, -85f, 85f);
        }
    }

    private void handleUp(int id, float x, float y, PUBGPlayer player) {
        btnFire = false;
        btnFireLeft = false;
        btnReload = false;
        btnJump = false;
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
