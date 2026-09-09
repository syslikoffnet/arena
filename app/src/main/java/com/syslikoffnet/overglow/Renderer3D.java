package com.syslikoffnet.overglow;

import android.opengl.GLES20;
import java.util.ArrayList;

/**
 * 3D OpenGL ES 2.0 Рендерер: освещение, карта, шейдеры, 3D модели ботов,
 * трейсеры, частицы и 3D оружие от 1-го лица (Viewmodel).
 */
public final class Renderer3D {

    private int prog3D;
    private int uMVP3D, uModel3D, uLightDir3D, uMuzzlePos3D, uMuzzleInt3D, uTex3D, uColor3D;
    private int uFogColor3D, uFogStart3D, uFogEnd3D;
    private int aPos3D, aNorm3D, aUV3D;

    private final Math3D.Mat4 projMat = new Math3D.Mat4();
    private final Math3D.Mat4 viewMat = new Math3D.Mat4();
    private final Math3D.Mat4 modelMat = new Math3D.Mat4();
    private final Math3D.Mat4 mvpMat = new Math3D.Mat4();
    private final Math3D.Mat4 tempMat = new Math3D.Mat4();

    public int width = 1920;
    public int height = 1080;

    public void init() {
        GLUtil.initTextures();
        ModelGenerator.init();

        prog3D = GLUtil.createProgram(GLUtil.VS_3D, GLUtil.FS_3D);

        uMVP3D = GLES20.glGetUniformLocation(prog3D, "uMVP");
        uModel3D = GLES20.glGetUniformLocation(prog3D, "uModel");
        uLightDir3D = GLES20.glGetUniformLocation(prog3D, "uLightDir");
        uMuzzlePos3D = GLES20.glGetUniformLocation(prog3D, "uMuzzlePos");
        uMuzzleInt3D = GLES20.glGetUniformLocation(prog3D, "uMuzzleIntensity");
        uTex3D = GLES20.glGetUniformLocation(prog3D, "uTex");
        uColor3D = GLES20.glGetUniformLocation(prog3D, "uColor");
        uFogColor3D = GLES20.glGetUniformLocation(prog3D, "uFogColor");
        uFogStart3D = GLES20.glGetUniformLocation(prog3D, "uFogStart");
        uFogEnd3D = GLES20.glGetUniformLocation(prog3D, "uFogEnd");

        aPos3D = GLES20.glGetAttribLocation(prog3D, "aPos");
        aNorm3D = GLES20.glGetAttribLocation(prog3D, "aNorm");
        aUV3D = GLES20.glGetAttribLocation(prog3D, "aUV");
    }

    public void resize(int w, int h) {
        this.width = w;
        this.height = h;
        GLES20.glViewport(0, 0, w, h);
    }

    public void render(Player player, Map3D map, ArrayList<BotAI> bots, ParticleSystem particles) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        // Очистка экрана
        GLES20.glClearColor(0.55f, 0.72f, 0.88f, 1.0f); // Небесный цвет
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(prog3D);

        // Направление солнца и параметры тумана
        GLES20.glUniform3f(uLightDir3D, -0.6f, -0.8f, -0.5f);
        GLES20.glUniform3f(uFogColor3D, 0.55f, 0.72f, 0.88f);
        GLES20.glUniform1f(uFogStart3D, 40f);
        GLES20.glUniform1f(uFogEnd3D, 180f);

        // Вспышка выстрела (динамический свет)
        GLES20.glUniform3f(uMuzzlePos3D, particles.muzzleFlashPos.x, particles.muzzleFlashPos.y, particles.muzzleFlashPos.z);
        GLES20.glUniform1f(uMuzzleInt3D, particles.muzzleFlashIntensity);

        // Расчет матрицы проекции с учетом зума ADS
        float fov = 75f - (player.adsFactor * (player.getActiveWeapon().adsZoom > 3f ? 55f : 25f));
        float aspect = (float) width / Math.max(1, height);
        projMat.perspective(fov, aspect, 0.1f, 350f);

        // Камера от 1-го лица
        float camYaw = player.yaw + player.recoilYawOffset;
        float camPitch = player.pitch + player.recoilPitchOffset;

        float radYaw = camYaw * Math3D.TO_RAD;
        float radPitch = camPitch * Math3D.TO_RAD;

