package com.syslikoffnet.overglow;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Игровой HUD в духе мобильных BR (собственная реализация рисовки):
 * - Кнопки рисуются по той же раскладке, что и обработчик ввода (общая геометрия MobileInputController)
 * - Миникарта с зонами/дорогами/структурами, компас-линейка, счётчик живых
 * - Динамический прицел с разбросом, хит-маркеры, дуга направления урона
 * - Оптические сетки: коллиматор / 4x ACOG / 8x mil-dot (полноэкранные, с виньеткой)
 * - Индикатор лечения, слоты оружия с обвеса, список nearby-лута
 * - Водительский прибор (скорость/топливо), высота парения и купол
 */
public final class PUBGTouchHUD {

    public final MobileInputController input = new MobileInputController();

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    public void resize(int w, int h) {
        input.setScreenSize(w, h);
    }

    public boolean onTouchEvent(MotionEvent event, PUBGPlayer player, PUBGMap map) {
        input.onTouchEvent(event, player, map, player.camera);
        return true;
    }

    public boolean isShooting() { return input.isFiring(); }

    // =========================================================================
    public void render(Canvas c, int w, int h, PUBGGame game) {
        PUBGPlayer player = game.player;
        PUBGMap map = game.map;

        if (player.moveMode == PUBGPlayer.MODE_IN_PLANE) {
            renderPlaneHUD(c, w, h, player);
            renderJoystickBase(c, false);
            return;
        }
        if (player.moveMode == PUBGPlayer.MODE_FREEFALL || player.moveMode == PUBGPlayer.MODE_PARACHUTE) {
            renderSkydiveHUD(c, w, h, player);
            renderJoystickBase(c, true);
            renderButtons(c);
            return;
        }
        if (player.moveMode == PUBGPlayer.MODE_DRIVING) {
            renderDrivingHUD(c, w, h, player);
            renderJoystickBase(c, true);
            renderButtons(c);
            renderCompass(c, w, player.camera.yaw);
            return;
        }

        // --- Пешком ---
        boolean inScope = player.adsFactor > 0.6f && currentZoom(player) > 3f;
        if (inScope) {
            renderScopeOverlay(c, w, h, player);
        } else {
            renderCrosshair(c, w, h, player);
        }
        renderMinimap(c, w, h, player, map, game.bots);
        renderCompass(c, w, player.camera.yaw);
        renderStatus(c, w, h, player);
        renderNearbyLoot(c, w, h, player, map);
        renderKillFeed(c, game);
        renderHealBar(c, w, h, player);
        renderDamageArc(c, w, h, player);
        renderHitMarker(c, w, h, player);
        renderJoystickBase(c, true);
        renderButtons(c);
        if (player.isDead) {
            pText.setColor(0xCCFF1744);
            pText.setTextSize(30);
            pText.setTextAlign(Paint.Align.CENTER);
            c.drawText("ВЫ ВЫБЫЛИ", w / 2f, h * 0.4f, pText);
            pText.setTextAlign(Paint.Align.LEFT);
        }
    }

    private float currentZoom(PUBGPlayer player) {
        Weapon w = player.getActiveWeapon();
        return w != null ? w.getAdsZoom() : 1f;
    }

    // =========================================================================
    // Кнопки: общая геометрия с MobileInputController
    // =========================================================================
    private void renderButtons(Canvas c) {
        MobileInputController in = input;
        for (int r = 0; r < MobileInputController.NUM_ROLES; r++) {
            if (!in.visible[r]) continue;
            float x = in.bx[r], y = in.by[r], rad = in.br[r];
            boolean held = in.pressed[r];

            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(held ? 0x88FFC53D : 0x4610161E);
            c.drawCircle(x, y, rad, pFill);
            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setStrokeWidth(Math.max(1.6f, rad * 0.045f));
            pStroke.setColor(held ? 0xFFFFD766 : 0x99FFFFFF);
            c.drawCircle(x, y, rad, pStroke);

            drawIcon(c, r, x, y, rad);
        }
    }

    private void drawIcon(Canvas c, int role, float x, float y, float r) {
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(Math.max(1.5f, r * 0.07f));
        pStroke.setColor(0xF2FFFFFF);
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xF2FFFFFF);

