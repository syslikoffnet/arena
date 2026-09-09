package com.syslikoffnet.overglow;

import android.opengl.GLES20;
import java.util.ArrayList;

/**
 * 3D Рендерер высокого качества для PUBG Mobile:
 * - Шейдеры Blinn-Phong со зеркальными бликами и контурным светом (Rim Light)
 * - Высокодетализированный персонаж: Шлем 3 уровня (Алтын с забралом), Броня 3 уровня, Рюкзак, Сковорода на поясе
 * - Транспорт: 3D Багги и УАЗ с 4 независимыми крутящимися колесами
 * - Мир: 2-этажные дома с двускатными крышами и открывающимися дверями, сосны, камни
 * - Грузовой самолет C-130 с 4 двигателями, Аирдроп с синим брезентом и парашютом
 */
public final class PUBGRenderer {

    private int prog3D;
    private int uMVP3D, uModel3D, uEyePos3D, uLightDir3D, uMuzzlePos3D, uMuzzleInt3D, uTex3D, uColor3D;
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
        uEyePos3D = GLES20.glGetUniformLocation(prog3D, "uEyePos");
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

        GLES20.glClearColor(0.46f, 0.68f, 0.88f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(prog3D);

        // Проекция
        float fov = 75f - (player.adsFactor * 25f);
        float aspect = (float) width / Math.max(1, height);
        projMat.perspective(fov, aspect, 0.1f, 500f);

        // Камера от 3-го лица (TPP) над правым плечом
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
            eye.set(map.planePos.x - fX * 28f, map.planePos.y + 14f, map.planePos.z - fZ * 28f);
            target.set(map.planePos);
        } else if (player.isTPP && player.adsFactor < 0.8f) {
            float dist = (player.moveMode == PUBGPlayer.MODE_DRIVING) ? 7.2f : 3.4f;
            eye.set(player.pos.x - fX * dist + (float) Math.cos(radYaw) * 0.65f,
                    player.pos.y + 1.85f - fY * dist * 0.5f,
                    player.pos.z - fZ * dist + (float) Math.sin(radYaw) * 0.65f);
            target.set(player.pos.x + fX * 25f, player.pos.y + 1.6f + fY * 25f, player.pos.z + fZ * 25f);
        } else {
            eye.set(player.pos.x, player.pos.y + 1.7f, player.pos.z);
            target.set(eye.x + fX * 25f, eye.y + fY * 25f, eye.z + fZ * 25f);
        }

        viewMat.lookAt(eye, target, new Math3D.Vec3(0, 1, 0));

        // Юниформы освещения и камеры
        GLES20.glUniform3f(uEyePos3D, eye.x, eye.y, eye.z);
        GLES20.glUniform3f(uLightDir3D, -0.6f, -0.8f, -0.4f);
        GLES20.glUniform3f(uFogColor3D, 0.46f, 0.68f, 0.88f);
        GLES20.glUniform1f(uFogStart3D, 80f);
        GLES20.glUniform1f(uFogEnd3D, 420f);

        // Вспышка выстрела
        GLES20.glUniform3f(uMuzzlePos3D, particles.muzzleFlashPos.x, particles.muzzleFlashPos.y, particles.muzzleFlashPos.z);
        GLES20.glUniform1f(uMuzzleInt3D, particles.muzzleFlashIntensity);

        // 1. Земля (Трава Эрангеля)
        drawMesh(ModelGenerator.groundMesh, GLUtil.texGrass, 0, 0, 0, 1f, 1f, 1f, 0, 1f, 1f, 1f, 1f);

        // 2. Дома в Починках (Стены + Черепичные крыши)
        for (PUBGMap.Obstacle obs : map.obstacles) {
            drawMesh(ModelGenerator.boxMesh, obs.texture,
                    obs.getCenterX(), obs.getCenterY(), obs.getCenterZ(),
                    obs.getWidth(), obs.getHeight(), obs.getDepth(),
                    0, 1f, 1f, 1f, 1f);
        }

        // 3. Открывающиеся деревянные двери с ручками
        for (InteractiveDoor door : map.doors) {
            drawMeshRotated(ModelGenerator.boxMesh, GLUtil.texCrate,
                    door.hingeX + 0.8f, door.hingeY + 1.2f, door.hingeZ,
                    door.width, door.height, door.thickness,
                    door.baseAngle + door.currentAngle, 0);
        }

