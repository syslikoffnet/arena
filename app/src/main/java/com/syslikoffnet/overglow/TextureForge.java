package com.syslikoffnet.overglow;

import android.graphics.Bitmap;

/**
 * TextureForge — процедурный генератор бесшовных PBR-материалов (собственная реализация,
 * никакие сторонние тайл-файлы не используются; всё считается кодом):
 * - Seamless value-noise + fBm (периодический шум, теснится без швов)
 * - На каждый материал: Albedo (512/256) + Height → нормаль-мапа (Sobel)
 * - Таблица материалов MATS[]: tile-размер в метрах, roughness, metalness, tint
 * - Отдельные текстуры: небо (equirect), мягкая blob-тень, glow-спрайт частиц
 * Вызывать init() только из GL-потока (GLUtil.initTextures).
 */
public final class TextureForge {

    private TextureForge() {}

    // =========================================================================
    // ID материалов
    // =========================================================================
    public static final int MAT_GRASS = 0;
    public static final int MAT_DIRT = 1;
    public static final int MAT_ROAD = 2;
    public static final int MAT_ROCK = 3;
    public static final int MAT_CONCRETE = 4;
    public static final int MAT_PLASTER = 5;
    public static final int MAT_BRICK = 6;
    public static final int MAT_ROOF = 7;
    public static final int MAT_WOOD = 8;
    public static final int MAT_CRATE = 9;
    public static final int MAT_METAL = 10;
    public static final int MAT_CORRUGATED = 11;
    public static final int MAT_VEHICLE = 12;
    public static final int MAT_KEVLAR = 13;
    public static final int MAT_CAMO = 14;
    public static final int MAT_GUN = 15;
    public static final int MAT_GOLD = 16;
    public static final int MAT_DRAGON = 17;
    public static final int MAT_TARP = 18;
    public static final int MAT_CANOPY = 19;
    public static final int MAT_BARK = 20;
    public static final int MAT_LEAVES = 21;
    public static final int MAT_SKIN = 22;
    public static final int MAT_SAND = 23;
    public static final int MAT_WHITE = 24;
    public static final int NUM_MATS = 25;

    public static final class Mat {
        public int albedoTex;      // GL id
        public int normalTex;      // GL id
        public float tile = 2f;    // метров на 1 повтор (world-UV режим)
        public float uvMul = 1f;   // тайлинг для объектных UV (персонажи/оружие)
        public float rough = 0.75f;
        public float metal = 0f;
        public float bump = 1f;    // сила нормаль-мапы
        public boolean worldUV = true; // true → planar projection по миру (стены/земля)
        public float r = 1f, g = 1f, b = 1f; // колор-тint поверх альбедо
    }

    public static final Mat[] MATS = new Mat[NUM_MATS];
    static {
        for (int i = 0; i < NUM_MATS; i++) MATS[i] = new Mat();
    }

    // Спец-текстуры
    public static int texSky, texShadowBlob, texGlow;

