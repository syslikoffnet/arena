package com.syslikoffnet.overglow;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;

/**
 * Тактический 2D HUD (Standoff 2 & PUBG style):
 * - Прицел с динамическим расширением отдачи
 * - Оптический прицел AWM (Снайперский скоуп с линзой)
 * - Хитмаркеры и индикаторы направления полученного урона
 * - Здоровье, Броня, Патроны, Полоса слотов оружия
 * - Круговой Радар (Миникарта) с позициями тиммейтов, врагов и зоны
 * - Килл-фид (Лента убийств с иконками хедшота)
 */
public final class HUD2D {

    public static final class KillLog {
        public String killer;
        public String victim;
        public String weapon;
        public boolean isHeadshot;
        public float timer = 4.0f;

        public KillLog(String killer, String victim, String weapon, boolean isHeadshot) {
            this.killer = killer;
            this.victim = victim;
            this.weapon = weapon;
            this.isHeadshot = isHeadshot;
        }
    }

    public final ArrayList<KillLog> killLogs = new ArrayList<>();

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public void update(float dt) {
        for (int i = killLogs.size() - 1; i >= 0; i--) {
            killLogs.get(i).timer -= dt;
            if (killLogs.get(i).timer <= 0) {
                killLogs.remove(i);
            }
        }
    }

    public void addKill(String killer, String victim, String weapon, boolean headshot) {
        killLogs.add(new KillLog(killer, victim, weapon, headshot));
        if (killLogs.size() > 5) killLogs.remove(0);
    }

    public void render(Canvas c, int w, int h, Player player, TouchControls touch,
                       Map3D map, ArrayList<BotAI> bots, int teamTScore, int teamCTScore, float matchTimer) {
        // 1. Снайперский прицел при максимальном зуме (AWM)
        if (player.adsFactor > 0.85f && player.getActiveWeapon().adsZoom > 3.0f) {
            renderSniperScope(c, w, h);
        } else {
            // Обычное динамическое перекрестие
            renderCrosshair(c, w, h, player);
        }

        // 2. Хитмаркер при попадании по врагу
        if (player.hitMarkerTimer > 0) {
            renderHitMarker(c, w, h, player.hitMarkerHeadshot);
        }

        // 3. Индикатор получения урона
        if (player.damageIndicatorTimer > 0) {
            renderDamageIndicator(c, w, h, player.damageAttackerAngle);
        }

        // 4. Полоски HP и Брони
        renderStatusBars(c, w, h, player);

        // 5. Патроны и активное оружие
        renderAmmo(c, w, h, player);

        // 6. Слоты оружия
        renderWeaponSlots(c, w, h, player);

        // 7. Радар (Миникарта)
        renderRadar(c, w, h, player, map, bots);

        // 8. Килл-фид
        renderKillFeed(c, w, h);

        // 9. Счет матча и таймер
        renderScoreHeader(c, w, h, teamTScore, teamCTScore, matchTimer);

        // 10. Кнопки тач-управления
        renderTouchButtons(c, w, h, touch, player);
    }

    private void renderCrosshair(Canvas c, int w, int h, Player player) {
        float cx = w / 2f;
        float cy = h / 2f;

        float spread = 12f + player.recoilPitchOffset * 8f;
        if (player.isCrouching) spread *= 0.65f;
        if (player.isAiming) spread *= 0.4f;

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xCC00FF88); // Неоново-зеленый
        pStroke.setStrokeWidth(3f);

        float len = 14f;
        // Верх
        c.drawLine(cx, cy - spread - len, cx, cy - spread, pStroke);
        // Низ
        c.drawLine(cx, cy + spread, cx, cy + spread + len, pStroke);
        // Лево
        c.drawLine(cx - spread - len, cy, cx - spread, cy, pStroke);
        // Право
        c.drawLine(cx + spread, cy, cx + spread + len, cy, pStroke);

