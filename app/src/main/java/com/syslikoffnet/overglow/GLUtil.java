package com.syslikoffnet.overglow;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Утилиты OpenGL ES 2.0: компиляция шейдеров, буферы, высокодетализированные HD-текстуры PUBG Mobile.
 */
public final class GLUtil {

    private GLUtil() {}

    public static final String VS_3D =
            "uniform mat4 uMVP;\n" +
            "uniform mat4 uModel;\n" +
            "uniform vec3 uLightDir;\n" +
            "uniform vec3 uMuzzlePos;\n" +
            "uniform float uMuzzleIntensity;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNorm;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "varying float vLight;\n" +
            "varying float vFogDist;\n" +
            "void main() {\n" +
            "    vec4 worldPos = uModel * vec4(aPos, 1.0);\n" +
            "    gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "    vUV = aUV;\n" +
            "    vec3 norm = normalize((uModel * vec4(aNorm, 0.0)).xyz);\n" +
            "    float diff = max(dot(norm, -uLightDir), 0.0);\n" +
            "    float ambient = 0.44;\n" +
            "    float muzzleDist = length(worldPos.xyz - uMuzzlePos);\n" +
            "    float muzzleLight = (uMuzzleIntensity / (1.0 + muzzleDist * muzzleDist * 0.15));\n" +
            "    vLight = ambient + diff * 0.56 + muzzleLight;\n" +
            "    vFogDist = length(worldPos.xyz);\n" +
            "}\n";

    public static final String FS_3D =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec4 uColor;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform float uFogStart;\n" +
            "uniform float uFogEnd;\n" +
            "varying vec2 vUV;\n" +
            "varying float vLight;\n" +
            "varying float vFogDist;\n" +
            "void main() {\n" +
            "    vec4 texColor = texture2D(uTex, vUV) * uColor;\n" +
            "    vec3 litColor = texColor.rgb * vLight;\n" +
            "    float fogFactor = clamp((vFogDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);\n" +
            "    vec3 finalColor = mix(litColor, uFogColor, fogFactor * 0.65);\n" +
            "    gl_FragColor = vec4(finalColor, texColor.a);\n" +
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
    // Текстуры игры
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
        texSandstone = loadBitmapTexture(genSandstoneBitmap());
        texConcrete = loadBitmapTexture(genConcreteBitmap());
        texCrate = loadBitmapTexture(genCrateBitmap());
        texMetal = loadBitmapTexture(genMetalBitmap());
        texWeaponDark = loadBitmapTexture(genWeaponDarkBitmap());
        texWeaponGold = loadBitmapTexture(genWeaponGoldBitmap());
        texWeaponDragon = loadBitmapTexture(genWeaponDragonBitmap());
        texPlayerT = loadBitmapTexture(genCamoBitmap(0xFF33691E, 0xFF1B5E20, 0xFF3E2723)); // Woodland Camo
        texPlayerCT = loadBitmapTexture(genCamoBitmap(0xFF263238, 0xFF37474F, 0xFF102027)); // Urban SWAT Camo
        texGrass = loadBitmapTexture(genGrassBitmap());
        texRoof = loadBitmapTexture(genRoofBitmap());
        texAirdropTarp = loadBitmapTexture(genAirdropTarpBitmap());
        texVehicle = loadBitmapTexture(genVehicleBitmap());
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

