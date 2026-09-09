package com.syslikoffnet.overglow;

import android.graphics.Canvas;
import android.graphics.RectF;

import java.util.ArrayList;

/**
 * Все экранные интерфейсы рисуются на Canvas: заголовок, выбор героя,
 * магазин, настройки, статистика, карточки левел-апа, пауза, смерть, победа.
 */
public final class Screens {

    /** Кнопка. */
    public static final class Btn {
        public final RectF r = new RectF();
        public String label;
        public int id;
        public int color = Art.PLAYER;
        public boolean glow; // подсветка
    }

    private final ArrayList<Btn> btns = new ArrayList<>();
    private final Game game;

    public Screens(Game game) {
        this.game = game;
    }

    public void reset() {
        btns.clear();
    }

    /** Обработка тапа. Возвращает true, если тап съеден. */
    public boolean onTap(float x, float y) {
        for (int i = btns.size() - 1; i >= 0; i--) {
            Btn b = btns.get(i);
            if (b.r.contains(x, y)) {
                Sound.play(Sound.CLICK);
                game.onButton(b.id);
                return true;
            }
        }
        return false;
    }

    // ================================================================ общее

    private void btn(Canvas c, float x, float y, float w, float h, String label,
                     int id, int color) {
        Btn b = new Btn();
        b.r.set(x, y, x + w, y + h);
        b.label = label;
        b.id = id;
        b.color = color;
        btns.add(b);

        // Фон
        Art.P_FILL.setStyle(android.graphics.Paint.Style.FILL);
        Art.P_FILL.setColor(0x14182A44);
        c.drawRoundRect(b.r, 18, 18, Art.P_FILL);
        // Обводка
        Art.P_STROKE.setStyle(android.graphics.Paint.Style.STROKE);
        Art.P_STROKE.setStrokeWidth(2.5f);
        Art.P_STROKE.setColor(color);
        c.drawRoundRect(b.r, 18, 18, Art.P_STROKE);
        // Текст
        Art.P_TEXT_MID.setColor(color);
        Art.P_TEXT_MID.setTextAlign(android.graphics.Paint.Align.CENTER);
        c.drawText(label, x + w / 2, y + h / 2 + 16, Art.P_TEXT_MID);
    }

    private void dim(Canvas c, int w, int h, float a) {
        Art.P_FILL.setColor(android.graphics.Color.argb((int) (a * 255), 2, 4, 9));
        c.drawRect(0, 0, w, h, Art.P_FILL);
    }

    private void title(Canvas c, int w, float y, String text, int color) {
        float size = Math.min(86, w / (text.length() * 0.62f));
        Art.P_TEXT_BIG.setTextSize(size);
        Art.P_TEXT_BIG.setColor(color);
        Art.P_TEXT_BIG.setTextAlign(android.graphics.Paint.Align.CENTER);
        c.drawText(text, w / 2f, y, Art.P_TEXT_BIG);
        Art.P_TEXT_BIG.setTextSize(86);
    }

    private void text(Canvas c, float x, float y, String s, int color, float size,
                      android.graphics.Paint.Align align) {
        Art.P_TEXT.setColor(color);
        Art.P_TEXT.setTextSize(size);
        Art.P_TEXT.setTextAlign(align);
        c.drawText(s, x, y, Art.P_TEXT);
    }

    // ================================================================ TITLE

