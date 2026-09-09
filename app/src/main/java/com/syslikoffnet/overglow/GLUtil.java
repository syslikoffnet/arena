package com.syslikoffnet.overglow;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Графический движок Unreal Engine 4 (UE4 Mobile PBR Pipeline):
 * - Cook-Torrance Microfacet Specular BRDF (GGX Normal Distribution, Schlick-GGX Geometry, Schlick Fresnel)
 * - ACES Filmic Tone Mapping + Gamma Correction для кинематографичной цветопередачи PUBG Mobile
 * - Динамический Rim Light (Fresnel контурный свет) для объема персонажей и техники
 * - 512x512 PBR-текстуры (Трава Эрангеля, штукатурка Починок с кирпичом, черепица, дуб, кевлар, оружейная сталь)
 */
public final class GLUtil {

    private GLUtil() {}

    // Шейдер UE4 Mobile PBR + ACES Tone Mapping
    public static final String VS_3D =
            "uniform mat4 uMVP;\n" +
            "uniform mat4 uModel;\n" +
            "uniform vec3 uEyePos;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNorm;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "varying vec3 vWorldPos;\n" +
            "varying vec3 vNorm;\n" +
            "varying vec3 vViewDir;\n" +
            "varying float vFogDist;\n" +
            "void main() {\n" +
            "    vec4 worldPos = uModel * vec4(aPos, 1.0);\n" +
            "    vWorldPos = worldPos.xyz;\n" +
            "    vNorm = normalize((uModel * vec4(aNorm, 0.0)).xyz);\n" +
            "    vViewDir = normalize(uEyePos - worldPos.xyz);\n" +
            "    vUV = aUV;\n" +
            "    vFogDist = length(uEyePos - worldPos.xyz);\n" +
            "    gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "}\n";

    public static final String FS_3D =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec4 uColor;\n" +
            "uniform vec3 uLightDir;\n" +
            "uniform vec3 uMuzzlePos;\n" +
            "uniform float uMuzzleIntensity;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform float uFogStart;\n" +
            "uniform float uFogEnd;\n" +
            "varying vec2 vUV;\n" +
            "varying vec3 vWorldPos;\n" +
            "varying vec3 vNorm;\n" +
            "varying vec3 vViewDir;\n" +
            "varying float vFogDist;\n" +
            "\n" +
            "// ACES Filmic Tone Mapping Curve (UE4 Standard)\n" +
            "vec3 ACESFilm(vec3 x) {\n" +
            "    float a = 2.51;\n" +
            "    float b = 0.03;\n" +
            "    float c = 2.43;\n" +
            "    float d = 0.59;\n" +
            "    float e = 0.14;\n" +
            "    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec4 texColor = texture2D(uTex, vUV) * uColor;\n" +
            "    vec3 N = normalize(vNorm);\n" +
            "    vec3 L = normalize(-uLightDir);\n" +
            "    vec3 V = normalize(vViewDir);\n" +
            "    vec3 H = normalize(L + V);\n" +
            "\n" +
            "    // Cook-Torrance Microfacet Specular BRDF\n" +
            "    float NdotL = max(dot(N, L), 0.0);\n" +
            "    float NdotV = max(dot(N, V), 0.001);\n" +
            "    float NdotH = max(dot(N, H), 0.0);\n" +
            "    float VdotH = max(dot(V, H), 0.0);\n" +
            "\n" +
            "    // D: GGX Normal Distribution\n" +
            "    float alpha = 0.35;\n" +
            "    float alpha2 = alpha * alpha;\n" +
            "    float denom = (NdotH * NdotH * (alpha2 - 1.0) + 1.0);\n" +
            "    float D = alpha2 / (3.14159 * denom * denom);\n" +
            "\n" +
            "    // F: Schlick Fresnel\n" +
            "    vec3 F0 = vec3(0.04);\n" +
            "    vec3 F = F0 + (1.0 - F0) * pow(1.0 - VdotH, 5.0);\n" +
            "\n" +
            "    // G: Schlick-GGX Geometry\n" +
            "    float k = (alpha + 1.0) * (alpha + 1.0) / 8.0;\n" +
            "    float g1 = NdotV / (NdotV * (1.0 - k) + k);\n" +
            "    float g2 = NdotL / (NdotL * (1.0 - k) + k);\n" +
            "    float G = g1 * g2;\n" +
            "\n" +
            "    vec3 specular = (D * F * G) / (4.0 * NdotV * NdotL + 0.001);\n" +
            "\n" +
            "    // Rim Light (Fresnel Glow)\n" +
            "    float rim = pow(1.0 - NdotV, 3.5) * 0.40;\n" +
            "\n" +
            "    // Динамическая вспышка выстрела\n" +
            "    float muzzleDist = length(vWorldPos - uMuzzlePos);\n" +
            "    float muzzle = uMuzzleIntensity / (1.0 + muzzleDist * muzzleDist * 0.15);\n" +
            "\n" +
            "    // Солнечный и окружающий рассеянный свет\n" +
            "    vec3 sunColor = vec3(1.15, 1.05, 0.95);\n" +
            "    vec3 skyColor = vec3(0.32, 0.42, 0.55);\n" +
            "    vec3 ambient = skyColor * 0.45;\n" +
            "    vec3 diffuse = texColor.rgb * sunColor * NdotL;\n" +
            "    vec3 lit = ambient * texColor.rgb + diffuse + (specular * sunColor * NdotL) + vec3(rim + muzzle);\n" +
            "\n" +
            "    // Атмосферный туман горизонта\n" +
            "    float fog = clamp((vFogDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);\n" +
            "    vec3 colorWithFog = mix(lit, uFogColor, fog * 0.65);\n" +
            "\n" +
            "    // ACES Filmic Tone Mapping\n" +
            "    vec3 finalToneMapped = ACESFilm(colorWithFog);\n" +
            "    gl_FragColor = vec4(finalToneMapped, texColor.a);\n" +
            "}\n";

    public static int createProgram(String vsSource, String fsSource) {
        int vs = loadShader(GLES20.GL_VERTEX_SHADER, vsSource);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fsSource);
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vs);
        GLES20.glAttachShader(prog, fs);
        GLES20.glLinkProgram(prog);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            GLES20.glDeleteProgram(prog);
            return 0;
        }
        return prog;
    }

    private static int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        return shader;
    }

    public static FloatBuffer createFloatBuffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(data);
        fb.position(0);
        return fb;
    }

    public static ShortBuffer createShortBuffer(short[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 2);
        bb.order(ByteOrder.nativeOrder());
        ShortBuffer sb = bb.asShortBuffer();
        sb.put(data);
        sb.position(0);
        return sb;
    }

    // =========================================================================
    // Ultra-HD Текстуры PUBG Mobile (512x512)
    // =========================================================================
    public static int texSandstone;
    public static int texConcrete;
    public static int texCrate;
    public static int texMetal;
    public static int texWeaponDark;
    public static int texWeaponGold;
    public static int texWeaponDragon;
    public static int texPlayerT;
    public static int texPlayerCT;
    public static int texParticle;
    public static int texWhite;
    public static int texGrass;
    public static int texRoof;
    public static int texAirdropTarp;
    public static int texVehicle;

    public static void initTextures() {
        texWhite = loadBitmapTexture(genSolidBitmap(16, 16, 0xFFFFFFFF));
        texGrass = loadBitmapTexture(genRealisticGrassBitmap());
        texSandstone = loadBitmapTexture(genRealisticDirtBitmap());
        texConcrete = loadBitmapTexture(genRealisticHouseWallBitmap());
        texRoof = loadBitmapTexture(genRealisticRoofTilesBitmap());
        texCrate = loadBitmapTexture(genRealisticWoodDoorBitmap());
        texMetal = loadBitmapTexture(genRealisticGunMetalBitmap());
        texWeaponDark = loadBitmapTexture(genM416TextureBitmap());
        texWeaponGold = loadBitmapTexture(genGoldSkinBitmap());
        texWeaponDragon = loadBitmapTexture(genDragonSkinBitmap());
        texPlayerT = loadBitmapTexture(genRussianWoodlandCamoBitmap());
        texPlayerCT = loadBitmapTexture(genBlackOpsKevlarBitmap());
        texAirdropTarp = loadBitmapTexture(genRealisticAirdropBitmap());
        texVehicle = loadBitmapTexture(genMilitaryVehicleBitmap());
        texParticle = loadBitmapTexture(genParticleBitmap());
    }

    private static int loadBitmapTexture(Bitmap bmp) {
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        bmp.recycle();
        return tex[0];
    }

    private static Bitmap genSolidBitmap(int w, int h, int color) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(color);
        return bmp;
    }

    private static Bitmap genRealisticGrassBitmap() {
        int S = 512;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF3B6324); // Базовый зеленый оттенок Эрангеля

        Paint p = new Paint();
        p.setAntiAlias(true);

        // Грунтовые пятна
        p.setColor(0xFF4A381C);
        for (int i = 0; i < 40; i++) {
            float rx = (float)(Math.random() * S), ry = (float)(Math.random() * S);
            float rw = (float)(Math.random() * 80 + 30), rh = (float)(Math.random() * 80 + 30);
            c.drawOval(new RectF(rx, ry, rx + rw, ry + rh), p);
        }

        // Травинки и текстура
        p.setColor(0xFF4E7E2E);
        p.setStrokeWidth(2.5f);
        for (int i = 0; i < 1500; i++) {
            float rx = (float) (Math.random() * S);
            float ry = (float) (Math.random() * S);
            c.drawLine(rx, ry, rx + (float)(Math.random() * 6 - 3), ry - (float)(Math.random() * 12 + 6), p);
        }

        p.setColor(0xFF2B4A18);
        for (int i = 0; i < 1200; i++) {
            float rx = (float) (Math.random() * S);
            float ry = (float) (Math.random() * S);
            c.drawLine(rx, ry, rx + (float)(Math.random() * 4 - 2), ry - (float)(Math.random() * 8 + 4), p);
        }
        return bmp;
    }

    private static Bitmap genRealisticDirtBitmap() {
        int S = 512;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF5D482F); // Грунтовая тропа
        Paint p = new Paint();
        p.setColor(0xFF44341F);
        for (int i = 0; i < 2000; i++) {
            c.drawCircle((float)(Math.random() * S), (float)(Math.random() * S), (float)(Math.random() * 3 + 1), p);
        }
        return bmp;
    }

    private static Bitmap genRealisticHouseWallBitmap() {
        int S = 512;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFE2E4E6); // Белая европейская штукатурка домов Починок

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Кирпичные прогляды под штукатуркой
        p.setColor(0xFFB04A2E);
        c.drawRect(40, 360, 240, 480, p);
        p.setColor(0xFF7A2E18);
        p.setStrokeWidth(3f);
        for (int y = 360; y < 480; y += 24) {
            c.drawLine(40, y, 240, y, p);
            int off = (y / 24) % 2 == 0 ? 0 : 20;
            for (int x = 40 + off; x < 240; x += 40) c.drawLine(x, y, x, y + 24, p);
        }

        // Оконные обрамления
        p.setColor(0xFF455A64);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8f);
        c.drawRect(60, 60, 200, 200, p);
        c.drawLine(130, 60, 130, 200, p);
        c.drawLine(60, 130, 200, 130, p);

        c.drawRect(312, 60, 452, 200, p);
        c.drawLine(382, 60, 382, 200, p);
        c.drawLine(312, 130, 452, 130, p);

        // Стекло (легкий голубоватый градиент)
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x8890CAF9);
        c.drawRect(64, 64, 196, 196, p);
        c.drawRect(316, 64, 448, 196, p);

        return bmp;
    }

    private static Bitmap genRealisticRoofTilesBitmap() {
        int S = 512;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF8D3C28); // Терракотовая черепица

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF541C0E);
        p.setStrokeWidth(5f);
        RectF rect = new RectF();

        for (int y = 0; y < S; y += 32) {
            c.drawLine(0, y, S, y, p);
            int off = (y / 32) % 2 == 0 ? 0 : 24;
            for (int x = off; x < S; x += 48) {
                // Дугообразные черепичные плитки
                rect.set(x, y, x + 48, y + 32);
                p.setStyle(Paint.Style.STROKE);
                c.drawArc(rect, 0, 180, false, p);
            }
        }
        return bmp;
    }

    private static Bitmap genRealisticWoodDoorBitmap() {
        int S = 512;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF5D4037); // Темный массив дуба

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF3E2723);
        p.setStrokeWidth(8f);

        // Деревянные доски
        for (int x = 0; x < S; x += 64) {
            c.drawLine(x, 0, x, S, p);
        }

        // Декоративные филенки
        p.setStrokeWidth(6f);
        p.setStyle(Paint.Style.STROKE);
        c.drawRect(32, 40, S - 32, 220, p);
        c.drawRect(32, 260, S - 32, S - 40, p);

        // Латунная ручка двери
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFD700);
        c.drawCircle(S - 64, S / 2f, 18, p);
        p.setColor(0xFFB8860B);
        c.drawCircle(S - 64, S / 2f, 8, p);

        return bmp;
    }

    private static Bitmap genRealisticGunMetalBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF21272B); // Оружейное воронение
        Paint p = new Paint();
        p.setColor(0xFF15191C);
        p.setStrokeWidth(3f);
        for (int i = 0; i < S; i += 12) c.drawLine(0, i, S, i, p);
        p.setColor(0xFF37474F);
        p.setStrokeWidth(1f);
        for (int i = 0; i < 300; i++) {
            c.drawPoint((float)(Math.random() * S), (float)(Math.random() * S), p);
        }
        return bmp;
    }

    private static Bitmap genM416TextureBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF1A1E24);
        Paint p = new Paint();
        p.setColor(0xFF0F1215);
        p.setStrokeWidth(4f);
        for (int x = 0; x < S; x += 16) c.drawLine(x, 0, x, S, p);
        return bmp;
    }

    private static Bitmap genGoldSkinBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFFFC107); // Золотой скин Фараона
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFF8F00);
        p.setStrokeWidth(5f);
        c.drawLine(0, 0, S, S, p);
        c.drawLine(0, S, S, 0, p);
        p.setColor(0xFFFFF8E1);
        p.setStrokeWidth(2f);
        c.drawCircle(S / 2f, S / 2f, 60, p);
        return bmp;
    }

    private static Bitmap genDragonSkinBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFB71C1C); // Огненный дракон
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFF5252);
        c.drawCircle(S / 2f, S / 2f, 70, p);
        p.setColor(0xFFFFD54F);
        c.drawCircle(S / 2f, S / 2f, 35, p);
        return bmp;
    }

    private static Bitmap genRussianWoodlandCamoBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF33691E); // Зеленая флора
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        int[] cols = {0xFF1B5E20, 0xFF3E2723, 0xFF102027, 0xFF558B2F};
        for (int i = 0; i < 50; i++) {
            p.setColor(cols[i % cols.length]);
            float rx = (float) (Math.random() * S), ry = (float) (Math.random() * S);
            float rw = (float) (Math.random() * 55 + 25), rh = (float) (Math.random() * 55 + 25);
            c.drawOval(new RectF(rx, ry, rx + rw, ry + rh), p);
        }
        return bmp;
    }

    private static Bitmap genBlackOpsKevlarBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF1E272C); // Тактический кевлар
        Paint p = new Paint();
        p.setColor(0xFF10161A);
        p.setStrokeWidth(2f);
        for (int y = 0; y < S; y += 8) {
            c.drawLine(0, y, S, y, p);
        }
        for (int x = 0; x < S; x += 8) {
            c.drawLine(x, 0, x, S, p);
        }
        return bmp;
    }

    private static Bitmap genRealisticAirdropBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF0D47A1); // Синий тент
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF1976D2);
        p.setStrokeWidth(8f);
        c.drawLine(0, 0, S, S, p);
        c.drawLine(0, S, S, 0, p);
        // Желтые стропы крепления
        p.setColor(0xFFFFD700);
        p.setStrokeWidth(12f);
        c.drawLine(30, 0, 30, S, p);
        c.drawLine(S - 30, 0, S - 30, S, p);
        return bmp;
    }

    private static Bitmap genMilitaryVehicleBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF2E4033); // Армейский УАЗ
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF1C271F);
        p.setStrokeWidth(8f);
        c.drawRect(12, 12, S - 12, S - 12, p);
        return bmp;
    }

    private static Bitmap genParticleBitmap() {
        int S = 64;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        p.setShader(new RadialGradient(S / 2f, S / 2f, S / 2f, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(S / 2f, S / 2f, S / 2f, p);
        return bmp;
    }
}
