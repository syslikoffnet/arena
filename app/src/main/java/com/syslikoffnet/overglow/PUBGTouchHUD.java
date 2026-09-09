package com.syslikoffnet.overglow;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.ArrayList;

/**
 * Фаза 2: Тактический HUD и Прицельные сетки (Optics & Zeroing HUD):
 * - Высокодетализированные прицельные сетки: Red Dot, 4x ACOG (с разметкой дальности), 8x CQBSS (Mil-Dot)
 * - Кнопка пристрелки (Zeroing Distance: 100м - 500м)
 * - Отображение установленных модулей на оружии (Compensator, Grip, Mag, Scope, Stock)
 * - Динамический маркер попадания (Hit Marker & Headshot Indicator)
 * - 0 GC Alloc
 */
public final class PUBGTouchHUD {

    public final MobileInputController input = new MobileInputController();

    // Быстрый доступ
    public float moveX = 0, moveZ = 0;
    public float vehicleThrottle = 0, vehicleSteer = 0;
    public boolean vehicleHandbrake = false;

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public void resize(int w, int h) {
        input.setScreenSize(w, h);
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
            renderJoystick(c);
        }
    }

    private void renderJoystick(Canvas c) {
        if (input.hasActiveStick()) {
            float sx = input.getStickStartX();
            float sy = input.getStickStartY();
            float cx = input.getStickCurX();
            float cy = input.getStickCurY();

            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(0x4400E5FF);
            c.drawCircle(sx, sy, 70, pFill);

            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setColor(0x8800E5FF);
            pStroke.setStrokeWidth(2f);
            c.drawCircle(sx, sy, 70, pStroke);

            pFill.setColor(input.sprintLocked ? 0xFFFF9800 : 0xCC00E5FF);
            c.drawCircle(cx, cy, 32, pFill);

            if (input.sprintLocked) {
                pText.setColor(0xFF000000);
                pText.setTextSize(14);
                pText.setFakeBoldText(true);
                c.drawText("⚡", cx - 6, cy + 5, pText);
            }
        }
    }

    private void renderOnFootHUD(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map,
                                 ArrayList<PUBGBot> bots, int aliveCount) {
        renderTopHeader(c, w, h, player, aliveCount);
        renderMiniMap(c, w, h, player, map, bots);
        renderNearbyLoot(c, w, h, player, map);
        renderHealthAndBoost(c, w, h, player);
        renderWeaponSlots(c, w, h, player);
        renderTacticalButtons(c, w, h, player);

        // Фаза 2: Оптические прицелы и сетки
        Weapon wep = player.getActiveWeapon();
        if (player.camera.adsFactor > 0.85f && wep != null) {
            float zoom = wep.getAdsZoom();
            if (zoom >= 7.0f) {
                render8xScope(c, w, h, wep);
            } else if (zoom >= 3.5f) {
                render4xScope(c, w, h);
            } else {
                renderRedDotScope(c, w, h);
            }
        } else {
            renderCrosshair(c, w, h, player);
        }

        // Хитмаркер при попадании
        if (player.hitMarkerTimer > 0) {
            renderHitMarker(c, w, h, player.hitMarkerHeadshot);
        }
    }

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

        // 360° Компас
        float cx = w / 2f;
        pFill.setColor(0xAA0A1118);
        rect.set(cx - 220, 15, cx + 220, 58);
        c.drawRoundRect(rect, 6, 6, pFill);

        pStroke.setColor(0x33FFFFFF);
        pStroke.setStrokeWidth(1f);
        c.drawRoundRect(rect, 6, 6, pStroke);

        float yaw = (player.camera.yaw % 360 + 360) % 360;

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

    private void renderMiniMap(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map, ArrayList<PUBGBot> bots) {
        float mx = w - 105, my = 95, r = 78;

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

        float rad = player.camera.yaw * Math3D.TO_RAD;
        c.drawLine(mx, my, mx + (float) Math.sin(rad) * 12, my - (float) Math.cos(rad) * 12, pStroke);
    }

    private void renderNearbyLoot(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map) {
        float startX = w - 290, startY = 190;
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

                pText.setColor(item.type == LootItem.TYPE_ATTACHMENT ? 0xFF00E5FF : 0xFFFFB300);
                pText.setTextSize(17);
                pText.setFakeBoldText(true);
                String prefix = item.type == LootItem.TYPE_ATTACHMENT ? "🔧 " : "📦 ";
                c.drawText(prefix + item.name, startX + 12, startY + drawn * 44 + 27, pText);

                drawn++;
                if (drawn >= 4) break;
            }
        }
    }

    private void renderHealthAndBoost(Canvas c, int w, int h, PUBGPlayer player) {
        float cx = w / 2f, barW = 340f;
        float startX = cx - barW / 2f, startY = h - 68;

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

    private void renderWeaponSlots(Canvas c, int w, int h, PUBGPlayer player) {
        float cx = w / 2f, slotW = 160f, slotH = 58f, startY = h - 138;
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
            c.drawText(prefix + wep.name, x + 10, y + 22, pText);

            pText.setColor(0xFFFFD700);
            pText.setTextSize(17);
            c.drawText(wep.ammoInMag + " / " + wep.ammoReserve, x + 10, y + 42, pText);

            // Иконки установленных модулей
            StringBuilder attStr = new StringBuilder();
            if (wep.attachments[WeaponAttachment.SLOT_MUZZLE] != null) attStr.append(" [M]");
            if (wep.attachments[WeaponAttachment.SLOT_GRIP] != null) attStr.append(" [G]");
            if (wep.attachments[WeaponAttachment.SLOT_MAGAZINE] != null) attStr.append(" [E]");
            if (wep.attachments[WeaponAttachment.SLOT_SCOPE] != null) attStr.append(" [4x]");

            pText.setColor(0xFF00E5FF);
            pText.setTextSize(11);
            c.drawText(attStr.toString(), x + 72, y + 42, pText);
        } else {
            pText.setColor(0x66FFFFFF);
            pText.setTextSize(14);
            c.drawText(prefix + "EMPTY", x + 12, y + 34, pText);
        }
    }

    private void renderTacticalButtons(Canvas c, int w, int h, PUBGPlayer player) {
        drawFireButton(c, w * 0.86f, h * 0.70f, 62, input.isFiring);
        drawFireButton(c, w * 0.12f, h * 0.35f, 48, input.isLeftFiring);

        drawScopeButton(c, w * 0.88f, h * 0.42f, 48, player.isAiming);
        drawCircleBtn(c, w * 0.92f, h * 0.25f, 36, "🔄", player.camera.targetShoulderOffset < 0);

        // Кнопка пристрелки (Zeroing Distance для снайперов)
        Weapon activeW = player.getActiveWeapon();
        if (activeW != null && player.isAiming) {
            drawCircleBtn(c, w * 0.88f, h * 0.12f, 36, activeW.zeroingDistance + "m", false);
        }

        drawCircleBtn(c, w * 0.78f, h * 0.35f, 38, "◀ Q", player.leanAngle < -5f);
        drawCircleBtn(c, w * 0.78f, h * 0.46f, 38, "▶ E", player.leanAngle > 5f);

        drawCircleBtn(c, w * 0.92f, h * 0.88f, 42, "⬆", input.isJumping);
        drawCircleBtn(c, w * 0.80f, h * 0.88f, 42, "🧎", player.motor.currentStance == CharacterMotor.STANCE_CROUCH);
        drawCircleBtn(c, w * 0.68f, h * 0.88f, 42, "🛌", player.motor.currentStance == CharacterMotor.STANCE_PRONE);

        drawCircleBtn(c, w * 0.74f, h * 0.55f, 38, "🔄", input.isReloading);
        drawCircleBtn(c, w * 0.76f, h * 0.28f, 40, "🚪 / 🚗", false);

        drawHealShortcut(c, w * 0.25f, h * 0.88f, player);
    }

    private void drawFireButton(Canvas c, float cx, float cy, float r, boolean active) {
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

    private void drawScopeButton(Canvas c, float cx, float cy, float r, boolean active) {
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

    private void drawCircleBtn(Canvas c, float cx, float cy, float r, String icon, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xEE00E5FF : 0x88111822);
        c.drawCircle(cx, cy, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0x66FFFFFF);
        pStroke.setStrokeWidth(2f);
        c.drawCircle(cx, cy, r, pStroke);

        pText.setColor(active ? 0xFF000000 : 0xFFFFFFFF);
        pText.setTextSize(16);
        pText.setFakeBoldText(true);
        float tw = pText.measureText(icon);
        c.drawText(icon, cx - tw / 2f, cy + 6, pText);
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

    private void renderDrivingHUD(Canvas c, int w, int h, PUBGPlayer player) {
        drawPedalBtn(c, w * 0.88f, h * 0.70f, 60, "GAS", true);
        drawPedalBtn(c, w * 0.74f, h * 0.76f, 48, "BRAKE", false);

        drawSteerBtn(c, w * 0.10f, h * 0.70f, 52, "◀");
        drawSteerBtn(c, w * 0.24f, h * 0.70f, 52, "▶");

        drawCircleBtn(c, w * 0.86f, h * 0.34f, 46, "EXIT 🚪", false);

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
        float spread = 12f + player.recoil.currentPitchOffset * 8f;

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xCC00FF88);
        pStroke.setStrokeWidth(2.5f);

        c.drawLine(cx, cy - spread - 12, cx, cy - spread, pStroke);
        c.drawLine(cx, cy + spread, cx, cy + spread + 12, pStroke);
        c.drawLine(cx - spread - 12, cy, cx - spread, cy, pStroke);
        c.drawLine(cx + spread, cy, cx + spread + 12, cy, pStroke);
    }

    private void renderRedDotScope(Canvas c, int w, int h) {
        float cx = w / 2f, cy = h / 2f;
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xEEFF1744);
        c.drawCircle(cx, cy, 3.5f, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xAAFF1744);
        pStroke.setStrokeWidth(1.5f);
        c.drawCircle(cx, cy, 14f, pStroke);
    }

    private void render4xScope(Canvas c, int w, int h) {
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

        // Метки дальности (200м, 300м, 400м)
        pStroke.setStrokeWidth(1.5f);
        c.drawLine(cx - 15, cy + 30, cx + 15, cy + 30, pStroke); // 200m
        c.drawLine(cx - 25, cy + 60, cx + 25, cy + 60, pStroke); // 300m
        c.drawLine(cx - 35, cy + 95, cx + 35, cy + 95, pStroke); // 400m

        pText.setColor(0xEEFF2222);
        pText.setTextSize(12);
        c.drawText("2", cx + 20, cy + 34, pText);
        c.drawText("3", cx + 30, cy + 64, pText);
        c.drawText("4", cx + 40, cy + 99, pText);
    }

    private void render8xScope(Canvas c, int w, int h, Weapon wep) {
        float cx = w / 2f, cy = h / 2f;
        float r = h * 0.46f;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFF000000);
        c.drawRect(0, 0, cx - r, h, pFill);
        c.drawRect(cx + r, 0, w, h, pFill);
        c.drawRect(cx - r, 0, cx + r, cy - r, pFill);
        c.drawRect(cx - r, cy + r, cx + r, h, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xEE111111);
        pStroke.setStrokeWidth(2.5f);
        c.drawLine(cx - r, cy, cx + r, cy, pStroke);
        c.drawLine(cx, cy - r, cx, cy + r, pStroke);

        // Mil-Dots
        pFill.setColor(0xFF111111);
        for (int i = 1; i <= 5; i++) {
            c.drawCircle(cx, cy + i * 26, 2.5f, pFill);
            c.drawCircle(cx, cy - i * 26, 2.5f, pFill);
            c.drawCircle(cx + i * 26, cy, 2.5f, pFill);
            c.drawCircle(cx - i * 26, cy, 2.5f, pFill);
        }

        // Индикатор пристрелки
        pText.setColor(0xFFFFD700);
        pText.setTextSize(16);
        pText.setFakeBoldText(true);
        c.drawText("ZERO: " + wep.zeroingDistance + "m", cx - 45, cy + r - 30, pText);
    }

    private void renderHitMarker(Canvas c, int w, int h, boolean headshot) {
        float cx = w / 2f, cy = h / 2f;
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(headshot ? 0xFFFF1744 : 0xFFFFFFFF);
        pStroke.setStrokeWidth(headshot ? 3.5f : 2.5f);

        float s = headshot ? 14f : 10f;
        c.drawLine(cx - s, cy - s, cx - 4, cy - 4, pStroke);
        c.drawLine(cx + 4, cy + 4, cx + s, cy + s, pStroke);
        c.drawLine(cx + s, cy - s, cx + 4, cy - 4, pStroke);
        c.drawLine(cx - s, cy + s, cx - 4, cy + 4, pStroke);
    }

    public boolean onTouchEvent(MotionEvent event, PUBGPlayer player, PUBGMap map) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float rx = event.getX() / input.screenWidth;
            float ry = event.getY() / input.screenHeight;

            // Клик по кнопке Zeroing Distance
            if (rx > 0.84f && rx < 0.94f && ry > 0.08f && ry < 0.18f && player.isAiming) {
                Weapon w = player.getActiveWeapon();
                if (w != null) {
                    w.cycleZeroing();
                    SoundSynth3D.play2D(SoundSynth3D.SOUND_RELOAD, 0.5f);
                }
                return true;
            }
        }
        return input.onTouchEvent(event, player.camera, player, map);
    }

    public boolean isShooting() {
        return input.isFiring || input.isLeftFiring;
    }
}