    public void drawTitle(Canvas c, int w, int h, float t) {
        btns.clear();
        // Плавающие сферы на фоне
        for (int i = 0; i < 5; i++) {
            float fx = w / 2f + (float) Math.sin(t * 0.3f + i * 2.1f) * w * 0.36f;
            float fy = h * 0.2f + (float) Math.cos(t * 0.23f + i * 1.3f) * h * 0.16f;
            int[] cols = {0x667DE2FF, 0x66B388FF, 0x66FF8A3D, 0x664F8CFF, 0x6634D399};
            Art.glowAt(c, cols[i] | 0xFF000000, fx, fy,
                    w * 0.24f * (0.7f + 0.3f * (float) Math.sin(t + i)), 60);
        }
        // Глитч-логотип
        float size = Math.min(86, w / (8 * 0.62f));
        Art.P_TEXT_BIG.setTextSize(size);
        Art.P_TEXT_BIG.setTextAlign(android.graphics.Paint.Align.CENTER);
        float ly = h * 0.24f;
        float glitch = (float) Math.sin(t * 7.3f) * (float) Math.sin(t * 3.1f);
        float off = glitch > 0.55f ? 5 : 2.5f;
        // RGB-расщепление
        Art.P_TEXT_BIG.setColor(0xFF22DDFF);
        c.drawText("OVERGLOW", w / 2f - off, ly, Art.P_TEXT_BIG);
        Art.P_TEXT_BIG.setColor(0xFFFF2E9E);
        c.drawText("OVERGLOW", w / 2f + off, ly, Art.P_TEXT_BIG);
        Art.P_TEXT_BIG.setColor(Art.WHITE);
        c.drawText("OVERGLOW", w / 2f, ly, Art.P_TEXT_BIG);
        Art.P_TEXT_BIG.setTextSize(86);
        // Случайный горизонтальный разрыв
        if (glitch > 0.72f) {
            android.graphics.Paint p = Art.P_FILL;
            p.setColor(0x6605070D);
            c.drawRect(w * 0.2f, ly - size * 0.55f, w * 0.8f, ly - size * 0.40f, p);
        }
        // Подзаголовок
        text(c, w / 2f, h * 0.24f + 44, L.t("howto") + " · neon survivors",
                Art.DIM, 26, android.graphics.Paint.Align.CENTER);

        // Свечение вокруг кнопки PLAY
        float bw = Math.min(w * 0.72f, 420);
        float bx = (w - bw) / 2f;
        btn(c, bx, h * 0.42f, bw, 84, L.t("play"), Game.B_PLAY, Art.PLAYER);
        btn(c, bx, h * 0.42f + 104, bw, 68, L.t("chars"), Game.B_CHARS, Art.ELITE);
        btn(c, bx, h * 0.42f + 188, bw, 68, L.t("shop"), Game.B_SHOP, Art.GOLD);

        // Нижний ряд: настройки/статистика/ачивки
        float qw = (bw - 32) / 3f;
        btn(c, bx, h * 0.42f + 272, qw, 60, "⚙", Game.B_SETTINGS, Art.DIM);
        btn(c, bx + qw + 16, h * 0.42f + 272, qw, 60, L.t("stats"), Game.B_STATS, Art.DIM);
        btn(c, bx + 2 * (qw + 16), h * 0.42f + 272, qw, 60, "★ " + L.t("ach"),
                Game.B_ACH, Art.GOLD);

        // Золото
        text(c, w / 2f, h - 30, "◈ " + S.gold(game.ctx) + " " + L.t("gold"),
                Art.GOLD, 28, android.graphics.Paint.Align.CENTER);
    }

    // ================================================================ CHARS