        float forwardX = -(float) Math.sin(radYaw) * (float) Math.cos(radPitch);
        float forwardY = (float) Math.sin(radPitch);
        float forwardZ = (float) Math.cos(radYaw) * (float) Math.cos(radPitch);

        Math3D.Vec3 eye = new Math3D.Vec3(player.pos.x, player.pos.y + player.eyeHeight, player.pos.z);
        Math3D.Vec3 target = new Math3D.Vec3(eye.x + forwardX, eye.y + forwardY, eye.z + forwardZ);
        Math3D.Vec3 up = new Math3D.Vec3(0, 1, 0);

        viewMat.lookAt(eye, target, up);

        // 1. Рендер Земли
        drawMesh(ModelGenerator.groundMesh, GLUtil.texSandstone, 0, 0, 0, 1, 1, 1, 0, 1f, 1f, 1f, 1f);

        // 2. Рендер Препятствий Карты (Стены, ящики, здания)
        for (Map3D.Obstacle obs : map.obstacles) {
            drawMesh(ModelGenerator.boxMesh, obs.texture,
                    obs.getCenterX(), obs.getCenterY(), obs.getCenterZ(),
                    obs.getWidth(), obs.getHeight(), obs.getDepth(),
                    0, obs.r, obs.g, obs.b, 1f);
        }

        // 3. Рендер 3D Ботов
        for (BotAI bot : bots) {
            if (!bot.isDead) {
                renderBot3D(bot);
            }
        }

        // 4. Рендер Снарядов (Ракеты RPG / Гранаты)
        for (ParticleSystem.Projectile p : particles.projectiles) {
            Mesh pMesh = p.type == 0 ? ModelGenerator.rpgMesh : ModelGenerator.grenadeMesh;
            drawMesh(pMesh, GLUtil.texMetal, p.x, p.y, p.z, 0.8f, 0.8f, 0.8f, 0, 1f, 1f, 1f, 1f);
        }

        // 5. Рендер 3D Трейсеров пуль
        renderTracers(particles);

        // 6. Рендер 3D Частиц (Кровь, искры, дым, взрывы)
        renderParticles(particles, eye);

