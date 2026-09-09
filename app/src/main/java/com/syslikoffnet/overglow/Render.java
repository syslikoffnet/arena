package com.syslikoffnet.overglow;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Отрисовка мира и HUD. Камера следует за игроком, зум = высота/1000.
 * Вся «красота» — слои glow-спрайтов, формы Path, кольца.
 */
public final class Render {

    private Render() {}

    private static final Path PATH = new Path();
    private static final Paint TMP = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Скрин-координаты конвертируются: sx = (wx - camX) * scale + w/2
    private static float scale = 1f;

    /** Текущий зум камеры. */
    public static float zoom() {
        return scale;
    }

    public static void drawWorld(Canvas c, int w, int h, World world, Game game,
                                 Input input, float dt) {
        scale = Math.min(w, h) / 900f;

        // ---- Тряска экрана ----
        float shx = 0, shy = 0;
        if (world.shake > 0.1f) {
            shx = (float) ((world.rnd.nextFloat() - 0.5f) * world.shake);
            shy = (float) ((world.rnd.nextFloat() - 0.5f) * world.shake);
        }

        // ---- Фон ----
        TMP.setColor(Art.BG);
        c.drawRect(0, 0, w, h, TMP);

        int sc = c.save();
        c.translate(w / 2f + shx, h / 2f + shy);
        c.scale(scale, scale);
        c.translate(-world.camX, -world.camY);

        drawBackground(c, world, w, h, scale);

        // ---- Пикапы ----
        for (World.Pickup p : world.pickups) {
            if (!p.active) continue;
            drawPickup(c, p, world);
        }

        // ---- Телеграфы метеоров ----
        if (game.director != null) {
            for (float[] m : game.director.meteors) {
                float t = m[2];
                if (t < 0) continue;
                if (t < 0.95f) {
                    // Прицельный круг
                    float pulse = 0.5f + 0.5f * (float) Math.abs(Math.sin(t * 7f));
                    TMP.setStyle(Paint.Style.STROKE);
                    TMP.setStrokeWidth(2 + 4 * pulse);
                    TMP.setColor(Art.fade(0xFFFF8A3D, 0.4f + 0.4f * pulse));
                    c.drawCircle(m[0], m[1], 125 * (0.5f + 0.5f * t / 1.25f), TMP);
                    TMP.setStyle(Paint.Style.FILL);
                    TMP.setColor(Art.fade(0xFFFF8A3D, 0.10f + 0.08f * pulse));
                    c.drawCircle(m[0], m[1], 125, TMP);
                    // Крестик в центре
                    TMP.setStyle(Paint.Style.STROKE);
                    TMP.setStrokeWidth(2);
                    TMP.setColor(Art.fade(0xFFFFC59D, 0.6f));
                    c.drawLine(m[0] - 14, m[1], m[0] + 14, m[1], TMP);
                    c.drawLine(m[0], m[1] - 14, m[0], m[1] + 14, TMP);
                } else {
                    // Падающий метеор
                    float k = (1.25f - t) / 0.3f; // 1 → 0
                    float my = m[1] - k * 900;
                    Art.glowAt(c, 0xFFFF8A3D, m[0], my, 140, 200);
                    TMP.setStyle(Paint.Style.STROKE);
                    TMP.setStrokeWidth(10 * k + 4);
                    TMP.setColor(0xAAFFC59D);
                    c.drawLine(m[0], my - 90, m[0], my, TMP);
                    TMP.setStyle(Paint.Style.FILL);
                    TMP.setColor(0xFFFFFFFF);
                    c.drawCircle(m[0], my, 12 * k + 6, TMP);
                }
            }
        }

        // ---- Враги ----
        float visW = (w / scale) * 0.6f + 100;
        float visH = (h / scale) * 0.6f + 100;
        for (int i = 0; i < world.enemies.size(); i++) {
            World.Enemy e = world.enemies.get(i);
            if (e.deadFlag) continue;
            if (Math.abs(e.x - world.camX) > visW || Math.abs(e.y - world.camY) > visH)
                continue;
            drawEnemy(c, e, world);
        }

        // ---- Снаряды ----
        for (World.Bullet b : world.bullets) {
            if (!b.active) continue;
            drawBullet(c, b, world);
        }

        // ---- Игрок ----
        if (!world.dead) drawPlayer(c, world, game);

        // ---- Молнии ----
        for (float[] z : world.zaps) {
            float k = z[4] / 0.18f;
            TMP.setStrokeWidth(6 * k + 2);
            TMP.setColor(Art.WHITE);
            TMP.setStyle(Paint.Style.STROKE);
            c.drawLine(z[0], z[1], z[2], z[3], TMP);
            TMP.setStrokeWidth(2.5f * k + 1);
            TMP.setColor(Art.PLAYER);
            c.drawLine(z[0], z[1], z[2], z[3], TMP);
        }

        // ---- Лучи ----
        for (float[] b : world.beams) {
            float k = b[4] / 0.14f;
            TMP.setStrokeWidth(26 * k);
            TMP.setColor(0x667DE2FF);
            TMP.setStyle(Paint.Style.STROKE);
            c.drawLine(b[0], b[1], b[2], b[3], TMP);
            TMP.setStrokeWidth(9 * k);
            TMP.setColor(0xFFFFFFFF);
            c.drawLine(b[0], b[1], b[2], b[3], TMP);
        }

        // ---- Частицы ----
        for (World.Part p : world.parts) {
            if (!p.active) continue;
            float k = p.life / p.maxLife;
            if (p.ring) {
                float r = p.r0 + (p.r1 - p.r0) * (1 - k);
                TMP.setStyle(Paint.Style.STROKE);
                TMP.setStrokeWidth(3 + 8 * k);
                TMP.setColor(Art.fade(p.color, k * 0.8f));
                c.drawCircle(p.x, p.y, r, TMP);
            } else {
                TMP.setStyle(Paint.Style.FILL);
                TMP.setColor(Art.fade(p.color, k));
                float r = p.r0 * (0.4f + 0.6f * k);
                c.drawCircle(p.x, p.y, r, TMP);
            }
        }

        // ---- Цифры урона (с тёмной подложкой для читаемости) ----
        for (World.DText d : world.dtexts) {
            if (!d.active) continue;
            float k = Math.min(1, d.life / 0.5f);
            TMP.setStyle(Paint.Style.FILL);
            TMP.setTextSize(d.size * (0.7f + 0.3f * k));
            TMP.setTextAlign(Paint.Align.CENTER);
            TMP.setColor(Art.fade(0xCC05070D, k));
            c.drawText(d.text, d.x + 2, d.y + 2, TMP);
            TMP.setColor(Art.fade(d.color, k));
            c.drawText(d.text, d.x, d.y, TMP);
        }

        c.restoreToCount(sc);

        // ---- Оверлей-затемнение не в игре рисуют Screens ----

        drawHud(c, w, h, world, game);
        drawJoystick(c, input);

        // Виньетка
        Art.vignette(c, w, h);

        // Красная вспышка при уроне
        if (world.flashScreen > 0) {
            TMP.setColor(Color.argb((int) (110 * Math.min(1, world.flashScreen)), 255, 40, 70));
            c.drawRect(0, 0, w, h, TMP);
        }

        // Предупреждение о боссе
        if (game.director != null && game.director.bossWarnT > 0) {
            float a = 0.6f + 0.4f * (float) Math.sin(game.director.bossWarnT * 12);
            TMP.setColor(Color.argb((int) (200 * a), 255, 46, 99));
            TMP.setTextAlign(Paint.Align.CENTER);
            TMP.setTextSize(Math.min(40, w / 10f));
            c.drawText(L.t("bossIncoming"), w / 2f, h * 0.3f, TMP);
        }

        // Стрелка наплыва
        if (game.director != null && game.director.surgeWarnT > 0) {
            drawSurgeArrow(c, w, h, game.director.surgeAngle,
                    game.director.surgeWarnT);
        }

        // Баннер события
        if (game.director != null && game.director.bannerT > 0
                && game.director.bannerText != null) {
            float in = Math.min(1f, (3f - game.director.bannerT) * 5f);
            float out = Math.min(1f, game.director.bannerT * 2.5f);
            float a = Math.min(in, out);
            TMP.setTextAlign(Paint.Align.CENTER);
            TMP.setTextSize(Math.min(42, w / 9f));
            TMP.setColor(Color.argb((int) (230 * a), 245, 197, 66));
            c.drawText(game.director.bannerText, w / 2f, h * 0.22f, TMP);
        }
    }

