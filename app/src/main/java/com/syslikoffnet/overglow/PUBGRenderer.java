package com.syslikoffnet.overglow;

import android.opengl.GLES20;

import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * 3D Рендерer нового поколения:
 * - Террейн 161×161 по аналитической функции высот + дорожные ленты по рельефу
 * - Все материалы — процедурные albedo+normal из TextureForge (мир: planar world-UV без швов)
 * - Солдаты с походкой (сwing ног/рук от фазы шага), стойками, обвесом и оружием в руках
 * - Oружие от первого лица (viewmodel) с покачиванием и отдачей, зум оптики через FOV
 * - Blob-тени, трейсеры (GL_LINES), частицы/искры/взрывы (GL_POINTS), свет вспышки
 */
public final class PUBGRenderer {

    private int prog3D, progFX;
    private int uMVP, uModel, uEyePos, uUVScale, uLightDir, uMuzzlePos, uMuzzleInt;
    private int uTexA, uTexN, uColor, uMode, uWorldUV, uInvTile, uUseNormal, uBump, uRough, uMetal;
    private int uFogColor, uFogStart, uFogEnd;
    private int fUMVP, fUPointScale, fUSprite;

    private final float[] projM = new float[16];
    private final float[] viewM = new float[16];
    private final float[] vpM = new float[16];
    private final float[] modelM = new float[16];
    private final float[] mvpM = new float[16];

    public int width = 1920, height = 1080;

    private PUBGMap builtFor;
    private Mesh terrainMesh, roadMesh;

    // FX динамический буфер: вершина = pos(3) + color(4) + size(1)
    private static final int FX_MAX_VERTS = 2048;
    private final float[] fxData = new float[FX_MAX_VERTS * 8];
    private final FloatBuffer fxBuf = GLUtil.createFloatBuffer(fxData);
    private int fxCount;

    // Покачивание viewmodel
    private float prevCamYaw, prevCamPitch, swayX, swayY;

    public void init() {
        GLUtil.initTextures();          // TextureForge: генерация всех материалов (GL-поток)
        ModelGenerator.init();

        prog3D = GLUtil.createProgram(GLUtil.VS_3D, GLUtil.FS_3D);
        uMVP = GLES20.glGetUniformLocation(prog3D, "uMVP");
        uModel = GLES20.glGetUniformLocation(prog3D, "uModel");
        uEyePos = GLES20.glGetUniformLocation(prog3D, "uEyePos");
        uUVScale = GLES20.glGetUniformLocation(prog3D, "uUVScale");
        uLightDir = GLES20.glGetUniformLocation(prog3D, "uLightDir");
        uMuzzlePos = GLES20.glGetUniformLocation(prog3D, "uMuzzlePos");
        uMuzzleInt = GLES20.glGetUniformLocation(prog3D, "uMuzzleIntensity");
        uTexA = GLES20.glGetUniformLocation(prog3D, "uTexA");
        uTexN = GLES20.glGetUniformLocation(prog3D, "uTexN");
        uColor = GLES20.glGetUniformLocation(prog3D, "uColor");
        uMode = GLES20.glGetUniformLocation(prog3D, "uMode");
        uWorldUV = GLES20.glGetUniformLocation(prog3D, "uWorldUV");
        uInvTile = GLES20.glGetUniformLocation(prog3D, "uInvTile");
        uUseNormal = GLES20.glGetUniformLocation(prog3D, "uUseNormal");
        uBump = GLES20.glGetUniformLocation(prog3D, "uBump");
        uRough = GLES20.glGetUniformLocation(prog3D, "uRough");
        uMetal = GLES20.glGetUniformLocation(prog3D, "uMetal");
        uFogColor = GLES20.glGetUniformLocation(prog3D, "uFogColor");
        uFogStart = GLES20.glGetUniformLocation(prog3D, "uFogStart");
        uFogEnd = GLES20.glGetUniformLocation(prog3D, "uFogEnd");

        progFX = GLUtil.createFXProgram(GLUtil.VS_FX, GLUtil.FS_FX);
        fUMVP = GLES20.glGetUniformLocation(progFX, "uMVP");
        fUPointScale = GLES20.glGetUniformLocation(progFX, "uPointScale");
        fUSprite = GLES20.glGetUniformLocation(progFX, "uSprite");

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glHint(GLES20.GL_GENERATE_MIPMAP_HINT, GLES20.GL_NICEST);
    }

    public void resize(int w, int h) {
        width = w; height = h;
        GLES20.glViewport(0, 0, w, h);
    }

    private void rebuildWorld(PUBGMap map) {
        terrainMesh = ModelGenerator.buildTerrain(map, 161, 300f);
        roadMesh = ModelGenerator.buildRoads(map);
        builtFor = map;
    }