    private static Bitmap genGrassBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF4C7835); // Трава Эрангеля
        Paint p = new Paint();
        p.setColor(0xFF385E24);
        for (int i = 0; i < 800; i++) {
            float rx = (float) (Math.random() * S);
            float ry = (float) (Math.random() * S);
            c.drawLine(rx, ry, rx + (float)(Math.random() * 4 - 2), ry + (float)(Math.random() * 8 + 4), p);
        }
        return bmp;
    }

    private static Bitmap genRoofBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF8D3C28); // Красная черепица
        Paint p = new Paint();
        p.setColor(0xFF5C2415);
        p.setStrokeWidth(4);
        for (int y = 0; y < S; y += 32) {
            c.drawLine(0, y, S, y, p);
            int off = (y / 32) % 2 == 0 ? 0 : 24;
            for (int x = off; x < S; x += 48) {
                c.drawLine(x, y, x, y + 32, p);
            }
        }
        return bmp;
    }

    private static Bitmap genAirdropTarpBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF0D47A1); // Синий брезент аирдропа
        Paint p = new Paint();
        p.setColor(0xFF1976D2);
        p.setStrokeWidth(6);
        c.drawLine(0, 0, S, S, p);
        c.drawLine(0, S, S, 0, p);
        return bmp;
    }

    private static Bitmap genVehicleBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF2E4033); // Армейский темно-зеленый
        Paint p = new Paint();
        p.setColor(0xFF1B261E);
        p.setStrokeWidth(6);
        c.drawRect(8, 8, S - 8, S - 8, p);
        return bmp;
    }

    private static Bitmap genCamoBitmap(int c1, int c2, int c3) {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(c1);
        Paint p = new Paint();
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 40; i++) {
            p.setColor(i % 2 == 0 ? c2 : c3);
            float rx = (float) (Math.random() * S);
            float ry = (float) (Math.random() * S);
            float rw = (float) (Math.random() * 45 + 20);
            float rh = (float) (Math.random() * 45 + 20);
            c.drawOval(new RectF(rx, ry, rx + rw, ry + rh), p);
        }
        return bmp;
    }

    private static Bitmap genSandstoneBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF5A7E3E); // Полевой грунт с травой
        Paint p = new Paint();
        p.setColor(0xFF486730);
        for (int i = 0; i < 500; i++) {
            c.drawCircle((float) (Math.random() * S), (float) (Math.random() * S), (float) (Math.random() * 3), p);
        }
        return bmp;
    }

    private static Bitmap genConcreteBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFCED2D5); // Белая штукатурка домов Починок
        Paint p = new Paint();
        p.setColor(0xFF8F9498);
        p.setStrokeWidth(3);
        p.setStyle(Paint.Style.STROKE);
        // Оконные рамы
        c.drawRect(24, 24, 100, 100, p);
        c.drawLine(62, 24, 62, 100, p);
        c.drawRect(156, 24, 232, 100, p);
        c.drawLine(194, 24, 194, 100, p);
        return bmp;
    }

    private static Bitmap genCrateBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF6D4C41); // Темное дерево дверей
        Paint p = new Paint();
        p.setColor(0xFF3E2723);
        p.setStrokeWidth(6);
        for (int x = 0; x < S; x += 32) {
            c.drawLine(x, 0, x, S, p);
        }
        // Дверная ручка
        p.setColor(0xFFFFD700);
        c.drawCircle(S - 36, S / 2f, 10, p);
        return bmp;
    }

    private static Bitmap genMetalBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF263238); // Оружейная сталь
        Paint p = new Paint();
        p.setColor(0xFF37474F);
        p.setStrokeWidth(4);
        for (int x = 0; x < S; x += 48) {
            c.drawLine(x, 0, x, S, p);
        }
        return bmp;
    }

    private static Bitmap genWeaponDarkBitmap() {
        int S = 128;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF1E2328);
        Paint p = new Paint();
        p.setColor(0xFF101418);
        for (int i = 0; i < S; i += 8) c.drawLine(0, i, S, i, p);
        return bmp;
    }

    private static Bitmap genWeaponGoldBitmap() {
        int S = 128;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFFFC107);
        Paint p = new Paint();
        p.setColor(0xFFFF8F00);
        p.setStrokeWidth(4);
        c.drawLine(0, 0, S, S, p);
        c.drawLine(0, S, S, 0, p);
        return bmp;
    }

    private static Bitmap genWeaponDragonBitmap() {
        int S = 128;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFB71C1C);
        Paint p = new Paint();
        p.setColor(0xFFFF5252);
        c.drawCircle(64, 64, 40, p);
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