    public void drawChars(Canvas c, int w, int h) {
        btns.clear();
        dim(c, w, h, 1f);
        title(c, w, h * 0.12f, L.t("chars"), Art.ELITE);

        boolean[] unlocked = S.chars(game.ctx);
        int sel = S.selectedChar(game.ctx);
        int[] colors = {Art.PLAYER, 0xFFFFE066, 0xFF9BA8B8, 0xFFB388FF};

        float cw = Math.min(w * 0.9f, 460);
        float ch = 150;
        for (int i = 0; i < 4; i++) {
            float cy = h * 0.18f + i * (ch + 14);
            boolean on = unlocked[i];
            int col = colors[i];

            Btn b = new Btn();
            b.r.set((w - cw) / 2f, cy, (w + cw) / 2f, cy + ch);
            b.id = Game.B_CHAR_0 + i;
            btns.add(b);

            Art.P_FILL.setColor(on ? 0x14182A44 : 0x0A0E18);
            c.drawRoundRect(b.r, 20, 20, Art.P_FILL);
            Art.P_STROKE.setColor(sel == i ? col : (on ? Art.FAINT : 0x3348586E));
            Art.P_STROKE.setStrokeWidth(3);
            c.drawRoundRect(b.r, 20, 20, Art.P_STROKE);
            if (sel == i) {
                Art.P_STROKE.setColor(col);
                Art.P_STROKE.setStrokeWidth(1.5f);
                c.drawRoundRect(new RectF(b.r.left - 6, b.r.top - 6,
                        b.r.right + 6, b.r.bottom + 6), 24, 24, Art.P_STROKE);
            }

            // Аватар героя
            float ax = b.r.left + 56, ay = cy + ch / 2;
            c.drawBitmap(Art.glow(col), ax - 60, ay - 60, Art.P_BMP);
            Art.P_FILL.setColor(on ? col : Art.FAINT);
            c.drawCircle(ax, ay, 26, Art.P_FILL);
            Art.P_FILL.setColor(0xFFFFFFFF);
            c.drawCircle(ax, ay, 11, Art.P_FILL);

            // Имя + описание
            String name = on ? L.t("c" + i) : L.t("c" + i) + "  🔒";
            text(c, b.r.left + 110, cy + 58, name, on ? col : Art.FAINT, 38,
                    android.graphics.Paint.Align.LEFT);
            text(c, b.r.left + 110, cy + 96, on ? L.t("cd" + i)
                            : L.t("buy") + " ◈" + Upgrades.charPrice(i),
                    Art.DIM, 24, android.graphics.Paint.Align.LEFT);
            text(c, b.r.right - 20, cy + 96,
                    sel == i ? L.t("selected") : (on ? L.t("select") : L.t("locked")),
                    sel == i ? col : Art.DIM, 22, android.graphics.Paint.Align.RIGHT);
        }

        btn(c, 20, h - 90, 160, 60, L.t("back"), Game.B_BACK, Art.DIM);
    }

    // ================================================================ SHOP

    public void drawShop(Canvas c, int w, int h) {
        btns.clear();
        dim(c, w, h, 1f);
        title(c, w, h * 0.10f, L.t("shop"), Art.GOLD);
        text(c, w / 2f, h * 0.10f + 44, "◈ " + S.gold(game.ctx) + " " + L.t("gold"),
                Art.GOLD, 30, android.graphics.Paint.Align.CENTER);

        int[] meta = game.meta;
        String[] names = {L.t("metaHp"), L.t("metaDmg"), L.t("metaSpd"),
                L.t("metaMag"), L.t("metaGreed")};
        String[] descs = {"+20 HP", "+8%", "+3%", "+15%", "+10%"};
        int[] cols = {Art.HEAL, 0xFFFF6B6B, 0xFF6BD4FF, 0xFFB388FF, Art.GOLD};

        float cw = Math.min(w * 0.94f, 500);
        float ch = 86;
        for (int i = 0; i < 5; i++) {
            float cy = h * 0.17f + i * (ch + 10);
            int lvl = meta[i];
            int price = Upgrades.metaPrice(lvl);

            Btn b = new Btn();
            b.r.set((w - cw) / 2f, cy, (w + cw) / 2f, cy + ch);
            b.id = Game.B_META_0 + i;
            btns.add(b);

            Art.P_FILL.setColor(0x14182A44);
            c.drawRoundRect(b.r, 16, 16, Art.P_FILL);
            Art.P_STROKE.setColor(cols[i]);
            Art.P_STROKE.setStrokeWidth(2);
            c.drawRoundRect(b.r, 16, 16, Art.P_STROKE);

            text(c, b.r.left + 20, cy + 36, names[i], cols[i], 30,
                    android.graphics.Paint.Align.LEFT);
            text(c, b.r.left + 20, cy + 66, descs[i] + " ×" + lvl, Art.DIM, 22,
                    android.graphics.Paint.Align.LEFT);

            // Пипсы уровня
            for (int p = 0; p < 5; p++) {
                Art.P_FILL.setColor(p < lvl ? cols[i] : 0x3348586E);
                c.drawCircle(b.r.right - 150 + p * 22, cy + 32, 7, Art.P_FILL);
            }
            if (lvl >= 5) {
                text(c, b.r.right - 20, cy + 64, L.t("maxed"), Art.DIM, 24,
                        android.graphics.Paint.Align.RIGHT);
            } else {
                text(c, b.r.right - 20, cy + 64, "◈ " + price,
                        S.gold(game.ctx) >= price ? Art.GOLD : Art.DANGER, 26,
                        android.graphics.Paint.Align.RIGHT);
            }
        }

        btn(c, 20, h - 90, 160, 60, L.t("back"), Game.B_BACK, Art.DIM);
    }

