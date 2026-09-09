package com.syslikoffnet.overglow;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Random;

/** Вся «графика»: краски, кэш glow-спрайтов, звёзды, палитры. Ноль ассетов. */
public final class Art {

    private Art() {}

    // --- Палитра ---
    public static final int BG = 0xFF05070D;
    public static final int GRID = 0xFF141D2E;
    public static final int PLAYER = 0xFF7DE2FF;
    public static final int XP = 0xFF4F8CFF;
    public static final int GOLD = 0xFFF5C542;
    public static final int HEAL = 0xFF34D399;
    public static final int ENEMY = 0xFFFF4D6D;
    public static final int ENEMY2 = 0xFFFF8A3D;
    public static final int BOSS = 0xFFFF2E63;
    public static final int ELITE = 0xFFB388FF;
    public static final int WHITE = 0xFFE7EDF5;
    public static final int DIM = 0xFF8CA0B8;
    public static final int FAINT = 0xFF48586E;
    public static final int DANGER = 0xFFFF5470;

    // --- Краски (переиспользуемые) ---
    public static final Paint P_FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint P_STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint P_TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint P_TEXT_BIG = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint P_TEXT_MID = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint P_BMP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public static void init() {
        P_TEXT.setColor(WHITE);
        P_TEXT.setTextSize(28);
        P_TEXT.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        P_TEXT_MID.setColor(WHITE);
        P_TEXT_MID.setTextSize(44);
        P_TEXT_MID.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        P_TEXT_BIG.setColor(WHITE);
        P_TEXT_BIG.setTextSize(86);
        P_TEXT_BIG.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    // --- Кэш glow-спрайтов (радиальный градиент по цвету) ---
    private static final HashMap<Integer, Bitmap> GLOW = new HashMap<>();

    /** Мягкое свечение заданного цвета (битмап 128×128, центр — цвет). */
    public static Bitmap glow(int color) {
        Bitmap b = GLOW.get(color);
        if (b != null) return b;
        int s = 128;
        b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int transparent = color & 0x00FFFFFF;
        RadialGradient g = new RadialGradient(s / 2f, s / 2f, s / 2f,
                new int[]{color, (color & 0x66FFFFFF), transparent},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(g);
        c.drawRect(0, 0, s, s, p);
        GLOW.put(color, b);
        return b;
    }

    /** Свечение как Paint с шейдером (для колец/текста). */
    public static Paint glowPaint(int color, float radius) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(0, 0, Math.max(1, radius),
                new int[]{color, color & 0x33FFFFFF, color & 0x00FFFFFF},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        return p;
    }

    // --- Виньетка под размер экрана ---
    private static Bitmap vignette;
    private static int vw, vh;

    public static void vignette(Canvas canvas, int w, int h) {
        if (vignette == null || vw != w || vh != h) {
            vw = w;
            vh = h;
            vignette = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h),
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(vignette);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(w / 2f, h / 2f,
                    Math.max(w, h) * 0.75f,
                    new int[]{0x00000000, 0xB8000000, 0xF0000000},
                    new float[]{0.45f, 0.85f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
        canvas.drawBitmap(vignette, 0, 0, P_BMP);
    }

    // --- Туманности фона (прекеш, детерминированные позиции) ---
    private static Bitmap[] nebulas;
    private static float[] nebX, nebY, nebR;

    private static void initNebulas() {
        if (nebulas != null) return;
        int[] cols = {0x331A2E5C, 0x29331B4D, 0x290E3B45, 0x26284B6E, 0x2E1B2E5C, 0x24402050};
        nebulas = new Bitmap[cols.length];
        for (int i = 0; i < cols.length; i++) {
            int s = 256;
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            int col = cols[i] | 0xFF000000;
            int base = col & 0x00FFFFFF;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(s / 2f, s / 2f, s / 2f,
                    new int[]{col & 0x66FFFFFF, base & 0x2AFFFFFF, 0},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, s, s, p);
            nebulas[i] = b;
        }
        Random r = new Random(99);
        nebX = new float[cols.length];
        nebY = new float[cols.length];
        nebR = new float[cols.length];
        for (int i = 0; i < cols.length; i++) {
            nebX[i] = r.nextFloat() * 3600f;
            nebY[i] = r.nextFloat() * 3600f;
            nebR[i] = 420f + r.nextFloat() * 460f;
        }
    }

    /** Нарисовать слой туманностей с параллаксом (k<1 — дальше). */
    public static void drawNebulas(Canvas c, float camX, float camY,
                                   int w, int h, float zoom, float k) {
        initNebulas();
        // Смещение камеры с параллаксом: мир «сдвигается» медленнее
        float ox = w / 2f - (camX * k) * zoom;
        float oy = h / 2f - (camY * k) * zoom;
        for (int i = 0; i < nebulas.length; i++) {
            float sx = ox + (nebX[i] + (1 - k) * 1800f) * zoom;
            float sy = oy + (nebY[i] + (1 - k) * 1800f) * zoom;
            float r = nebR[i] * zoom;
            if (sx + r < -50 || sx - r > w + 50 || sy + r < -50 || sy - r > h + 50) continue;
            dstRect.set(sx - r, sy - r, sx + r, sy + r);
            c.drawBitmap(nebulas[i], null, dstRect, P_BMP);
        }
    }

    private static final android.graphics.RectF dstRect = new android.graphics.RectF();

    /** Glow-спрайт растянутый в квадрат size×size с центром в (x,y). */
    public static void glowAt(android.graphics.Canvas c, int color, float x, float y,
                              float size, int alpha) {
        dstRect.set(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f);
        P_BMP.setAlpha(alpha);
        c.drawBitmap(glow(color), null, dstRect, P_BMP);
        P_BMP.setAlpha(255);
    }

    /** Пыль: два слоя мерцающих точек с параллаксом. */
    public static void drawDust(Canvas c, float camX, float camY, int w, int h,
                                float zoom, float k, float time, int seed, int n, int alpha) {
        float ox = w / 2f - (camX * k) * zoom;
        float oy = h / 2f - (camY * k) * zoom;
        Random r = new Random(seed);
        P_FILL.setStyle(android.graphics.Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * 3600f;
            float y = r.nextFloat() * 3600f;
            float br = 0.4f + 0.6f * r.nextFloat();
            float sx = ox + (x + (1 - k) * 1800f) * zoom;
            float sy = oy + (y + (1 - k) * 1800f) * zoom;
            if (sx < -10 || sx > w + 10 || sy < -10 || sy > h + 10) continue;
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 2.2f + i * 1.7f);
            int a = (int) (alpha * br * tw);
            if (a <= 2) continue;
            P_FILL.setColor((a << 24) | 0x9DC8FF);
            c.drawCircle(sx, sy, (1.2f + br * 2f) * Math.max(0.6f, zoom), P_FILL);
        }
    }

    // --- Звёзды фона (детерминированные) ---
    private static float[] stars;

    /** n звёзд в диапазоне 0..range (по обеим осям), глубина parallax в 3-м числе. */
    public static float[] stars(int n, long seed, float range) {
        if (stars != null && stars.length == n * 3) return stars;
        stars = new float[n * 3];
        Random r = new Random(seed);
        for (int i = 0; i < n; i++) {
            stars[i * 3] = r.nextFloat() * range;
            stars[i * 3 + 1] = r.nextFloat() * range;
            stars[i * 3 + 2] = 0.3f + r.nextFloat() * 0.7f; // яркость
        }
        return stars;
    }

    /** Линейный градиент (для карточек и кнопок). */
    public static Paint grad(float x0, float y0, float x1, float y1, int c0, int c1) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(x0, y0, x1, y1, c0, c1, Shader.TileMode.CLAMP));
        return p;
    }

    /** Затемнение/затухание цвета. */
    public static int fade(int color, float k) {
        int a = (int) (Color.alpha(color) * k);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
