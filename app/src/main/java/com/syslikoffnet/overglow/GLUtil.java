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
 * Утилиты OpenGL ES 2.0: компиляция шейдеров, буферы, процедурные HD-текстуры.
 */
public final class GLUtil {

    private GLUtil() {}

    // Шейдер для 3D геометрии (свет, текстура, туман, вспышки выстрела)
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
            "    float ambient = 0.42;\n" +
            "    float muzzleDist = length(worldPos.xyz - uMuzzlePos);\n" +
            "    float muzzleLight = (uMuzzleIntensity / (1.0 + muzzleDist * muzzleDist * 0.15));\n" +
            "    vLight = ambient + diff * 0.58 + muzzleLight;\n" +
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
            "    vec3 finalColor = mix(litColor, uFogColor, fogFactor * 0.75);\n" +
            "    gl_FragColor = vec4(finalColor, texColor.a);\n" +
            "}\n";

    // Шейдер для 2D UI / частиц / прицелов
    public static final String VS_2D =
            "uniform mat4 uMVP;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "void main() {\n" +
            "    gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "    vUV = aUV;\n" +
            "}\n";

    public static final String FS_2D =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec4 uColor;\n" +
            "varying vec2 vUV;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(uTex, vUV) * uColor;\n" +
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
    // Текстуры игры (генерируются процедурно при старте)
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
    public static int texScope;
    public static int texWhite;

    public static void initTextures() {
        texWhite = loadBitmapTexture(genSolidBitmap(16, 16, 0xFFFFFFFF));
        texSandstone = loadBitmapTexture(genSandstoneBitmap());
        texConcrete = loadBitmapTexture(genConcreteBitmap());
        texCrate = loadBitmapTexture(genCrateBitmap());
        texMetal = loadBitmapTexture(genMetalBitmap());
        texWeaponDark = loadBitmapTexture(genWeaponDarkBitmap());
        texWeaponGold = loadBitmapTexture(genWeaponGoldBitmap());
        texWeaponDragon = loadBitmapTexture(genWeaponDragonBitmap());
        texPlayerT = loadBitmapTexture(genPlayerTBitmap());
        texPlayerCT = loadBitmapTexture(genPlayerCTBitmap());
        texParticle = loadBitmapTexture(genParticleBitmap());
        texScope = loadBitmapTexture(genScopeBitmap());
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

    private static Bitmap genSandstoneBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFD4B184); // Песчаник Dust2
        Paint p = new Paint();
        p.setColor(0xFFB89366);
        p.setStrokeWidth(3);
        // Кирпичные швы
        for (int y = 0; y < S; y += 32) {
            c.drawLine(0, y, S, y, p);
            int off = (y / 32) % 2 == 0 ? 0 : 32;
            for (int x = off; x < S; x += 64) {
                c.drawLine(x, y, x, y + 32, p);
            }
        }
        // Шум/текстура
        p.setColor(0x22000000);
        for (int i = 0; i < 400; i++) {
            float rx = (float) (Math.random() * S);
            float ry = (float) (Math.random() * S);
            c.drawCircle(rx, ry, (float) (Math.random() * 2 + 1), p);
        }
        return bmp;
    }

    private static Bitmap genConcreteBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF7A8288);
        Paint p = new Paint();
        p.setColor(0xFF555C62);
        p.setStrokeWidth(2);
        c.drawLine(0, 0, S, S, p);
        c.drawLine(0, S, S, 0, p);
        p.setColor(0x28000000);
        for (int i = 0; i < 600; i++) {
            c.drawCircle((float) (Math.random() * S), (float) (Math.random() * S), (float) (Math.random() * 2), p);
        }
        return bmp;
    }

    private static Bitmap genCrateBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF8B5A2B); // Деревянный ящик
        Paint p = new Paint();
        p.setColor(0xFF5C3A1E);
        p.setStrokeWidth(8);
        p.setStyle(Paint.Style.STROKE);
        c.drawRect(4, 4, S - 4, S - 4, p);
        c.drawLine(8, 8, S - 8, S - 8, p);
        c.drawLine(8, S - 8, S - 8, 8, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF3B2513);
        c.drawCircle(16, 16, 4, p);
        c.drawCircle(S - 16, 16, 4, p);
        c.drawCircle(16, S - 16, 4, p);
        c.drawCircle(S - 16, S - 16, 4, p);
        return bmp;
    }

    private static Bitmap genMetalBitmap() {
        int S = 256;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF333A42);
        Paint p = new Paint();
        p.setColor(0xFF22262B);
        p.setStrokeWidth(4);
        for (int x = 0; x < S; x += 64) {
            c.drawLine(x, 0, x, S, p);
        }
        p.setColor(0xFF55606E);
        p.setStrokeWidth(2);
        for (int y = 0; y < S; y += 64) {
            c.drawLine(0, y, S, y, p);
        }
        return bmp;
    }

    private static Bitmap genWeaponDarkBitmap() {
        int S = 128;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF222428);
        Paint p = new Paint();
        p.setColor(0xFF141518);
        for (int i = 0; i < S; i += 8) {
            c.drawLine(0, i, S, i, p);
        }
        return bmp;
    }

    private static Bitmap genWeaponGoldBitmap() {
        int S = 128;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFFFC72C); // Золотой скин
        Paint p = new Paint();
        p.setColor(0xFFE5A812);
        p.setStrokeWidth(4);
        c.drawLine(0, 0, S, S, p);
        c.drawLine(0, S, S, 0, p);
        return bmp;
    }

    private static Bitmap genWeaponDragonBitmap() {
        int S = 128;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF1E1010);
        Paint p = new Paint();
        p.setColor(0xFFFF3333); // Огненный дракон
        Path path = new Path();
        path.moveTo(10, 60);
        path.lineTo(40, 20);
        path.lineTo(90, 80);
        path.lineTo(120, 30);
        path.lineTo(100, 110);
        path.lineTo(30, 90);
        path.close();
        c.drawPath(path, p);
        return bmp;
    }

    private static Bitmap genPlayerTBitmap() {
        int S = 128;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF8B4513); // Террорист (коричнево-красный камуфляж)
        Paint p = new Paint();
        p.setColor(0xFFB22222);
        c.drawRect(20, 20, 108, 108, p);
        return bmp;
    }

    private static Bitmap genPlayerCTBitmap() {
        int S = 128;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF1E3F66); // Спецназ (сине-серый камуфляж)
        Paint p = new Paint();
        p.setColor(0xFF2E5B88);
        c.drawRect(20, 20, 108, 108, p);
        return bmp;
    }

    private static Bitmap genParticleBitmap() {
        int S = 64;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        p.setShader(new RadialGradient(S / 2f, S / 2f, S / 2f,
                0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(S / 2f, S / 2f, S / 2f, p);
        return bmp;
    }

    private static Bitmap genScopeBitmap() {
        int S = 512;
        Bitmap bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0x00000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF111111);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4);
        c.drawCircle(S / 2f, S / 2f, S / 2f - 16, p);
        // Перекрестие
        p.setStrokeWidth(2);
        p.setColor(0xFFFF2222);
        c.drawLine(S / 2f, 20, S / 2f, S - 20, p);
        c.drawLine(20, S / 2f, S - 20, S / 2f, p);
        // Мил-доты
        p.setStyle(Paint.Style.FILL);
        for (int i = -3; i <= 3; i++) {
            if (i == 0) continue;
            c.drawCircle(S / 2f + i * 40, S / 2f, 3, p);
            c.drawCircle(S / 2f, S / 2f + i * 40, 3, p);
        }
        return bmp;
    }
}