        // 4. Деревья вокруг карты
        for (int x = -160; x <= 160; x += 40) {
            for (int z = -160; z <= 160; z += 40) {
                if (Math.abs(x) > 50 || Math.abs(z) > 50) {
                    drawMesh(ModelGenerator.treeMesh, GLUtil.texGrass, x + (z % 15), 0, z + (x % 15), 1.2f, 1.2f, 1.2f, 0, 1f, 1f, 1f, 1f);
                }
            }
        }

        // 5. 3D Транспорт (Багги и УАЗ с 4 отдельными колесами)
        for (Vehicle3D v : map.vehicles) {
            renderVehicle3D(v);
        }

        // 6. 3D Лут на полу (M416, AKM, AWM, Аптечки, Сковорода)
        float lootRot = (System.currentTimeMillis() % 3600) / 10f;
        for (LootItem item : map.loot) {
            if (!item.isTaken) {
                renderLootItem3D(item, lootRot);
            }
        }

        // 7. Самолет C-130 в небе
        if (map.planeProgress < 1.0f) {
            drawMeshRotated(ModelGenerator.cargoPlaneMesh, GLUtil.texMetal,
                    map.planePos.x, map.planePos.y, map.planePos.z,
                    1f, 1f, 1f, 45f, 0);
        }

        // 8. Аирдроп с синим брезентом и красным куполом парашюта
        if (map.airdropActive) {
            drawMesh(ModelGenerator.airdropBoxMesh, GLUtil.texAirdropTarp,
                    map.airdropPos.x, map.airdropPos.y + 0.9f, map.airdropPos.z, 1f, 1f, 1f, 0, 1f, 1f, 1f, 1f);
            if (!map.airdropLanded) {
                drawMesh(ModelGenerator.parachuteCanopyMesh, GLUtil.texWeaponDragon,
                        map.airdropPos.x, map.airdropPos.y + 4.5f, map.airdropPos.z, 1f, 1f, 1f, 0, 1f, 1f, 1f, 1f);
            }
        }

        // 9. Персонаж игрока (Высокодетализированный боец PUBG)
        if (player.isTPP && player.moveMode != PUBGPlayer.MODE_IN_PLANE) {
            renderSoldier3D(player.pos.x, player.pos.y, player.pos.z, player.yaw, player.pitch,
                    GLUtil.texPlayerCT, player.helmetLevel, player.vestLevel, player.backpackLevel,
                    player.hasPan, player.getActiveWeapon());
        }