        switch (role) {
            case MobileInputController.R_FIRE:
                c.drawCircle(x, y, r * 0.42f, pStroke);
                c.drawCircle(x, y, r * 0.14f, pFill);
                c.drawLine(x, y - r * 0.62f, x, y - r * 0.5f, pStroke);
                c.drawLine(x, y + r * 0.62f, x, y + r * 0.5f, pStroke);
                c.drawLine(x - r * 0.62f, y, x - r * 0.5f, y, pStroke);
                c.drawLine(x + r * 0.62f, y, x + r * 0.5f, y, pStroke);
                break;
            case MobileInputController.R_ADS:
                c.drawCircle(x, y, r * 0.46f, pStroke);
                c.drawCircle(x, y, r * 0.10f, pFill);
                break;
            case MobileInputController.R_JUMP: {
                path.reset();
                path.moveTo(x, y - r * 0.5f);
                path.lineTo(x - r * 0.36f, y - r * 0.04f);
                path.lineTo(x - r * 0.15f, y - r * 0.04f);
                path.lineTo(x - r * 0.15f, y + r * 0.48f);
                path.lineTo(x + r * 0.15f, y + r * 0.48f);
                path.lineTo(x + r * 0.15f, y - r * 0.04f);
                path.lineTo(x + r * 0.36f, y - r * 0.04f);
                path.close();
                c.drawPath(path, pFill);
                break;
            }
            case MobileInputController.R_CROUCH:
                c.drawLine(x - r * 0.42f, y + r * 0.25f, x + r * 0.42f, y + r * 0.25f, pStroke);
                path.reset();
                path.moveTo(x - r * 0.3f, y - r * 0.35f);
                path.lineTo(x, y + r * 0.05f);
                path.lineTo(x + r * 0.3f, y - r * 0.35f);
                c.drawPath(path, pStroke);
                break;
            case MobileInputController.R_PRONE:
                c.drawLine(x - r * 0.5f, y + r * 0.18f, x + r * 0.25f, y + r * 0.18f, pStroke);
                c.drawCircle(x + r * 0.42f, y + r * 0.02f, r * 0.13f, pFill);
                path.reset();
                path.moveTo(x - r * 0.2f, y - r * 0.4f);
                path.lineTo(x - r * 0.2f, y - r * 0.1f);
                path.lineTo(x + r * 0.05f, y - r * 0.25f);
                c.drawPath(path, pStroke);
                break;
            case MobileInputController.R_PEEK_L:
            case MobileInputController.R_PEEK_R: {
                float sgn = (role == MobileInputController.R_PEEK_L) ? -1f : 1f;
                c.drawCircle(x - sgn * r * 0.12f, y - r * 0.18f, r * 0.16f, pFill);
                path.reset();
                path.moveTo(x - sgn * r * 0.34f, y + r * 0.08f);
                path.lineTo(x + sgn * r * 0.34f, y + r * 0.08f);
                path.lineTo(x + sgn * r * 0.05f, y + r * 0.42f);
                path.lineTo(x - sgn * r * 0.34f, y + r * 0.2f);
                c.drawPath(path, pFill);
                break;
            }
            case MobileInputController.R_RELOAD:
                c.drawArc(x - r * 0.4f, y - r * 0.4f, x + r * 0.4f, y + r * 0.4f, 40, 260, false, pStroke);
                path.reset();
                path.moveTo(x + r * 0.32f, y - r * 0.52f);
                path.lineTo(x + r * 0.52f, y - r * 0.16f);
                path.lineTo(x + r * 0.14f, y - r * 0.28f);
                c.drawPath(path, pFill);
                break;
            case MobileInputController.R_HEAL:
                c.drawRoundRect(x - r * 0.12f, y - r * 0.42f, x + r * 0.12f, y + r * 0.42f, 3, 3, pFill);
                c.drawRoundRect(x - r * 0.42f, y - r * 0.12f, x + r * 0.42f, y + r * 0.12f, 3, 3, pFill);
                break;
            case MobileInputController.R_BOOST:
                path.reset();
                path.moveTo(x + r * 0.12f, y - r * 0.45f);
                path.lineTo(x - r * 0.3f, y + r * 0.06f);
                path.lineTo(x - r * 0.02f, y + r * 0.06f);
                path.lineTo(x - r * 0.14f, y + r * 0.48f);
                path.lineTo(x + r * 0.3f, y - r * 0.06f);
                path.lineTo(x + r * 0.02f, y - r * 0.06f);
                path.close();
                pFill.setColor(0xFFF2E24B);
                c.drawPath(path, pFill);
                break;
            case MobileInputController.R_THROW:
                c.drawCircle(x, y + r * 0.08f, r * 0.30f, pStroke);
                c.drawRoundRect(x - r * 0.10f, y - r * 0.48f, x + r * 0.10f, y - r * 0.18f, 2, 2, pFill);
                break;
            case MobileInputController.R_INTERACT:
                pText.setColor(0xFFFFFFFF);
                pText.setTextSize(r * 0.62f);
                pText.setTextAlign(Paint.Align.CENTER);
                c.drawText("Исп.", x, y + r * 0.22f, pText);
                pText.setTextAlign(Paint.Align.LEFT);
                break;
            case MobileInputController.R_CHUTE:
                path.reset();
                path.moveTo(x - r * 0.5f, y + r * 0.1f);
                path.quadTo(x, y - r * 0.62f, x + r * 0.5f, y + r * 0.1f);
                c.drawPath(path, pStroke);
                c.drawLine(x - r * 0.5f, y + r * 0.1f, x, y + r * 0.45f, pStroke);
                c.drawLine(x + r * 0.5f, y + r * 0.1f, x, y + r * 0.45f, pStroke);
                break;
            case MobileInputController.R_GAS:
                pText.setColor(0xFFB9F6CA);
                pText.setTextSize(r * 0.42f);
                pText.setTextAlign(Paint.Align.CENTER);
                c.drawText("ГАЗ", x, y + r * 0.14f, pText);
                pText.setTextAlign(Paint.Align.LEFT);
                break;
            case MobileInputController.R_BRAKE:
                pText.setColor(0xFFFF8A80);
                pText.setTextSize(r * 0.38f);
                pText.setTextAlign(Paint.Align.CENTER);
                c.drawText("ТОРМ", x, y + r * 0.12f, pText);
                pText.setTextAlign(Paint.Align.LEFT);
                break;
            case MobileInputController.R_HB:
                c.drawCircle(x, y, r * 0.34f, pStroke);
                c.drawLine(x, y - r * 0.6f, x, y - r * 0.34f, pStroke);
                break;
            default: {
                String lbl = shortLabel(role);
                if (lbl != null) {
                    pText.setColor(0xF2FFFFFF);
                    pText.setTextSize(r * 0.55f);
                    pText.setTextAlign(Paint.Align.CENTER);
                    c.drawText(lbl, x, y + r * 0.2f, pText);
                    pText.setTextAlign(Paint.Align.LEFT);
                }
                break;
            }
        }
    }

    private String shortLabel(int role) {
        switch (role) {
            case MobileInputController.R_FIRE_L: return "ОГОНЬ";
            case MobileInputController.R_SLOT1: return "1";
            case MobileInputController.R_SLOT2: return "2";
            case MobileInputController.R_SLOT3: return "3";
            case MobileInputController.R_FPP: return "FPP";
            case MobileInputController.R_SHOULDER: return "П";
            case MobileInputController.R_ZERO: return "ПР";
            case MobileInputController.R_HORN: return "К";
            case MobileInputController.R_EXIT: return "ВЫХОД";
            case MobileInputController.R_CAMSW: return "CAM";
            default: return null;
        }
    }

    // =========================================================================
    // Стик
    // =========================================================================
    private void renderJoystickBase(Canvas c, boolean showActive) {
        float w = input.screenWidth, h = input.screenHeight;
        float defX = w * 0.16f, defY = h * 0.72f;

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(2.5f);
        pStroke.setColor(0x33FFFFFF);
        c.drawCircle(defX, defY, input.stickRadius, pStroke);

        float ox = showActive && input.hasActiveStick() ? input.getStickStartX() : defX;
        float oy = showActive && input.hasActiveStick() ? input.getStickStartY() : defY;
        float kx = showActive && input.hasActiveStick() ? input.getStickCurX() : defX;
        float ky = showActive && input.hasActiveStick() ? input.getStickCurY() : defY;

        if (showActive) {
            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(0x33FFFFFF);
            c.drawCircle(ox, oy, input.stickRadius, pFill);
            pStroke.setColor(0x99FFFFFF);
            c.drawCircle(ox, oy, input.stickRadius, pStroke);
            pFill.setColor(input.sprintLocked ? 0xFFE8A33D : 0x99FFFFFF);
            c.drawCircle(kx, ky, input.stickRadius * 0.44f, pFill);
            pStroke.setColor(0xCCFFFFFF);
            c.drawCircle(kx, ky, input.stickRadius * 0.44f, pStroke);
        }

        if (input.sprintLocked) {
            pText.setColor(0xFFFFC53D);
            pText.setTextSize(13);
            pText.setTextAlign(Paint.Align.CENTER);
            c.drawText("БЕГ", ox, oy - input.stickRadius - 8, pText);
            pText.setTextAlign(Paint.Align.LEFT);
        }
    }

    // =========================================================================
    // Прицел
    // =========================================================================
    private void renderCrosshair(Canvas c, int w, int h, PUBGPlayer player) {
        float cx = w / 2f, cy = h / 2f;
        Weapon wep = player.getActiveWeapon();
        float spread = 6f;
        if (wep != null) spread += player.recoil.currentPitchOffset * 26f;
        spread += player.motor.horizontalSpeed * 1.5f;
        if (player.isAiming && wep != null && wep.getAdsZoom() < 3f) spread *= 0.45f;
        spread = Math3D.clamp(spread, 4f, 60f);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(2.2f);
        pStroke.setColor(0xE6FFFFFF);
        float len = 9f;
        c.drawLine(cx, cy - spread, cx, cy - spread - len, pStroke);
        c.drawLine(cx, cy + spread, cx, cy + spread + len, pStroke);
        c.drawLine(cx - spread, cy, cx - spread - len, cy, pStroke);
        c.drawLine(cx + spread, cy, cx + spread + len, cy, pStroke);
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xE6FFFFFF);
        c.drawCircle(cx, cy, 1.6f, pFill);
    }

    private void renderHitMarker(Canvas c, int w, int h, PUBGPlayer player) {
        if (player.hitMarkerTimer <= 0) return;
        float a = player.hitMarkerTimer / 0.22f;
        float cx = w / 2f, cy = h / 2f, s = 13f, g = 5f;
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(3f);
        int alpha = (int) (230 * Math3D.clamp(a, 0f, 1f));
        pStroke.setColor(player.hitMarkerHeadshot
                ? ((0xFF << 24) | 0x00FF1744)
                : ((alpha << 24) | 0x00FFFFFF));
        c.drawLine(cx - g - s, cy - g - s, cx - g, cy - g, pStroke);
        c.drawLine(cx + g + s, cy - g - s, cx + g, cy - g, pStroke);
        c.drawLine(cx - g - s, cy + g + s, cx - g, cy + g, pStroke);
        c.drawLine(cx + g + s, cy + g + s, cx + g, cy + g, pStroke);
        pStroke.setAlpha(255);
    }

    private void renderDamageArc(Canvas c, int w, int h, PUBGPlayer player) {
        if (player.damageIndicatorTimer <= 0) return;
        float a = player.damageIndicatorTimer / 0.6f;
        float cx = w / 2f, cy = h / 2f;
        float rad = (float) (player.damageAngle * Math.PI / 180.0);
        float ax = cx + (float) Math.sin(rad) * 120f;
        float ay = cy - (float) Math.cos(rad) * 120f;
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(9f);
        pStroke.setColor(0xFFE53935);
        pStroke.setAlpha((int) (200 * a));
        path.reset();
        path.moveTo(ax - 34f, ay);
        path.lineTo(ax, ay - 24f);
        path.lineTo(ax + 34f, ay);
        c.drawPath(path, pStroke);
        pStroke.setAlpha(255);
    }

    // =========================================================================
    // Оптика: коллиматор / 4x / 8x
    // =========================================================================
    private void renderScopeOverlay(Canvas c, int w, int h, PUBGPlayer player) {
        float zoom = currentZoom(player);
        float cx = w / 2f, cy = h / 2f;

        if (zoom >= 7.5f) {
            // 8x: круглая маска
            float R = Math.min(w, h) * 0.46f;
            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(0xFF000000);
            path.reset();
            path.addRect(0, 0, w, h, Path.Direction.CW);
            path.addCircle(cx, cy, R, Path.Direction.CCW);
            c.drawPath(path, pFill);
        } else {
            // 4x: горизонтальные чёрные полосы
            float band = h * 0.13f;
            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(0xEE000000);
            c.drawRect(0, 0, w, band, pFill);
            c.drawRect(0, h - band, w, h, pFill);
        }

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(1.6f);
        pStroke.setColor(0xE6FFFFFF);
        // центральный маркер + mil-dots
        c.drawLine(cx - 14, cy, cx - 4, cy, pStroke);
        c.drawLine(cx + 4, cy, cx + 14, cy, pStroke);
        c.drawLine(cx, cy - 14, cx, cy - 4, pStroke);
        c.drawLine(cx, cy + 4, cx, cy + 14, pStroke);
        float md = 18f * (zoom > 6 ? 1.2f : 1f);
        for (int i = 1; i <= 4; i++) {
            c.drawLine(cx - 4, cy + md * i, cx + 4, cy + md * i, pStroke);
        }
        for (int i = 1; i <= 2; i++) {
            c.drawLine(cx + md * i, cy - 3, cx + md * i, cy + 3, pStroke);
            c.drawLine(cx - md * i, cy - 3, cx - md * i, cy + 3, pStroke);
        }

        // Пристрелка (zeroing)
        Weapon wep = player.getActiveWeapon();
        if (wep != null) {
            pText.setColor(0xB3FFFFFF);
            pText.setTextSize(14);
            pText.setTextAlign(Paint.Align.CENTER);
            c.drawText("ZERO " + wep.zeroingDistance + "m", cx, cy + h * 0.16f, pText);
            pText.setTextAlign(Paint.Align.LEFT);
        }
    }

    // =========================================================================
    // Миникарта
    // =========================================================================
    private void renderMinimap(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map, ArrayList<PUBGBot> bots) {
        float R = h * 0.115f;
        float mx = w - R - 14, my = R + 14;
        float scale = R / 130f; // обзор ±130 м

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xB00B141C);
        c.drawCircle(mx, my, R, pFill);
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(2f);
        pStroke.setColor(0x66FFFFFF);
        c.drawCircle(mx, my, R, pStroke);

        c.save();
        path.reset();
        path.addCircle(mx, my, R - 1, Path.Direction.CW);
        c.clipPath(path);

        // Дороги
        pStroke.setColor(0x66FFFFFF);
        pStroke.setStrokeWidth(2f);
        for (float[] rd : map.roads) {
            c.drawLine(mx + (rd[0] - player.pos.x) * scale, my + (rd[1] - player.pos.z) * scale,
                       mx + (rd[2] - player.pos.x) * scale, my + (rd[3] - player.pos.z) * scale, pStroke);
        }
        // Строения
        pFill.setColor(0x99CBD3DA);
        for (PUBGMap.Obstacle o : map.obstacles) {
            float dx = (o.getCenterX() - player.pos.x) * scale;
            float dz = (o.getCenterZ() - player.pos.z) * scale;
            if (Math.abs(dx) > R + 6 || Math.abs(dz) > R + 6) continue;
            float ww = Math.max(1.5f, o.getWidth() * scale), hh2 = Math.max(1.5f, o.getDepth() * scale);
            c.drawRect(mx + dx - ww / 2f, my + dz - hh2 / 2f, mx + dx + ww / 2f, my + dz + hh2 / 2f, pFill);
        }
        // Зоны
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(2f);
        pStroke.setColor(0xFFB0BEC5);
        c.drawCircle(mx + (map.whiteZoneX - player.pos.x) * scale, my + (map.whiteZoneZ - player.pos.z) * scale,
                map.whiteZoneRadius * scale, pStroke);
        pStroke.setColor(0x9940C4FF);
        c.drawCircle(mx + (map.blueZoneX - player.pos.x) * scale, my + (map.blueZoneZ - player.pos.z) * scale,
                map.blueZoneRadius * scale, pStroke);
        // Аирдроп
        if (map.airdropActive) {
            pFill.setColor(0xFFFF5252);
            c.drawCircle(mx + (map.airdropPos.x - player.pos.x) * scale, my + (map.airdropPos.z - player.pos.z) * scale, 3.4f, pFill);
        }
        // Боты — только очень близко (перф + баланс)
        pFill.setColor(0xFFFF1744);
        for (PUBGBot b : bots) {
            if (b.isDead) continue;
            float dx = (b.pos.x - player.pos.x) * scale, dz = (b.pos.z - player.pos.z) * scale;
            if (Math.abs(dx) > R || Math.abs(dz) > R) continue;
            c.drawCircle(mx + dx, my + dz, 2.6f, pFill);
        }
        // Игрок-стрелка
        float pr = player.camera.yaw * Math3D.TO_RAD;
        float ax = mx, ay = my;
        path.reset();
        path.moveTo(ax + (float) Math.sin(pr) * 7f, ay + (float) Math.cos(pr) * 7f);
        path.lineTo(ax + (float) Math.sin(pr + 2.5f) * 6f, ay + (float) Math.cos(pr + 2.5f) * 6f);
        path.lineTo(ax + (float) Math.sin(pr - 2.5f) * 6f, ay + (float) Math.cos(pr - 2.5f) * 6f);
        path.close();
        pFill.setColor(0xFF90EE90);
        c.drawPath(path, pFill);
        c.restore();

        // Таймер зоны под миникартой
        pText.setColor(0xCCFFFFFF);
        pText.setTextSize(13);
        pText.setTextAlign(Paint.Align.CENTER);
        c.drawText(String.format(Locale.US, "ЗОНА %d  %02d:%02d", map.zonePhase,
                (int) (map.zoneShrinkTimer) / 60, (int) (map.zoneShrinkTimer) % 60), mx, my + R + 16, pText);
        pText.setTextAlign(Paint.Align.LEFT);
    }

    // =========================================================================
    // Компас-линейка
    // =========================================================================
    private void renderCompass(Canvas c, int w, float yaw) {
        float cy = 26;
        float step = w * 0.035f;
        pText.setColor(0x99FFFFFF);
        pText.setTextSize(12);
        pText.setTextAlign(Paint.Align.CENTER);
        int deg = (int) ((yaw % 360) + 360) % 360;
        for (int i = -6; i <= 6; i++) {
            int a = (deg / 15 * 15 + i * 15) % 360;
            float frac = (a - deg) / 15f * step + w / 2f;
            if (a % 45 == 0) {
                c.drawLine(frac, cy + 6, frac, cy + 14, pStroke);
                String lbl;
                if (a == 0) lbl = "С";
                else if (a == 90) lbl = "В";
                else if (a == 180) lbl = "Ю";
                else if (a == 270) lbl = "З";
                else lbl = String.valueOf(a);
                c.drawText(lbl, frac, cy + 2, pText);
            } else {
                c.drawLine(frac, cy + 9, frac, cy + 14, pStroke);
            }
        }
        pText.setTextAlign(Paint.Align.LEFT);
        pFill.setColor(0xFFFFD740);
        c.drawCircle(w / 2f, cy + 15, 2.4f, pFill);
    }

    // =========================================================================
    // Состояние: HP/броня/boost + оружие
    // =========================================================================
    private void renderStatus(Canvas c, int w, int h, PUBGPlayer player) {
        float bx = w * 0.16f, bw = w * 0.24f, by = h * 0.935f;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0x66000000);
        c.drawRoundRect(bx - 4, by - 4, bx + bw + 4, by + 26, 6, 6, pFill);

        // HP
        pFill.setColor(player.health > 25 ? 0xFF66BB6A : 0xFFE53935);
        c.drawRect(bx, by + 12, bx + bw * player.health / 100f, by + 20, pFill);
        // Boost
        pFill.setColor(0xFFFFD54F);
        c.drawRect(bx, by + 4, bx + bw * player.boost / 100f, by + 10, pFill);
        // Armor
        pFill.setColor(0xFF4FC3F7);
        float armor = Math.max(player.vestLevel, player.helmetLevel) / 3f;
        c.drawRect(bx + bw * 0.75f, by - 0f, bx + bw * 0.75f + bw * 0.25f * armor, by + 3, pFill);

        // Оружие и боезапас
        Weapon wep = player.getActiveWeapon();
        pText.setTextSize(15);
        pText.setColor(0xDDFFFFFF);
        if (wep != null) {
            String ammo = wep.ammoInMag + " / " + wep.ammoReserve;
            pText.setTextAlign(Paint.Align.LEFT);
            c.drawText(wep.name, bx, by - 34, pText);
            pText.setTextSize(20);
            pText.setFakeBoldText(true);
            c.drawText(ammo, bx + bw * 0.62f, by - 34, pText);
            pText.setFakeBoldText(false);
        }

        // Живые/убийства — как в PUBG, под компасом справа
        pText.setTextSize(15);
        pText.setColor(0xE6FFFFFF);
        pText.setTextAlign(Paint.Align.RIGHT);
        c.drawText("ЖИВЫХ: " + game_alive, w - 16, h * 0.05f, pText);
        c.drawText("УБИЙСТВ: " + player.kills, w - 16, h * 0.05f + 20, pText);
        pText.setTextAlign(Paint.Align.LEFT);

        // Слоты подписи
        pText.setColor(0x99FFFFFF);
        pText.setTextSize(11);
        for (int s = 0; s < 3; s++) {
            int role = MobileInputController.R_SLOT1 + s;
            if (!input.visible[role]) continue;
            Weapon ws = (s < player.weapons.length) ? player.weapons[s] : null;
            if (ws != null) {
                c.drawText(shortWeapon(ws), input.bx[role] - 24, input.by[role] - input.br[role] - 6, pText);
            }
        }
    }

    public int game_alive = 100; // синхронизируется игрой каждый кадр

    private String shortWeapon(Weapon w) {
        if (w == null) return "-";
        return w.name;
    }

    // =========================================================================
    // Рядом с лутом
    // =========================================================================
    private void renderNearbyLoot(Canvas c, int w, int h, PUBGPlayer player, PUBGMap map) {
        ArrayList<LootItem> near = new ArrayList<>();
        for (LootItem it : map.loot) {
            if (it.isTaken) continue;
            float d2 = Math3D.distSq(player.pos.x, player.pos.y, player.pos.z, it.x, it.y, it.z);
            if (d2 < 14f * 14f) near.add(it);
            if (near.size() >= 4) break;
        }
        if (near.isEmpty()) return;
        float lx = w - 210, ly = h * 0.30f;
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0x800B141C);
        c.drawRoundRect(lx - 8, ly - 24, lx + 200, ly + near.size() * 22 + 6, 6, 6, pFill);
        pText.setColor(0xAAFFFFFF);
        pText.setTextSize(12);
        c.drawText("ПОДБОР", lx, ly - 8, pText);
        for (int i = 0; i < near.size(); i++) {
            LootItem it = near.get(i);
            float d = (float) Math.sqrt(Math3D.distSq(player.pos.x, player.pos.y, player.pos.z, it.x, it.y, it.z));
            pText.setColor(0xFFECEFF1);
            pText.setTextSize(13);
            c.drawText(it.name, lx, ly + i * 22 + 8, pText);
            pText.setColor(0xFF90A4AE);
            pText.setTextSize(11);
            c.drawText(String.format(Locale.US, "%.0fm", d), lx + 168, ly + i * 22 + 8, pText);
        }
    }

    // =========================================================================
    private void renderKillFeed(Canvas c, PUBGGame game) {
        float y = h_feedStart;
        for (int i = 0; i < game.killFeed.length; i++) {
            String s = game.killFeed[i];
            if (s == null || game.killFeedT[i] <= 0) continue;
            int a = (int) (255 * Math3D.clamp(game.killFeedT[i], 0f, 1f));
            pText.setTextSize(14);
            pText.setARGB(a, 255, 255, 255);
            float tw = pText.measureText(s);
            pFill.setStyle(Paint.Style.FILL);
            pFill.setARGB(a / 2, 10, 14, 20);
            c.drawRoundRect(16, y - 15, 16 + tw + 12, y + 4, 4, 4, pFill);
            c.drawText(s, 22, y, pText);
            y += 22;
        }
        pText.setAlpha(255);
    }

    private final float h_feedStart = 74f;

    // =========================================================================
    private void renderHealBar(Canvas c, int w, int h, PUBGPlayer player) {
        if (player.healTimer <= 0) return;
        float prog = 1f - player.healTimer / Math.max(0.001f, player.maxHealTimer);
        float bw = 240, bx = w / 2f - bw / 2f, by = h * 0.78f;
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0x99000000);
        c.drawRoundRect(bx - 6, by - 6, bx + bw + 6, by + 18, 6, 6, pFill);
        pFill.setColor(0xFF66BB6A);
        c.drawRect(bx, by, bx + bw * prog, by + 6, pFill);
        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(12);
        c.drawText("ЛЕЧЕНИЕ", bx + bw + 12, by + 8, pText);
    }

    // =========================================================================
    // Вождение
    // =========================================================================
    private void renderDrivingHUD(Canvas c, int w, int h, PUBGPlayer player) {
        Vehicle3D v = player.currentVehicle;
        if (v == null) return;
        float cx = w / 2f, by = h - 60;
        pText.setTextAlign(Paint.Align.CENTER);
        pText.setTextSize(34);
        pText.setFakeBoldText(true);
        pText.setColor(0xF2FFFFFF);
        c.drawText(String.valueOf((int) Math.abs(v.speedKmH)), cx, by, pText);
        pText.setFakeBoldText(false);
        pText.setTextSize(12);
        pText.setColor(0xAARRGGBB | 0xFF000000);
        pText.setColor(0x99FFFFFF);
        c.drawText("км/ч", cx, by + 16, pText);
        // Топливо
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0x66000000);
        c.drawRoundRect(cx - 70, by + 22, cx + 70, by + 32, 4, 4, pFill);
        pFill.setColor(v.fuel > 25 ? 0xFFFFD54F : 0xFFE53935);
        c.drawRect(cx - 68, by + 24, cx - 68 + 136 * v.fuel / 100f, by + 30, pFill);
        // Режим D/R
        pText.setTextSize(13);
        pText.setColor(v.speedKmH < -1 ? 0xFFFF8A65 : 0xFFB9F6CA);
        c.drawText(v.speedKmH < -1 ? "R" : "D", cx + 100, by, pText);
        pText.setTextAlign(Paint.Align.LEFT);

        pText.setColor(0xCCFFFFFF);
        pText.setTextSize(13);
        pText.setTextAlign(Paint.Align.CENTER);
        c.drawText("СТИК = РУЛЬ · ГАЗ/ТОРМ справа", w / 2f, 30, pText);
        pText.setTextAlign(Paint.Align.LEFT);
    }

    // =========================================================================
    // Прыжок с парашютом
    // =========================================================================
    private void renderSkydiveHUD(Canvas c, int w, int h, PUBGPlayer player) {
        float kmh = Math.abs(player.fallSpeed);
        pText.setTextAlign(Paint.Align.CENTER);
        pText.setTextSize(15);
        pText.setColor(0xE6FFFFFF);
        c.drawText(String.format(Locale.US, "ВЫСОТА %.0fm  ·  %.0f км/ч", player.altitude, kmh), w / 2f, 44, pText);

        if (player.moveMode == PUBGPlayer.MODE_FREEFALL) {
            pText.setColor(kmh > 200 ? 0xFFFF8A65 : 0xFF90CAF9);
            c.drawText(kmh > 200 ? "СТОЕЧНЫЙ РЕЖИМ" : (kmh > 160 ? "ПРЫЖОК ВПЕРЕД" : "ПАДЕНИЕ"), w / 2f, 66, pText);
            if (player.altitude > 70) {
                pText.setColor(0xB3FFFFFF);
                pText.setTextSize(13);
                c.drawText("Расскроется автоматически на 50м", w / 2f, h * 0.92f, pText);
            }
        } else {
            pText.setColor(0xFFA5D6A7);
            c.drawText("ПАРАШЮТ ОТКРЫТ — управляй стиком", w / 2f, 66, pText);
        }
        pText.setTextAlign(Paint.Align.LEFT);
    }

    private void renderPlaneHUD(Canvas c, int w, int h, PUBGPlayer player) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xCC0B141C);
        rect.set(w / 2f - 170, h * 0.72f, w / 2f + 170, h * 0.72f + 74);
        c.drawRoundRect(rect, 10, 10, pFill);
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(2.5f);
        pStroke.setColor(input.pressed[MobileInputController.R_CHUTE] ? 0xFFFFD740 : 0xFFFF7043);
        c.drawRoundRect(rect, 10, 10, pStroke);
        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(22);
        pText.setFakeBoldText(true);
        pText.setTextAlign(Paint.Align.CENTER);
        c.drawText("НАЖМИ — ПРЫЖОК", w / 2f, h * 0.72f + 46, pText);
        pText.setFakeBoldText(false);
        pText.setTextSize(13);
        pText.setColor(0xB3FFFFFF);
        c.drawText("Свободный падение: управляй взглядом", w / 2f, h * 0.72f + 66, pText);
        pText.setTextAlign(Paint.Align.LEFT);
    }
}
