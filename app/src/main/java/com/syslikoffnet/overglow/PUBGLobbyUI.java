package com.syslikoffnet.overglow;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * 1-в-1 Главное Меню и Лобби PUBG Mobile:
 * - Точный интерфейс: Профиль, Валюты (BP, AG, UC), Royale Pass (C7S20)
 * - Виджет команды / друзей слева, микрофон, динамик, чат
 * - Меню выбора карт (Erangel, Miramar, Sanhok, Livik, TPP/FPP, Solo/Squad)
 * - Большая фирменная жёлтая кнопка "▶ НАЧАТЬ / START" с оценкой времени
 * - Нижняя панель: Сезон, RP, Мастерская, Инвентарь, Миссии, Клан, Магазин
 * - Окна: Выбор режима, Инвентарь со скинами, Настройки графики, звука и гироскопа
 */
public final class PUBGLobbyUI {

    public static final int MODAL_NONE = 0;
    public static final int MODAL_MAP_SELECT = 1;
    public static final int MODAL_INVENTORY = 2;
    public static final int MODAL_SETTINGS = 3;
    public static final int MODAL_SHOP = 4;
    public static final int MODAL_ROYALE_PASS = 5;

    public int currentModal = MODAL_NONE;

    public static final int MAP_ERANGEL = 0;
    public static final int MAP_MIRAMAR = 1;
    public static final int MAP_SANHOK = 2;
    public static final int MAP_LIVIK = 3;
    public int selectedMap = MAP_ERANGEL;

    public boolean isTPP = true;
    public int teamMode = 0;

    public int invTab = 0;
    public int selectedGunSkin = 0;
    public int selectedHelmetSkin = 2;

    public int graphicsQuality = 2;
    public int gyroMode = PUBGGyroscope.MODE_ALWAYS_ON;

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    public void render(Canvas c, int w, int h, PUBGPlayer player, float matchingTimer) {
        renderTopProfileBar(c, w, h);
        renderRoyalePassWidget(c, w, h);
        renderCurrenciesBar(c, w, h);
        renderLeftTeamDock(c, w, h);
        renderStartButtonAndMapWidget(c, w, h);
        renderBottomNavMenu(c, w, h);

        if (matchingTimer > 0) {
            renderMatchmakingPopup(c, w, h, matchingTimer);
        }

        if (currentModal == MODAL_MAP_SELECT) {
            renderMapSelectDialog(c, w, h);
        } else if (currentModal == MODAL_INVENTORY) {
            renderInventoryModal(c, w, h, player);
        } else if (currentModal == MODAL_SETTINGS) {
            renderSettingsModal(c, w, h);
        } else if (currentModal == MODAL_SHOP) {
            renderShopModal(c, w, h);
        } else if (currentModal == MODAL_ROYALE_PASS) {
            renderRoyalePassModal(c, w, h);
        }
    }

    private void renderTopProfileBar(Canvas c, int w, int h) {
        float avX = 20, avY = 15, avS = 54;
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFF1E2836);
        rect.set(avX, avY, avX + avS, avY + avS);
        c.drawRoundRect(rect, 8, 8, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xFFFFD700);
        pStroke.setStrokeWidth(3f);
        c.drawRoundRect(rect, 8, 8, pStroke);

        pFill.setColor(0xFFFF9800);
        rect.set(avX - 2, avY + avS - 16, avX + 22, avY + avS + 2);
        c.drawRoundRect(rect, 4, 4, pFill);
        pText.setColor(0xFF000000);
        pText.setTextSize(12);
        pText.setFakeBoldText(true);
        c.drawText("68", avX + 2, avY + avS - 3, pText);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(18);
        pText.setFakeBoldText(true);
        c.drawText("PUBG_PRO_99", avX + avS + 12, avY + 20, pText);

        pText.setColor(0xFFFFD700);
        pText.setTextSize(14);
        c.drawText("👑 CROWN I • 4,180 RP", avX + avS + 12, avY + 40, pText);