        // 10. Боты (Экипированные враги)
        for (PUBGBot bot : bots) {
            if (!bot.isDead && !bot.isParachuting) {
                renderSoldier3D(bot.pos.x, bot.pos.y, bot.pos.z, bot.yaw, bot.pitch,
                        GLUtil.texPlayerT, bot.helmetLevel, bot.vestLevel, 2, true, bot.weapon);
            }
        }
    }

    private void renderSoldier3D(float x, float y, float z, float yaw, float pitch,
                                 int uniformTex, int helmetLv, int vestLv, int backpackLv,
                                 boolean hasPan, Weapon wep) {
        drawMeshRotated(ModelGenerator.soldierTorsoMesh, uniformTex, x, y + 0.88f, z, 1f, 1f, 1f, yaw, 0);

        if (vestLv > 0) {
            drawMeshRotated(ModelGenerator.vest3Mesh, GLUtil.texMetal, x, y + 0.92f, z, 1f, 1f, 1f, yaw, 0);
        }

        if (backpackLv > 0) {
            drawMeshRotated(ModelGenerator.backpack3Mesh, GLUtil.texVehicle, x, y + 0.90f, z, 1f, 1f, 1f, yaw, 0);
        }

        if (hasPan) {
            drawMeshRotated(ModelGenerator.panMesh, GLUtil.texMetal, x, y + 0.55f, z - 0.22f, 1f, 1f, 1f, yaw + 180f, 45f);
        }

        drawMeshRotated(ModelGenerator.soldierHeadMesh, GLUtil.texWeaponDark, x, y + 1.55f, z, 1f, 1f, 1f, yaw, pitch);

        if (helmetLv > 0) {
            drawMeshRotated(ModelGenerator.helmet3Mesh, GLUtil.texMetal, x, y + 1.62f, z, 1f, 1f, 1f, yaw, pitch);
        }

        drawMeshRotated(ModelGenerator.soldierLegMesh, uniformTex, x - 0.16f, y + 0.38f, z, 1f, 1f, 1f, yaw, 0);
        drawMeshRotated(ModelGenerator.soldierLegMesh, uniformTex, x + 0.16f, y + 0.38f, z, 1f, 1f, 1f, yaw, 0);

        if (wep != null) {
            Mesh wMesh = (wep.id == Weapon.ID_AWM) ? ModelGenerator.awmMesh :
                         ((wep.id == Weapon.ID_AKR) ? ModelGenerator.akmMesh : ModelGenerator.m416Mesh);
            int wTex = (wep.id == Weapon.ID_AWM) ? GLUtil.texWeaponGold : GLUtil.texWeaponDark;
            drawMeshRotated(wMesh, wTex, x + 0.22f, y + 1.18f, z + 0.15f, 1f, 1f, 1f, yaw, pitch);
        }
    }

    private void renderVehicle3D(Vehicle3D v) {
        Mesh bodyMesh = (v.type == Vehicle3D.TYPE_BUGGY) ? ModelGenerator.buggyBodyMesh : ModelGenerator.uazBodyMesh;
        drawMeshRotated(bodyMesh, GLUtil.texVehicle, v.pos.x, v.pos.y, v.pos.z, 1f, 1f, 1f, v.yaw, 0);

        float fw = (v.type == Vehicle3D.TYPE_BUGGY) ? 0.85f : 0.95f;
        float fd = (v.type == Vehicle3D.TYPE_BUGGY) ? 1.25f : 1.45f;
        float wheelY = v.pos.y + 0.42f;

        float rad = v.yaw * Math3D.TO_RAD;
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);

        renderSingleWheel(v.pos.x - cos * fw - sin * fd, wheelY, v.pos.z - sin * fw + cos * fd, v.yaw + v.steering, v.wheelSpin);
        renderSingleWheel(v.pos.x + cos * fw - sin * fd, wheelY, v.pos.z + sin * fw + cos * fd, v.yaw + v.steering, v.wheelSpin);
        renderSingleWheel(v.pos.x - cos * fw + sin * fd, wheelY, v.pos.z - sin * fw - cos * fd, v.yaw, v.wheelSpin);
        renderSingleWheel(v.pos.x + cos * fw + sin * fd, wheelY, v.pos.z + sin * fw - cos * fd, v.yaw, v.wheelSpin);
    }

    private void renderSingleWheel(float wx, float wy, float wz, float yaw, float spin) {
        drawMeshRotated(ModelGenerator.wheelMesh, GLUtil.texMetal, wx, wy, wz, 1f, 1f, 1f, yaw, spin);
    }

    private void renderLootItem3D(LootItem item, float rot) {
        float ly = item.y + 0.25f;
        switch (item.type) {
            case LootItem.TYPE_WEAPON:
                Mesh wMesh = (item.subId == Weapon.ID_AWM) ? ModelGenerator.awmMesh :
                             ((item.subId == Weapon.ID_AKR) ? ModelGenerator.akmMesh : ModelGenerator.m416Mesh);
                drawMeshRotated(wMesh, GLUtil.texWeaponGold, item.x, ly, item.z, 1.2f, 1.2f, 1.2f, rot, 15f);
                break;
            case LootItem.TYPE_MEDKIT:
                drawMeshRotated(ModelGenerator.firstAidMesh, GLUtil.texWhite, item.x, ly, item.z, 1.2f, 1.2f, 1.2f, rot, 0);
                break;
            case LootItem.TYPE_ENERGY_DRINK:
                drawMeshRotated(ModelGenerator.energyDrinkMesh, GLUtil.texAirdropTarp, item.x, ly, item.z, 1.4f, 1.4f, 1.4f, rot, 0);
                break;
            case LootItem.TYPE_HELMET:
                drawMeshRotated(ModelGenerator.helmet3Mesh, GLUtil.texMetal, item.x, ly, item.z, 1.3f, 1.3f, 1.3f, rot, 0);
                break;
            case LootItem.TYPE_PAN:
                drawMeshRotated(ModelGenerator.panMesh, GLUtil.texMetal, item.x, ly, item.z, 1.3f, 1.3f, 1.3f, rot, 30f);
                break;
            default:
                drawMeshRotated(ModelGenerator.boxMesh, GLUtil.texCrate, item.x, ly, item.z, 0.4f, 0.4f, 0.4f, rot, 0);
                break;
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
