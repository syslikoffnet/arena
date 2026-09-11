package com.syslikoffnet.overglow;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Графический ядро: GLES20 PBR-lite конвейер поверх процедурных материалов TextureForge.
 * - Cook-Torrance упрощённый (GGX + Schlick-Fresnel) с roughness/metalness на материал
 * - Normal mapping (albedo + normal-мапа), мир-UV planar projection для статики (без швов и растяжения)
 * - ACES Filmic tonemap, атмосферный туман, динамический свет от вспышки выстрела
 * - Второй мини-шейдер: трейсеры (линии) и частицы (GL_POINTS со спрайтом)
 */
public final class GLUtil {

    private GLUtil() {}

    public static final String TAG = "OverglowGL";

    // =========================================================================
    // Основной 3D шейдер
    // =========================================================================
    public static final String VS_3D =
            "uniform mat4 uMVP;\n" +
            "uniform mat4 uModel;\n" +
            "uniform vec3 uEyePos;\n" +
            "uniform vec2 uUVScale;\n" +
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
            "    vUV = aUV * uUVScale;\n" +
            "    vFogDist = length(uEyePos - worldPos.xyz);\n" +
            "    gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "}\n";

    public static final String FS_3D =
            "precision mediump float;\n" +
            "uniform sampler2D uTexA;\n" +
            "uniform sampler2D uTexN;\n" +
            "uniform vec4 uColor;\n" +
            "uniform vec3 uLightDir;\n" +
            "uniform vec3 uMuzzlePos;\n" +
            "uniform float uMuzzleIntensity;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform float uFogStart;\n" +
            "uniform float uFogEnd;\n" +
            "uniform float uMode;       // 0 = lit PBR, 1 = unlit (небо), 2 = blob-shadow decal\n" +
            "uniform float uWorldUV;    // 1 = planar projection по миру\n" +
            "uniform float uInvTile;    // 1/tileMeters для world-UV\n" +
            "uniform float uUseNormal;  // использовать normal map\n" +
            "uniform float uBump;       // сила нормали\n" +
            "uniform float uRough;\n" +
            "uniform float uMetal;\n" +
            "varying vec2 vUV;\n" +
            "varying vec3 vWorldPos;\n" +
            "varying vec3 vNorm;\n" +
            "varying vec3 vViewDir;\n" +
            "varying float vFogDist;\n" +
            "\n" +
            "vec3 ACESFilm(vec3 x) {\n" +
            "    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec3 N = normalize(vNorm);\n" +
            "    // UV: объектные или мировая planar-проекция по доминирующей оси нормали\n" +
            "    vec2 uv = vUV;\n" +
            "    vec3 an = abs(N);\n" +
            "    if (uWorldUV > 0.5) {\n" +
            "        if (an.x > an.y && an.x > an.z)      uv = vWorldPos.zy * uInvTile;\n" +
            "        else if (an.z > an.y)                 uv = vWorldPos.xz * uInvTile;\n" +
            "        else                                  uv = vWorldPos.xz * uInvTile;\n" +
            "    }\n" +
            "\n" +
            "    // ---- Режим 2: мягкая blob-тень (умножающий декань) ----\n" +
            "    if (uMode > 1.5) {\n" +
            "        float s = texture2D(uTexA, uv).a * uColor.a;\n" +
            "        gl_FragColor = vec4(0.02, 0.03, 0.04, s);\n" +
            "        return;\n" +
            "    }\n" +
            "\n" +
            "    vec4 alb = texture2D(uTexA, uv) * uColor;\n" +
            "\n" +
            "    // ---- Normal mapping через доминирующую ось (без dFdx, ES2-safe) ----\n" +
            "    if (uUseNormal > 0.5) {\n" +
            "        vec3 nt = texture2D(uTexN, uv).xyz * 2.0 - 1.0;\n" +
            "        vec3 T, B;\n" +
            "        if (an.x > an.y && an.x > an.z)      { T = vec3(0.0, 0.0, -1.0); B = vec3(0.0, 1.0, 0.0); }\n" +
            "        else if (an.z > an.y)                 { T = vec3(1.0, 0.0, 0.0);  B = vec3(0.0, 1.0, 0.0); }\n" +
            "        else                                  { T = vec3(1.0, 0.0, 0.0);  B = vec3(0.0, 0.0, 1.0); }\n" +
            "        N = normalize(N + (T * nt.x + B * nt.y) * uBump * 0.7);\n" +
            "    }\n" +
            "\n" +
            "    if (uMode > 0.5) { // unlit (небо)\n" +
            "        gl_FragColor = vec4(ACESFilm(alb.rgb), 1.0);\n" +
            "        return;\n" +
            "    }\n" +
            "\n" +
            "    vec3 V = normalize(vViewDir);\n" +
            "    vec3 L = normalize(-uLightDir);\n" +
            "    float NdotL = max(dot(N, L), 0.0);\n" +
            "    float NdotV = max(dot(N, V), 0.001);\n" +
            "\n" +
            "    float alpha = max(0.024, uRough * uRough);\n" +
            "    vec3 H = normalize(L + V);\n" +
            "    float NdotH = max(dot(N, H), 0.0);\n" +
            "\n" +
            "    // D: GGX\n" +
            "    float a2 = alpha * alpha;\n" +
            "    float dnom = NdotH * NdotH * (a2 - 1.0) + 1.0;\n" +
            "    float D = a2 / (3.14159 * dnom * dnom + 0.0001);\n" +
            "\n" +
            "    // F: Schlick\n" +
            "    vec3 F0 = mix(vec3(0.04), alb.rgb, uMetal);\n" +
            "    float f = pow(1.0 - max(dot(H, V), 0.0), 5.0);\n" +
            "    vec3 F = F0 + (1.0 - F0) * f;\n" +
            "\n" +
            "    // G: Schlick-GGX\n" +
            "    float k = (alpha + 1.0) * (alpha + 1.0) / 8.0;\n" +
            "    float G = (NdotV / (NdotV * (1.0 - k) + k)) * (NdotL / (NdotL * (1.0 - k) + k));\n" +
            "\n" +
            "    vec3 specular = (D * F * G) / (4.0 * NdotV * NdotL + 0.001);\n" +
            "\n" +
            "    // Контурный Fresnel-рим\n" +
            "    float rim = pow(1.0 - NdotV, 3.5) * 0.32;\n" +
            "\n" +
            "    // Динамический свет вспышки\n" +
            "    float muzzleDist = length(vWorldPos - uMuzzlePos);\n" +
            "    vec3 mL = normalize(uMuzzlePos - vWorldPos);\n" +
            "    float muzzleN = max(dot(N, mL), 0.0);\n" +
            "    vec3 muzzle = vec3(1.0, 0.78, 0.42) * (uMuzzleIntensity * muzzleN / (1.0 + muzzleDist * muzzleDist * 0.12));\n" +
            "\n" +
            "    vec3 sunColor = vec3(1.18, 1.06, 0.92);\n" +
            "    vec3 skyAmb = vec3(0.34, 0.44, 0.58);\n" +
            "    vec3 diffuse = alb.rgb * (1.0 - uMetal * 0.85);\n" +
            "    vec3 lit = diffuse * sunColor * NdotL + diffuse * skyAmb * 0.55 + specular * sunColor * NdotL + vec3(rim) * 0.5 + muzzle * diffuse * 1.6;\n" +
            "\n" +
            "    float fog = clamp((vFogDist - uFogStart) / (uFogEnd - uFogStart + 0.001), 0.0, 1.0);\n" +
            "    lit = mix(lit, uFogColor, fog * 0.72);\n" +
            "\n" +
            "    gl_FragColor = vec4(ACESFilm(lit), alb.a);\n" +
            "}\n";

    // =========================================================================
    // FX шейдер: трейсеры (LINES) и частицы (POINTS)
    // =========================================================================
    public static final String VS_FX =
            "uniform mat4 uMVP;\n" +
            "uniform float uPointScale;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec4 aColor;\n" +
            "attribute float aSize;\n" +
            "varying vec4 vColor;\n" +
            "void main() {\n" +
            "    vec4 p = uMVP * vec4(aPos, 1.0);\n" +
            "    gl_Position = p;\n" +
            "    gl_PointSize = clamp(aSize * uPointScale / max(1.0, p.w), 1.0, 128.0);\n" +
            "    vColor = aColor;\n" +
            "}\n";

    public static final String FS_FX =
            "precision mediump float;\n" +
            "uniform float uSprite; // 1 = круглый спрайт (gl_PointCoord), 0 = просто цвет\n" +
            "varying vec4 vColor;\n" +
            "void main() {\n" +
            "    float a = vColor.a;\n" +
            "    if (uSprite > 0.5) {\n" +
            "        vec2 d = gl_PointCoord - vec2(0.5);\n" +
            "        float r = length(d) * 2.0;\n" +
            "        a *= clamp(1.0 - r, 0.0, 1.0);\n" +
            "        a *= a;\n" +
            "    }\n" +
            "    gl_FragColor = vec4(vColor.rgb, a);\n" +
            "}\n";

    // =========================================================================
    // Утилиты
    // =========================================================================
    public static int createProgram(String vsSource, String fsSource) {
        int vs = loadShader(GLES20.GL_VERTEX_SHADER, vsSource);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fsSource);
        if (vs == 0 || fs == 0) return 0;
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vs);
        GLES20.glAttachShader(prog, fs);
        GLES20.glBindAttribLocation(prog, 0, "aPos");
        GLES20.glBindAttribLocation(prog, 1, "aNorm");
        GLES20.glBindAttribLocation(prog, 2, "aUV");
        GLES20.glLinkProgram(prog);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link failed: " + GLES20.glGetProgramInfoLog(prog));
            GLES20.glDeleteProgram(prog);
            return 0;
        }
        return prog;
    }

    /** FX-программа без нормалей/UV — только aPos/aColor/aSize */
    public static int createFXProgram(String vsSource, String fsSource) {
        int vs = loadShader(GLES20.GL_VERTEX_SHADER, vsSource);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fsSource);
        if (vs == 0 || fs == 0) return 0;
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vs);
        GLES20.glAttachShader(prog, fs);
        GLES20.glBindAttribLocation(prog, 0, "aPos");
        GLES20.glBindAttribLocation(prog, 1, "aColor");
        GLES20.glBindAttribLocation(prog, 2, "aSize");
        GLES20.glLinkProgram(prog);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "FX link failed: " + GLES20.glGetProgramInfoLog(prog));
            GLES20.glDeleteProgram(prog);
            return 0;
        }
        return prog;
    }

    private static int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
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

    /** Загрузка Bitmap в GL-текстуру (mipmaps + линейная фильтрация) */
    public static int uploadTexture(Bitmap bmp, boolean clampEdges) {
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        int wrap = clampEdges ? GLES20.GL_CLAMP_TO_EDGE : GLES20.GL_REPEAT;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        return tex[0];
    }

    /** Инициализация процедурных материалов (GL-поток, один раз за контекст) */
    public static void initTextures() {
        TextureForge.init();
    }
}
