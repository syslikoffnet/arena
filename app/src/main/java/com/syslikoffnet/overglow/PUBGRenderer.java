package com.syslikoffnet.overglow;

import android.opengl.GLES20;
import java.util.ArrayList;

/**
 * 3D Рендерер высокого качества для PUBG Mobile (Фаза 1):
 * - Unreal Engine 4 PBR модель освещения с ACES Filmic Tone Mapping
 * - Идеальная TPP SpringArm Камера с физическими коллизиями и защитой от прохождения сквозь стены
 * - Раздельное позиционирование корпуса персонажа (bodyYaw) и наклона прицела (camPitch)
 * - Детализированные модели бойцов, шлема Алтын, бронежилетов, сковороды и зданий
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

        GLES20.glClearColor(0.42f, 0.65f, 0.85f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(prog3D);

        // 1. Проекция камеры с динамическим FOV для ADS
        float fov = 75f - (player.camera.adsFactor * 32f);
        float aspect = (float) width / Math.max(1, height);
        projMat.perspective(fov, aspect, 0.1f, 600f);

        // 2. Позиция камеры из SpringArm системы
        Math3D.Vec3 eye = player.camera.eye;
        Math3D.Vec3 target = player.camera.target;

        if (player.moveMode == PUBGPlayer.MODE_IN_PLANE) {
            float rad = player.camera.yaw * Math3D.TO_RAD;
            float fX = (float) Math.sin(rad), fZ = (float) Math.cos(rad);
            eye = new Math3D.Vec3(map.planePos.x - fX * 32f, map.planePos.y + 14f, map.planePos.z - fZ * 32f);
            target = map.planePos;
        }

        viewMat.lookAt(eye, target, player.camera.up);

        // 3. Юниформы шейдера
        GLES20.glUniform3f(uEyePos3D, eye.x, eye.y, eye.z);
        GLES20.glUniform3f(uLightDir3D, -0.6f, -0.8f, -0.4f);
        GLES20.glUniform3f(uFogColor3D, 0.42f, 0.65f, 0.85f);
        GLES20.glUniform1f(uFogStart3D, 80f);
        GLES20.glUniform1f(uFogEnd3D, 450f);

        GLES20.glUniform3f(uMuzzlePos3D, particles.muzzleFlashPos.x, particles.muzzleFlashPos.y, particles.muzzleFlashPos.z);
        GLES20.glUniform1f(uMuzzleInt3D, particles.muzzleFlashIntensity);

        // 4. Земля
        drawMesh(ModelGenerator.groundMesh, GLUtil.texGrass, 0, 0, 0, 1f, 1f, 1f, 0, 1f, 1f, 1f, 1f);

        // 5. Препятствия и дома Починок
        for (PUBGMap.Obstacle obs : map.obstacles) {
            drawMesh(ModelGenerator.boxMesh, obs.texture,
                    obs.getCenterX(), obs.getCenterY(), obs.getCenterZ(),
                    obs.getWidth(), obs.getHeight(), obs.getDepth(),
                    0, 1f, 1f, 1f, 1f);
        }

        // 6. Открывающиеся деревянные двери
        for (InteractiveDoor door : map.doors) {
            drawMeshRotated(ModelGenerator.boxMesh, GLUtil.texCrate,
                    door.hingeX + 0.8f, door.hingeY + 1.2f, door.hingeZ,
                    door.width, door.height, door.thickness,
                    door.baseAngle + door.currentAngle, 0);
        }

        // 7. Деревья
        for (int x = -160; x <= 160; x += 40) {
            for (int z = -160; z <= 160; z += 40) {
                if (Math.abs(x) > 50 || Math.abs(z) > 50) {
                    float y = map.getTerrainHeight(x, z);
                    drawMesh(ModelGenerator.treeMesh, GLUtil.texGrass, x + (z % 15), y, z + (x % 15), 1.2f, 1.2f, 1.2f, 0, 1f, 1f, 1f, 1f);
                }
            }
        }

        // 8. Транспорт
        for (Vehicle3D v : map.vehicles) {
            renderVehicle3D(v);
        }

        // 9. Лут на полу
        float lootRot = (System.currentTimeMillis() % 3600) / 10f;
        for (LootItem item : map.loot) {
            if (!item.isTaken) {
                renderLootItem3D(item, lootRot);
            }
        }

        // 10. Самолет C-130
        if (map.planeProgress < 1.0f) {
            drawMeshRotated(ModelGenerator.cargoPlaneMesh, GLUtil.texMetal,
                    map.planePos.x, map.planePos.y, map.planePos.z,
                    1f, 1f, 1f, 45f, 0);
        }

        // 11. Аирдроп
        if (map.airdropActive) {
            drawMesh(ModelGenerator.airdropBoxMesh, GLUtil.texAirdropTarp,
                    map.airdropPos.x, map.airdropPos.y + 0.9f, map.airdropPos.z, 1f, 1f, 1f, 0, 1f, 1f, 1f, 1f);
            if (!map.airdropLanded) {
                drawMesh(ModelGenerator.parachuteCanopyMesh, GLUtil.texWeaponDragon,
                        map.airdropPos.x, map.airdropPos.y + 4.5f, map.airdropPos.z, 1f, 1f, 1f, 0, 1f, 1f, 1f, 1f);
            }
        }

        // 12. Персонаж игрока (Корпус ориентирован по bodyYaw, взгляд по camera.pitch)
        if (player.isTPP && player.moveMode != PUBGPlayer.MODE_IN_PLANE && player.camera.adsFactor < 0.95f) {
            renderSoldier3D(player.pos.x, player.pos.y, player.pos.z,
                    player.motor.bodyYaw, player.camera.pitch,
                    GLUtil.texPlayerCT, player.helmetLevel, player.vestLevel, player.backpackLevel,
                    player.hasPan, player.getActiveWeapon(), player.motor.currentStance);
        }

        // 13. Боты
        for (PUBGBot bot : bots) {
            if (!bot.isDead && !bot.isParachuting) {
                renderSoldier3D(bot.pos.x, bot.pos.y, bot.pos.z, bot.yaw, bot.pitch,
                        GLUtil.texPlayerT, bot.helmetLevel, bot.vestLevel, 2, true, bot.weapon, CharacterMotor.STANCE_STAND);
            }
        }
    }

    private void renderSoldier3D(float x, float y, float z, float bodyYaw, float aimPitch,
                                 int uniformTex, int helmetLv, int vestLv, int backpackLv,
                                 boolean hasPan, Weapon wep, int stance) {
        float bodyHeight = (stance == CharacterMotor.STANCE_PRONE) ? 0.20f :
                ((stance == CharacterMotor.STANCE_CROUCH) ? 0.60f : 0.88f);

        // Торс
        drawMeshRotated(ModelGenerator.soldierTorsoMesh, uniformTex, x, y + bodyHeight, z, 1f, 1f, 1f, bodyYaw, 0);

        if (vestLv > 0) {
            drawMeshRotated(ModelGenerator.vest3Mesh, GLUtil.texMetal, x, y + bodyHeight + 0.04f, z, 1f, 1f, 1f, bodyYaw, 0);
        }

        if (backpackLv > 0) {
            drawMeshRotated(ModelGenerator.backpack3Mesh, GLUtil.texVehicle, x, y + bodyHeight + 0.02f, z, 1f, 1f, 1f, bodyYaw, 0);
        }

        if (hasPan) {
            drawMeshRotated(ModelGenerator.panMesh, GLUtil.texMetal, x, y + bodyHeight - 0.30f, z - 0.22f, 1f, 1f, 1f, bodyYaw + 180f, 45f);
        }

        // Голова и шлем (поворачиваются по взгляду прицеливания)
        float headHeight = bodyHeight + 0.65f;
        drawMeshRotated(ModelGenerator.soldierHeadMesh, GLUtil.texWeaponDark, x, y + headHeight, z, 1f, 1f, 1f, bodyYaw, aimPitch);

        if (helmetLv > 0) {
            drawMeshRotated(ModelGenerator.helmet3Mesh, GLUtil.texMetal, x, y + headHeight + 0.07f, z, 1f, 1f, 1f, bodyYaw, aimPitch);
        }

        // Ноги
        if (stance != CharacterMotor.STANCE_PRONE) {
            float legHeight = (stance == CharacterMotor.STANCE_CROUCH) ? 0.20f : 0.38f;
            drawMeshRotated(ModelGenerator.soldierLegMesh, uniformTex, x - 0.16f, y + legHeight, z, 1f, 1f, 1f, bodyYaw, 0);
            drawMeshRotated(ModelGenerator.soldierLegMesh, uniformTex, x + 0.16f, y + legHeight, z, 1f, 1f, 1f, bodyYaw, 0);
        }

        // Оружие в руках
        if (wep != null) {
            Mesh wMesh = (wep.id == Weapon.ID_AWM) ? ModelGenerator.awmMesh :
                         ((wep.id == Weapon.ID_AKR) ? ModelGenerator.akmMesh : ModelGenerator.m416Mesh);
            int wTex = (wep.id == Weapon.ID_AWM) ? GLUtil.texWeaponGold : GLUtil.texWeaponDark;
            drawMeshRotated(wMesh, wTex, x + 0.22f, y + bodyHeight + 0.30f, z + 0.15f, 1f, 1f, 1f, bodyYaw, aimPitch);
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