    // =========================================================================
    // Кадр
    // =========================================================================
    public void render(PUBGPlayer player, PUBGMap map, ArrayList<PUBGBot> bots, ParticleSystem particles) {
        if (builtFor != map) rebuildWorld(map);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(0.62f, 0.74f, 0.86f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // ---------- Камера ----------
        Math3D.Vec3 eye;
        Math3D.Vec3 target;
        if (player.moveMode == PUBGPlayer.MODE_IN_PLANE) {
            float rad = player.camera.yaw * Math3D.TO_RAD;
            eye = new Math3D.Vec3(map.planePos.x - (float) Math.sin(rad) * 34f,
                    map.planePos.y + 12f, map.planePos.z - (float) Math.cos(rad) * 34f);
            target = map.planePos;
        } else {
            eye = player.camera.eye;
            target = player.camera.target;
        }

        // Тряска от попаданий/отдачи
        float shake = player.camera.shakePitch;
        float ex = eye.x + (float) (Math.random() - 0.5) * Math.abs(shake) * 0.06f;
        float ey = eye.y + (float) (Math.random() - 0.5) * Math.abs(shake) * 0.06f;
        float ez = eye.z + (float) (Math.random() - 0.5) * Math.abs(shake) * 0.06f;

        // Roll от пик-наклона: up = worldUp·cos + right·sin (right = экранное «вправо»)
        float roll = player.leanAngle * 7f * Math3D.TO_RAD;
        float sr = (float) Math.sin(roll), cr = (float) Math.cos(roll);
        Math3D.Vec3 rt = player.camera.right;
        Math3D.Vec3 upV = new Math3D.Vec3(rt.x * sr, cr, rt.z * sr);
        Math3D.Mat4 projTmp = new Math3D.Mat4();
        Math3D.Mat4 viewTmp = new Math3D.Mat4();

        float fov = computeFov(player);
        float aspect = (float) width / Math.max(1, height);
        projTmp.perspective(fov, aspect, 0.08f, 900f);
        viewTmp.lookAt(new Math3D.Vec3(ex, ey, ez), target, upV);

        System.arraycopy(projTmp.m, 0, projM, 0, 16);
        System.arraycopy(viewTmp.m, 0, viewM, 0, 16);
        mat4mul(projM, viewM, vpM);

        // ---------- Основной прогон ----------
        GLES20.glUseProgram(prog3D);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, buildVPOnly(), 0);
        GLES20.glUniform3f(uEyePos, ex, ey, ez);
        GLES20.glUniform3f(uLightDir, -0.55f, -0.78f, -0.32f);
        GLES20.glUniform3f(uFogColor, 0.66f, 0.76f, 0.88f);
        GLES20.glUniform1f(uFogStart, 110f);
        GLES20.glUniform1f(uFogEnd, 620f);
        GLES20.glUniform3f(uMuzzlePos, particles.muzzleFlashPos.x, particles.muzzleFlashPos.y, particles.muzzleFlashPos.z);
        GLES20.glUniform1f(uMuzzleInt, particles.muzzleFlashIntensity);

        // Небо
        drawSky(ex, ey, ez);

        // Террейн + дороги
        drawWorldMesh(terrainMesh, TextureForge.MAT_GRASS);
        drawWorldMesh(roadMesh, TextureForge.MAT_ROAD);

        // Препятствия (стены/дома/контейнеры) — planar UV
        for (PUBGMap.Obstacle o : map.obstacles) {
            float dist2 = ((o.getCenterX() - ex) * (o.getCenterX() - ex) + (o.getCenterZ() - ez) * (o.getCenterZ() - ez));
            if (dist2 > 640f * 640f) continue; // LOD-отсечение дальней мелочи
            composeModel(o.getCenterX(), o.getCenterY(), o.getCenterZ(), 0, 0, 0,
                    o.getWidth(), o.getHeight(), o.getDepth());
            bindMaterial(o.material, 1);
            GLES20.glUniform4f(uColor, o.r, o.g, o.b, 1f);
            applyMVP();
            ModelGenerator.boxMesh.draw(0, 1, 2);
        }

        // Двери (вращение на петлях)
        for (InteractiveDoor d : map.doors) {
            float ang = d.baseAngle + d.currentAngle;
            float rad = ang * Math3D.TO_RAD;
            float midX = d.hingeX + (float) Math.cos(rad) * (d.width / 2f);
            float midZ = d.hingeZ + (float) Math.sin(rad) * (d.width / 2f);
            composeModel(midX, d.hingeY + d.height / 2f, midZ, 90f - ang, 0, 0, 0.14f, d.height, d.width);
            bindMaterial(TextureForge.MAT_WOOD, 1);
            GLES20.glUniform4f(uColor, 1f, 1f, 1f, 1f);
            applyMVP();
            ModelGenerator.boxMesh.draw(0, 1, 2);
        }

        // Декорации
        for (int i = 0; i < map.decors.size(); i++) {
            PUBGMap.Decor dc = map.decors.get(i);
            float dist2 = ((dc.x - ex) * (dc.x - ex) + (dc.z - ez) * (dc.z - ez));
            if (dist2 > 340f * 340f && dc.kind != PUBGMap.Decor.TOWER && dc.kind != PUBGMap.Decor.ROOF) continue;
            drawDecor(dc);
        }

        // Аирдроп
        if (map.airdropActive) {
            composeModel(map.airdropPos.x, map.airdropPos.y, map.airdropPos.z, 0, 0, 0, 1, 1, 1);
            bindMaterial(TextureForge.MAT_TARP, 1);
            GLES20.glUniform4f(uColor, 1f, 1f, 1f, 1f);
            applyMVP();
            ModelGenerator.airdropBoxMesh.draw(0, 1, 2);
            if (!map.airdropLanded) {
                composeModel(map.airdropPos.x, map.airdropPos.y + 4.5f, map.airdropPos.z, 0, 0, 0, 1, 1, 1);
                bindMaterial(TextureForge.MAT_CANOPY, 0);
                applyMVP();
                ModelGenerator.parachuteCanopyMesh.draw(0, 1, 2);
            }
        }

        // Самолёт
        composeModelFromVecs(map.planePos.x, map.planePos.y, map.planePos.z, 45f, 0, 0, 1, 1, 1);
        bindMaterial(TextureForge.MAT_VEHICLE, 0);
        applyMVP();
        ModelGenerator.cargoPlaneMesh.draw(0, 1, 2);

        // Лут на земле
        for (LootItem item : map.loot) {
            if (item.isTaken) continue;
            float dx = item.x - ex, dz = item.z - ez;
            if (dx * dx + dz * dz > 70f * 70f) continue;
            drawLoot(item);
        }

        // Боты
        for (int i = 0; i < bots.size(); i++) {
            PUBGBot bot = bots.get(i);
            if (bot.isDead) continue;
            float dx = bot.pos.x - ex, dz = bot.pos.z - ez;
            if (dx * dx + dz * dz > 300f * 300f) continue;
            renderSoldier(bot.pos.x, bot.pos.y, bot.pos.z, bot.yaw, -bot.pitch,
                    0, bot.legAngle, 1.0f, bot.weapon, bot.helmetLevel, bot.vestLevel, 1,
                    0.92f, 0.88f, 0.78f, bot.isParachuting, false, false);
        }

        // Игрок (скрыт в FPP и полностью в TPP-прицеле 4x+)
        boolean hidePlayerBody = !player.isTPP || (player.adsFactor > 0.85f && getZoom(player) > 3f);
        if (!player.isDead && player.moveMode != PUBGPlayer.MODE_IN_PLANE && !hidePlayerBody) {
            renderSoldier(player.pos.x, player.pos.y, player.pos.z, player.motor.bodyYaw, -player.camera.pitch,
                    player.motor.currentStance, player.motor.walkPhase, player.motor.walkBlend,
                    player.getActiveWeapon(), player.helmetLevel, player.vestLevel, player.backpackLevel,
                    1f, 1f, 1f, false, false, player.isCrouching);
        }

        // Парашют игрока
        if (player.moveMode == PUBGPlayer.MODE_PARACHUTE) {
            composeModel(player.pos.x, player.pos.y + 1.2f, player.pos.z, player.motor.bodyYaw, 0, 0, 1, 1, 1);
            bindMaterial(TextureForge.MAT_CANOPY, 0);
            applyMVP();
            ModelGenerator.parachuteCanopyMesh.draw(0, 1, 2);
        }

        // Транспорт
        for (int i = 0; i < map.vehicles.size(); i++) {
            Vehicle3D v = map.vehicles.get(i);
            renderVehicle(v);
        }

        // ---------- Тени-блибы ----------
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        drawBlobShadow(player, map, ex, ez);
        for (int i = 0; i < bots.size(); i++) {
            PUBGBot bot = bots.get(i);
            if (bot.isDead || bot.isParachuting) continue;
            float dx = bot.pos.x - ex, dz = bot.pos.z - ez;
            if (dx * dx + dz * dz > 60f * 60f) continue;
            float gy = map.getTerrainHeight(bot.pos.x, bot.pos.z) + 0.04f;
            composeModel(bot.pos.x, gy, bot.pos.z, 0, 0, 0, 1.5f, 1, 1.5f);
            drawShadowQuad();
        }
        for (int i = 0; i < map.vehicles.size(); i++) {
            Vehicle3D v = map.vehicles.get(i);
            float dx = v.pos.x - ex, dz = v.pos.z - ez;
            if (dx * dx + dz * dz > 60f * 60f) continue;
            composeModel(v.pos.x, v.pos.y + 0.05f, v.pos.z, 0, 0, 0, 3.6f, 1, 4.8f);
            drawShadowQuad();
        }
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);