    // ================================================================ SETTINGS

    public void drawSettings(Canvas c, int w, int h) {
        btns.clear();
        dim(c, w, h, 1f);
        title(c, w, h * 0.12f, L.t("settings"), Art.DIM);

        float bw = Math.min(w * 0.8f, 440);
        float bx = (w - bw) / 2f;
        float y = h * 0.24f;

        // Тумблеры
        toggle(c, bx, y, bw, L.t("sound"), S.sound(game.ctx), Game.B_T_SOUND);
        y += 84;
        toggle(c, bx, y, bw, L.t("music"), S.music(game.ctx), Game.B_T_MUSIC);
        y += 84;
        toggle(c, bx, y, bw, L.t("vibrate"), S.vibrate(game.ctx), Game.B_T_VIB);
        y += 84;

        // Язык
        btn(c, bx, y, bw, 68, L.t("language") + ":  "
                + (L.ru ? "RU" : "EN"), Game.B_T_LANG, Art.PLAYER);
        y += 110;

        // Сброс
        btn(c, bx, y, bw, 68, L.t("reset"), Game.B_RESET, Art.DANGER);
        y += 130;

        // Как играть
        btn(c, bx, y, bw, 68, L.t("howto"), Game.B_HOWTO, Art.DIM);

        btn(c, 20, h - 90, 160, 60, L.t("back"), Game.B_BACK, Art.DIM);
    }

    private void toggle(Canvas c, float x, float y, float w, String label,
                        boolean on, int id) {
        Btn b = new Btn();
        b.r.set(x, y, x + w, y + 68);
        b.id = id;
        btns.add(b);

        Art.P_FILL.setColor(0x14182A44);
        c.drawRoundRect(b.r, 16, 16, Art.P_FILL);
        text(c, x + 24, y + 44, label, on ? Art.WHITE : Art.DIM, 32,
                android.graphics.Paint.Align.LEFT);
        // Переключатель
        float tx = x + w - 90;
        float ty = y + 34;
        Art.P_STROKE.setStrokeWidth(3);
        Art.P_STROKE.setColor(on ? Art.HEAL : Art.FAINT);
        c.drawRoundRect(new RectF(tx, ty - 16, tx + 66, ty + 16), 16, 16, Art.P_STROKE);
        Art.P_FILL.setColor(on ? Art.HEAL : Art.FAINT);
        c.drawCircle(on ? tx + 48 : tx + 18, ty, 12, Art.P_FILL);
    }

    // ================================================================ HOWTO

    public void drawHowTo(Canvas c, int w, int h) {
        btns.clear();
        dim(c, w, h, 1f);
        title(c, w, h * 0.12f, L.t("howto"), Art.PLAYER);

        String[] tips = {L.t("htMove"), L.t("htAuto"), L.t("htLevel"),
                L.t("htGold"), L.t("htBoss")};
        float y = h * 0.24f;
        for (String tip : tips) {
            // Многострочный перенос по словам
            y = drawWrapped(c, w / 2f, y, w * 0.84f, tip, Art.WHITE, 28) + 34;
        }

        btn(c, 20, h - 90, 160, 60, L.t("back"), Game.B_BACK, Art.DIM);
    }