    // =========================================================================
    // Хэши и периодический шум
    // =========================================================================
    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1013904223;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return h & 0x7fffffff;
    }

    private static float hashF(int x, int y, int seed) {
        return hash(x, y, seed) * (1f / 2147483647f);
    }

    private static float fade(float t) {
        return t * t * (3f - 2f * t);
    }

    /** Периодический value-noise: решётка P x P заворачивается по модулю P → бесшовно */
    private static float vnoise(float fx, float fy, int P, int seed) {
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        float tx = fade(fx - x0);
        float ty = fade(fy - y0);
        int xa = ((x0 % P) + P) % P;
        int xb = ((x0 + 1) % P + P) % P;
        int ya = ((y0 % P) + P) % P;
        int yb = ((y0 + 1) % P + P) % P;
        float v00 = hashF(xa, ya, seed);
        float v10 = hashF(xb, ya, seed);
        float v01 = hashF(xa, yb, seed);
        float v11 = hashF(xb, yb, seed);
        return (v00 + (v10 - v00) * tx) * (1f - ty) + (v01 + (v11 - v01) * tx) * ty;
    }

    /** Фрактальный шум, бесшовный при базовой решётке base (каждая октава 2x) */
    private static float fbm(float u, float v, int base, int seed, int octaves) {
        float sum = 0f, amp = 0.5f, tot = 0f;
        int P = base;
        for (int o = 0; o < octaves; o++) {
            sum += vnoise(u * P, v * P, P, seed + o * 77) * amp;
            tot += amp;
            amp *= 0.5f;
            P *= 2;
        }
        return sum / tot;
    }

    private static float ridged(float u, float v, int base, int seed, int octaves) {
        float sum = 0f, amp = 0.5f, tot = 0f;
        int P = base;
        for (int o = 0; o < octaves; o++) {
            float n = vnoise(u * P, v * P, P, seed + o * 31);
            float r = 1f - Math.abs(n * 2f - 1f);
            sum += r * r * amp;
            tot += amp;
            amp *= 0.5f;
            P *= 2;
        }
        return sum / tot;
    }

    private static int clamp255(float v) {
        int i = (int) v;
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    private static int rgb(float r, float g, float b) {
        return 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int mixC(int c0, int c1, float t) {
        if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
        float r0 = (c0 >> 16) & 0xFF, g0 = (c0 >> 8) & 0xFF, b0 = c0 & 0xFF;
        float r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        return rgb(r0 + (r1 - r0) * t, g0 + (g1 - g0) * t, b0 + (b1 - b0) * t);
    }

    // =========================================================================
    // Каркас генерации: албедо + высота → две GL-текстуры
    // =========================================================================
    private interface Gen {
        /** @return packed ARGB color; height в out[0] (0..1) */
        int shade(float u, float v, float[] out);
    }

    private static int bake(int S, Gen gen) {
        int[] pix = new int[S * S];
        float[] hgt = new float[S * S];
        float[] ho = new float[1];
        for (int y = 0; y < S; y++) {
            float v = y / (float) S;
            for (int x = 0; x < S; x++) {
                ho[0] = 0.5f;
                int c = gen.shade(x / (float) S, v, ho);
                int i = y * S + x;
                pix[i] = c;
                hgt[i] = Math3D.clamp(ho[0], 0f, 1f);
            }
        }
        int albedo = uploadPixels(S, S, pix, false);
        int normal = uploadSobelNormal(S, hgt, 2.6f);
        // упаковка id в long невозможен в сигнатуре — возвращаем albedo, normal кладём в lastNormal
        lastNormal = normal;
        return albedo;
    }

    private static int lastNormal;

    /** Sobel-нормаль из карты высоты, обёртка по краям (seamless) */
    private static int uploadSobelNormal(int S, float[] h, float strength) {
        int[] out = new int[S * S];
        for (int y = 0; y < S; y++) {
            int ym = (y + S - 1) % S, yp = (y + 1) % S;
            for (int x = 0; x < S; x++) {
                int xm = (x + S - 1) % S, xp = (x + 1) % S;
                float dx = (h[y * S + xp] - h[y * S + xm]) * strength
                        + 0.5f * (h[yp * S + xp] - h[ym * S + xm]) * strength
                        + 0.5f * (h[ym * S + xp] - h[yp * S + xm]) * strength;
                float dy = (h[yp * S + x] - h[ym * S + x]) * strength
                        + 0.5f * (h[yp * S + xp] - h[ym * S + xm]) * strength
                        + 0.5f * (h[yp * S + xm] - h[ym * S + xp]) * strength;
                float nx = -dx, ny = -dy, nz = 1f;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= len; ny /= len; nz /= len;
                out[y * S + x] = rgb((nx * 0.5f + 0.5f) * 255f, (ny * 0.5f + 0.5f) * 255f, (nz * 0.5f + 0.5f) * 255f);
            }
        }
        return uploadPixels(S, S, out, false);
    }

    private static int uploadPixels(int w, int h, int[] pix, boolean clampEdges) {
        Bitmap bmp = Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888);
        int id = GLUtil.uploadTexture(bmp, clampEdges);
        bmp.recycle();
        return id;
    }

    // =========================================================================
    // Инициализация всех материалов (GL-поток)
    // =========================================================================
    public static void init() {
        buildGrass();
        buildDirt();
        buildRoad();
        buildRock();
        buildConcrete();
        buildPlaster();
        buildBrick();
        buildRoof();
        buildWood();
        buildCrate();
        buildMetal();
        buildCorrugated();
        buildVehicle();
        buildKevlar();
        buildCamo();
        buildGun();
        buildGold();
        buildDragon();
        buildTarp();
        buildCanopy();
        buildBark();
        buildLeaves();
        buildSkin();
        buildSand();
        buildWhite();
        buildSky();
        buildSpecials();
        configure();
    }

    // ---------------- материалы ----------------
    private static void put(int mat, int S, Gen g) {
        MATS[mat].albedoTex = bake(S, g);
        MATS[mat].normalTex = lastNormal;
    }

    private static void buildGrass() {
        put(MAT_GRASS, 512, (u, v, h) -> {
            float patch = fbm(u, v, 4, 11, 4);
            float dry = fbm(u, v, 3, 91, 3);
            float blade = vnoise(u * 220f, v * 220f, 220, 5);
            float tufts = fbm(u, v, 16, 23, 2);
            int base = mixC(rgb(52, 84, 34), rgb(74, 110, 44), patch);
            base = mixC(base, rgb(122, 116, 62), Math3D.clamp((dry - 0.58f) * 2.6f, 0f, 1f)); // сухие вытоптанные пятна
            base = mixC(base, rgb(96, 82, 52), Math3D.clamp((patch - 0.68f) * 3f, 0f, 1f));   // земля
            float bladeLight = (blade - 0.5f) * 26f * (0.4f + tufts);
            float hi = fbm(u, v, 64, 7, 1);
            base = rgb(((base >> 16) & 0xFF) + bladeLight + hi * 10f,
                       ((base >> 8) & 0xFF) + bladeLight + hi * 14f,
                       (base & 0xFF) + bladeLight + hi * 6f);
            h[0] = 0.45f + tufts * 0.25f + (blade - 0.5f) * 0.45f;
            return base;
        });
    }

    private static void buildDirt() {
        put(MAT_DIRT, 256, (u, v, h) -> {
            float n = fbm(u, v, 8, 42, 4);
            float gravel = vnoise(u * 128f, v * 128f, 128, 9);
            int base = mixC(rgb(96, 72, 48), rgb(140, 112, 78), n);
            float stone = Math3D.clamp((gravel - 0.78f) * 4.5f, 0f, 1f);
            base = mixC(base, rgb(168, 160, 148), stone);
            h[0] = 0.4f + n * 0.25f + stone * 0.6f;
            return base;
        });
    }

    private static void buildRoad() {
        put(MAT_ROAD, 512, (u, v, h) -> {
            float agg = vnoise(u * 300f, v * 300f, 300, 3);
            float n = fbm(u, v, 6, 55, 3);
            int base = mixC(rgb(58, 58, 62), rgb(92, 92, 96), agg * 0.5f + n * 0.5f);
            // Трещины: ридж-шум тонкими линиями
            float crack = ridged(u, v, 5, 66, 3);
            float crackMask = Math3D.clamp((crack - 0.9f) * 22f, 0f, 1f) * Math3D.clamp((crack - 0.86f) * 8f, 0f, 1f);
            base = mixC(base, rgb(28, 28, 30), crackMask);
            // Латки (более тёмный свежий асфальт)
            float patch = fbm(u, v, 3, 123, 2);
            if (patch > 0.63f) base = mixC(base, rgb(44, 44, 48), (patch - 0.63f) * 3f);
            h[0] = 0.5f + agg * 0.18f - crackMask * 0.5f;
            return base;
        });
    }

    private static void buildRock() {
        put(MAT_ROCK, 256, (u, v, h) -> {
            float n = fbm(u, v, 6, 77, 4);
            float cr = ridged(u, v, 4, 88, 3);
            int base = mixC(rgb(96, 92, 86), rgb(150, 144, 132), n);
            base = mixC(base, rgb(60, 58, 54), Math3D.clamp((cr - 0.75f) * 3.5f, 0f, 1f));
            float lichen = vnoise(u * 60f, v * 60f, 60, 12);
            if (lichen > 0.8f) base = mixC(base, rgb(150, 158, 96), (lichen - 0.8f) * 4f);
            h[0] = 0.5f + n * 0.3f - Math3D.clamp((cr - 0.75f) * 2f, 0f, 1f) * 0.5f;
            return base;
        });
    }

    private static void buildConcrete() {
        put(MAT_CONCRETE, 512, (u, v, h) -> {
            float n = fbm(u, v, 5, 200, 4);
            float grain = vnoise(u * 200f, v * 200f, 200, 210);
            int base = mixC(rgb(148, 146, 138), rgb(186, 184, 176), n);
            base = rgb(((base >> 16) & 0xFF) + (grain - 0.5f) * 14f, ((base >> 8) & 0xFF) + (grain - 0.5f) * 14f, (base & 0xFF) + (grain - 0.5f) * 14f);
            // Швы опалубки
            float sx = Math.min(Math.abs(u - 0.5f), Math.abs(v - 0.5f));
            float seam = Math3D.clamp(1f - Math.abs((v > 0.5f ? v - 0.5f : 0.5f - v)) * 0f, 0f, 1f);
            if ((u % 0.5f) < 0.006f || (v % 0.5f) < 0.006f) {
                base = mixC(base, rgb(90, 88, 82), 0.8f);
            }
            // Тёмные затёки
            float stain = fbm(u, v, 2, 313, 2);
            if (stain > 0.66f) base = mixC(base, rgb(110, 108, 100), (stain - 0.66f) * 2.5f);
            // Поры
            float pit = vnoise(u * 340f, v * 340f, 340, 424);
            float pitMask = Math3D.clamp((pit - 0.86f) * 7f, 0f, 1f);
            base = mixC(base, rgb(96, 94, 88), pitMask);
            h[0] = 0.5f + grain * 0.16f - pitMask * 0.5f - ((u % 0.5f) < 0.006f || (v % 0.5f) < 0.006f ? 0.3f : 0f);
            return base;
        });
    }

    private static void buildPlaster() {
        put(MAT_PLASTER, 512, (u, v, h) -> {
            float n = fbm(u, v, 6, 500, 4);
            int base = mixC(rgb(196, 186, 166), rgb(222, 214, 198), n);
            // Осыпающаяся штукатурка снизу → кирпич
            float crk = fbm(u, v, 3, 555, 2);
            float wear = Math3D.clamp((v - 0.68f) * 3f, 0f, 1f) * Math3D.clamp((crk - 0.35f) * 2.5f, 0f, 1f);
            if (wear > 0.05f) {
                float br = Math3D.clamp((v * 10f) % 1f * 3f, 0f, 1f);
                int brickCol = mixC(rgb(140, 70, 46), rgb(118, 58, 40), br);
                base = mixC(base, brickCol, wear);
            }
            // Сажяные потёки сверху
            float grime = fbm(u, v, 2, 606, 2);
            base = mixC(base, rgb(120, 116, 108), Math3D.clamp((grime - 0.6f) * 1.6f, 0f, 1f) * (1f - v) * 0.8f);
            float stip = vnoise(u * 260f, v * 260f, 260, 610);
            base = rgb(((base >> 16) & 0xFF) + (stip - 0.5f) * 12f, ((base >> 8) & 0xFF) + (stip - 0.5f) * 12f, (base & 0xFF) + (stip - 0.5f) * 12f);
            h[0] = 0.5f + stip * 0.25f - wear * 0.25f;
            return base;
        });
    }

    private static void buildBrick() {
        put(MAT_BRICK, 256, (u, v, h) -> {
            float rows = 8f;
            float vr = v * rows;
            int row = (int) Math.floor(vr);
            float fr = vr - row;
            float off = (row & 1) == 0 ? 0f : 0.5f;
            float uc = (u + off) % 1f;
            float cols = 4f;
            float hr = uc * cols;
            int col = (int) Math.floor(hr);
            float fh = hr - col;
            float mortar = 0.075f;
            boolean inMortar = fr < mortar || fr > 1f - mortar || fh < mortar || fh > 1f - mortar;
            float brickVar = hashF(col, row, 701) * 1f;
            int brickCol = mixC(rgb(136, 66, 44), rgb(168, 96, 62), brickVar * 0.6f + fbm(u, v, 12, 702, 2) * 0.4f);
            int base = inMortar ? mixC(rgb(176, 172, 162), rgb(150, 146, 138), vnoise(u * 80f, v * 80f, 80, 703)) : brickCol;
            // выщербленности на кирпиче
            float chip = vnoise(u * 120f, v * 120f, 120, 704);
            if (!inMortar && chip > 0.88f) base = mixC(base, rgb(96, 50, 36), (chip - 0.88f) * 8f);
            h[0] = inMortar ? 0.15f : 0.75f + chip * 0.1f;
            return base;
        });
    }

    private static void buildRoof() {
        put(MAT_ROOF, 256, (u, v, h) -> {
            float cols = 6f;
            float uc = u * cols;
            float fu = uc - (float) Math.floor(uc);
            int col = (int) Math.floor(uc);
            // Полукруглая черепица: косинусная подсветка гребня
            float dome = (float) Math.sin(fu * Math.PI);
            int terracotta = mixC(rgb(122, 58, 38), rgb(188, 108, 66), 0.35f + dome * 0.6f);
            float rowsV = v * 12f;
            int row = (int) Math.floor(rowsV);
            float fr = rowsV - row;
            float rowShade = fr < 0.12f ? 0.55f : 1f; // теневой стыд ряда
            float n = fbm(u, v, 8, 800, 3);
            int base = rgb(((terracotta >> 16) & 0xFF) * rowShade * (0.85f + n * 0.3f),
                           ((terracotta >> 8) & 0xFF) * rowShade * (0.85f + n * 0.3f),
                           (terracotta & 0xFF) * rowShade * (0.85f + n * 0.3f));
            // Мох по краям гребней
            float moss = fbm(u, v, 4, 801, 2);
            if (moss > 0.62f && dome > 0.55f) base = mixC(base, rgb(78, 96, 46), (moss - 0.62f) * 2.5f);
            h[0] = 0.3f + dome * 0.5f + (fr < 0.1f ? -0.2f : 0f);
            return base;
        });
    }

    private static void buildWood() {
        put(MAT_WOOD, 256, (u, v, h) -> {
            float planks = 5f;
            float pv = v * planks;
            int pl = (int) Math.floor(pv);
            float fp = pv - pl;
            float grain = (float) Math.sin((u * 26f + fbm(u, v, 4, pl * 3 + 10, 3) * 9f) * 1f) * 0.5f + 0.5f;
            int plankBase = mixC(rgb(122, 88, 54), rgb(158, 118, 76), grain * 0.55f + hashF(pl, 0, 810) * 0.4f);
            float seam = (fp < 0.03f || fp > 0.97f) ? 1f : 0f;
            int base = mixC(plankBase, rgb(58, 40, 24), seam * 0.9f);
            float scratches = vnoise(u * 140f, v * 900f, 140, 811);
            base = rgb(((base >> 16) & 0xFF) + (scratches - 0.5f) * 16f, ((base >> 8) & 0xFF) + (scratches - 0.5f) * 15f, (base & 0xFF) + (scratches - 0.5f) * 12f);
            h[0] = 0.5f + grain * 0.18f - seam * 0.6f;
            return base;
        });
    }

    private static void buildCrate() {
        put(MAT_CRATE, 256, (u, v, h) -> {
            float grain = (float) Math.sin((u * 40f + fbm(u, v, 3, 20, 3) * 6f)) * 0.5f + 0.5f;
            int wood = mixC(rgb(138, 100, 60), rgb(170, 130, 84), grain * 0.5f + fbm(u, v, 10, 30, 2) * 0.5f);
            // Доски + рама
            float boards = 5f;
            float bv = v * boards;
            float fb = bv - (float) Math.floor(bv);
            boolean boardSeam = fb < 0.045f || fb > 0.955f;
            boolean frame = u < 0.07f || u > 0.93f || v < 0.07f || v > 0.93f;
            boolean diag = Math.abs((u - 0.5f) - (v - 0.5f)) < 0.045f || Math.abs((u - 0.5f) + (v - 0.5f)) < 0.045f;
            int base = wood;
            if (boardSeam) base = mixC(base, rgb(66, 44, 26), 0.85f);
            if (frame) base = mixC(base, rgb(96, 66, 38), 0.6f);
            if (diag) base = mixC(base, rgb(96, 66, 38), 0.45f);
            // Гвозди по раме
            float nail = vnoise(u * 200f, v * 200f, 200, 40);
            if (frame && nail > 0.975f) base = rgb(60, 60, 64);
            h[0] = 0.5f + grain * 0.12f - (boardSeam ? 0.35f : 0f) + (frame ? 0.22f : 0f) + (diag ? 0.12f : 0f);
            return base;
        });
    }

    private static void buildMetal() {
        put(MAT_METAL, 256, (u, v, h) -> {
            float brush = vnoise(u * 1500f, v * 60f, 128, 900);
            float n = fbm(u, v, 6, 901, 3);
            int base = mixC(rgb(96, 100, 106), rgb(140, 144, 148), brush * 0.35f + n * 0.65f);
            // Ржавые пятна по краям
            float rust = fbm(u, v, 3, 902, 3);
            float rustMask = Math3D.clamp((rust - 0.56f) * 2.8f, 0f, 1f);
            base = mixC(base, mixC(rgb(126, 66, 32), rgb(88, 44, 24), fbm(u, v, 14, 903, 2)), rustMask);
            // Заклёпки по горизонтали
            float rp = Math.abs(((v * 4f) % 1f) - 0.5f);
            float rq = Math.abs(((u * 8f) % 1f) - 0.5f);
            float rivetD = (float) Math.sqrt(rp * rp * 4f + rq * rq * 4f);
            float rivet = Math3D.clamp(1f - rivetD * 9f, 0f, 1f);
            base = mixC(base, rgb(170, 172, 176), rivet * 0.8f);
            h[0] = 0.5f + brush * 0.06f - rustMask * 0.15f + rivet * 0.5f;
            return base;
        });
    }

    private static void buildCorrugated() {
        put(MAT_CORRUGATED, 256, (u, v, h) -> {
            float ribs = 16f;
            float ru = u * ribs;
            float shape = (float) Math.cos((ru - (float) Math.floor(ru)) * Math.PI * 2f) * 0.5f + 0.5f;
            float n = fbm(u, v, 8, 1000, 3);
            int paint = mixC(rgb(64, 84, 74), rgb(120, 140, 128), shape * 0.55f + n * 0.45f);
            // Ржавчина снизу и на стыках
            float rust = fbm(u, v, 4, 1001, 3);
            float mask = Math3D.clamp((rust - 0.5f) * 2.2f, 0f, 1f) * Math3D.clamp((v - 0.55f) * 2.2f, 0f, 1f) + Math3D.clamp((rust - 0.78f) * 4f, 0f, 1f) * (1f - v) * 0.6f;
            mask = Math.min(mask, 1f);
            int base = mixC(paint, rgb(120, 62, 30), mask);
            // Потёртости гребней
            base = mixC(base, rgb(160, 162, 160), Math3D.clamp((shape - 0.92f) * 12f, 0f, 1f) * (1f - mask) * 0.6f);
            h[0] = 0.2f + shape * 0.75f;
            return base;
        });
    }

    private static void buildVehicle() {
        put(MAT_VEHICLE, 256, (u, v, h) -> {
            float n = fbm(u, v, 8, 1100, 3);
            int base = mixC(rgb(74, 82, 56), rgb(96, 104, 72), n);
            // Панельные линии
            float pl = Math.min(Math.abs(((u * 2f) % 1f) - 0.5f), Math.abs(((v * 2f) % 1f) - 0.5f));
            float panel = Math3D.clamp(1f - pl * 60f, 0f, 1f);
            base = mixC(base, rgb(40, 46, 32), panel * 0.8f);
            // Царапины и сколы до грунта
            float sc = vnoise(u * 240f, v * 30f, 240, 1101);
            float scratch = Math3D.clamp((sc - 0.9f) * 10f, 0f, 1f);
            base = mixC(base, rgb(150, 150, 146), scratch * 0.7f);
            // Грязь снизу
            float mud = fbm(u, v, 5, 1102, 3);
            base = mixC(base, rgb(84, 66, 44), Math3D.clamp((mud - 0.5f) * 1.8f, 0f, 1f) * Math3D.clamp((v - 0.6f) * 2.5f, 0f, 1f));
            h[0] = 0.5f + n * 0.1f - panel * 0.25f + scratch * 0.1f;
            return base;
        });
    }

    private static void buildKevlar() {
        put(MAT_KEVLAR, 256, (u, v, h) -> {
            float weave = (hashF((int) (u * 256f) & 3, (int) (v * 256f) & 3, 1200) - 0.5f) * 10f;
            float n = fbm(u, v, 10, 1201, 2);
            int base = rgb(38 + weave + n * 8f, 40 + weave + n * 8f, 42 + weave + n * 8f);
            // MOLLE стропы
            float band = Math.abs(((v * 6f) % 1f) - 0.5f);
            float strap = Math3D.clamp(1f - band * 9f, 0f, 1f);
            base = mixC(base, rgb(52, 54, 50), strap * 0.9f);
            float stitch = vnoise(u * 400f, v * 400f, 400, 1202);
            h[0] = 0.45f + (weave + 5f) * 0.02f + strap * 0.45f + stitch * 0.05f;
            return base;
        });
    }

    private static void buildCamo() {
        // Собственная абстрактная "флора"-схема (не копия конкретного тайла)
        put(MAT_CAMO, 256, (u, v, h) -> {
            float baseN = fbm(u, v, 3, 1300, 4);
            float greenN = fbm(u, v, 4, 1301, 4);
            float brownN = fbm(u, v, 5, 1302, 3);
            float blackN = fbm(u, v, 9, 1303, 2);
            int col = mixC(rgb(108, 104, 74), rgb(74, 90, 52), Math3D.clamp((baseN - 0.3f) * 3f, 0f, 1f)); // фон хаки→зелень
            col = mixC(col, rgb(58, 72, 40), Math3D.clamp((greenN - 0.55f) * 3f, 0f, 1f));                    // тёмно-зелёные кляксы
            col = mixC(col, rgb(92, 70, 44), Math3D.clamp((brownN - 0.6f) * 3.2f, 0f, 1f));                   // бурые
            col = mixC(col, rgb(36, 40, 28), Math3D.clamp((blackN - 0.82f) * 6f, 0f, 1f));                    // чёрные мазки
            float weave = (vnoise(u * 300f, v * 300f, 300, 1304) - 0.5f) * 12f;
            col = rgb(((col >> 16) & 0xFF) + weave, ((col >> 8) & 0xFF) + weave, (col & 0xFF) + weave);
            h[0] = 0.5f + weave * 0.012f;
            return col;
        });
    }

    private static void buildGun() {
        put(MAT_GUN, 256, (u, v, h) -> {
            float n = fbm(u, v, 20, 1400, 2);
            float micro = vnoise(u * 512f, v * 512f, 512, 1401);
            int base = mixC(rgb(34, 35, 38), rgb(64, 66, 70), n * 0.6f + micro * 0.4f);
            // Насечки затвора (agripping) горизонтальными полосами
            float band = ((v * 32f) % 1f);
            float knurl = Math3D.clamp(1f - Math.abs(band - 0.5f) * 5f, 0f, 1f) * (band > 0.3f ? 1f : 0f);
            base = mixC(base, rgb(88, 90, 94), knurl * 0.55f);
            // Потёртости граней
            float wearN = fbm(u, v, 7, 1402, 3);
            base = mixC(base, rgb(120, 122, 126), Math3D.clamp((wearN - 0.72f) * 4f, 0f, 1f));
            h[0] = 0.4f + micro * 0.2f + knurl * 0.35f;
            return base;
        });
    }

    private static void buildGold() {
        put(MAT_GOLD, 128, (u, v, h) -> {
            float sheen = (float) Math.sin((u + v) * Math.PI * 4f) * 0.5f + 0.5f;
            float n = fbm(u, v, 8, 1500, 2);
            int base = mixC(rgb(180, 138, 36), rgb(255, 222, 120), sheen * 0.5f + n * 0.5f);
            h[0] = 0.5f + n * 0.1f;
            return base;
        });
    }

    private static void buildDragon() {
        put(MAT_DRAGON, 128, (u, v, h) -> {
            float scales = ridged(u, v, 10, 1600, 3);
            float flame = (float) Math.sin((u * 6f + fbm(u, v, 3, 1601, 2) * 3f) * Math.PI * 2f) * 0.5f + 0.5f;
            int base = mixC(rgb(96, 20, 16), rgb(168, 52, 24), scales * 0.7f + 0.3f);
            base = mixC(base, rgb(240, 160, 40), Math3D.clamp((flame - 0.7f) * 3f, 0f, 1f) * 0.7f);
            h[0] = 0.4f + scales * 0.4f;
            return base;
        });
    }

    private static void buildTarp() {
        put(MAT_TARP, 256, (u, v, h) -> {
            float weave = (vnoise(u * 180f, v * 180f, 180, 1700) - 0.5f) * 18f + (vnoise(u * 180f + 33f, v * 180f, 180, 1701) - 0.5f) * 10f;
            float wrinkle = ridged(u, v, 3, 1702, 3);
            int base = mixC(rgb(52, 76, 108), rgb(86, 116, 150), wrinkle * 0.8f);
            base = rgb(((base >> 16) & 0xFF) + weave, ((base >> 8) & 0xFF) + weave, (base & 0xFF) + weave);
            // Швы и люверсы по периметру
            float edge = Math.min(Math.min(u, 1f - u), Math.min(v, 1f - v));
            if (edge < 0.06f) base = mixC(base, rgb(40, 60, 88), 0.7f);
            float g = Math3D.clamp(1f - (float) Math.sqrt(Math.pow(Math.abs(((u * 6f) % 1f) - 0.5f) * 2f, 2) + 0f) * 3f, 0f, 1f);
            if (edge < 0.04f && g > 0.55f) base = rgb(170, 172, 176);
            h[0] = 0.5f + weave * 0.018f + wrinkle * 0.2f;
            return base;
        });
    }

    private static void buildCanopy() {
        put(MAT_CANOPY, 256, (u, v, h) -> {
            float cx = u - 0.5f, cy = v - 0.5f;
            float ang = (float) Math.atan2(cy, cx);
            int sector = (int) Math.floor((ang / (Math.PI * 2) + 0.5f) * 8f);
            int base = (sector & 1) == 0 ? rgb(206, 62, 48) : rgb(222, 220, 210);
            float weave = (vnoise(u * 220f, v * 220f, 220, 1800) - 0.5f) * 14f;
            base = rgb(((base >> 16) & 0xFF) + weave, ((base >> 8) & 0xFF) + weave, (base & 0xFF) + weave);
            float rad = (float) Math.sqrt(cx * cx + cy * cy);
            float seam = Math3D.clamp(1f - Math.abs(rad * 8f % 1f - 0.5f) * 8f, 0f, 1f);
            base = mixC(base, rgb(60, 60, 60), seam * 0.3f);
            h[0] = 0.5f + weave * 0.02f + seam * 0.1f;
            return base;
        });
    }

    private static void buildBark() {
        put(MAT_BARK, 256, (u, v, h) -> {
            float strips = fbm(u, v, 10, 1900, 2);
            float depth = ridged(u, v, 12, 1901, 4);
            int base = mixC(rgb(70, 50, 34), rgb(116, 84, 54), strips * 0.6f + (1f - depth) * 0.4f);
            base = mixC(base, rgb(38, 26, 18), Math3D.clamp((0.35f - depth) * 4f, 0f, 1f)); // глубокие щели
            float moss = fbm(u, v, 3, 1902, 2);
            base = mixC(base, rgb(72, 96, 44), Math3D.clamp((moss - 0.55f) * 2.5f, 0f, 1f) * Math3D.clamp((v - 0.45f) * 2.5f, 0f, 1f));
            h[0] = depth * 0.9f;
            return base;
        });
    }

    private static void buildLeaves() {
        put(MAT_LEAVES, 256, (u, v, h) -> {
            float clump = fbm(u, v, 8, 2000, 3);
            float micro = vnoise(u * 160f, v * 160f, 160, 2001);
            int base = mixC(rgb(30, 48, 20), rgb(64, 92, 34), clump * 0.7f + micro * 0.3f);
            base = mixC(base, rgb(96, 120, 44), Math3D.clamp((clump - 0.62f) * 3f, 0f, 1f)); // светлая верхушка
            float dead = fbm(u, v, 13, 2002, 2);
            base = mixC(base, rgb(96, 78, 34), Math3D.clamp((dead - 0.8f) * 5f, 0f, 1f));
            h[0] = 0.4f + clump * 0.4f + micro * 0.2f;
            return base;
        });
    }

    private static void buildSkin() {
        put(MAT_SKIN, 128, (u, v, h) -> {
            float n = fbm(u, v, 10, 2100, 2);
            int base = mixC(rgb(176, 134, 102), rgb(198, 158, 124), n);
            float stubble = vnoise(u * 260f, v * 260f, 260, 2101);
            if (stubble > 0.72f) base = mixC(base, rgb(120, 96, 78), (stubble - 0.72f) * 2f);
            h[0] = 0.5f + n * 0.08f;
            return base;
        });
    }

    private static void buildSand() {
        put(MAT_SAND, 256, (u, v, h) -> {
            float ripple = (float) Math.sin((v * 26f + fbm(u, v, 3, 2200, 2) * 6f) * Math.PI * 2f) * 0.5f + 0.5f;
            float grain = vnoise(u * 260f, v * 260f, 260, 2201);
            int base = mixC(rgb(196, 172, 122), rgb(224, 204, 158), ripple * 0.55f + grain * 0.45f);
            h[0] = 0.4f + ripple * 0.3f + grain * 0.15f;
            return base;
        });
    }

    private static void buildWhite() {
        put(MAT_WHITE, 4, (u, v, h) -> {
            h[0] = 0.5f;
            return rgb(255, 255, 255);
        });
        MATS[MAT_WHITE].bump = 0f;
    }

    // =========================================================================
    // Небо и спец-текстуры
    // =========================================================================
    private static void buildSky() {
        final int W = 512, H = 256;
        int[] pix = new int[W * H];
        int horizon = rgb(198, 218, 232);
        int mid = rgb(128, 172, 214);
        int zenith = rgb(64, 110, 168);
        for (int y = 0; y < H; y++) {
            float vv = y / (float) (H - 1); // 0 = зенит, 1 = горизонт
            int g = mixC(mixC(mid, zenith, Math3D.clamp((0.45f - vv) * 2.4f, 0f, 1f)), horizon,
                    Math3D.clamp((vv - 0.55f) * 2.2f, 0f, 1f));
            for (int x = 0; x < W; x++) {
                float uu = x / (float) W;
                int col = g;
                // Облака: слой кучевых в средней полосе
                if (vv > 0.18f && vv < 0.86f) {
                    float cl = fbm(uu, vv * 0.55f, 5, 3000, 4);
                    float band = Math3D.clamp(1f - Math.abs(vv - 0.62f) * 3.4f, 0f, 1f);
                    float cov = Math3D.clamp((cl - (0.62f - band * 0.22f)) * 3.4f, 0f, 1f);
                    int cloud = mixC(rgb(208, 214, 220), rgb(252, 252, 250), cov);
                    col = mixC(col, cloud, cov * (0.35f + band * 0.6f));
                    // Подсветка снизу на закатной стороне
                    col = mixC(col, rgb(255, 226, 190), Math3D.clamp((vv - 0.72f) * 3f, 0f, 1f) * cov * 0.5f);
                }
                // Солнечное сияние у горизонта (u≈0.25)
                float sd = Math.min(Math.abs(uu - 0.25f), 1f - Math.abs(uu - 0.25f));
                float sun = Math3D.clamp(1f - (sd * sd * 14f + (1f - vv) * (1f - vv) * 2.2f), 0f, 1f);
                col = mixC(col, rgb(255, 244, 214), sun * 0.85f);
                pix[y * W + x] = col;
            }
        }
        texSky = uploadPixels(W, H, pix, true);
    }

    private static void buildSpecials() {
        // Blob shadow: чёрный круг с мягким альфа-спадом (в R-канале)
        int S = 64;
        int[] pix = new int[S * S];
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float dx = (x - S / 2f + 0.5f) / (S / 2f);
                float dy = (y - S / 2f + 0.5f) / (S / 2f);
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = Math3D.clamp(1f - d, 0f, 1f);
                a = a * a * (3f - 2f * a);
                int ai = (int) (a * 255);
                pix[y * S + x] = (ai << 24) | 0x00FFFFFF; // белый с альфой (инвертируется шейдером)
            }
        }
        texShadowBlob = uploadPixels(S, S, pix, true);

        // Glow-спрайт для частиц (белый радиальный)
        int G = 64;
        int[] gp = new int[G * G];
        for (int y = 0; y < G; y++) {
            for (int x = 0; x < G; x++) {
                float dx = (x - G / 2f + 0.5f) / (G / 2f);
                float dy = (y - G / 2f + 0.5f) / (G / 2f);
                float d = Math3D.clamp(1f - (float) Math.sqrt(dx * dx + dy * dy), 0f, 1f);
                float a = d * d;
                gp[y * G + x] = ((int) (a * 255) << 24) | 0x00FFFFFF;
            }
        }
        texGlow = uploadPixels(G, G, gp, true);
    }

    // =========================================================================
    // Параметры материалов (тайлинг/шейдинг)
    // =========================================================================
    private static void configure() {
        setup(MAT_GRASS, 2.4f, 0.95f, 0.0f, 1.1f, true);
        setup(MAT_DIRT, 1.8f, 0.98f, 0.0f, 1.0f, true);
        setup(MAT_ROAD, 2.0f, 0.88f, 0.05f, 0.8f, true);
        setup(MAT_ROCK, 2.2f, 0.9f, 0.0f, 1.2f, true);
        setup(MAT_CONCRETE, 3.0f, 0.85f, 0.0f, 1.0f, true);
        setup(MAT_PLASTER, 2.6f, 0.9f, 0.0f, 1.0f, true);
        setup(MAT_BRICK, 2.4f, 0.92f, 0.0f, 1.15f, true);
        setup(MAT_ROOF, 1.5f, 0.8f, 0.0f, 1.2f, true);
        setup(MAT_WOOD, 2.2f, 0.78f, 0.0f, 1.0f, true);
        setup(MAT_CRATE, 1.4f, 0.82f, 0.0f, 1.0f, true);
        setup(MAT_METAL, 1.6f, 0.45f, 0.75f, 0.9f, true);
        setup(MAT_CORRUGATED, 2.4f, 0.5f, 0.55f, 1.15f, true);
        setup(MAT_VEHICLE, 2.2f, 0.55f, 0.25f, 0.9f, true);
        setup(MAT_KEVLAR, 0.5f, 0.8f, 0.05f, 0.9f, false);
        setup(MAT_CAMO, 0.85f, 0.9f, 0f, 0.7f, false);
        setup(MAT_GUN, 0.3f, 0.42f, 0.85f, 0.9f, false);
        setup(MAT_GOLD, 0.3f, 0.22f, 1f, 0.4f, false);
        setup(MAT_DRAGON, 0.34f, 0.35f, 0.6f, 0.8f, false);
        setup(MAT_TARP, 2.0f, 0.85f, 0f, 0.9f, true);
        setup(MAT_CANOPY, 3.0f, 0.88f, 0f, 0.6f, false);
        setup(MAT_BARK, 1.2f, 0.95f, 0f, 1.3f, true);
        setup(MAT_LEAVES, 2.0f, 0.95f, 0f, 0.9f, true);
        setup(MAT_SKIN, 0.25f, 0.65f, 0f, 0.5f, false);
        setup(MAT_SAND, 2.2f, 0.95f, 0f, 0.9f, true);
        setup(MAT_WHITE, 1f, 0.9f, 0f, 0f, false);
    }

    private static void setup(int mat, float tile, float rough, float metal, float bump, boolean worldUV) {
        Mat m = MATS[mat];
        m.tile = tile;
        m.rough = rough;
        m.metal = metal;
        m.bump = bump;
        m.worldUV = worldUV;
        m.uvMul = worldUV ? 1f : 1f;
    }

    public static Mat mat(int id) {
        return MATS[id >= 0 && id < NUM_MATS ? id : MAT_WHITE];
    }
}