    /** Пульсирующая стрелка на краю экрана в направлении наплыва. */
    private static void drawSurgeArrow(Canvas c, int w, int h, float angle, float t) {
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 14f);
        float cx = w / 2f, cy = h / 2f;
        float dist = Math.min(w, h) * 0.30f;
        float ax = cx + (float) Math.cos(angle) * dist;
        float ay = cy + (float) Math.sin(angle) * dist;
        // glow под стрелкой
        Art.glowAt(c, 0xFFFF5470, ax, ay, 160 * pulse, 120);
        c.save();
        c.rotate((float) Math.toDegrees(angle), ax, ay);
        TMP.setStyle(Paint.Style.FILL);
        TMP.setColor(Color.argb((int) (230 * pulse), 255, 84, 112));
        PATH.reset();
        PATH.moveTo(ax + 34, ay);
        PATH.lineTo(ax - 20, ay - 24);
        PATH.lineTo(ax - 8, ay);
        PATH.lineTo(ax - 20, ay + 24);
        PATH.close();
        c.drawPath(PATH, TMP);
        c.restore();
    }

    // ---------------------------------------------------------------- фон

    private static void drawBackground(Canvas c, World world, int w, int h, float zoom) {
        // --- Дальние слои: туманности и пыль с параллаксом ---
        Art.drawNebulas(c, world.camX, world.camY, w, h, zoom, 0.35f);
        Art.drawDust(c, world.camX, world.camY, w, h, zoom, 0.5f, world.time, 11, 90, 90);
        Art.drawDust(c, world.camX, world.camY, w, h, zoom, 0.8f, world.time, 23, 60, 130);

        // Сетка
        float step = 160f;
        TMP.setStyle(Paint.Style.STROKE);
        TMP.setStrokeWidth(1.5f);

        float x0 = world.camX - w / scale, x1 = world.camX + w / scale;
        float y0 = world.camY - h / scale, y1 = world.camY + h / scale;

        // Ограничить видимой ареной
        x0 = Math.max(x0, 0);
        y0 = Math.max(y0, 0);
        x1 = Math.min(x1, World.ARENA);
        y1 = Math.min(y1, World.ARENA);

        TMP.setColor(Art.GRID);
        Path grid = new Path();
        for (float x = (float) Math.floor(x0 / step) * step; x <= x1; x += step) {
            grid.moveTo(x, y0);
            grid.lineTo(x, y1);
        }
        for (float y = (float) Math.floor(y0 / step) * step; y <= y1; y += step) {
            grid.moveTo(x0, y);
            grid.lineTo(x1, y);
        }
        c.drawPath(grid, TMP);

        // Границы арены
        TMP.setStyle(Paint.Style.STROKE);
        TMP.setStrokeWidth(6);
        TMP.setColor(0x667DE2FF);
        c.drawRect(0, 0, World.ARENA, World.ARENA, TMP);

        // Пульсирующее свечение границы
        float pulse = 0.5f + 0.5f * (float) Math.sin(world.time * 2f);
        TMP.setStrokeWidth(2);
        TMP.setColor(Art.fade(Art.PLAYER, 0.3f + 0.3f * pulse));
        c.drawRect(8, 8, World.ARENA - 8, World.ARENA - 8, TMP);

        // «Звёзды» — светящиеся точки
        float[] stars = Art.stars(140, 7, World.ARENA);
        TMP.setStyle(Paint.Style.FILL);
        for (int i = 0; i < stars.length; i += 3) {
            float sx = stars[i], sy = stars[i + 1], br = stars[i + 2];
            float tw = 0.5f + 0.5f * (float) Math.sin(world.time * 3f + i);
            if (sx < x0 - 40 || sx > x1 + 40 || sy < y0 - 40 || sy > y1 + 40) continue;
            TMP.setColor(Color.argb((int) (120 * br * tw), 125, 226, 255));
            c.drawCircle(sx, sy, 2 + br * 2, TMP);
        }
    }

    // ---------------------------------------------------------------- игрок

    private static void drawPlayer(Canvas c, World world, Game game) {
        float x = world.px, y = world.py;
        // Пульс ауры
        float pulse = 1 + 0.08f * (float) Math.sin(world.time * 6f);
        int color = game.charGlowColor();

        // Трейл
        TMP.setStyle(Paint.Style.FILL);
        for (int i = 0; i < world.trailX.length; i++) {
            int idx = (world.trailPtr - 1 - i + world.trailX.length * 2) % world.trailX.length;
            float k = 1f - (float) i / world.trailX.length;
            TMP.setColor(Art.fade(color, 0.30f * k));
            c.drawCircle(world.trailX[idx], world.trailY[idx], 13 * k, TMP);
        }

        // Плазменная аура-оружие
        for (int i = 0; i < world.weapons.size(); i++) {
            if (world.weapons.get(i).id == Weapons.W_AURA) {
                float r = world.weapons.get(i).range();
                float ap = 0.5f + 0.5f * (float) Math.sin(world.time * 5f);
                TMP.setStyle(Paint.Style.STROKE);
                TMP.setStrokeWidth(5 + 4 * ap);
                TMP.setColor(Art.fade(0xFFFF8A3D, 0.5f + 0.3f * ap));
                c.drawCircle(x, y, r, TMP);
                TMP.setStrokeWidth(1.5f);
                TMP.setColor(Art.fade(0xFFFFC59D, 0.35f));
                c.drawCircle(x, y, r * 0.92f, TMP);
                TMP.setStyle(Paint.Style.FILL);
            }
        }

        // Внешнее свечение (двойной слой — сочнее)
        Art.glowAt(c, color, x, y, 260, 130);
        Art.glowAt(c, color, x, y, 150, 235);
        // Ядро
        TMP.setStyle(Paint.Style.FILL);
        TMP.setColor(color);
        c.drawCircle(x, y, 16 * pulse, TMP);
        TMP.setColor(0xFFFFFFFF);
        c.drawCircle(x, y, 8, TMP);
        // Орбитальные искры
        for (int i = 0; i < 3; i++) {
            float a = world.time * 3.4f + i * (float) (Math.PI * 2 / 3);
            TMP.setColor(Art.fade(color, 0.85f));
            c.drawCircle(x + (float) Math.cos(a) * 30, y + (float) Math.sin(a) * 30, 4, TMP);
        }
        // Щит — гексагон
        if (world.shield) {
            float sp = 0.6f + 0.4f * (float) Math.sin(world.time * 4f);
            TMP.setStyle(Paint.Style.STROKE);
            TMP.setStrokeWidth(3.5f);
            TMP.setColor(Art.fade(0xFF6BD4FF, 0.5f + 0.4f * sp));
            drawPolygon(c, x, y, 34, 6, world.time * 0.8f);
        }
        // Неуязвимость — мерцание
        if (world.hurtCd > 0 && ((int) (world.hurtCd * 20) % 2) == 0) {
            TMP.setStyle(Paint.Style.STROKE);
            TMP.setStrokeWidth(3);
            TMP.setColor(0xAAFFFFFF);
            c.drawCircle(x, y, 24, TMP);
        }
        // Радиус магнита (едва заметный)
        TMP.setStyle(Paint.Style.STROKE);
        TMP.setStrokeWidth(1.5f);
        TMP.setColor(0x2222AAFF);
        c.drawCircle(x, y, world.magnetR, TMP);
    }

    // ---------------------------------------------------------------- враги

    private static void drawEnemy(Canvas c, World.Enemy e, World world) {
        float r = e.r;
        int base = e.hitFlash > 0 ? 0xFFFFFFFF : e.color;
        int glowColor = e.elite ? Art.ELITE : base;

        // Спавн: материализация с кольцом
        if (e.spawnT > 0) {
            float k = 1f - e.spawnT / 0.45f;
            TMP.setStyle(Paint.Style.STROKE);
            TMP.setStrokeWidth(3);
            TMP.setColor(Art.fade(glowColor, 0.8f * (1 - k)));
            c.drawCircle(e.x, e.y, e.r + (1 - k) * 60, TMP);
            Art.glowAt(c, glowColor, e.x, e.y, r * 6.4f * k, (int) (255 * k));
            return;
        }

        // «Дыхание» — лёгкая пульсация
        float breathe = 1f + 0.05f * (float) Math.sin(world.time * 5f + e.x * 0.03f);
        r *= breathe;

        // Glow-подложка (двойная: широкая тусклая + плотная яркая)
        Art.glowAt(c, glowColor, e.x, e.y, r * 7.2f, 80);
        Art.glowAt(c, glowColor, e.x, e.y, r * 3.8f, 230);

        TMP.setStyle(Paint.Style.FILL);
        TMP.setColor(base);

        switch (e.type) {
            case World.E_MITE:
                c.drawCircle(e.x, e.y, r * 0.8f, TMP);
                break;
            case World.E_RUNNER:
                // Треугольник по направлению к игроку
                drawTriangle(c, e, world, r);
                break;
            case World.E_BRUTE:
                drawPolygon(c, e.x, e.y, r, 6, world.time * 0.5f);
                break;
            case World.E_SPITTER:
                drawPolygon(c, e.x, e.y, r, 4, world.time * 1.2f);
                break;
            case World.E_SPLITTER:
                drawPolygon(c, e.x, e.y, r, 4, world.time * 0.8f + 0.78f);
                break;
            case World.E_SHIELDED: {
                c.drawCircle(e.x, e.y, r * 0.75f, TMP);
                TMP.setStyle(Paint.Style.STROKE);
                TMP.setStrokeWidth(4);
                TMP.setColor(0xCC6BD4FF);
                c.drawCircle(e.x, e.y, r, TMP);
                break;
            }
            case World.E_BOMBER: {
                float bl = 0.5f + 0.5f * (float) Math.abs(Math.sin(world.time * 8f));
                c.drawCircle(e.x, e.y, r * (0.8f + 0.15f * bl), TMP);
                TMP.setColor(0xFFFFFFFF);
                c.drawCircle(e.x, e.y, r * 0.3f * bl, TMP);
                break;
            }
            case World.E_BAT: {
                // Две «дуги-крыла»
                float flap = (float) Math.sin(world.time * 14f + e.x * 0.05f) * 0.5f;
                TMP.setStyle(Paint.Style.STROKE);
                TMP.setStrokeWidth(5);
                c.drawArc(e.x - r, e.y - r * (0.7f + flap), r, r * (0.7f - flap), -140, 100, false, TMP);
                c.drawArc(e.x, e.y - r * (0.7f + flap), r, r * (0.7f - flap), 140, -100, false, TMP);
                TMP.setStyle(Paint.Style.FILL);
                c.drawCircle(e.x, e.y, r * 0.35f, TMP);
                break;
            }
            case World.E_BOSS: {
                drawBoss(c, e, world);
                break;
            }
            case World.E_GREED: {
                // Золотой ромб с «хвостом» искр
                drawDiamond(c, e.x, e.y, r * 1.2f);
                TMP.setColor(0xFFFFFFFF);
                drawDiamond(c, e.x, e.y, r * 0.55f);
                float ta = world.time * 9f;
                for (int k = 0; k < 3; k++) {
                    float a = ta + k * 2.1f;
                    TMP.setColor(Art.fade(Art.GOLD, 0.7f));
                    c.drawCircle(e.x - (float) Math.cos(a) * 26,
                            e.y - (float) Math.sin(a) * 26, 3.5f, TMP);
                }
                // Таймер жизни — тающая дуга
                TMP.setStyle(Paint.Style.STROKE);
                TMP.setStrokeWidth(3);
                TMP.setColor(Art.GOLD);
                float lifeK = 1f - e.t / 14f;
                c.drawArc(new android.graphics.RectF(e.x - r * 1.6f, e.y - r * 1.6f,
                        e.x + r * 1.6f, e.y + r * 1.6f), -90, 360 * lifeK, false, TMP);
                TMP.setStyle(Paint.Style.FILL);
                break;
            }
            default:
                c.drawCircle(e.x, e.y, r * 0.8f, TMP);
        }

        // Элита — корона из ромбов
        if (e.elite) {
            TMP.setColor(Art.GOLD);
            for (int k = 0; k < 4; k++) {
                float a = world.time * 2f + k * (float) (Math.PI / 2);
                float ox = e.x + (float) Math.cos(a) * (r + 14);
                float oy = e.y + (float) Math.sin(a) * (r + 14);
                drawDiamond(c, ox, oy, 7);
            }
        }

        // HP-бар для повреждённых
        if (e.hp < e.hpMax && e.type != World.E_BOSS) {
            float bw = r * 2;
            TMP.setColor(0x88000000);
            c.drawRect(e.x - bw / 2, e.y - r - 12, e.x - bw / 2 + (bw), e.y - r - 12 + (5), TMP);
            float k = Math.max(0, e.hp / e.hpMax);
            TMP.setColor(k > 0.4f ? 0xFF9DFF6B : 0xFFFF5470);
            c.drawRect(e.x - bw / 2, e.y - r - 12, e.x - bw / 2 + (bw * k), e.y - r - 12 + (5), TMP);
        }

        // Замедление — синий оттенок
        if (e.slowT > 0) {
            TMP.setStyle(Paint.Style.STROKE);
            TMP.setStrokeWidth(2);
            TMP.setColor(0x886BD4FF);
            c.drawCircle(e.x, e.y, r + 4, TMP);
        }
    }

    private static void drawBoss(Canvas c, World.Enemy e, World world) {
        float r = e.r;
        if (e.queen) {
            drawQueen(c, e, world);
            return;
        }
        // Вращающиеся шипы
        TMP.setStyle(Paint.Style.FILL);
        TMP.setColor(e.hitFlash > 0 ? 0xFFFFFFFF : (int) World.ETAB[World.E_BOSS][4]);
        for (int k = 0; k < 8; k++) {
            float a = world.time * 1.2f + k * (float) (Math.PI / 4);
            float ox = e.x + (float) Math.cos(a) * (r + 20);
            float oy = e.y + (float) Math.sin(a) * (r + 20);
            drawPolygon(c, ox, oy, 14, 3, a);
        }
        // Тело
        drawPolygon(c, e.x, e.y, r, 8, world.time * 0.4f);
        // Внутреннее ядро
        float pulse = 0.6f + 0.4f * (float) Math.sin(world.time * 5f);
        TMP.setColor(0xFFFFFFFF);
        drawPolygon(c, e.x, e.y, r * 0.45f * pulse + r * 0.2f, 8, -world.time * 0.8f);
        // «Глаз»
        float dx = world.px - e.x, dy = world.py - e.y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > 1) {
            TMP.setColor(0xFFFFE066);
            c.drawCircle(e.x + dx / d * r * 0.3f, e.y + dy / d * r * 0.3f,
                    r * 0.14f, TMP);
        }
    }

    /** Королева Роя: тело + пульсирующие крылья-дуги + корона. */
    private static void drawQueen(Canvas c, World.Enemy e, World world) {
        float r = e.r;
        int col = e.hitFlash > 0 ? 0xFFFFFFFF : 0xFFB388FF;
        // Крылья — четыре дуги
        float flap = (float) Math.sin(world.time * 6f) * 0.35f;
        TMP.setStyle(Paint.Style.STROKE);
        TMP.setStrokeWidth(7);
        TMP.setColor(col);
        for (int k = 0; k < 4; k++) {
            float dir = k < 2 ? -1 : 1;
            float side = k % 2 == 0 ? -1 : 1;
            float base = dir < 0 ? -30 : 210;
            c.save();
            c.rotate(side * (18 + flap * 20), e.x, e.y);
            c.drawArc(e.x - r * 1.6f, e.y - r * 1.1f, e.x, e.y + r * 1.1f,
                    base, 60, false, TMP);
            c.restore();
        }
        // Тело — веретено
        TMP.setStyle(Paint.Style.FILL);
        TMP.setColor(col);
        drawPolygon(c, e.x, e.y, r, 6, world.time * 0.5f);
        // Ядро-«сердце»
        float pulse = 0.6f + 0.4f * (float) Math.sin(world.time * 7f);
        TMP.setColor(0xFFFFE066);
        c.drawCircle(e.x, e.y, r * 0.3f * pulse + r * 0.12f, TMP);
        // Корона из ромбов
        TMP.setColor(0xFFF5C542);
        for (int k = 0; k < 5; k++) {
            float a = -1.57f + (k - 2) * 0.38f;
            float ox = e.x + (float) Math.cos(a) * (r + 16);
            float oy = e.y + (float) Math.sin(a) * (r + 16);
            drawDiamond(c, ox, oy, 9 - Math.abs(k - 2) * 2);
        }
    }

    private static void drawTriangle(Canvas c, World.Enemy e, World world, float r) {
        float dx = world.px - e.x, dy = world.py - e.y;
        float a = (float) Math.atan2(dy, dx);
        PATH.reset();
        PATH.moveTo(e.x + (float) Math.cos(a) * r, e.y + (float) Math.sin(a) * r);
        PATH.lineTo(e.x + (float) Math.cos(a + 2.4f) * r, e.y + (float) Math.sin(a + 2.4f) * r);
        PATH.lineTo(e.x + (float) Math.cos(a - 2.4f) * r, e.y + (float) Math.sin(a - 2.4f) * r);
        PATH.close();
        c.drawPath(PATH, TMP);
    }

    private static void drawPolygon(Canvas c, float x, float y, float r, int n, float rot) {
        PATH.reset();
        for (int i = 0; i < n; i++) {
            float a = rot + (float) (Math.PI * 2) * i / n;
            float px = x + (float) Math.cos(a) * r;
            float py = y + (float) Math.sin(a) * r;
            if (i == 0) PATH.moveTo(px, py);
            else PATH.lineTo(px, py);
        }
        PATH.close();
        c.drawPath(PATH, TMP);
    }

    private static void drawDiamond(Canvas c, float x, float y, float r) {
        PATH.reset();
        PATH.moveTo(x, y - r);
        PATH.lineTo(x + r, y);
        PATH.lineTo(x, y + r);
        PATH.lineTo(x - r, y);
        PATH.close();
        c.drawPath(PATH, TMP);
    }

    // ---------------------------------------------------------------- прочее

    private static void drawBullet(Canvas c, World.Bullet b, World world) {
        if (b.enemy) {
            // Вражеский снаряд — ядро с ореолом
            Art.glowAt(c, 0xFFFF2E63, b.x, b.y, b.r * 6, 190);
            TMP.setStyle(Paint.Style.FILL);
            TMP.setColor(0xFFFFFFFF);
            c.drawCircle(b.x, b.y, b.r * 0.7f, TMP);
            return;
        }
        int col;
        switch (b.kind) {
            case 1: col = 0xFF9D6BFF; break;  // сферы
            case 2: col = Art.PLAYER; break;  // ракеты
            case 3: col = 0xFF8AE8FF; break;  // осколки
            case 5: col = 0xFF7DE2FF; break;  // бумеранг
            default: col = Art.PLAYER; break; // пульс
        }
        if (b.kind == 6) {
            // Чёрная дыра
            Art.glowAt(c, 0xFF6BD4FF, b.x, b.y, 130, 140);
            TMP.setStyle(Paint.Style.FILL);
            TMP.setColor(0xFF050510);
            c.drawCircle(b.x, b.y, b.r, TMP);
            TMP.setStyle(Paint.Style.STROKE);
            TMP.setStrokeWidth(3);
            TMP.setColor(0xFFB388FF);
            float wr = b.r + 6 + 3 * (float) Math.sin(world.time * 8f);
            c.drawCircle(b.x, b.y, wr, TMP);
            return;
        }
        // Streak — вытянутый след по скорости
        if (b.kind == 0 || b.kind == 3 || b.kind == 5) {
            Art.P_STROKE.setStyle(Paint.Style.STROKE);
            Art.P_STROKE.setStrokeWidth(b.r * 1.4f);
            Art.P_STROKE.setColor(Art.fade(col, 0.45f));
            c.drawLine(b.x - b.vx * 0.035f, b.y - b.vy * 0.035f, b.x, b.y, Art.P_STROKE);
        }
        TMP.setStyle(Paint.Style.FILL);
        TMP.setColor(col);
        if (b.kind == 3) {
            // Осколок — ромб по направлению
            float a = (float) Math.atan2(b.vy, b.vx);
            PATH.reset();
            float r = b.r * 1.6f;
            PATH.moveTo(b.x + (float) Math.cos(a) * r, b.y + (float) Math.sin(a) * r);
            PATH.lineTo(b.x + (float) Math.cos(a + 2.3f) * r * 0.6f, b.y + (float) Math.sin(a + 2.3f) * r * 0.6f);
            PATH.lineTo(b.x - (float) Math.cos(a) * r * 0.5f, b.y - (float) Math.sin(a) * r * 0.5f);
            PATH.lineTo(b.x + (float) Math.cos(a - 2.3f) * r * 0.6f, b.y + (float) Math.sin(a - 2.3f) * r * 0.6f);
            PATH.close();
            c.drawPath(PATH, TMP);
        } else if (b.kind == 2) {
            // Ракета — тело + огонёк
            c.drawCircle(b.x, b.y, b.r, TMP);
            TMP.setColor(0xFFFFE066);
            c.drawCircle(b.x - b.vx * 0.02f, b.y - b.vy * 0.02f, b.r * 0.5f, TMP);
        } else if (b.kind == 5) {
            // Бумеранг — вращающийся «диск»
            float sp = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
            float a = world.time * 18f;
            c.save();
            c.rotate((float) Math.toDegrees(Math.atan2(b.vy, b.vx)) + a, b.x, b.y);
            TMP.setColor(0xFFFFFFFF);
            PATH.reset();
            PATH.moveTo(b.x + b.r * 1.6f, b.y);
            PATH.lineTo(b.x, b.y + b.r * 0.5f);
            PATH.lineTo(b.x - b.r * 1.6f, b.y);
            PATH.lineTo(b.x, b.y - b.r * 0.5f);
            PATH.close();
            c.drawPath(PATH, TMP);
            c.restore();
            if (sp > 0) {
                Art.glowAt(c, 0xFF7DE2FF, b.x, b.y, b.r * 5, 120);
            }
        } else {
            c.drawCircle(b.x, b.y, b.r, TMP);
            TMP.setColor(0xFFFFFFFF);
            c.drawCircle(b.x, b.y, b.r * 0.45f, TMP);
        }
    }

    private static void drawPickup(Canvas c, World.Pickup p, World world) {
        float bob = (float) Math.sin(world.time * 4f + p.x * 0.05f) * 3;
        float x = p.x, y = p.y + bob;
        int color;
        float r;
        switch (p.kind) {
            case World.PU_XP1: color = Art.XP; r = 7; break;
            case World.PU_XP2: color = 0xFF7DE2FF; r = 9; break;
            case World.PU_XP3: color = 0xFFB388FF; r = 12; break;
            case World.PU_GOLD: color = Art.GOLD; r = 8; break;
            case World.PU_HEAL: color = Art.HEAL; r = 11; break;
            case World.PU_MAGNET: color = 0xFF22DDFF; r = 12; break;
            case World.PU_BOMB: color = 0xFFFFFFFF; r = 13; break;
            case World.PU_CHEST: color = Art.GOLD; r = 18; break;
            case World.PU_SHIELD: color = 0xFF6BD4FF; r = 12; break;
            case World.PU_GOLD2: color = 0xFFF5C542; r = 13; break;
            case World.PU_RELIC: color = Upgrades.relicColor(p.val); r = 16; break;
            default: color = Art.WHITE; r = 8; break;
        }
        // Glow (мерцающий)
        float twk = 0.75f + 0.25f * (float) Math.sin(world.time * 5f + p.x * 0.03f);
        Art.glowAt(c, color, x, y, r * 6 * twk, 210);
        TMP.setStyle(Paint.Style.FILL);
        TMP.setColor(color);
        switch (p.kind) {
            case World.PU_XP1:
            case World.PU_XP2:
            case World.PU_XP3: {
                // Вращающийся кристалл
                c.save();
                c.rotate((float) Math.toDegrees(world.time * 2.2f + p.x * 0.02f), x, y);
                drawDiamond(c, x, y, r * (p.kind == World.PU_XP3 ? 1.15f : 1f));
                c.restore();
                TMP.setColor(0xAAFFFFFF);
                drawDiamond(c, x, y, r * 0.4f);
                break;
            }
            case World.PU_HEAL:
                // Крест
                c.drawRect(x - r * 0.3f, y - r, x - r * 0.3f + (r * 0.6f), y - r + (r * 2), TMP);
                c.drawRect(x - r, y - r * 0.3f, x - r + (r * 2), y - r * 0.3f + (r * 0.6f), TMP);
                break;
            case World.PU_CHEST:
                // Сундук: тёмный с золотой окантовкой
                TMP.setColor(0xFF3A2A08);
                c.drawRect(x - r, y - r * 0.7f, x - r + (r * 2), y - r * 0.7f + (r * 1.4f), TMP);
                TMP.setStyle(Paint.Style.STROKE);
                TMP.setStrokeWidth(3);
                TMP.setColor(color);
                c.drawRect(x - r, y - r * 0.7f, x + r, y + r * 0.7f, TMP);
                TMP.setStyle(Paint.Style.FILL);
                drawDiamond(c, x, y, 5);
                break;
            case World.PU_BOMB:
                c.drawCircle(x, y, r, TMP);
                TMP.setColor(0xFFFF2E63);
                c.drawCircle(x, y, r * 0.45f, TMP);
                break;
            case World.PU_MAGNET:
                TMP.setStyle(Paint.Style.STROKE);
                TMP.setStrokeWidth(4);
                c.drawArc(x - r, y - r, r * 2, r * 2, 180, 180, false, TMP);
                TMP.setStyle(Paint.Style.FILL);
                c.drawCircle(x - r, y, 3.5f, TMP);
                c.drawCircle(x + r, y, 3.5f, TMP);
                break;
            case World.PU_GOLD:
                c.save();
                c.rotate((float) Math.toDegrees(world.time * 3f + p.y * 0.02f), x, y);
                drawDiamond(c, x, y, r * 1.1f);
                c.restore();
                TMP.setColor(0xAAFFFFFF);
                drawDiamond(c, x, y, r * 0.4f);
                break;
            case World.PU_SHIELD:
                TMP.setStyle(Paint.Style.STROKE);
                TMP.setStrokeWidth(4);
                c.drawCircle(x, y, r, TMP);
                TMP.setStyle(Paint.Style.FILL);
                c.drawCircle(x, y, r * 0.4f, TMP);
                break;
            case World.PU_GOLD2:
                TMP.setColor(0xFFFFFFFF);
                c.drawCircle(x, y, r * 0.45f, TMP);
                TMP.setColor(color);
                c.drawCircle(x - r * 0.5f, y, r * 0.45f, TMP);
                c.drawCircle(x + r * 0.5f, y, r * 0.45f, TMP);
                break;
            case World.PU_RELIC: {
                // Пульсирующая реликвия-звезда
                float rot = world.time * 1.5f;
                c.save();
                c.rotate((float) Math.toDegrees(rot), x, y);
                TMP.setColor(color);
                drawPolygon(c, x, y, r, 5, -1.57f);
                c.restore();
                TMP.setColor(0xFFFFFFFF);
                drawPolygon(c, x, y, r * 0.4f, 5, -1.57f);
                break;
            }
            default:
                c.drawCircle(x, y, r, TMP);
        }
    }

    // ---------------------------------------------------------------- HUD

    private static void drawHud(Canvas c, int w, int h, World world, Game game) {
        float pad = 14;

        // XP-бар (верхняя полоса)
        float xpK = Math.min(1f, world.xp / (float) world.xpNext());
        TMP.setStyle(Paint.Style.FILL);
        TMP.setColor(0x33000000);
        c.drawRect(0, 0, w, 16, TMP);
        TMP.setColor(Art.XP);
        c.drawRect(0, 0, w * xpK, 16, TMP);

        // Уровень
        TMP.setTextAlign(Paint.Align.LEFT);
        TMP.setColor(Art.WHITE);
        TMP.setTextSize(22);
        c.drawText("LV " + world.level, pad, 44, TMP);

        // Таймер по центру
        TMP.setTextAlign(Paint.Align.CENTER);
        TMP.setTextSize(30);
        TMP.setColor(world.time >= 1195f && !world.endless ? Art.GOLD : Art.WHITE);
        c.drawText(World.fmtTime(world.time), w / 2f, 52, TMP);

        // Золото и киллы справа
        TMP.setTextAlign(Paint.Align.RIGHT);
        TMP.setColor(Art.GOLD);
        TMP.setTextSize(22);
        c.drawText("◈ " + world.goldRun, w - pad, 44, TMP);
        TMP.setColor(Art.DIM);
        c.drawText(world.kills + " " + L.t("kills"), w - pad, 70, TMP);

        // HP-бар под XP-баром
        float hpW = Math.min(w * 0.5f, 320);
        float hpH = 18;
        float hpY = 26;
        float hpX = pad;
        TMP.setStyle(Paint.Style.FILL);
        TMP.setColor(0x55000000);
        c.drawRect(hpX, hpY, hpX + (hpW), hpY + (hpH), TMP);
        float hpK = Math.max(0, world.hp / world.hpMax);
        int hpCol = hpK > 0.5f ? Art.HEAL : (hpK > 0.25f ? Art.GOLD : Art.DANGER);
        TMP.setColor(hpCol);
        c.drawRect(hpX, hpY, hpX + (hpW * hpK), hpY + (hpH), TMP);
        TMP.setColor(0x66FFFFFF);
        c.drawRect(hpX, hpY + hpH - 3, hpX + (hpW * hpK), hpY + hpH - 3 + (3), TMP);
        TMP.setStyle(Paint.Style.STROKE);
        TMP.setStrokeWidth(2);
        TMP.setColor(0x88FFFFFF);
        c.drawRect(hpX, hpY, hpX + hpW, hpY + hpH, TMP);
        // Цифры HP
        TMP.setTextAlign(Paint.Align.CENTER);
        TMP.setTextSize(13);
        TMP.setColor(0xFFFFFFFF);
        c.drawText((int) world.hp + "/" + (int) world.hpMax, hpX + hpW / 2, hpY + hpH - 4, TMP);

        // Ряд оружий игрока (иконки с уровнем)
        float ix = pad;
        float iy = hpY + hpH + 12;
        for (int i = 0; i < world.weapons.size(); i++) {
            Weapons wp = world.weapons.get(i);
            int wcol = weaponColor(wp.id);
            // Мини-глоу
            Art.glowAt(c, wcol, ix + 14, iy + 14, 40, 90);
            TMP.setStyle(Paint.Style.STROKE);
            TMP.setStrokeWidth(2);
            TMP.setColor(wcol);
            c.drawRect(ix, iy, ix + 28, iy + 28, TMP);
            TMP.setStyle(Paint.Style.FILL);
            TMP.setColor(0x33101626);
            c.drawRect(ix, iy, ix + 28, iy + 28, TMP);
            TMP.setColor(wcol);
            c.drawCircle(ix + 14, iy + 10, 5, TMP);
            TMP.setTextAlign(Paint.Align.CENTER);
            TMP.setTextSize(12);
            TMP.setColor(0xFFFFFFFF);
            c.drawText(String.valueOf(wp.lvl), ix + 14, iy + 25, TMP);
            if (wp.awakened) {
                TMP.setColor(0xFFFFE066);
                TMP.setTextSize(13);
                c.drawText("★", ix + 24, iy + 11, TMP);
            }
            ix += 34;
        }

        // Реликвии — ряд иконок справа под золотом
        float ry = 100;
        for (int i = 0; i < World.RELIC_COUNT; i++) {
            if (!world.relics[i]) continue;
            int rc = Upgrades.relicColor(i);
            Art.glowAt(c, rc, w - pad - 14, ry + 10, 36, 110);
            TMP.setStyle(Paint.Style.FILL);
            TMP.setColor(rc);
            drawPolygon(c, w - pad - 14, ry + 10, 8, 5, -1.57f);
            ry += 26;
        }

        // Killstreak-баннер
        if (world.streakN >= 10 && world.streakT > 0) {
            int tier = world.streakN >= 250 ? 4 : world.streakN >= 120 ? 3
                    : world.streakN >= 60 ? 2 : world.streakN >= 25 ? 1 : 0;
            if (tier > world.streakShown) {
                world.streakShown = tier;
                Sound.play(Sound.LEVELUP);
                world.shake = Math.max(world.shake, 6);
            }
            String ksText = tier >= 4 ? L.t("ks4") : tier == 3 ? L.t("ks3")
                    : tier == 2 ? L.t("ks2") : L.t("ks1");
            float a = Math.min(1f, world.streakT);
            TMP.setTextAlign(Paint.Align.CENTER);
            TMP.setTextSize(Math.min(44, w / 9f));
            TMP.setColor(Art.fade(0xFFFFE066, a));
            c.drawText(ksText, w / 2f, h * 0.24f, TMP);
            TMP.setTextSize(26);
            TMP.setColor(Art.fade(0xCCFFFFFF, a));
            c.drawText("×" + world.streakN, w / 2f, h * 0.24f + 36, TMP);
        }

        // Виньетка при низком HP
        if (world.hp < world.hpMax * 0.3f && !world.dead) {
            float pulse = 0.5f + 0.5f * (float) Math.abs(Math.sin(world.time * 4f));
            TMP.setStyle(Paint.Style.FILL);
            TMP.setColor(Color.argb((int) (110 * pulse), 255, 30, 60));
            TMP.setStrokeWidth(14);
            // Рамка
            float bw2 = 16;
            c.drawRect(0, 0, w, bw2, TMP);
            c.drawRect(0, h - bw2, w, h, TMP);
            c.drawRect(0, 0, bw2, h, TMP);
            c.drawRect(w - bw2, 0, w, h, TMP);
        }

        // Комбо
        if (world.comboN >= 5 && world.comboT > 0) {
            float k = Math.min(1, world.comboT / 0.6f);
            TMP.setTextAlign(Paint.Align.CENTER);
            TMP.setTextSize(34);
            TMP.setColor(Art.fade(0xFFFF8A3D, k));
            c.drawText("×" + world.comboN + " " + L.t("combo"), w / 2f, h * 0.16f, TMP);
        }

        // HP-бар босса
        if (world.bossAlive) {
            for (int i = 0; i < world.enemies.size(); i++) {
                World.Enemy e = world.enemies.get(i);
                if (e.type == World.E_BOSS && !e.deadFlag) {
                    float bw = w * 0.8f;
                    float bx = (w - bw) / 2f;
                    float by = h * 0.08f;
                    TMP.setStyle(Paint.Style.FILL);
                    TMP.setColor(0x66000000);
                    c.drawRect(bx, by, bx + (bw), by + (14), TMP);
                    TMP.setColor(e.queen ? 0xFFB388FF : Art.BOSS);
                    c.drawRect(bx, by, bx + (bw * Math.max(0, e.hp / e.hpMax)), by + (14), TMP);
                    TMP.setStyle(Paint.Style.STROKE);
                    TMP.setStrokeWidth(1.5f);
                    TMP.setColor(0xFFFFE066);
                    c.drawRect(bx, by, bx + bw, by + 14, TMP);
                    break;
                }
            }
        }
    }

    /** Цвет оружия по id (для HUD-иконок). */
    private static int weaponColor(int id) {
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

    private static void drawJoystick(Canvas c, Input input) {
        if (!input.stickActive) return;
        TMP.setStyle(Paint.Style.STROKE);
        TMP.setStrokeWidth(3);
        TMP.setColor(0x33E7EDF5);
        c.drawCircle(input.stickStartX, input.stickStartY, 90, TMP);
        TMP.setColor(0x66E7EDF5);
        c.drawCircle(input.stickStartX, input.stickStartY, 8, TMP);
        // Ручка
        float kx = input.stickStartX + input.stickX * 90;
        float ky = input.stickStartY + input.stickY * 90;
        TMP.setStyle(Paint.Style.FILL);
        TMP.setColor(0x557DE2FF);
        c.drawCircle(kx, ky, 42, TMP);
        TMP.setColor(0xCC7DE2FF);
        c.drawCircle(kx, ky, 20, TMP);
    }
}