    private float drawWrapped(Canvas c, float cx, float y, float maxW, String s,
                              int color, float size) {
        Art.P_TEXT.setTextSize(size);
        Art.P_TEXT.setColor(color);
        Art.P_TEXT.setTextAlign(android.graphics.Paint.Align.CENTER);
        String[] words = s.split(" ");
        String line = "";
        float lineH = size * 1.4f;
        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (Art.P_TEXT.measureText(test) > maxW && !line.isEmpty()) {
                c.drawText(line, cx, y, Art.P_TEXT);
                y += lineH;
                line = word;
            } else {
                line = test;
            }
        }
        if (!line.isEmpty()) {
            c.drawText(line, cx, y, Art.P_TEXT);
            y += lineH;
        }
        return y;
    }

    // ================================================================ STATS

    public void drawStats(Canvas c, int w, int h) {
        btns.clear();
        dim(c, w, h, 1f);
        title(c, w, h * 0.12f, L.t("stats"), Art.DIM);

        org.json.JSONObject st = S.stats(game.ctx);
        float y = h * 0.26f;
        y = stat(c, w, y, L.t("best"), World.fmtTime(st.optInt("bestTime")));
        y = stat(c, w, y, L.t("bestLvl"), String.valueOf(st.optInt("bestLvl")));
        y = stat(c, w, y, L.t("totalKills"), String.valueOf(st.optInt("totalKills")));
        y = stat(c, w, y, L.t("runs"), String.valueOf(st.optInt("runs")));
        y = stat(c, w, y, L.t("totalGold"), String.valueOf(st.optInt("totalGold")));

        btn(c, 20, h - 90, 160, 60, L.t("back"), Game.B_BACK, Art.DIM);
    }

    private float stat(Canvas c, int w, float y, String label, String value) {
        text(c, w / 2f - 20, y, label, Art.DIM, 30, android.graphics.Paint.Align.RIGHT);
        text(c, w / 2f + 20, y, value, Art.WHITE, 30, android.graphics.Paint.Align.LEFT);
        return y + 64;
    }

    // ================================================================ LEVELUP

    public Upgrades.Card[] cards;
    public float cardAnimT; // анимация появления карточек

    public void drawLevelUp(Canvas c, int w, int h, World world) {
        btns.clear();
        dim(c, w, h, 0.86f);
        title(c, w, h * 0.16f, L.t("levelup"), Art.GOLD);
        text(c, w / 2f, h * 0.16f + 44, L.t("choose"), Art.WHITE, 28,
                android.graphics.Paint.Align.CENTER);

        int n = cards != null ? cards.length : 0;
        if (n == 0) return;

        // Анимация: карточки вылетают с задержкой
        float[] scaleArr = new float[n];
        for (int i = 0; i < n; i++) {
            float lt = cardAnimT - i * 0.09f;
            float k = lt <= 0 ? 0f : Math.min(1f, lt / 0.16f);
            k = 1f - (1f - k) * (1f - k) * (1f - k); // ease-out cubic
            scaleArr[i] = 0.6f + 0.4f * k;
        }

        boolean portrait = h > w;
        if (portrait) {
            // Вертикально
            float cw = Math.min(w * 0.88f, 460);
            float ch = Math.min((h * 0.62f) / n - 16, 130);
            float total = n * ch + (n - 1) * 16;
            float y0 = h * 0.24f + (h * 0.62f - total) / 2f;
            for (int i = 0; i < n; i++) {
                drawCard(c, (w - cw) / 2f, y0 + i * (ch + 16), cw, ch, cards[i],
                        world, i, scaleArr[i]);
            }
        } else {
            // Горизонтально
            float cw = Math.min((w * 0.9f) / n - 14, 340);
            float ch = Math.min(h * 0.5f, 260);
            float total = n * cw + (n - 1) * 14;
            float x0 = (w - total) / 2f;
            float y0 = h * 0.30f;
            for (int i = 0; i < n; i++) {
                drawCard(c, x0 + i * (cw + 14), y0, cw, ch, cards[i], world, i,
                        scaleArr[i]);
            }
        }
    }

    private void drawCard(Canvas c, float x, float y, float w, float h,
                          Upgrades.Card card, World world, int idx, float cardScale) {
        int color;
        if (card.kind == 2) color = Upgrades.passiveColor(card.id);
        else color = card.kind == 0 ? Art.PLAYER : Art.GOLD;

        Btn b = new Btn();
        b.r.set(x, y, x + w, y + h);
        b.id = Game.B_CARD_0 + idx;
        b.color = color;
        btns.add(b);

        // Масштаб появления
        float sc = cardScale;
        c.save();
        c.scale(sc, sc, x + w / 2f, y + h / 2f);

        // Подложка + свечение (растянутое на карточку)
        Art.P_BMP.setAlpha(70);
        c.drawBitmap(Art.glow(color), null, new RectF(x, y, x + w, y + h), Art.P_BMP);
        Art.P_BMP.setAlpha(255);
        Art.P_FILL.setColor(0xF0101626);
        c.drawRoundRect(b.r, 20, 20, Art.P_FILL);
        Art.P_STROKE.setStrokeWidth(3);
        Art.P_STROKE.setColor(color);
        c.drawRoundRect(b.r, 20, 20, Art.P_STROKE);

        // Текст
        float midX = x + w / 2f;
        text(c, midX, y + h * 0.34f, card.title, color, Math.min(34, w / 12f),
                android.graphics.Paint.Align.CENTER);
        Art.P_TEXT.setColor(Art.WHITE);
        Art.P_TEXT.setTextSize(Math.min(26, w / 15f));
        Art.P_TEXT.setTextAlign(android.graphics.Paint.Align.CENTER);
        c.drawText(card.sub, midX, y + h * 0.60f, Art.P_TEXT);
        // Номер
        text(c, x + 22, y + h - 20, (idx + 1) + "", Art.FAINT, 24,
                android.graphics.Paint.Align.LEFT);

        c.restore();
    }

    // ================================================================ ACHIEVEMENTS

    public void drawAch(Canvas c, int w, int h) {
        btns.clear();
        dim(c, w, h, 1f);
        title(c, w, h * 0.09f, L.t("ach"), Art.GOLD);

        boolean[] ach = S.achievements(game.ctx);
        int done = 0;
        for (boolean b : ach) if (b) done++;

        text(c, w / 2f, h * 0.09f + 40, done + "/10 · " + S.gold(game.ctx) + " ◈",
                Art.DIM, 26, android.graphics.Paint.Align.CENTER);

        float y = h * 0.17f;
        float rowH = Math.min(64, (h * 0.68f) / 10);
        for (int i = 0; i < 10; i++) {
            boolean got = ach[i];
            int col = got ? Art.GOLD : Art.FAINT;
            // Иконка
            Art.glowAt(c, got ? Art.GOLD : 0x3348586E, w / 2f - w * 0.36f, y - 6,
                    rowH * 1.2f, got ? 120 : 40);
            text(c, w / 2f - w * 0.36f, y + 4, got ? "★" : "☆", col,
                    rowH * 0.6f, android.graphics.Paint.Align.CENTER);
            // Название и награда
            text(c, w / 2f - w * 0.28f, y - rowH * 0.05f,
                    L.t("ach" + i), got ? Art.WHITE : Art.FAINT, rowH * 0.38f,
                    android.graphics.Paint.Align.LEFT);
            text(c, w / 2f - w * 0.28f, y + rowH * 0.34f,
                    "+" + S.ACH_REWARD[i] + " ◈", got ? Art.GOLD : 0x5548586E,
                    rowH * 0.3f, android.graphics.Paint.Align.LEFT);
            y += rowH;
        }

        btn(c, 20, h - 90, 160, 60, L.t("back"), Game.B_BACK, Art.DIM);
    }

    // ================================================================ PAUSE

    public void drawPause(Canvas c, int w, int h, World world) {
        btns.clear();
        dim(c, w, h, 0.78f);
        title(c, w, h * 0.09f, L.t("paused"), Art.WHITE);
        text(c, w / 2f, h * 0.09f + 34, World.fmtTime(world.time) + " · LV "
                + world.level + " · " + world.kills + " " + L.t("kills"),
                Art.DIM, 24, android.graphics.Paint.Align.CENTER);

        // --- Панель билда ---
        float by = h * 0.15f;
        text(c, w / 2f, by + 20, L.t("build"), 0xFFFFE066, 28,
                android.graphics.Paint.Align.CENTER);
        float rowY = by + 46;
        // Оружия
        for (int i = 0; i < world.weapons.size(); i++) {
            Weapons wp = world.weapons.get(i);
            String line = Weapons.name(wp.id) + "  " + wp.lvl + "/8"
                    + (wp.awakened ? " ★" : "");
            text(c, w / 2f, rowY, line, weaponHudColor(wp.id), 26,
                    android.graphics.Paint.Align.CENTER);
            rowY += 34;
        }
        if (world.weapons.isEmpty()) {
            text(c, w / 2f, rowY, "—", Art.FAINT, 26, android.graphics.Paint.Align.CENTER);
            rowY += 34;
        }
        // Пассивки
        rowY += 8;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Upgrades.PASSIVES; i++) {
            if (Upgrades.passiveLvls[i] > 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(Upgrades.passiveName(i)).append(" ")
                        .append(Upgrades.passiveLvls[i]);
            }
        }
        if (sb.length() > 0) {
            text(c, w / 2f, rowY, sb.toString(), 0xFF8AE8FF, 22,
                    android.graphics.Paint.Align.CENTER);
        }
        rowY += 40;
        // Реликвии
        if (world.relicCount > 0) {
            text(c, w / 2f, rowY, L.t("relics") + ":", 0xFFB388FF, 24,
                    android.graphics.Paint.Align.CENTER);
            rowY += 32;
            StringBuilder rb = new StringBuilder();
            for (int i = 0; i < World.RELIC_COUNT; i++) {
                if (world.relics[i]) {
                    if (rb.length() > 0) rb.append("  ");
                    rb.append(Upgrades.relicName(i));
                }
            }
            float ty = rowY;
            Art.P_TEXT.setTextSize(20);
            Art.P_TEXT.setColor(0xFFD9C8FF);
            Art.P_TEXT.setTextAlign(android.graphics.Paint.Align.CENTER);
            c.drawText(rb.toString(), w / 2f, ty, Art.P_TEXT);
            rowY += 30;
        } else {
            text(c, w / 2f, rowY, L.t("relics") + ": " + L.t("noRelics"),
                    Art.FAINT, 22, android.graphics.Paint.Align.CENTER);
            rowY += 30;
        }

        // --- Кнопки (компактно, если мало места) ---
        float bw = Math.min(w * 0.72f, 400);
        float bx = (w - bw) / 2f;
        float ky = Math.max(rowY + 12, h * 0.55f);
        boolean compact = (ky + 318) > h;
        if (compact) {
            btn(c, bx, ky, bw, 56, L.t("resume"), Game.B_RESUME, Art.HEAL);
            btn(c, bx, ky + 66, bw, 48, L.t("restart"), Game.B_RESTART, Art.GOLD);
            btn(c, bx, ky + 122, bw, 48, L.t("toMenu"), Game.B_MENU, Art.DIM);
        } else {
            btn(c, bx, ky, bw, 76, L.t("resume"), Game.B_RESUME, Art.HEAL);
            btn(c, bx, ky + 96, bw, 64, L.t("restart"), Game.B_RESTART, Art.GOLD);
            btn(c, bx, ky + 178, bw, 64, L.t("toMenu"), Game.B_MENU, Art.DIM);

            // Быстрые настройки
            float qw = (bw - 12) / 2f;
            btn(c, bx, ky + 262, qw, 56,
                    L.t("sound") + ": " + (S.sound(game.ctx) ? "ON" : "OFF"),
                    Game.B_T_SOUND, Art.DIM);
            btn(c, bx + qw + 12, ky + 262, qw, 56,
                    L.t("music") + ": " + (S.music(game.ctx) ? "ON" : "OFF"),
                    Game.B_T_MUSIC, Art.DIM);
        }
    }

    /** Цвет оружия для панели билда. */
    private static int weaponHudColor(int id) {
        switch (id) {
            case Weapons.W_PULSE: return Art.PLAYER;
            case Weapons.W_CHAIN: return 0xFF8AE8FF;
            case Weapons.W_BLADES: return 0xFFB8C6D9;
            case Weapons.W_NOVA: return 0xFF6BD4FF;
            case Weapons.W_SPHERES: return 0xFF9D6BFF;
            case Weapons.W_LASER: return 0xFFFF2E9E;
            case Weapons.W_MISSILES: return 0xFFFFE066;
            case Weapons.W_FROST: return 0xFF8AF5FF;
            case Weapons.W_BOOMERANG: return 0xFF7DE2FF;
            case Weapons.W_STORM: return 0xFF9DFF6B;
            case Weapons.W_AURA: return 0xFFFF8A3D;
            case Weapons.W_BLACKHOLE: return 0xFFB388FF;
            default: return Art.WHITE;
        }
    }

    // ================================================================ DEAD

    public void drawDead(Canvas c, int w, int h, World world) {
        btns.clear();
        dim(c, w, h, 0.88f);
        title(c, w, h * 0.2f, L.t("gameover"), Art.DANGER);
        text(c, w / 2f, h * 0.2f + 44, L.t("survived") + " " + World.fmtTime(world.time),
                Art.WHITE, 34, android.graphics.Paint.Align.CENTER);

        float y = h * 0.34f;
        y = stat(c, w, y, L.t("level"), String.valueOf(world.level));
        y = stat(c, w, y, L.t("kills"), String.valueOf(world.kills));
        y = stat(c, w, y, L.t("goldEarned"), String.valueOf(world.goldRun));

        if (game.newBest) {
            text(c, w / 2f, y + 10, L.t("newBest"), Art.GOLD, 34,
                    android.graphics.Paint.Align.CENTER);
            y += 40;
        }
        if (game.achNewest) {
            text(c, w / 2f, y + 10, L.t("achGot") + "  +" + game.achReward + " ◈",
                    0xFFB388FF, 30, android.graphics.Paint.Align.CENTER);
        }

        float bw = Math.min(w * 0.72f, 400);
        float bx = (w - bw) / 2f;
        float ky = Math.max(h * 0.62f, y + 40);
        btn(c, bx, ky, bw, 80, L.t("restart"), Game.B_RESTART, Art.PLAYER);
        btn(c, bx, ky + 100, bw, 64, L.t("toMenu"), Game.B_MENU, Art.DIM);
    }

    // ================================================================ WIN

    public void drawWin(Canvas c, int w, int h, World world) {
        btns.clear();
        dim(c, w, h, 0.85f);
        title(c, w, h * 0.18f, L.t("victory"), Art.GOLD);
        text(c, w / 2f, h * 0.18f + 44, L.t("victorySub"), Art.WHITE, 30,
                android.graphics.Paint.Align.CENTER);

        float y = h * 0.32f;
        y = stat(c, w, y, L.t("level"), String.valueOf(world.level));
        y = stat(c, w, y, L.t("kills"), String.valueOf(world.kills));
        y = stat(c, w, y, L.t("goldEarned"), String.valueOf(world.goldRun));
        if (game.achNewest) {
            text(c, w / 2f, y + 10, L.t("achGot") + "  +" + game.achReward + " ◈",
                    0xFFB388FF, 30, android.graphics.Paint.Align.CENTER);
            y += 40;
        }

        float bw = Math.min(w * 0.72f, 440);
        float bx = (w - bw) / 2f;
        float ky = Math.max(h * 0.60f, y + 30);
        btn(c, bx, ky, bw, 80, L.t("endless"), Game.B_ENDLESS, Art.PLAYER);
        btn(c, bx, ky + 100, bw, 64, L.t("restart"), Game.B_RESTART, Art.GOLD);
        btn(c, bx, ky + 180, bw, 64, L.t("toMenu"), Game.B_MENU, Art.DIM);
    }
}