        // ---------- Viewmodel (FPP / прицеливание) ----------
        drawViewModel(player);

        // ---------- FX: трейсеры и частицы ----------
        renderFX(player, map, particles);
    }

    private float computeFov(PUBGPlayer player) {
        float base = 72f;
        if (player.moveMode == PUBGPlayer.MODE_DRIVING && player.currentVehicle != null) {
            base = 72f + player.currentVehicle.speedKmH * 0.05f;
        }
        float zoom = getZoom(player);
        float adsFov = Math3D.clamp(70f / zoom, 8f, 60f);
        return Math3D.lerp(base, adsFov, player.adsFactor);
    }

    private float getZoom(PUBGPlayer player) {
        Weapon w = player.getActiveWeapon();
        return w != null ? w.getAdsZoom() : 1.3f;
    }

    // =========================================================================
    // Небесный купол
    // =========================================================================
    private void drawSky(float ex, float ey, float ez) {
        GLES20.glDepthMask(false);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        composeModel(ex, ey - 28f, ez, 0, 0, 0, 1, 1, 1);
        GLES20.glUniform1f(uMode, 1f);
        GLES20.glUniform1f(uWorldUV, 0f);
        GLES20.glUniform2f(uUVScale, 1f, 1f);
        GLES20.glUniform1f(uUseNormal, 0f);
        GLES20.glUniform4f(uColor, 1f, 1f, 1f, 1f);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TextureForge.texSky);
        GLES20.glUniform1i(uTexA, 0);
        applyMVP();
        ModelGenerator.skyDomeMesh.draw(0, 1, 2);
        GLES20.glUniform1f(uMode, 0f);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glDepthMask(true);
    }

    // =========================================================================
    // Декорации
    // =========================================================================
    private void drawDecor(PUBGMap.Decor dc) {
        Mesh m;
        int uvMode = 1;
        switch (dc.kind) {
            case PUBGMap.Decor.ROOF: m = ModelGenerator.roofMesh; break;
            case PUBGMap.Decor.TRUNK: m = ModelGenerator.trunkMesh; break;
            case PUBGMap.Decor.CANOPY: m = ModelGenerator.canopyMesh; break;
            case PUBGMap.Decor.BUSH: m = ModelGenerator.bushMesh; break;
            case PUBGMap.Decor.ROCK: m = ModelGenerator.rockMesh; break;
            case PUBGMap.Decor.BARREL: m = ModelGenerator.barrelMesh; break;
            case PUBGMap.Decor.CRATE: m = ModelGenerator.crateMesh; break;
            case PUBGMap.Decor.WINDOW: m = ModelGenerator.windowFrameMesh; break;
            case PUBGMap.Decor.GLASS: m = ModelGenerator.windowGlassMesh; uvMode = 0; break;
            case PUBGMap.Decor.SANDBAG: m = ModelGenerator.sandbagMesh; break;
            case PUBGMap.Decor.TOWER: m = ModelGenerator.watchtowerMesh; break;
            default: m = ModelGenerator.boxMesh; break;
        }
        composeModel(dc.x, dc.y, dc.z, dc.rotY, 0, 0, dc.sx, dc.sy, dc.sz);
        bindMaterial(dc.material, uvMode);
        GLES20.glUniform4f(uColor, 1f, 1f, 1f, 1f);
        applyMVP();
        m.draw(0, 1, 2);
    }

    // =========================================================================
    // Лут на земле
    // =========================================================================
    private void drawLoot(LootItem item) {
        Mesh mesh;
        int mat;
        float bob = (float) Math.sin(System.currentTimeMillis() * 0.003f + item.x) * 0.05f;
        float ly = item.y + 0.15f + bob;
        switch (item.type) {
            case LootItem.TYPE_WEAPON:
                mesh = weaponMeshFor(item.subId);
                mat = item.subId == Weapon.ID_AWM ? TextureForge.MAT_GUN : TextureForge.MAT_GUN;
                break;
            case LootItem.TYPE_MEDKIT: mesh = ModelGenerator.firstAidMesh; mat = TextureForge.MAT_WHITE; break;
            case LootItem.TYPE_ENERGY_DRINK: mesh = ModelGenerator.energyDrinkMesh; mat = TextureForge.MAT_METAL; break;
            case LootItem.TYPE_HELMET: mesh = ModelGenerator.helmet3Mesh; mat = TextureForge.MAT_KEVLAR; break;
            case LootItem.TYPE_VEST: mesh = ModelGenerator.vest3Mesh; mat = TextureForge.MAT_KEVLAR; break;
            case LootItem.TYPE_BACKPACK: mesh = ModelGenerator.backpack3Mesh; mat = TextureForge.MAT_CAMO; break;
            case LootItem.TYPE_PAN: mesh = ModelGenerator.panMesh; mat = TextureForge.MAT_METAL; break;
            default: mesh = ModelGenerator.boxMesh; mat = TextureForge.MAT_CRATE; break;
        }
        float rot = (item.id * 47) % 360;
        float s = item.type == LootItem.TYPE_WEAPON ? 1f : 0.9f;
        composeModel(item.x, ly, item.z, rot, 0, 0, s, s, s);
        bindMaterial(mat, 0);
        if (item.type == LootItem.TYPE_MEDKIT) GLES20.glUniform4f(uColor, 1f, 0.95f, 0.95f, 1f);
        else GLES20.glUniform4f(uColor, 1f, 1f, 1f, 1f);
        applyMVP();
        mesh.draw(0, 1, 2);
    }

    private Mesh weaponMeshFor(int weaponId) {
        switch (weaponId) {
            case Weapon.ID_M4: return ModelGenerator.m416Mesh;
            case Weapon.ID_AKR: return ModelGenerator.akmMesh;
            case Weapon.ID_AWM: return ModelGenerator.awmMesh;
            case Weapon.ID_DEAGLE: return ModelGenerator.deagleMesh;
            case Weapon.ID_MP5: return ModelGenerator.mp5Mesh;
            case Weapon.ID_SHOTGUN: return ModelGenerator.shotgunMesh;
            case Weapon.ID_RPG: return ModelGenerator.rpgMesh;
            case Weapon.ID_KNIFE: return ModelGenerator.knifeMesh;
            case Weapon.ID_GRENADE: return ModelGenerator.grenadeMesh;
            default: return ModelGenerator.m416Mesh;
        }
    }

    // =========================================================================
    // Солдат по частям по стойке и фазе шага
    // =========================================================================
    private void renderSoldier(float x, float y, float z, float bodyYaw, float aimPitch, int stance,
                               float walkPhase, float moveBlend, Weapon wep,
                               int helmetLvl, int vestLvl, int bpLvl,
                               float tintR, float tintG, float tintB,
                               boolean parachuting, boolean isPlayer, boolean crouchBlend) {
        float hf = crouchBlend ? 0.72f : 1f;      // высота-масштаб по стойке
        if (stance == CharacterMotor.STANCE_PRONE) hf = 0.34f;

        float swing = (float) Math.sin(walkPhase) * (0.35f + moveBlend * 0.75f);
        boolean armed = wep != null;

        float legOff = 0.115f;
        float hipY = y + 0.84f * hf;
        if (stance != CharacterMotor.STANCE_PRONE) {
            // ЛЕВАЯ нога
            pushLeg(x - (float) Math.cos(bodyYaw * Math3D.TO_RAD) * legOff, hipY,
                    z + (float) Math.sin(bodyYaw * Math3D.TO_RAD) * legOff, bodyYaw, swing * (armed ? 0.45f : 1f), tintR, tintG, tintB);
            // ПРАВАЯ нога
            pushLeg(x + (float) Math.cos(bodyYaw * Math3D.TO_RAD) * legOff, hipY,
                    z - (float) Math.sin(bodyYaw * Math3D.TO_RAD) * legOff, bodyYaw, -swing * (armed ? 0.45f : 1f), tintR, tintG, tintB);
        }

        // Торс
        float torsoY = y + (stance == CharacterMotor.STANCE_PRONE ? 0.28f : 1.20f * hf);
        if (stance == CharacterMotor.STANCE_PRONE) {
            composeModel(x, torsoY, z, bodyYaw, 80f, 0, 1f, 1f, 1f);
            bindMaterial(TextureForge.MAT_CAMO, 0);
            GLES20.glUniform4f(uColor, tintR, tintG, tintB, 1f);
            applyMVP();
            ModelGenerator.soldierTorsoMesh.draw(0, 1, 2);
        } else {
            composeModel(x, torsoY, z, bodyYaw, armed && playerLeanAim(aimPitch) ? -6f * hf : 0f, 0, 1f, hf, 1f);
            bindMaterial(TextureForge.MAT_CAMO, 0);
            GLES20.glUniform4f(uColor, tintR, tintG, tintB, 1f);
            applyMVP();
            ModelGenerator.soldierTorsoMesh.draw(0, 1, 2);

            if (vestLvl > 0) {
                composeModel(x, torsoY + 0.03f, z, bodyYaw, 0, 0, 1f, hf, 1f);
                bindMaterial(TextureForge.MAT_KEVLAR, 0);
                GLES20.glUniform4f(uColor, vestLvl >= 3 ? 0.5f : 1f, vestLvl >= 3 ? 0.5f : 1f, vestLvl >= 3 ? 0.55f : 1f, 1f);
                applyMVP();
                ModelGenerator.vest3Mesh.draw(0, 1, 2);
            }
            if (bpLvl > 0) {
                composeModel(x, torsoY, z, bodyYaw, 0, 0, 0.7f + bpLvl * 0.15f, 0.7f + bpLvl * 0.12f, 0.7f + bpLvl * 0.15f);
                bindMaterial(TextureForge.MAT_CAMO, 0);
                GLES20.glUniform4f(uColor, tintR * 0.65f, tintG * 0.65f, tintB * 0.65f, 1f);
                applyMVP();
                ModelGenerator.backpack3Mesh.draw(0, 1, 2);
            }
        }

        // Руки (pivot плечо) с махом; с оружием — обе вперёд
        float shY = y + 1.47f * hf;
        float armL, armR;
        if (armed && !parachuting) {
            armL = 1.35f; armR = 1.45f; // подняты к оружию
        } else if (parachuting) {
            armL = 2.3f; armR = 2.3f;   // руки вверх за стропы
        } else {
            armL = -swing * 0.8f; armR = swing * 0.8f;
        }
        pushArm(x - (float) Math.cos(bodyYaw * Math3D.TO_RAD) * 0.31f, shY,
                z + (float) Math.sin(bodyYaw * Math3D.TO_RAD) * 0.31f, bodyYaw, armL, tintR, tintG, tintB);
        pushArm(x + (float) Math.cos(bodyYaw * Math3D.TO_RAD) * 0.31f, shY,
                z - (float) Math.sin(bodyYaw * Math3D.TO_RAD) * 0.31f, bodyYaw, armR, tintR, tintG, tintB);

        // Голова + шлем
        float headY = y + (stance == CharacterMotor.STANCE_PRONE ? 0.42f : 1.72f * hf);
        float headZ = z + (stance == CharacterMotor.STANCE_PRONE ? 0.55f : 0f);
        composeModel(x, headY, headZ, bodyYaw, aimPitch * 0.5f, 0, 1, 1, 1);
        bindMaterial(TextureForge.MAT_SKIN, 0);
        GLES20.glUniform4f(uColor, 1f, 1f, 1f, 1f);
        applyMVP();
        ModelGenerator.soldierHeadMesh.draw(0, 1, 2);
        if (helmetLvl > 0) {
            composeModel(x, headY + 0.08f, headZ, bodyYaw, aimPitch * 0.5f, 0, 1, 1, 1);
            bindMaterial(TextureForge.MAT_KEVLAR, 0);
            GLES20.glUniform4f(uColor, helmetLvl >= 3 ? 0.55f : 0.85f, helmetLvl >= 3 ? 0.58f : 0.85f, helmetLvl >= 3 ? 0.6f : 0.88f, 1f);
            applyMVP();
            ModelGenerator.helmet3Mesh.draw(0, 1, 2);
        }

        // Оружие в руках
        if (armed) {
            float fwd = 0.55f;
            float wz = z + (float) Math.sin(bodyYaw * Math3D.TO_RAD) * fwd;
            float wx = x + (float) Math.cos(bodyYaw * Math3D.TO_RAD) * fwd;
            composeModel(wx, torsoY + 0.1f, wz, bodyYaw, aimPitch, 0, 1, 1, 1);
            bindMaterial(wep.getMaterial(), 0);
            GLES20.glUniform4f(uColor, 1f, 1f, 1f, 1f);
            applyMVP();
            wep.getMesh().draw(0, 1, 2);
        }
    }

    private boolean playerLeanAim(float aimPitch) {
        return aimPitch > -18f;
    }

    private void pushLeg(float x, float y, float z, float bodyYaw, float swingRad, float tr, float tg, float tb) {
        // Пивот в бедре: сначала translate, rotateZ — маятник в сагиттальной плоскости вокруг боковой оси
        composeModel(x, y, z, bodyYaw, (float) (swingRad * 57.3f), 0, 1, 1, 1);
        bindMaterial(TextureForge.MAT_CAMO, 0);
        GLES20.glUniform4f(uColor, tr * 0.92f, tg * 0.92f, tb * 0.92f, 1f);
        applyMVP();
        ModelGenerator.soldierLegPivotMesh.draw(0, 1, 2);
    }

    private void pushArm(float x, float y, float z, float bodyYaw, float swingRad, float tr, float tg, float tb) {
        composeModel(x, y, z, bodyYaw, (float) (swingRad * 57.3f), 0, 1, 1, 1);
        bindMaterial(TextureForge.MAT_CAMO, 0);
        GLES20.glUniform4f(uColor, tr * 0.96f, tg * 0.96f, tb * 0.96f, 1f);
        applyMVP();
        ModelGenerator.soldierArmPivotMesh.draw(0, 1, 2);
    }

    // =========================================================================
    // Транспорт
    // =========================================================================
    private void renderVehicle(Vehicle3D v) {
        if (v.destroyed) {
            // сгоревший остов
            composeModel(v.pos.x, v.pos.y + 0.6f, v.pos.z, v.yaw, 0, 0, 1f, 0.85f, 1f);
            bindMaterial(TextureForge.MAT_METAL, 0);
            GLES20.glUniform4f(uColor, 0.22f, 0.2f, 0.2f, 1f);
            applyMVP();
            ModelGenerator.boxMesh.draw(0, 1, 2);
            return;
        }
        Mesh body = (v.type == Vehicle3D.TYPE_BUGGY) ? ModelGenerator.buggyBodyMesh : ModelGenerator.uazBodyMesh;
        composeModel(v.pos.x, v.pos.y, v.pos.z, v.yaw, 0, 0, 1, 1, 1);
        bindMaterial(TextureForge.MAT_VEHICLE, 0);
        GLES20.glUniform4f(uColor, v.type == Vehicle3D.TYPE_BUGGY ? 1.15f : 1f, 1.1f, 1f, 1f);
        applyMVP();
        body.draw(0, 1, 2);

        // 4 колеса: передние довёрнуты на steering
        float rad = v.yaw * Math3D.TO_RAD;
        float fx = (float) Math.sin(rad), fz = (float) Math.cos(rad);
        float rx = (float) Math.cos(rad), rz = -(float) Math.sin(rad);
        float wOffX = rx * 1.05f, wOffZ = rz * 1.05f;
        float lOffX = fx * (v.type == Vehicle3D.TYPE_BUGGY ? 1.15f : 1.4f), lOffZ = fz * (v.type == Vehicle3D.TYPE_BUGGY ? 1.15f : 1.4f);
        float steerYaw = v.steering * 0.6f;
        for (int s = 0; s < 4; s++) {
            float lx = ((s < 2) ? 1f : -1f);
            float fz2 = ((s & 1) == 0 ? 1f : -1f);
            float wx = v.pos.x + lOffX * lx + wOffX * fz2;
            float wz = v.pos.z + lOffZ * lx + wOffZ * fz2;
            composeModel(wx, v.pos.y + 0.42f, wz, v.yaw + (lx > 0 ? steerYaw : 0), 0, 0, 1, 1, 1);
            bindMaterial(TextureForge.MAT_KEVLAR, 0);
            GLES20.glUniform4f(uColor, 0.1f, 0.1f, 0.1f, 1f);
            applyMVP();
            ModelGenerator.wheelMesh.draw(0, 1, 2);
        }
    }

    // =========================================================================
    // Blob shadow игрока под террейном
    // =========================================================================
    private void drawBlobShadow(PUBGPlayer player, PUBGMap map, float ex, float ez) {
        if (player.isDead || player.moveMode == PUBGPlayer.MODE_IN_PLANE) return;
        float gy = map.getTerrainHeight(player.pos.x, player.pos.z) + 0.04f;
        float s = player.moveMode == PUBGPlayer.MODE_DRIVING ? 3.2f : 1.4f;
        float alpha = player.moveMode == PUBGPlayer.MODE_DRIVING ? 0.3f : 0.38f;
        if (player.moveMode == PUBGPlayer.MODE_FREEFALL && player.altitude > 6f) return;
        composeModel(player.pos.x, gy, player.pos.z, 0, 0, 0, s, 1, s);
        GLES20.glUniform1f(uMode, 2f);
        GLES20.glUniform4f(uColor, 0f, 0f, 0f, alpha);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TextureForge.texShadowBlob);
        GLES20.glUniform1i(uTexA, 0);
        GLES20.glUniform1f(uWorldUV, 0f);
        GLES20.glUniform2f(uUVScale, 1f, 1f);
        applyMVP();
        ModelGenerator.shadowQuadMesh.draw(0, 1, 2);
        GLES20.glUniform1f(uMode, 0f);
    }

    private void drawShadowQuad() {
        GLES20.glUniform1f(uMode, 2f);
        GLES20.glUniform4f(uColor, 0f, 0f, 0f, 0.30f);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TextureForge.texShadowBlob);
        GLES20.glUniform1i(uTexA, 0);
        GLES20.glUniform1f(uWorldUV, 0f);
        GLES20.glUniform2f(uUVScale, 1f, 1f);
        applyMVP();
        ModelGenerator.shadowQuadMesh.draw(0, 1, 2);
        GLES20.glUniform1f(uMode, 0f);
    }

    // =========================================================================
    // Viewmodel: оружие от первого лица / прицеливание
    // =========================================================================
    private void drawViewModel(PUBGPlayer player) {
        if (player.isDead || player.moveMode != PUBGPlayer.MODE_ON_FOOT) return;
        Weapon wep = player.getActiveWeapon();
        if (wep == null) return;
        boolean fpp = !player.isTPP;
        boolean adsView = player.adsFactor > 0.25f && getZoom(player) <= 3f;
        if (!fpp && !adsView) return; // в TPP с высокой оптикой — только сетка прицела на HUD

        // Покачивание от резкости поворота камеры
        float dYaw = player.camera.yaw - prevCamYaw;
        float dPitch = player.camera.pitch - prevCamPitch;
        prevCamYaw = player.camera.yaw; prevCamPitch = player.camera.pitch;
        swayX = Math3D.lerp(swayX, Math3D.clamp(-dYaw * 0.020f, -0.05f, 0.05f), 0.25f);
        swayY = Math3D.lerp(swayY, Math3D.clamp(-dPitch * 0.020f, -0.05f, 0.05f), 0.25f);
        float bobX = (float) Math.sin(player.motor.walkPhase * 2f) * 0.012f * player.motor.walkBlend;
        float bobY = -Math.abs((float) Math.sin(player.motor.walkPhase)) * 0.014f * player.motor.walkBlend;

        Math3D.Vec3 e = player.camera.eye;
        Math3D.Vec3 f = player.camera.forward;
        Math3D.Vec3 r = player.camera.right;

        float spread = fpp ? 0.13f : 0.16f;
        float drop = fpp ? -0.14f : -0.12f;
        // ADS: сводим оружие к центру экрана
        spread = Math3D.lerp(spread, 0.005f, player.adsFactor);
        drop = Math3D.lerp(drop, -0.045f, player.adsFactor);

        float dist = 0.52f;
        float px = e.x + f.x * dist + r.x * (spread + swayX + bobX);
        float py = e.y + f.y * dist + drop + swayY + bobY;
        float pz = e.z + f.z * dist + r.z * (spread + swayX + bobX);

        // Знак: ось +Z модели совпадает с forward камеры при pitchDeg = -pitch
        float effPitch = -(player.camera.pitch + player.recoilPitch * 0.65f);

        composeModel(px, py, pz, player.camera.yaw, effPitch, 0, 0.9f, 0.9f, 0.9f);
        bindMaterial(wep.getMaterial(), 0);
        GLES20.glUniform4f(uColor, 1f, 1f, 1f, 1f);
        applyMVP();
        wep.getMesh().draw(0, 1, 2);
    }

    // =========================================================================
    // FX прогон: трейсеры (линии) + частицы (points) + glow лута
    // =========================================================================
    private void renderFX(PUBGPlayer player, PUBGMap map, ParticleSystem particles) {
        fxCount = 0;

        for (ParticleSystem.Tracer t : particles.tracers) {
            if (fxCount + 2 > FX_MAX_VERTS) break;
            float a = Math3D.clamp(1f - t.life / t.maxLife, 0f, 1f);
            pushVert(t.x0, t.y0, t.z0, 1.0f, 0.72f, 0.30f, a, 0f);
            pushVert(t.x1, t.y1, t.z1, 1.0f, 0.85f, 0.5f, a * 0.7f, 0f);
        }

        // Частицы
        for (int i = 0; i < particles.particles.size(); i++) {
            if (fxCount + 1 > FX_MAX_VERTS) break;
            ParticleSystem.Particle p = particles.particles.get(i);
            pushVert(p.x, p.y, p.z, p.r, p.g, p.b, p.a, p.size);
        }

        // Снаряды (гранаты/RPG)
        for (int i = 0; i < particles.projectiles.size(); i++) {
            if (fxCount + 1 > FX_MAX_VERTS) break;
            ParticleSystem.Projectile pr = particles.projectiles.get(i);
            pushVert(pr.x, pr.y, pr.z, 1.0f, 0.6f, 0.1f, 0.9f, 0.35f);
        }

        // Свечение лута рядом
        float ex = player.camera.eye.x, ez = player.camera.eye.z;
        for (LootItem item : map.loot) {
            if (item.isTaken || fxCount + 1 > FX_MAX_VERTS) continue;
            float dx = item.x - ex, dz = item.z - ez;
            float d2 = dx * dx + dz * dz;
            if (d2 > 45f * 45f) continue;
            float a = 0.55f * Math3D.clamp(1f - (float) Math.sqrt(d2) / 45f, 0f, 1f);
            pushVert(item.x, item.y + 0.3f, item.z, 1.0f, 0.85f, 0.4f, a, 0.3f);
        }
        if (map.airdropActive) {
            pushVert(map.airdropPos.x, map.airdropPos.y + 1.2f, map.airdropPos.z, 1f, 0.3f, 0.1f, 0.7f, 0.9f);
        }
        // Вспышка выстрела — яркая точка
        if (particles.muzzleFlashIntensity > 0.05f) {
            pushVert(particles.muzzleFlashPos.x, particles.muzzleFlashPos.y, particles.muzzleFlashPos.z,
                    1f, 0.9f, 0.5f, Math3D.clamp(particles.muzzleFlashIntensity, 0f, 1f), 0.55f);
        }

        if (fxCount == 0) return;

        fxBuf.position(0);
        fxBuf.put(fxData, 0, fxCount * 8);
        fxBuf.position(0);

        GLES20.glUseProgram(progFX);
        GLES20.glUniformMatrix4fv(fUMVP, 1, false, vpM, 0);
        GLES20.glUniform1f(fUPointScale, height * 0.9f);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE); // additive
        GLES20.glDepthMask(false);

        int stride = 8 * 4;
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, stride, fxBuf);
        GLES20.glEnableVertexAttribArray(0);
        fxBuf.position(3);
        GLES20.glVertexAttribPointer(1, 4, GLES20.GL_FLOAT, false, stride, fxBuf);
        GLES20.glEnableVertexAttribArray(1);
        fxBuf.position(7);
        GLES20.glVertexAttribPointer(2, 1, GLES20.GL_FLOAT, false, stride, fxBuf);
        GLES20.glEnableVertexAttribArray(2);
        fxBuf.position(0);

        // Трейсеры — первые (2 вершины на трейсер)
        int tracerCount = Math.min(particles.tracers.size() * 2, fxCount);
        if (tracerCount > 0) {
            GLES20.glUniform1f(fUSprite, 0f);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, tracerCount);
        }
        // Точки — остальные
        int pointsCount = fxCount - tracerCount;
        if (pointsCount > 0) {
            GLES20.glUniform1f(fUSprite, 1f);
            GLES20.glDrawArrays(GLES20.GL_POINTS, tracerCount, pointsCount);
        }

        GLES20.glDisableVertexAttribArray(0);
        GLES20.glDisableVertexAttribArray(1);
        GLES20.glDisableVertexAttribArray(2);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(prog3D);
    }

    private void pushVert(float x, float y, float z, float r, float g, float b, float a, float size) {
        int o = fxCount * 8;
        fxData[o] = x; fxData[o + 1] = y; fxData[o + 2] = z;
        fxData[o + 3] = r; fxData[o + 4] = g; fxData[o + 5] = b; fxData[o + 6] = a;
        fxData[o + 7] = size;
        fxCount++;
    }

    // =========================================================================
    // Матрицы и привязка материалов
    // =========================================================================
    private float[] buildVPOnly() {
        // MVP = VP * Model: uMVP обновляется в applyMVP(); здесь отдаём VP как заглушку
        return vpM;
    }

    private void applyMVP() {
        mat4mul(vpM, modelM, mvpM);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpM, 0);
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelM, 0);
    }

    /** model = T(x,y,z) * Ry(yaw) * Rx(pitch) * S */
    private void composeModel(float x, float y, float z, float yawDeg, float pitchDeg, float rollDeg,
                              float sx, float sy, float sz) {
        float cy = (float) Math.cos(yawDeg * Math3D.TO_RAD), sy2 = (float) Math.sin(yawDeg * Math3D.TO_RAD);
        float cp = (float) Math.cos(pitchDeg * Math3D.TO_RAD), sp = (float) Math.sin(pitchDeg * Math3D.TO_RAD);
        // R = Ry * Rx (строки):
        float m0 = cy, m1 = 0f, m2 = -sy2;
        float m4 = sy2 * sp, m5 = cp, m6 = cy * sp;
        float m8 = sy2 * cp, m9 = -sp, m10 = cy * cp;
        modelM[0] = m0 * sx; modelM[1] = m1 * sx; modelM[2] = m2 * sx; modelM[3] = 0f;
        modelM[4] = m4 * sy; modelM[5] = m5 * sy; modelM[6] = m6 * sy; modelM[7] = 0f;
        modelM[8] = m8 * sz; modelM[9] = m9 * sz; modelM[10] = m10 * sz; modelM[11] = 0f;
        modelM[12] = x; modelM[13] = y; modelM[14] = z; modelM[15] = 1f;
    }

    private void composeModelFromVecs(float x, float y, float z, float yawDeg, float pitchDeg, float rollDeg,
                                      float sx, float sy, float sz) {
        composeModel(x, y, z, yawDeg, pitchDeg, rollDeg, sx, sy, sz);
    }

    /** 4x4 column-major multiply out = a*b (без аллокаций) */
    private static void mat4mul(float[] a, float[] b, float[] out) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                out[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1]
                        + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
            }
        }
    }

    /**
     * @param uvMode 1 = planar world UV по метрам, 0 = объектные UV (персонажи/оружие)
     */
    private void bindMaterial(int matId, int uvMode) {
        TextureForge.Mat m = TextureForge.mat(matId);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m.albedoTex);
        GLES20.glUniform1i(uTexA, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m.normalTex);
        GLES20.glUniform1i(uTexN, 1);
        GLES20.glUniform1f(uUseNormal, 1f);
        GLES20.glUniform1f(uBump, m.bump);
        GLES20.glUniform1f(uRough, m.rough);
        GLES20.glUniform1f(uMetal, m.metal);
        GLES20.glUniform1f(uWorldUV, uvMode == 1 ? 1f : 0f);
        GLES20.glUniform1f(uInvTile, 1f / m.tile);
        GLES20.glUniform2f(uUVScale, uvMode == 1 ? 1f : m.uvMul, uvMode == 1 ? 1f : m.uvMul);
        GLES20.glUniform1f(uMode, 0f);
    }

    /** Мировые меши (террейн/дороги) — только альбедо, planar UV */
    private void drawWorldMesh(Mesh m, int matId) {
        if (m == null) return;
        composeModel(0, 0, 0, 0, 0, 0, 1, 1, 1);
        bindMaterial(matId, 1);
        GLES20.glUniform4f(uColor, 1f, 1f, 1f, 1f);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        applyMVP();
        m.draw(0, 1, 2);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
    }
}