        pText.setColor(0xFF00E5FF);
        pText.setTextSize(13);
        c.drawText("❤️ 1,420 • RU", avX + avS + 12, avY + 56, pText);
    }

    private void renderRoyalePassWidget(Canvas c, int w, int h) {
        float rx = w * 0.42f, ry = 15;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xCC111822);
        rect.set(rx, ry, rx + 160, ry + 45);
        c.drawRoundRect(rect, 6, 6, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xFFFFB300);
        pStroke.setStrokeWidth(1.5f);
        c.drawRoundRect(rect, 6, 6, pStroke);

        pText.setColor(0xFFFFB300);
        pText.setTextSize(16);
        pText.setFakeBoldText(true);
        c.drawText("RP C7S20", rx + 12, ry + 22, pText);

        pFill.setColor(0xFF263238);
        rect.set(rx + 12, ry + 28, rx + 148, ry + 36);
        c.drawRoundRect(rect, 3, 3, pFill);

        pFill.setColor(0xFFFFB300);
        rect.set(rx + 12, ry + 28, rx + 12 + (136 * 0.64f), ry + 36);
        c.drawRoundRect(rect, 3, 3, pFill);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(11);
        c.drawText("Lv. 64 / 100", rx + 85, ry + 22, pText);
    }

    private void renderCurrenciesBar(Canvas c, int w, int h) {
        float curX = w - 380;
        float curY = 15;

        renderCurrencyPill(c, curX, curY, "🪙", "85,240", 0xFFFFD700);
        renderCurrencyPill(c, curX + 110, curY, "G", "2,450", 0xFF81C784);
        renderCurrencyPill(c, curX + 210, curY, "💎", "1,200", 0xFF00E5FF);

        drawSmallIconBtn(c, w - 50, curY + 16, "⚙️");
        drawSmallIconBtn(c, w - 85, curY + 16, "✉️");
    }

    private void renderCurrencyPill(Canvas c, float x, float y, String icon, String value, int color) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xCC111822);
        rect.set(x, y, x + 98, y + 32);
        c.drawRoundRect(rect, 16, 16, pFill);

        pText.setColor(color);
        pText.setTextSize(14);
        pText.setFakeBoldText(true);
        c.drawText(icon, x + 8, y + 21, pText);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(13);
        c.drawText(value, x + 26, y + 21, pText);

        pText.setColor(0xFF00E676);
        pText.setTextSize(14);
        c.drawText("+", x + 84, y + 21, pText);
    }

    private void drawSmallIconBtn(Canvas c, float cx, float cy, String icon) {
        pFill.setColor(0xAA111822);
        c.drawCircle(cx, cy, 16, pFill);
        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(16);
        c.drawText(icon, cx - 8, cy + 6, pText);
    }

    private void renderLeftTeamDock(Canvas c, int w, int h) {
        float startY = h * 0.32f;
        float slotW = 120, slotH = 46;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xAA162230);
        rect.set(20, startY, 20 + slotW, startY + slotH);
        c.drawRoundRect(rect, 6, 6, pFill);

        pText.setColor(0xFFFFD700);
        pText.setTextSize(13);
        pText.setFakeBoldText(true);
        c.drawText("⭐ 1. You", 28, startY + 20, pText);
        pText.setColor(0xFF00E676);
        pText.setTextSize(11);
        c.drawText("READY", 28, startY + 36, pText);

        for (int i = 1; i <= 3; i++) {
            float y = startY + i * (slotH + 8);
            pFill.setColor(0x550A1018);
            rect.set(20, y, 20 + slotW, y + slotH);
            c.drawRoundRect(rect, 6, 6, pFill);

            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setColor(0x44FFFFFF);
            pStroke.setStrokeWidth(1.5f);
            c.drawRoundRect(rect, 6, 6, pStroke);

            pText.setColor(0x88FFFFFF);
            pText.setTextSize(13);
            c.drawText("+ INVITE " + (i + 1), 32, y + 28, pText);
        }

        drawMicrophoneControls(c, 20, startY + 4 * (slotH + 8) + 10);
    }

    private void drawMicrophoneControls(Canvas c, float x, float y) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xAA111822);
        rect.set(x, y, x + 120, y + 32);
        c.drawRoundRect(rect, 6, 6, pFill);

        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(13);
        pText.setFakeBoldText(true);
        c.drawText("🎤 Team", x + 10, y + 21, pText);
        c.drawText("🔊 All", x + 70, y + 21, pText);
    }

    private void renderStartButtonAndMapWidget(Canvas c, int w, int h) {
        float btnW = 280, btnH = 68;
        float btnX = 40, btnY = h - btnH - 25;

        float mapBoxY = btnY - 58;
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xCC111822);
        rect.set(btnX, mapBoxY, btnX + btnW, mapBoxY + 50);
        c.drawRoundRect(rect, 8, 8, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0x6600E5FF);
        pStroke.setStrokeWidth(1.5f);
        c.drawRoundRect(rect, 8, 8, pStroke);

        pFill.setColor(0xFFFF9800);
        rect.set(btnX + 10, mapBoxY + 8, btnX + 68, mapBoxY + 24);
        c.drawRoundRect(rect, 3, 3, pFill);
        pText.setColor(0xFF000000);
        pText.setTextSize(10);
        pText.setFakeBoldText(true);
        c.drawText("RANKED", btnX + 14, mapBoxY + 20, pText);

        String mapName = "CLASSIC • " + getMapName(selectedMap);
        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(15);
        c.drawText(mapName, btnX + 76, mapBoxY + 21, pText);

        String modeStr = (isTPP ? "TPP" : "FPP") + " • " + (teamMode == 0 ? "SOLO (БОТЫ)" : "SQUAD");
        pText.setColor(0xFFFFD700);
        pText.setTextSize(13);
        c.drawText(modeStr, btnX + 12, mapBoxY + 42, pText);

        pText.setColor(0xFF00E5FF);
        pText.setTextSize(16);
        c.drawText("▼", btnX + btnW - 24, mapBoxY + 32, pText);

        pFill.setColor(0xFFFFB300);
        rect.set(btnX, btnY, btnX + btnW, btnY + btnH);
        c.drawRoundRect(rect, 10, 10, pFill);

        pText.setColor(0xFF000000);
        pText.setTextSize(32);
        pText.setFakeBoldText(true);
        String startText = "▶ START";
        c.drawText(startText, btnX + 30, btnY + 46, pText);

        pText.setTextSize(14);
        c.drawText("⏱ 0:02", btnX + btnW - 85, btnY + 42, pText);
    }

    private void renderBottomNavMenu(Canvas c, int w, int h) {
        float startX = w - 580;
        float btnY = h - 65;
        float itemW = 92;

        String[] menuItems = {"🏆 SEASON", "🛡 RP", "🎒 INV", "🔫 LAB", "📋 TASKS", "🛒 SHOP"};
        int[] colors = {0xFFFFD700, 0xFFFFB300, 0xFF00E5FF, 0xFFE040FB, 0xFF81C784, 0xFFFF5252};

        for (int i = 0; i < menuItems.length; i++) {
            float x = startX + i * itemW;
            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(0xCC111822);
            rect.set(x, btnY, x + itemW - 6, btnY + 50);
            c.drawRoundRect(rect, 6, 6, pFill);

            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setColor(colors[i]);
            pStroke.setStrokeWidth(1.5f);
            c.drawRoundRect(rect, 6, 6, pStroke);

            pText.setColor(0xFFFFFFFF);
            pText.setTextSize(13);
            pText.setFakeBoldText(true);
            float tw = pText.measureText(menuItems[i]);
            c.drawText(menuItems[i], x + (itemW - 6 - tw) / 2f, btnY + 31, pText);
        }
    }

    private void renderMatchmakingPopup(Canvas c, int w, int h, float timer) {
        float cx = w / 2f, cy = h / 2f;
        float mw = 360, mh = 180;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xEE0D141F);
        rect.set(cx - mw / 2f, cy - mh / 2f, cx + mw / 2f, cy + mh / 2f);
        c.drawRoundRect(rect, 12, 12, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(0xFFFFB300);
        pStroke.setStrokeWidth(2f);
        c.drawRoundRect(rect, 12, 12, pStroke);

        pText.setColor(0xFFFFB300);
        pText.setTextSize(22);
        pText.setFakeBoldText(true);
        String mTitle = "MATCHMAKING...";
        c.drawText(mTitle, cx - pText.measureText(mTitle) / 2f, cy - 30, pText);

        pText.setColor(0xFF00E5FF);
        pText.setTextSize(36);
        String timeStr = "00:0" + (int) Math.ceil(timer);
        c.drawText(timeStr, cx - pText.measureText(timeStr) / 2f, cy + 18, pText);

        pFill.setColor(0xFFFF5252);
        rect.set(cx - 60, cy + 38, cx + 60, cy + 72);
        c.drawRoundRect(rect, 6, 6, pFill);
        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(16);
        c.drawText("CANCEL", cx - 32, cy + 60, pText);
    }

    private void renderMapSelectDialog(Canvas c, int w, int h) {
        c.drawColor(0xCC000000);
        float cx = w / 2f, cy = h / 2f;
        float dw = w * 0.82f, dh = h * 0.82f;
        float dx = cx - dw / 2f, dy = cy - dh / 2f;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFA0F1722);
        rect.set(dx, dy, dx + dw, dy + dh);
        c.drawRoundRect(rect, 12, 12, pFill);

        pText.setColor(0xFFFFB300);
        pText.setTextSize(26);
        pText.setFakeBoldText(true);
        c.drawText("SELECT MAP & MODE (ВЫБОР КАРТЫ)", dx + 30, dy + 45, pText);

        float cardW = (dw - 80) / 4f;
        float cardH = dh * 0.46f;
        float cardY = dy + 70;

        drawMapCard(c, dx + 30, cardY, cardW, cardH, "ERANGEL", "8x8 km • Classic", 0xFF4CAF50, selectedMap == 0);
        drawMapCard(c, dx + 30 + (cardW + 7), cardY, cardW, cardH, "MIRAMAR", "8x8 km • Desert", 0xFFFF9800, selectedMap == 1);
        drawMapCard(c, dx + 30 + (cardW + 7) * 2, cardY, cardW, cardH, "SANHOK", "4x4 km • Jungle", 0xFF00E676, selectedMap == 2);
        drawMapCard(c, dx + 30 + (cardW + 7) * 3, cardY, cardW, cardH, "LIVIK", "2x2 km • Fast Pace", 0xFF00E5FF, selectedMap == 3);

        float optY = cardY + cardH + 25;
        drawToggle(c, dx + 30, optY, 140, 42, "TPP (3-Е ЛИЦО)", isTPP);
        drawToggle(c, dx + 180, optY, 140, 42, "FPP (1-Е ЛИЦО)", !isTPP);

        drawToggle(c, dx + 360, optY, 110, 42, "SOLO", teamMode == 0);
        drawToggle(c, dx + 480, optY, 110, 42, "SQUAD", teamMode == 2);

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFFFFB300);
        rect.set(dx + dw - 180, dy + dh - 60, dx + dw - 30, dy + dh - 15);
        c.drawRoundRect(rect, 8, 8, pFill);

        pText.setColor(0xFF000000);
        pText.setTextSize(20);
        pText.setFakeBoldText(true);
        c.drawText("CONFIRM", dx + dw - 155, dy + dh - 30, pText);
    }

    private void drawMapCard(Canvas c, float x, float y, float w, float h, String name, String desc, int color, boolean selected) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(selected ? 0xDD1E2F44 : 0x88111822);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, 8, 8, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(selected ? 0xFFFFB300 : 0x44FFFFFF);
        pStroke.setStrokeWidth(selected ? 3f : 1.5f);
        c.drawRoundRect(rect, 8, 8, pStroke);

        pText.setColor(color);
        pText.setTextSize(20);
        pText.setFakeBoldText(true);
        c.drawText(name, x + 16, y + 36, pText);

        pText.setColor(0xAAFFFFFF);
        pText.setTextSize(13);
        c.drawText(desc, x + 16, y + 60, pText);

        if (selected) {
            pFill.setColor(0xFFFFB300);
            rect.set(x + w - 32, y + 8, x + w - 8, y + 32);
            c.drawRoundRect(rect, 4, 4, pFill);
            pText.setColor(0xFF000000);
            pText.setTextSize(16);
            c.drawText("✓", x + w - 26, y + 26, pText);
        }
    }

    private void drawToggle(Canvas c, float x, float y, float w, float h, String text, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xEEFFB300 : 0x66162230);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, 6, 6, pFill);

        pText.setColor(active ? 0xFF000000 : 0xFFFFFFFF);
        pText.setTextSize(14);
        pText.setFakeBoldText(true);
        float tw = pText.measureText(text);
        c.drawText(text, x + (w - tw) / 2f, y + h * 0.62f, pText);
    }

    private void renderInventoryModal(Canvas c, int w, int h, PUBGPlayer player) {
        c.drawColor(0xCC000000);
        float cx = w / 2f, cy = h / 2f;
        float dw = w * 0.88f, dh = h * 0.85f;
        float dx = cx - dw / 2f, dy = cy - dh / 2f;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFA0F1722);
        rect.set(dx, dy, dx + dw, dy + dh);
        c.drawRoundRect(rect, 12, 12, pFill);

        pText.setColor(0xFF00E5FF);
        pText.setTextSize(26);
        pText.setFakeBoldText(true);
        c.drawText("INVENTORY & WEAPON SKINS", dx + 30, dy + 45, pText);

        float tabX = dx + dw - 480;
        float tabY = dy + 70;

        drawSkinCard(c, tabX, tabY, 440, 60, "M416 • Glacier Lv.7 (Ледник)", 0xFF80D8FF, selectedGunSkin == 0);
        drawSkinCard(c, tabX, tabY + 70, 440, 60, "AKM • Dragonfire (Дракон)", 0xFFFF5252, selectedGunSkin == 1);
        drawSkinCard(c, tabX, tabY + 140, 440, 60, "AWM • Golden Pharaoh (Золото)", 0xFFFFD700, selectedGunSkin == 2);
        drawSkinCard(c, tabX, tabY + 210, 440, 60, "Helmet Lv.3 • Cyber Samurai", 0xFFE040FB, true);

        pFill.setColor(0xFF37474F);
        rect.set(dx + 30, dy + dh - 60, dx + 180, dy + dh - 15);
        c.drawRoundRect(rect, 6, 6, pFill);
        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(18);
        c.drawText("← CLOSE", dx + 65, dy + dh - 30, pText);
    }

    private void drawSkinCard(Canvas c, float x, float y, float w, float h, String text, int color, boolean active) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(active ? 0xDD1E2F44 : 0x77111822);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, 8, 8, pFill);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(active ? color : 0x33FFFFFF);
        pStroke.setStrokeWidth(2f);
        c.drawRoundRect(rect, 8, 8, pStroke);

        pText.setColor(color);
        pText.setTextSize(16);
        pText.setFakeBoldText(true);
        c.drawText(text, x + 16, y + 36, pText);

        if (active) {
            pText.setColor(0xFF00E676);
            pText.setTextSize(14);
            c.drawText("EQUIPPED", x + w - 95, y + 36, pText);
        }
    }

    private void renderSettingsModal(Canvas c, int w, int h) {
        c.drawColor(0xCC000000);
        float cx = w / 2f, cy = h / 2f;
        float dw = w * 0.78f, dh = h * 0.85f;
        float dx = cx - dw / 2f, dy = cy - dh / 2f;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFA0F1722);
        rect.set(dx, dy, dx + dw, dy + dh);
        c.drawRoundRect(rect, 12, 12, pFill);

        pText.setColor(0xFFFFB300);
        pText.setTextSize(26);
        pText.setFakeBoldText(true);
        c.drawText("SETTINGS (НАСТРОЙКИ PUBG MOBILE)", dx + 30, dy + 45, pText);

        // 1. Графика
        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(18);
        c.drawText("GRAPHICS QUALITY:", dx + 30, dy + 90, pText);
        drawToggle(c, dx + 30, dy + 105, 110, 38, "SMOOTH", graphicsQuality == 0);
        drawToggle(c, dx + 150, dy + 105, 110, 38, "BALANCED", graphicsQuality == 1);
        drawToggle(c, dx + 270, dy + 105, 110, 38, "HD (60 FPS)", graphicsQuality == 2);
        drawToggle(c, dx + 390, dy + 105, 110, 38, "ULTRA 90", graphicsQuality == 3);

        // 2. Гироскоп (Gyroscope Aiming)
        pText.setColor(0xFF00E5FF);
        c.drawText("GYROSCOPE (ГИРОСКОП):", dx + 30, dy + 175, pText);
        drawToggle(c, dx + 30, dy + 190, 150, 38, "ALWAYS ON (ВСЕГДА)", gyroMode == PUBGGyroscope.MODE_ALWAYS_ON);
        drawToggle(c, dx + 190, dy + 190, 150, 38, "SCOPE ON (ПРИЦЕЛ)", gyroMode == PUBGGyroscope.MODE_SCOPE_ONLY);
        drawToggle(c, dx + 350, dy + 190, 110, 38, "CLOSE (ВЫКЛ)", gyroMode == PUBGGyroscope.MODE_OFF);

        // 3. Звук
        pText.setColor(0xFFFFFFFF);
        c.drawText("AUDIO & 3D SPATIAL SOUND:", dx + 30, dy + 260, pText);
        drawToggle(c, dx + 30, dy + 275, 140, 38, "ENABLED (ВКЛ)", SoundSynth3D.enabled);
        drawToggle(c, dx + 180, dy + 275, 140, 38, "MUTED (ВЫКЛ)", !SoundSynth3D.enabled);

        // Закрыть
        pFill.setColor(0xFFFFB300);
        rect.set(dx + dw - 160, dy + dh - 55, dx + dw - 30, dy + dh - 15);
        c.drawRoundRect(rect, 6, 6, pFill);
        pText.setColor(0xFF000000);
        pText.setTextSize(18);
        c.drawText("APPLY", dx + dw - 120, dy + dh - 30, pText);
    }

    private void renderShopModal(Canvas c, int w, int h) {
        c.drawColor(0xCC000000);
        float cx = w / 2f, cy = h / 2f;
        float dw = w * 0.80f, dh = h * 0.78f;
        float dx = cx - dw / 2f, dy = cy - dh / 2f;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFA0F1722);
        rect.set(dx, dy, dx + dw, dy + dh);
        c.drawRoundRect(rect, 12, 12, pFill);

        pText.setColor(0xFFFF5252);
        pText.setTextSize(26);
        pText.setFakeBoldText(true);
        c.drawText("SHOP & CRATES (МАГАЗИН И ЯЩИКИ)", dx + 30, dy + 45, pText);

        drawSkinCard(c, dx + 30, dy + 80, 320, 70, "Premium Crate • 120 UC", 0xFFFFD700, false);
        drawSkinCard(c, dx + 30, dy + 165, 320, 70, "Classic Crate • 1080 BP", 0xFF00E5FF, false);
        drawSkinCard(c, dx + 30, dy + 250, 320, 70, "Lucky Spin • Pharaoh", 0xFFFFB300, false);

        pFill.setColor(0xFF37474F);
        rect.set(dx + dw - 160, dy + dh - 55, dx + dw - 30, dy + dh - 15);
        c.drawRoundRect(rect, 6, 6, pFill);
        pText.setColor(0xFFFFFFFF);
        pText.setTextSize(18);
        c.drawText("CLOSE", dx + dw - 120, dy + dh - 30, pText);
    }

    private void renderRoyalePassModal(Canvas c, int w, int h) {
        c.drawColor(0xCC000000);
        float cx = w / 2f, cy = h / 2f;
        float dw = w * 0.82f, dh = h * 0.80f;
        float dx = cx - dw / 2f, dy = cy - dh / 2f;

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xFA0F1722);
        rect.set(dx, dy, dx + dw, dy + dh);
        c.drawRoundRect(rect, 12, 12, pFill);

        pText.setColor(0xFFFFB300);
        pText.setTextSize(26);
        pText.setFakeBoldText(true);
        c.drawText("ROYALE PASS • SEASON C7S20", dx + 30, dy + 45, pText);

        drawSkinCard(c, dx + 30, dy + 80, 500, 65, "Rank 1: Neon Punk Set", 0xFFFF4081, true);
        drawSkinCard(c, dx + 30, dy + 155, 500, 65, "Rank 50: Golden M416 Finish", 0xFFFFD700, true);
        drawSkinCard(c, dx + 30, dy + 230, 500, 65, "Rank 100: Mythic Cyber Pharaoh", 0xFFE040FB, false);

        pFill.setColor(0xFFFFB300);
        rect.set(dx + dw - 180, dy + dh - 55, dx + dw - 30, dy + dh - 15);
        c.drawRoundRect(rect, 6, 6, pFill);
        pText.setColor(0xFF000000);
        pText.setTextSize(18);
        c.drawText("CLAIM ALL", dx + dw - 150, dy + dh - 30, pText);
    }

    private String getMapName(int id) {
        switch (id) {
            case MAP_ERANGEL: return "ERANGEL";
            case MAP_MIRAMAR: return "MIRAMAR";
            case MAP_SANHOK: return "SANHOK";
            case MAP_LIVIK: return "LIVIK";
            default: return "ERANGEL";
        }
    }

    public boolean handleClick(float x, float y, int w, int h, PUBGGame game) {
        if (currentModal == MODAL_MAP_SELECT) {
            float cx = w / 2f, cy = h / 2f;
            float dw = w * 0.82f, dh = h * 0.82f;
            float dx = cx - dw / 2f, dy = cy - dh / 2f;

            float cardW = (dw - 80) / 4f;
            float cardH = dh * 0.46f;
            float cardY = dy + 70;

            for (int i = 0; i < 4; i++) {
                float cardX = dx + 30 + i * (cardW + 7);
                if (x >= cardX && x <= cardX + cardW && y >= cardY && y <= cardY + cardH) {
                    selectedMap = i;
                    return true;
                }
            }

            float optY = cardY + cardH + 25;
            if (y >= optY && y <= optY + 42) {
                if (x >= dx + 30 && x <= dx + 170) isTPP = true;
                else if (x >= dx + 180 && x <= dx + 320) isTPP = false;
                else if (x >= dx + 360 && x <= dx + 470) teamMode = 0;
                else if (x >= dx + 480 && x <= dx + 590) teamMode = 2;
            }

            if (x >= dx + dw - 180 && x <= dx + dw - 30 && y >= dy + dh - 60 && y <= dy + dh - 15) {
                currentModal = MODAL_NONE;
                return true;
            }
            return true;
        }

        if (currentModal != MODAL_NONE) {
            float cx = w / 2f, cy = h / 2f;
            float dw = w * 0.88f, dh = h * 0.85f;
            float dx = cx - dw / 2f, dy = cy - dh / 2f;

            if (currentModal == MODAL_INVENTORY) {
                float tabX = dx + dw - 480, tabY = dy + 70;
                if (y >= tabY && y <= tabY + 60) selectedGunSkin = 0;
                else if (y >= tabY + 70 && y <= tabY + 130) selectedGunSkin = 1;
                else if (y >= tabY + 140 && y <= tabY + 200) selectedGunSkin = 2;

                if (x >= dx + 30 && x <= dx + 180 && y >= dy + dh - 60 && y <= dy + dh - 15) {
                    currentModal = MODAL_NONE;
                }
            } else if (currentModal == MODAL_SETTINGS) {
                if (x >= dx + 30 && x <= dx + 140 && y >= dy + 105 && y <= dy + 143) graphicsQuality = 0;
                else if (x >= dx + 150 && x <= dx + 260 && y >= dy + 105 && y <= dy + 143) graphicsQuality = 1;
                else if (x >= dx + 270 && x <= dx + 380 && y >= dy + 105 && y <= dy + 143) graphicsQuality = 2;
                else if (x >= dx + 390 && x <= dx + 500 && y >= dy + 105 && y <= dy + 143) graphicsQuality = 3;

                // Гироскоп
                if (x >= dx + 30 && x <= dx + 180 && y >= dy + 190 && y <= dy + 228) {
                    gyroMode = PUBGGyroscope.MODE_ALWAYS_ON;
                    game.gyroscope.mode = gyroMode;
                } else if (x >= dx + 190 && x <= dx + 340 && y >= dy + 190 && y <= dy + 228) {
                    gyroMode = PUBGGyroscope.MODE_SCOPE_ONLY;
                    game.gyroscope.mode = gyroMode;
                } else if (x >= dx + 350 && x <= dx + 460 && y >= dy + 190 && y <= dy + 228) {
                    gyroMode = PUBGGyroscope.MODE_OFF;
                    game.gyroscope.mode = gyroMode;
                }

                // Звук
                if (x >= dx + 30 && x <= dx + 170 && y >= dy + 275 && y <= dy + 313) SoundSynth3D.enabled = true;
                else if (x >= dx + 180 && x <= dx + 320 && y >= dy + 275 && y <= dy + 313) SoundSynth3D.enabled = false;

                if (x >= dx + dw - 160 && x <= dx + dw - 30 && y >= dy + dh - 55 && y <= dy + dh - 15) {
                    currentModal = MODAL_NONE;
                }
            } else {
                currentModal = MODAL_NONE;
            }
            return true;
        }

        float btnW = 280, btnH = 68, btnX = 40, btnY = h - btnH - 25;
        if (x >= btnX && x <= btnX + btnW && y >= btnY && y <= btnY + btnH) {
            game.startMatchmaking();
            return true;
        }

        float mapBoxY = btnY - 58;
        if (x >= btnX && x <= btnX + btnW && y >= mapBoxY && y <= mapBoxY + 50) {
            currentModal = MODAL_MAP_SELECT;
            return true;
        }

        float startX = w - 580, navY = h - 65, itemW = 92;
        if (y >= navY && y <= navY + 50) {
            int idx = (int) ((x - startX) / itemW);
            if (idx == 0) currentModal = MODAL_NONE;
            else if (idx == 1) currentModal = MODAL_ROYALE_PASS;
            else if (idx == 2) currentModal = MODAL_INVENTORY;
            else if (idx == 3) currentModal = MODAL_INVENTORY;
            else if (idx == 4) currentModal = MODAL_NONE;
            else if (idx == 5) currentModal = MODAL_SHOP;
            return true;
        }

        if (x >= w - 65 && x <= w - 30 && y >= 15 && y <= 50) {
            currentModal = MODAL_SETTINGS;
            return true;
        }

        return false;
    }
}
