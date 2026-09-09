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
 * Высокодетализированный 1-в-1 тактический интерфейс и HUD PUBG Mobile:
 * - Аутентичные векторные кнопки (Огонь с пулей, Прицел со сеткой, Наклоны Peek Q/E,
 *   Прыжок, Присед, Положение лёжа, Педали Газ/Тормоз, Клаксон)
 * - 360° Компас с засечками и сторонами света (N, NE, E, SE, S, SW, W, NW)
 * - Интерактивный список ближайшего лута (Proximity Loot) с иконками калибров
 * - Мини-карта с радаром шагов, выстрелов и кругами зоны
 */
public final class PUBGTouchHUD {

    // Чувствительность
    public float sensitivityX = 0.22f;
    public float sensitivityY = 0.20f;

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

        if (player.adsFactor > 0.85f && player.getActiveWeapon() != null && player.getActiveWeapon().adsZoom > 3f) {
            renderScope(c, w, h);
        } else {
            renderCrosshair(c, w, h, player);
        }
    }

    // ------------------------------------------------------------- 1. Компас и Счётчики
    private void renderTopHeader(Canvas c, int w, int h, PUBGPlayer player, int aliveCount) {
        // ALIVE & KILLS
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

        // Насечки градусов
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

        // Белый круг безопасной зоны
        pStroke.setColor(0xFFFFFFFF);
        pStroke.setStrokeWidth(2f);
        c.drawCircle(mx, my, r * (map.whiteZoneRadius / 300f), pStroke);

        // Синяя стена зоны
        pStroke.setColor(0xFF00B0FF);
        pStroke.setStrokeWidth(3f);
        c.drawCircle(mx, my, r * (map.blueZoneRadius / 300f), pStroke);

        // Игрок в центре со стрелкой взгляда
        pFill.setColor(0xFFFFD700);
        c.drawCircle(mx, my, 5, pFill);

        float rad = -player.yaw * Math3D.TO_RAD;
        c.drawLine(mx, my, mx + (float) Math.sin(-rad) * 12, my - (float) Math.cos(-rad) * 12, pStroke);
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

        // Буст (Оранжевый)
        pFill.setColor(0xFFFF9800);
        rect.set(startX, startY - 20, startX + (barW * (player.boost / 100f)), startY - 8);
        c.drawRoundRect(rect, 3, 3, pFill);

        // Здоровье (Белый / Красный при <25%)
        pFill.setColor(player.health > 25 ? 0xFFECEFF1 : 0xFFFF1744);
        rect.set(startX, startY - 4, startX + (barW * (player.health / 100f)), startY + 16);
        c.drawRoundRect(rect, 4, 4, pFill);

        // Шлем и Броня иконки рядом
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
        pFill.setColor(active ? 0xDD243547 : 0xAA111822);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, 6, 6, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(active ? 0xFFFFB300 : 0x44FFFFFF);
        pStroke.setStrokeWidth(active ? 2.5f : 1f);
        c.drawRoundRect(rect, 6, 6, pStroke);

        pText.setColor(active ? 0xFFFFB300 : 0xFFFFFFFF);
        pText.setTextSize(17);
        pText.setFakeBoldText(true);
        if (wep != null) {
            c.drawText(prefix + wep.name, x + 10, y + 26, pText);
            pText.setColor(0xFF00E5FF);
            pText.setTextSize(15);
            c.drawText(wep.ammoInMag + " / " + wep.ammoReserve + "  AUTO", x + 10, y + 48, pText);
        } else {
            c.drawText(prefix + "EMPTY", x + 10, y + 36, pText);
        }
    }

    // ------------------------------------------------------------- 6. Кнопки PUBG
    private void renderRealisticTouchButtons(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map) {
        // Левый аналоговый джойстик с автоспринтом
        if (stickPointerId != -1) {
            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setColor(0x55FFFFFF);
            pStroke.setStrokeWidth(3f);
            c.drawCircle(stickStartX, stickStartY, h * 0.14f, pStroke);

            // Иконка бегущего человечка (Спринт) сверху
            pText.setColor(0xFFFFB300);
            pText.setTextSize(18);
            c.drawText("🔒 SPRINT", stickStartX - 38, stickStartY - (h * 0.15f), pText);

            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(0xEE00E5FF);
            c.drawCircle(stickCurX, stickCurY, 34, pFill);
        }

        // 1. Главная кнопка ОГНЯ (Правая) - Векторная пуля и дульная вспышка
        drawFireButton(c, w * 0.85f, h * 0.70f, 56, btnFire);

        // 2. Левая кнопка ОГНЯ (под указательный палец Claw)
        drawFireButton(c, w * 0.12f, h * 0.35f, 45, btnFireLeft);

        // 3. Кнопка Прицела (ADS Scope с сеткой)
        drawScopeButton(c, w * 0.88f, h * 0.42f, 44, player.isAiming);

        // 4. Наклоны (Peek Left & Right Q / E)
        drawLeanButton(c, w * 0.72f, h * 0.40f, 34, "◀", player.leanAngle < -5f);
        drawLeanButton(c, w * 0.79f, h * 0.40f, 34, "▶", player.leanAngle > 5f);

        // 5. Прыжок, Присед, Лечь (Prone)
        drawTacticalStanceBtn(c, w * 0.92f, h * 0.88f, 38, "JUMP ⬆", btnJump);
        drawTacticalStanceBtn(c, w * 0.80f, h * 0.88f, 38, "CROUCH 🧎", player.isCrouching);
        drawTacticalStanceBtn(c, w * 0.68f, h * 0.88f, 38, "PRONE 🛌", player.isProning);

        // 6. Перезарядка
        drawTacticalStanceBtn(c, w * 0.72f, h * 0.52f, 36, "RELOAD 🔄", btnReload);

        // 7. Кнопка "Дверь / Войти в транспорт"
        drawDoorVehicleBtn(c, w * 0.75f, h * 0.28f, 42);

        // 8. Кнопка Аптечки и Энергетика
        drawMedkitQuickBtn(c, w * 0.24f, h * 0.88f, 38, "🩹 " + player.firstAidCount);

        // 9. Кнопка "Глаз" (Free-Look 360°)
        drawEyeButton(c, w * 0.75f, h * 0.16f, 32, player.isFreeLooking);
    }

    private void drawFireButton(Canvas c, float cx, float cy, float r, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xEEFF9800 : 0xAA111822);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(active ? 0xFFFFD700 : 0x88FFFFFF);
        pStroke.setStrokeWidth(3f);
        c.drawCircle(cx, cy, r, pStroke);

        // Рисование пули в центре
        pFill.setColor(0xFFFFD700);
        rect.set(cx - 8, cy - 14, cx + 8, cy + 14);
        c.drawRoundRect(rect, 4, 4, pFill);

        pFill.setColor(0xFFFF5722);
        c.drawCircle(cx, cy - 18, 5, pFill);
    }

    private void drawScopeButton(Canvas c, float cx, float cy, float r, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xEE00E5FF : 0xAA111822);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(active ? 0xFFFFFFFF : 0x8800E5FF);
        pStroke.setStrokeWidth(2.5f);
        c.drawCircle(cx, cy, r * 0.7f, pStroke);

        // Перекрестие
        c.drawLine(cx - r * 0.6f, cy, cx + r * 0.6f, cy, pStroke);
        c.drawLine(cx, cy - r * 0.6f, cx, cy + r * 0.6f, pStroke);
    }

    private void drawLeanButton(Canvas c, float cx, float cy, float r, String arrow, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xEEFFB300 : 0x88111822);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0x88FFFFFF);
        pStroke.setStrokeWidth(1.5f);
        c.drawCircle(cx, cy, r, pStroke);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(18);
        pText.setFakeBoldText(true);
        c.drawText("PEEK " + arrow, cx - 22, cy + 6, pText);
    }

    private void drawTacticalStanceBtn(Canvas c, float cx, float cy, float r, String text, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xEEFF9800 : 0x88111822);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(active ? 0xFFFFD700 : 0x55FFFFFF);
        pStroke.setStrokeWidth(2f);
        c.drawCircle(cx, cy, r, pStroke);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(12);
        pText.setFakeBoldText(true);
        float tw = pText.measureText(text);
        c.drawText(text, cx - tw / 2f, cy + 4, pText);
    }

    private void drawDoorVehicleBtn(Canvas c, float cx, float cy, float r) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xDD162230);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xFFFFB300);
        pStroke.setStrokeWidth(2f);
        c.drawCircle(cx, cy, r, pStroke);

        pText.setColor(0xFFFFB300);
        pText.setTextSize(14);
        pText.setFakeBoldText(true);
        c.drawText("DRIVE / OPEN", cx - 42, cy - 2, pText);
        c.drawText("🚪 / 🚗", cx - 20, cy + 18, pText);
    }

    private void drawMedkitQuickBtn(Canvas c, float cx, float cy, float r, String label) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xDD162230);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xFF00E676);
        pStroke.setStrokeWidth(2f);
        c.drawCircle(cx, cy, r, pStroke);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(14);
        pText.setFakeBoldText(true);
        c.drawText(label, cx - 24, cy + 5, pText);
    }

    private void drawEyeButton(Canvas c, float cx, float cy, float r, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xEE00E5FF : 0x88111822);
        c.drawCircle(cx, cy, r, pFill);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(18);
        c.drawText("👁", cx - 8, cy + 6, pText);
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
        // Педаль Газа (Gas)
        drawPedalBtn(c, w * 0.88f, h * 0.70f, 60, "GAS", true);
        // Педаль Тормоза (Brake)
        drawPedalBtn(c, w * 0.74f, h * 0.76f, 48, "BRAKE", false);

        // Руль Влево / Вправо
        drawSteerBtn(c, w * 0.10f, h * 0.70f, 52, "◀");
        drawSteerBtn(c, w * 0.24f, h * 0.70f, 52, "▶");

        // Выйти из машины
        drawTacticalStanceBtn(c, w * 0.86f, h * 0.34f, 46, "EXIT 🚪", false);

        // Спидометр по центру
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

        if (rx > 0.78f && rx < 0.94f && ry > 0.55f && ry < 0.85f) { btnFire = true; return; }
        if (rx < 0.22f && ry > 0.20f && ry < 0.50f) { btnFireLeft = true; return; }

        if (rx > 0.82f && ry > 0.32f && ry < 0.52f) { player.isAiming = !player.isAiming; return; }

        if (rx > 0.86f && ry > 0.80f) { btnJump = true; return; }
        if (rx > 0.74f && rx < 0.86f && ry > 0.80f) { player.isCrouching = !player.isCrouching; player.isProning = false; return; }
        if (rx > 0.62f && rx < 0.74f && ry > 0.80f) { player.isProning = !player.isProning; player.isCrouching = false; return; }

        if (rx > 0.68f && rx < 0.78f && ry > 0.46f && ry < 0.58f) { btnReload = true; return; }

        if (rx > 0.70f && rx < 0.82f && ry > 0.22f && ry < 0.35f) {
            player.interactDoors(map);
            player.enterExitVehicle(map);
            return;
        }

        if (rx > 0.20f && rx < 0.30f && ry > 0.80f) { player.useMedkit(); return; }

        if (rx > 0.35f && rx < 0.50f && ry > 0.80f) { player.activeSlot = 0; return; }
        if (rx > 0.50f && rx < 0.65f && ry > 0.80f) { player.activeSlot = 1; return; }

        if (x < screenW * 0.45f && stickPointerId == -1) {
            stickPointerId = id;
            stickStartX = x; stickStartY = y;
            stickCurX = x; stickCurY = y;
            return;
        }

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
        btnReload = false;
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
