package com.syslikoffnet.overglow;

import android.opengl.GLES20;
import java.util.ArrayList;

/**
 * 3D Рендерер для PUBG Mobile:
 * - Камера от 3-го лица (TPP) над правым плечом персонажа
 * - Самолет, парашюты, открывающиеся двери, транспорт, вращающийся лут на полу
 * - Аирдроп с красным парашютом, боты со шлемами и рюкзаками
 */
public final class PUBGRenderer {

    private int prog3D;
    private int uMVP3D, uModel3D, uLightDir3D, uMuzzlePos3D, uMuzzleInt3D, uTex3D, uColor3D;
    private int uFogColor3D, uFogStart3D, uFogEnd3D;
    private int aPos3D, aNorm3D, aUV3D;

    private final Math3D.Mat4 projMat = new Math3D.Mat4();
    private final Math3D.Mat4 viewMat = new Math3D.Mat4();
    private final Math3D.Mat4 modelMat = new Math3D.Mat4();
    private final Math3D.Mat4 mvpMat = new Math3D.Mat4();

    public int width = 1920, height = 1080;

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

    public void render(PUBGPlayer player, PUBGMap map, ArrayList<PUBGBot> bots, ParticleSystem particles) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        GLES20.glClearColor(0.48f, 0.68f, 0.88f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(prog3D);
        GLES20.glUniform3f(uLightDir3D, -0.6f, -0.8f, -0.4f);
        GLES20.glUniform3f(uFogColor3D, 0.48f, 0.68f, 0.88f);
        GLES20.glUniform1f(uFogStart3D, 60f);
        GLES20.glUniform1f(uFogEnd3D, 350f);

        // Проекция
        float fov = 75f - (player.adsFactor * 25f);
        float aspect = (float) width / Math.max(1, height);
        projMat.perspective(fov, aspect, 0.1f, 500f);

        // TPP Камера над плечом персонажа
        float camYaw = player.yaw + player.recoilYaw;
        float camPitch = player.pitch + player.recoilPitch;

        float radYaw = camYaw * Math3D.TO_RAD;
        float radPitch = camPitch * Math3D.TO_RAD;

        float fX = -(float) Math.sin(radYaw) * (float) Math.cos(radPitch);
        float fY = (float) Math.sin(radPitch);
        float fZ = (float) Math.cos(radYaw) * (float) Math.cos(radPitch);

        Math3D.Vec3 eye = new Math3D.Vec3();
        Math3D.Vec3 target = new Math3D.Vec3();

        if (player.moveMode == PUBGPlayer.MODE_IN_PLANE) {
            eye.set(map.planePos.x - fX * 25f, map.planePos.y + 12f, map.planePos.z - fZ * 25f);
            target.set(map.planePos);
        } else if (player.isTPP && player.adsFactor < 0.8f) {
            float dist = (player.moveMode == PUBGPlayer.MODE_DRIVING) ? 6.5f : 3.2f;
            eye.set(player.pos.x - fX * dist + (float) Math.cos(radYaw) * 0.6f,
                    player.pos.y + 1.8f - fY * dist * 0.5f,
                    player.pos.z - fZ * dist + (float) Math.sin(radYaw) * 0.6f);
            target.set(player.pos.x + fX * 20f, player.pos.y + 1.6f + fY * 20f, player.pos.z + fZ * 20f);
        } else {
            eye.set(player.pos.x, player.pos.y + 1.7f, player.pos.z);
            target.set(eye.x + fX * 20f, eye.y + fY * 20f, eye.z + fZ * 20f);
        }

        viewMat.lookAt(eye, target, new Math3D.Vec3(0, 1, 0));

        // 1. Земля
        drawMesh(ModelGenerator.groundMesh, GLUtil.texSandstone, 0, 0, 0, 1.5f, 1f, 1.5f, 0, 1f, 1f, 1f, 1f);

        // 2. Здания и препятствия
        for (PUBGMap.Obstacle obs : map.obstacles) {
            drawMesh(ModelGenerator.boxMesh, obs.texture,
                    obs.getCenterX(), obs.getCenterY(), obs.getCenterZ(),
                    obs.getWidth(), obs.getHeight(), obs.getDepth(),
                    0, 1f, 1f, 1f, 1f);
        }

        // 3. Открывающиеся двери
        for (InteractiveDoor door : map.doors) {
            drawMeshRotated(ModelGenerator.boxMesh, GLUtil.texCrate,
                    door.hingeX + 0.8f, door.hingeY + 1.2f, door.hingeZ,
                    door.width, door.height, door.thickness,
                    door.baseAngle + door.currentAngle, 0);
        }

        // 4. Лут на полу (плавно вращается)
        float lootRot = (System.currentTimeMillis() % 3600) / 10f;
        for (LootItem item : map.loot) {
            if (!item.isTaken) {
                drawMeshRotated(ModelGenerator.boxMesh, GLUtil.texWeaponGold,
                        item.x, item.y + 0.2f, item.z, 0.6f, 0.25f, 0.6f, lootRot, 0);
            }
        }

        // 5. Транспорт (Багги и УАЗ)
        for (Vehicle3D v : map.vehicles) {
            drawMeshRotated(ModelGenerator.boxMesh, GLUtil.texMetal,
                    v.pos.x, v.pos.y + 0.8f, v.pos.z, 2.2f, 1.4f, 4.0f, v.yaw, 0);
        }

        // 6. Грузовой самолет
        if (map.planeProgress < 1.0f) {
            drawMeshRotated(ModelGenerator.boxMesh, GLUtil.texMetal,
                    map.planePos.x, map.planePos.y, map.planePos.z, 14f, 6f, 28f, 45f, 0);
        }

        // 7. Аирдроп
        if (map.airdropActive) {
            drawMesh(ModelGenerator.boxMesh, GLUtil.texWeaponDragon,
                    map.airdropPos.x, map.airdropPos.y + 0.8f, map.airdropPos.z, 1.8f, 1.8f, 1.8f, 0, 1f, 1f, 1f, 1f);
        }

        // 8. Персонаж игрока (в режиме TPP)
        if (player.isTPP && player.moveMode != PUBGPlayer.MODE_IN_PLANE) {
            drawMeshRotated(ModelGenerator.charTorsoMesh, GLUtil.texPlayerCT,
                    player.pos.x, player.pos.y + 0.88f, player.pos.z, 1f, 1f, 1f, player.yaw, 0);
            drawMeshRotated(ModelGenerator.charHeadMesh, GLUtil.texMetal,
                    player.pos.x, player.pos.y + 1.55f, player.pos.z, 1f, 1f, 1f, player.yaw, player.pitch);
        }

        // 9. Боты
        for (PUBGBot bot : bots) {
            if (!bot.isDead && !bot.isParachuting) {
                drawMeshRotated(ModelGenerator.charTorsoMesh, GLUtil.texPlayerT,
                        bot.pos.x, bot.pos.y + 0.88f, bot.pos.z, 1f, 1f, 1f, bot.yaw, 0);
                drawMeshRotated(ModelGenerator.charHeadMesh, GLUtil.texMetal,
                        bot.pos.x, bot.pos.y + 1.55f, bot.pos.z, 1f, 1f, 1f, bot.yaw, bot.pitch);
            }
        }
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