        // 7. Рендер 3D Оружия от 1-го лица (Viewmodel в руках игрока)
        if (player.adsFactor < 0.95f || player.getActiveWeapon().adsZoom <= 3f) {
            renderFirstPersonWeapon(player);
        }
    }

    private void renderBot3D(BotAI bot) {
        int skinTex = bot.team == 0 ? GLUtil.texPlayerT : GLUtil.texPlayerCT;

        // Тело (Торс)
        drawMeshRotated(ModelGenerator.charTorsoMesh, skinTex,
                bot.pos.x, bot.pos.y + 0.88f, bot.pos.z,
                1f, 1f, 1f, bot.yaw, 0);

        // Голова
        drawMeshRotated(ModelGenerator.charHeadMesh, GLUtil.texMetal,
                bot.pos.x, bot.pos.y + 1.55f, bot.pos.z,
                1f, 1f, 1f, bot.yaw, bot.pitch);

        // Левая и Правая ноги с анимацией ходьбы
        float legSwing = (float) Math.sin(bot.legAngle) * 18f;
        drawMeshRotated(ModelGenerator.charLegMesh, skinTex,
                bot.pos.x - 0.16f, bot.pos.y + 0.35f, bot.pos.z,
                1f, 1f, 1f, bot.yaw, legSwing);
        drawMeshRotated(ModelGenerator.charLegMesh, skinTex,
                bot.pos.x + 0.16f, bot.pos.y + 0.35f, bot.pos.z,
                1f, 1f, 1f, bot.yaw, -legSwing);

        // Оружие в руках бота
        Weapon w = bot.weapon;
        if (w != null) {
            drawMeshRotated(w.getMesh(), w.getTexture(),
                    bot.pos.x + 0.20f, bot.pos.y + 1.15f, bot.pos.z,
                    0.8f, 0.8f, 0.8f, bot.yaw, bot.pitch);
        }
    }

    private void renderFirstPersonWeapon(Player player) {
        Weapon wep = player.getActiveWeapon();
        if (wep == null) return;

        // Очистка буфера глубины для оружия от 1-го лица, чтобы оно не проваливалось сквозь стены
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

        // Позиционирование viewmodel
        float radYaw = (player.yaw + player.recoilYawOffset) * Math3D.TO_RAD;
        float radPitch = (player.pitch + player.recoilPitchOffset) * Math3D.TO_RAD;

        float forwardX = -(float) Math.sin(radYaw) * (float) Math.cos(radPitch);
        float forwardY = (float) Math.sin(radPitch);
        float forwardZ = (float) Math.cos(radYaw) * (float) Math.cos(radPitch);

        float rightX = (float) Math.cos(radYaw);
        float rightZ = (float) Math.sin(radYaw);

        // Смещение в правый нижний угол или по центру при ADS
        float ads = player.adsFactor;
        float offsetX = Math3D.lerp(0.24f, 0f, ads) + player.bobX;
        float offsetY = Math3D.lerp(-0.22f, -0.14f, ads) + player.bobY;
        float offsetZ = 0.50f;

        float wx = player.pos.x + forwardX * offsetZ + rightX * offsetX;
        float wy = player.pos.y + player.eyeHeight + forwardY * offsetZ + offsetY;
        float wz = player.pos.z + forwardZ * offsetZ + rightZ * offsetX;

        drawMeshRotated(wep.getMesh(), wep.getTexture(),
                wx, wy, wz, 0.9f, 0.9f, 0.9f,
                player.yaw + player.recoilYawOffset,
                player.pitch + player.recoilPitchOffset);
    }

    private void renderTracers(ParticleSystem particles) {
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        for (ParticleSystem.Tracer t : particles.tracers) {
            float mx = (t.x0 + t.x1) / 2f;
            float my = (t.y0 + t.y1) / 2f;
            float mz = (t.z0 + t.z1) / 2f;
            drawMesh(ModelGenerator.boxMesh, GLUtil.texWhite,
                    mx, my, mz, 0.04f, 0.04f, Math3D.dist(t.x0, t.y0, t.z0, t.x1, t.y1, t.z1),
                    0, 1.0f, 0.9f, 0.4f, 1.0f);
        }
        GLES20.glEnable(GLES20.GL_CULL_FACE);
    }

    private void renderParticles(ParticleSystem particles, Math3D.Vec3 eye) {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        for (ParticleSystem.Particle p : particles.particles) {
            drawMesh(ModelGenerator.quad2DMesh, GLUtil.texParticle,
                    p.x, p.y, p.z, p.size, p.size, p.size, 0,
                    p.r, p.g, p.b, p.a);
        }

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
    }

    private void drawMesh(Mesh mesh, int texture, float x, float y, float z,
                          float sx, float sy, float sz, float rotY,
                          float r, float g, float b, float a) {
        modelMat.identity();
        modelMat.translate(x, y, z);
        if (rotY != 0) modelMat.rotate(rotY, 0, 1, 0);
        modelMat.scale(sx, sy, sz);

        mvpMat.set(projMat);
        mvpMat.multiply(viewMat);
        mvpMat.multiply(modelMat);

        GLES20.glUniformMatrix4fv(uMVP3D, 1, false, mvpMat.m, 0);
        GLES20.glUniformMatrix4fv(uModel3D, 1, false, modelMat.m, 0);
        GLES20.glUniform4f(uColor3D, r, g, b, a);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(uTex3D, 0);

        mesh.draw(aPos3D, aNorm3D, aUV3D);
    }

    private void drawMeshRotated(Mesh mesh, int texture, float x, float y, float z,
                                 float sx, float sy, float sz, float yaw, float pitch) {
        modelMat.identity();
        modelMat.translate(x, y, z);
        modelMat.rotate(yaw, 0, 1, 0);
        modelMat.rotate(pitch, 1, 0, 0);
        modelMat.scale(sx, sy, sz);

        mvpMat.set(projMat);
        mvpMat.multiply(viewMat);
        mvpMat.multiply(modelMat);

        GLES20.glUniformMatrix4fv(uMVP3D, 1, false, mvpMat.m, 0);
        GLES20.glUniformMatrix4fv(uModel3D, 1, false, modelMat.m, 0);
        GLES20.glUniform4f(uColor3D, 1f, 1f, 1f, 1f);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(uTex3D, 0);

        mesh.draw(aPos3D, aNorm3D, aUV3D);
    }
}