        // Центральная точка
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xEE00FF88);
        c.drawCircle(cx, cy, 2.5f, pFill);
    }

    private void renderSniperScope(Canvas c, int w, int h) {
        float cx = w / 2f;
        float cy = h / 2f;
        float r = h * 0.45f;

        // Затемнение по краям (черная маска вокруг линзы)
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFF000000);
        c.drawRect(0, 0, cx - r, h, pFill);
        c.drawRect(cx + r, 0, w, h, pFill);
        c.drawRect(cx - r, 0, cx + r, cy - r, pFill);
        c.drawRect(cx - r, cy + r, cx + r, h, pFill);

        // Черное кольцо прицела
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xFF111111);
        pStroke.setStrokeWidth(16f);
        c.drawCircle(cx, cy, r, pStroke);

        // Тонкое перекрестие
        pStroke.setColor(0xEEFF2222);
        pStroke.setStrokeWidth(2f);
        c.drawLine(cx - r, cy, cx + r, cy, pStroke);
        c.drawLine(cx, cy - r, cx, cy + r, pStroke);

        // Мил-доты (сетка)
        pFill.setColor(0xEEFF2222);
        for (int i = -4; i <= 4; i++) {
            if (i == 0) continue;
            c.drawCircle(cx + i * 45, cy, 3, pFill);
            c.drawCircle(cx, cy + i * 45, 3, pFill);
        }
    }

    private void renderHitMarker(Canvas c, int w, int h, boolean headshot) {
        float cx = w / 2f;
        float cy = h / 2f;
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(headshot ? 0xFFFF2222 : 0xFFFFFFFF);
        pStroke.setStrokeWidth(headshot ? 4f : 3f);

        float s = headshot ? 16f : 12f;
        c.drawLine(cx - s, cy - s, cx - s * 0.4f, cy - s * 0.4f, pStroke);
        c.drawLine(cx + s, cy - s, cx + s * 0.4f, cy - s * 0.4f, pStroke);
        c.drawLine(cx - s, cy + s, cx - s * 0.4f, cy + s * 0.4f, pStroke);
        c.drawLine(cx + s, cy + s, cx + s * 0.4f, cy + s * 0.4f, pStroke);
    }

    private void renderDamageIndicator(Canvas c, int w, int h, float angle) {
        float cx = w / 2f;
        float cy = h / 2f;
        c.save();
        c.rotate(angle, cx, cy);
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xAAFF1111);
        pStroke.setStrokeWidth(10f);
        rect.set(cx - 140, cy - 140, cx + 140, cy + 140);
        c.drawArc(rect, -120, 60, false, pStroke);
        c.restore();
    }

    private void renderStatusBars(Canvas c, int w, int h, Player player) {
        float startX = 30;
        float startY = h - 65;

        // Фон панели
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0x88000000);
        rect.set(startX - 10, startY - 45, startX + 260, startY + 45);
        c.drawRoundRect(rect, 8, 8, pFill);

        // HP
        pFill.setColor(0xFF222222);
        rect.set(startX, startY - 35, startX + 240, startY - 15);
        c.drawRoundRect(rect, 4, 4, pFill);
        pFill.setColor(player.health > 25 ? 0xFF00E676 : 0xFFFF1744);
        float hpW = 240f * (player.health / 100f);
        rect.set(startX, startY - 35, startX + hpW, startY - 15);
        c.drawRoundRect(rect, 4, 4, pFill);

        // Текст HP
        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(22);
        pText.setFakeBoldText(true);
        c.drawText("+ " + (int) player.health, startX + 10, startY - 18, pText);

        // Броня
        pFill.setColor(0xFF222222);
        rect.set(startX, startY + 5, startX + 240, startY + 25);
        c.drawRoundRect(rect, 4, 4, pFill);
        pFill.setColor(0xFF29B6F6);
        float apW = 240f * (player.armor / 100f);
        rect.set(startX, startY + 5, startX + apW, startY + 25);
        c.drawRoundRect(rect, 4, 4, pFill);

        // Текст AP
        c.drawText("🛡 " + (int) player.armor, startX + 10, startY + 22, pText);
    }

    private void renderAmmo(Canvas c, int w, int h, Player player) {
        Weapon wep = player.getActiveWeapon();
        if (wep == null) return;

        float startX = w - 240;
        float startY = h - 65;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0x88000000);
        rect.set(startX - 10, startY - 45, w - 20, startY + 45);
        c.drawRoundRect(rect, 8, 8, pFill);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(24);
        pText.setFakeBoldText(true);
        c.drawText(wep.name, startX, startY - 14, pText);

        if (wep.reloadTimer > 0) {
            pText.setColor(0xFFFF9800);
            pText.setTextSize(22);
            c.drawText("RELOADING...", startX, startY + 24, pText);
        } else if (!wep.isMelee) {
            pText.setColor(wep.ammoInMag > 5 ? 0xFF00E5FF : 0xFFFF3D00);
            pText.setTextSize(34);
            c.drawText(wep.ammoInMag + " / " + wep.ammoReserve, startX, startY + 28, pText);
        } else {
            pText.setColor(0xFFFFD700);
            pText.setTextSize(28);
            c.drawText("MELEE", startX, startY + 24, pText);
        }
    }

    private void renderWeaponSlots(Canvas c, int w, int h, Player player) {
        float startX = w * 0.36f;
        float slotW = w * 0.10f;
        float slotH = 50f;
        float startY = h - 65;

        for (int i = 0; i < 4; i++) {
            Weapon wep = player.inventory[i];
            if (wep == null) continue;

            boolean active = (i == player.activeSlot);
            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(active ? 0xCCFF9800 : 0x77000000);
            rect.set(startX + i * (slotW + 8), startY, startX + i * (slotW + 8) + slotW, startY + slotH);
            c.drawRoundRect(rect, 6, 6, pFill);

            pText.setColor(active ? 0xFF000000 : 0xFFFFFFFF);
            pText.setTextSize(18);
            pText.setFakeBoldText(true);
            String label = (i + 1) + ". " + wep.name;
            if (label.length() > 10) label = label.substring(0, 8) + "..";
            c.drawText(label, startX + i * (slotW + 8) + 8, startY + 32, pText);
        }
    }

    private void renderRadar(Canvas c, int w, int h, Player player, Map3D map, ArrayList<BotAI> bots) {
        float rx = 90;
        float ry = 90;
        float r = 70;

        // Фон радара
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xAA0A1118);
        c.drawCircle(rx, ry, r, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0x6600E5FF);
        pStroke.setStrokeWidth(2);
        c.drawCircle(rx, ry, r, pStroke);
        c.drawCircle(rx, ry, r * 0.5f, pStroke);

        // Игрок в центре
        pFill.setColor(0xFF00E676);
        c.drawCircle(rx, ry, 5, pFill);

        // Враги и союзники
        float rad = -player.yaw * Math3D.TO_RAD;
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        for (BotAI bot : bots) {
            if (bot.isDead) continue;
            float dx = (bot.pos.x - player.pos.x) * 1.5f;
            float dz = (bot.pos.z - player.pos.z) * 1.5f;

            float localX = dx * cos - dz * sin;
            float localY = dx * sin + dz * cos;

            if (localX * localX + localY * localY < r * r) {
                pFill.setColor(bot.team == player.team ? 0xFF29B6F6 : 0xFFFF1744);
                c.drawCircle(rx + localX, ry - localY, 4, pFill);
            }
        }
    }

    private void renderKillFeed(Canvas c, int w, int h) {
        float startX = w - 320;
        float startY = 50;

        pText.setTextSize(18);
        pText.setFakeBoldText(true);

        for (int i = 0; i < killLogs.size(); i++) {
            KillLog log = killLogs.get(i);
            pFill.setColor(0x88000000);
            rect.set(startX, startY + i * 32, w - 20, startY + i * 32 + 28);
            c.drawRoundRect(rect, 4, 4, pFill);

            pText.setColor(0xFF29B6F6);
            c.drawText(log.killer, startX + 8, startY + i * 32 + 20, pText);

            pText.setColor(0xFFFFD700);
            String mid = " [" + log.weapon + (log.isHeadshot ? " 🎯" : "") + "] ";
            c.drawText(mid, startX + 90, startY + i * 32 + 20, pText);

            pText.setColor(0xFFFF5252);
            c.drawText(log.victim, startX + 220, startY + i * 32 + 20, pText);
        }
    }

    private void renderScoreHeader(Canvas c, int w, int h, int scoreT, int scoreCT, float matchTimer) {
        float cx = w / 2f;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xAA000000);
        rect.set(cx - 150, 15, cx + 150, 65);
        c.drawRoundRect(rect, 8, 8, pFill);

        // Счет T (Красные)
        pText.setColor(0xFFFF1744);
        pText.setTextSize(26);
        pText.setFakeBoldText(true);
        c.drawText("T: " + scoreT, cx - 130, 50, pText);

        // Таймер
        int min = (int) matchTimer / 60;
        int sec = (int) matchTimer % 60;
        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(22);
        String timeStr = String.format("%02d:%02d", min, sec);
        c.drawText(timeStr, cx - 25, 50, pText);

        // Счет CT (Синие)
        pText.setColor(0xFF29B6F6);
        pText.setTextSize(26);
        c.drawText("CT: " + scoreCT, cx + 60, 50, pText);
    }

    private void renderTouchButtons(Canvas c, int w, int h, TouchControls touch, Player player) {
        // Левый Джойстик
        if (touch.isStickActive()) {
            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setColor(0x66FFFFFF);
            pStroke.setStrokeWidth(3);
            c.drawCircle(touch.getStickStartX(), touch.getStickStartY(), h * 0.14f, pStroke);

            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(0xAA00E5FF);
            c.drawCircle(touch.getStickCurX(), touch.getStickCurY(), 32, pFill);
        }

        // Кнопка Левой Стрельбы (Claw)
        drawButton(c, w * 0.11f, h * 0.35f, 45, "🔥", touch.btnFireLeft);

        // Кнопка Правой Стрельбы
        drawButton(c, w * 0.85f, h * 0.68f, 56, "🔥", touch.btnFire);

        // Кнопка Прицела (ADS)
        drawButton(c, w * 0.88f, h * 0.40f, 42, "🎯", player.isAiming);

        // Кнопка Прыжка
        drawButton(c, w * 0.92f, h * 0.90f, 38, "⬆", touch.btnJump);

        // Кнопка Приседа
        drawButton(c, w * 0.79f, h * 0.90f, 38, "🧎", player.isCrouching);

        // Кнопка Перезарядки
        drawButton(c, w * 0.71f, h * 0.47f, 38, "🔄", touch.btnReload);
    }

    private void drawButton(Canvas c, float cx, float cy, float radius, String text, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xCCFF9800 : 0x55000000);
        c.drawCircle(cx, cy, radius, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(active ? 0xFFFFD700 : 0x88FFFFFF);
        pStroke.setStrokeWidth(2.5f);
        c.drawCircle(cx, cy, radius, pStroke);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(radius * 0.9f);
        pText.setFakeBoldText(true);
        float tw = pText.measureText(text);
        c.drawText(text, cx - tw / 2f, cy + radius * 0.35f, pText);
    }
}
