package com.syslikoffnet.overglow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.ArrayList;

/**
 * Главный игровой контроллер (Фаза 2/3: баллистика + мобильный контроль):
 * - Полная схема управления мобильной BR: стик+обзор+кластеры кнопок (MobileInputController)
 * - BallisticsSystem (физическая баллистика, drag, g=9.81), RecoilController (спрей), HitboxSystem
 * - Одиночный/авто режимы, авто-перезарядка на пустом магазине, осколочные гранаты с уроном по площади
 * - Килл-фид, тик времени зоны, FOV/чувствительность в зависимости от оптики
 */
public final class PUBGGame {

    public static final int STATE_LOBBY = 0;
    public static final int STATE_MATCHING = 1;
    public static final int STATE_PLAYING = 2;
    public static final int STATE_VICTORY = 3;
    public static final int STATE_DEFEAT = 4;

    public final Context context;
    public int state = STATE_LOBBY;

    public PUBGPlayer player = new PUBGPlayer();
    public PUBGMap map = new PUBGMap();
    public final ArrayList<PUBGBot> bots = new ArrayList<>();
    public final ParticleSystem particles = new ParticleSystem();
    public final BallisticsSystem ballistics = new BallisticsSystem();
    public final PUBGTouchHUD touchHUD = new PUBGTouchHUD();
    public final PUBGLobbyUI lobbyUI = new PUBGLobbyUI();
    public final PUBGGyroscope gyroscope = new PUBGGyroscope();

    public float matchingTimer = 0f;
    public float matchTimer = 0f;
    public int aliveCount = 100;

    // Килл-фид: 5 строк с таймером
    public final String[] killFeed = new String[5];
    public final float[] killFeedT = new float[5];
    private int feedHead = 0;

    private final ParticleSystem.ExplosionEvent explosionScratch = new ParticleSystem.ExplosionEvent();

    private final Paint pPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public PUBGGame(Context ctx) {
        this.context = ctx;
        SoundSynth3D.init(ctx);
        gyroscope.init(ctx);
    }

    public void pushFeed(String s) {
        killFeed[feedHead] = s;
        killFeedT[feedHead] = 5f;
        feedHead = (feedHead + 1) % killFeed.length;
    }

    public void startMatchmaking() {
        state = STATE_MATCHING;
        matchingTimer = 3.0f;
    }

    public void launchMatch() {
        state = STATE_PLAYING;
        map = new PUBGMap();
        map.startPlaneFlight();

        player = new PUBGPlayer();
        player.isTPP = lobbyUI.isTPP;
        player.pos.set(map.planeStart);
        player.moveMode = PUBGPlayer.MODE_IN_PLANE;

        bots.clear();
        particles.particles.clear();
        particles.tracers.clear();
        particles.projectiles.clear();

        for (int i = 0; i < BallisticsSystem.POOL_SIZE; i++) {
            ballistics.pool[i].reset();
        }

        for (int i = 0; i < killFeed.length; i++) killFeedT[i] = 0;

        aliveCount = 100;
        matchTimer = 0;

        touchHUD.input.releaseAllForNewMatch();

        for (int i = 0; i < 15; i++) {
            float dropX = (float) ((Math.random() - 0.5) * 200);
            float dropZ = (float) ((Math.random() - 0.5) * 200);
            bots.add(new PUBGBot(i, "Player_" + (100 + i), dropX, dropZ));
        }

        SoundSynth3D.play2D(SoundSynth3D.SOUND_VICTORY, 0.6f);
    }

    public void update(float dt) {
        if (state == STATE_MATCHING) {
            matchingTimer -= dt;
            if (matchingTimer <= 0) {
                launchMatch();
            }
            return;
        }

        if (state != STATE_PLAYING) return;

        matchTimer += dt;
        map.update(dt);

        MobileInputController input = touchHUD.input;

        // --- Настройки управления из лобби ---
        input.camSens = lobbyUI.camSens;
        input.invertY = lobbyUI.invertY;
        if (Float.compare(input.buttonScale, lobbyUI.buttonScale) != 0) {
            input.buttonScale = lobbyUI.buttonScale;
            input.setScreenSize(input.screenWidth, input.screenHeight); // пересчёт раскладки
        }

        // --- Режимы раскладки кнопок ---
        int mode;
        if (player.moveMode == PUBGPlayer.MODE_IN_PLANE) mode = MobileInputController.MODE_PLANE;
        else if (player.moveMode == PUBGPlayer.MODE_FREEFALL || player.moveMode == PUBGPlayer.MODE_PARACHUTE) mode = MobileInputController.MODE_SKYDIVING;
        else if (player.moveMode == PUBGPlayer.MODE_DRIVING) mode = MobileInputController.MODE_DRIVING;
        else mode = MobileInputController.MODE_ONFOOT;
        input.setMode(mode);
        input.showInteract = (player.nearDoor != null || player.nearVehicle != null || player.moveMode == PUBGPlayer.MODE_DRIVING);
        input.showChute = (player.moveMode == PUBGPlayer.MODE_FREEFALL);
        input.inAds = player.isAiming;

        // --- ADS-зависимая чувствительность обзора (как с зумом в оприке) ---
        Weapon wep = player.getActiveWeapon();
        float zoom = wep != null ? wep.getAdsZoom() : 1.3f;
        float adsFov = Math3D.clamp(70f / zoom, 8f, 60f);
        float fovNow = Math3D.lerp(72f, adsFov, player.adsFactor);
        input.adsMul = Math3D.clamp(fovNow / 72f, 0.16f, 1f);

        // --- Гироскоп ---
        gyroscope.sensitivity = lobbyUI.gyroSens;
        gyroscope.mode = lobbyUI.gyroMode;
        gyroscope.applyToPlayer(player);

        // --- Обработка тапов кнопок ---
        handleTaps(input);

        // --- Движение ---
        boolean sprint = input.sprintLocked || (input.moveZ > 0.92f && Math.abs(input.moveX) < 0.3f);
        player.update(dt, map, input.moveX, input.moveZ, sprint);

        // Шаги
        if (player.motor.footstepPending) {
            player.motor.footstepPending = false;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_STEP, player.isSprinting ? 0.5f : 0.3f);
        }

        // --- Вождение ---
        if (player.moveMode == PUBGPlayer.MODE_DRIVING && player.currentVehicle != null) {
            player.currentVehicle.update(dt, map, bots, player,
                    input.vehicleThrottle(), input.vehicleSteer(), input.vehicleHandbrake());
            if (player.currentVehicle.destroyed) {
                player.enterExitVehicle(map);
                particles.spawnExplosion(player.pos.x, player.pos.y + 1f, player.pos.z);
            }
        }

        // --- Стрельба ---
        if (input.isFiring() && player.moveMode == PUBGPlayer.MODE_ON_FOOT && !player.isDead) {
            handleShooting();
        }

        // --- Баллистика ---
        ballistics.update(dt, map, player, bots, particles);

        int alive = 1;
        for (PUBGBot bot : bots) {
            bot.update(dt, map, player, bots, particles, ballistics);
            if (!bot.isDead) alive++;
            else if (!bot.announced) {
                bot.announced = true;
                if (bot.killedByPlayer) {
                    String g = bot.lastHitWeapon != null ? bot.lastHitWeapon.name : "оружия";
                    pushFeed("Вы убили " + bot.name + " (" + g + ")");
                } else {
                    pushFeed(bot.name + " был(а) ликвидирован(а)");
                }
            }
        }

        // --- Взрывы гранат: урон по площади ---
        while (particles.consumeExplosion(explosionScratch)) {
            applyExplosionDamage(explosionScratch);
        }

        particles.update(dt, null);
        aliveCount = alive;
        touchHUD.game_alive = alive;

        // Затухание килл-фида
        for (int i = 0; i < killFeedT.length; i++) {
            if (killFeedT[i] > 0) killFeedT[i] -= dt;
        }

        if (player.isDead) {
            state = STATE_DEFEAT;
        } else if (aliveCount == 1 && matchTimer > 20f) {
            state = STATE_VICTORY;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_VICTORY, 1.0f);
        }
    }

    private void handleTaps(MobileInputController input) {
        PUBGPlayer p = player;
        if (p.isDead) return;

        if (input.consumeTap(MobileInputController.R_ADS)) p.toggleAim();
        if (input.consumeTap(MobileInputController.R_JUMP)) p.pressJump();
        if (input.consumeTap(MobileInputController.R_CROUCH)) p.pressCrouch();
        if (input.consumeTap(MobileInputController.R_PRONE)) p.pressProne();
        if (input.consumeTap(MobileInputController.R_RELOAD)) p.reloadActive();
        if (input.consumeTap(MobileInputController.R_SLOT1)) p.swapSlots(0);
        if (input.consumeTap(MobileInputController.R_SLOT2)) p.swapSlots(1);
        if (input.consumeTap(MobileInputController.R_SLOT3)) p.swapSlots(2);
        if (input.consumeTap(MobileInputController.R_HEAL)) p.useMedkit();
        if (input.consumeTap(MobileInputController.R_BOOST)) p.useDrink();
        if (input.consumeTap(MobileInputController.R_INTERACT)) p.interact(map);
        if (input.consumeTap(MobileInputController.R_EXIT)) p.enterExitVehicle(map);
        if (input.consumeTap(MobileInputController.R_SHOULDER)) p.camera.switchShoulder();
        if (input.consumeTap(MobileInputController.R_FPP)) {
            p.isTPP = !p.isTPP;
            lobbyUI.isTPP = p.isTPP;
        }
        if (input.consumeTap(MobileInputController.R_ZERO)) {
            Weapon w = p.getActiveWeapon();
            if (w != null) w.cycleZeroing();
        }
        if (input.consumeTap(MobileInputController.R_CAMSW) && p.moveMode == PUBGPlayer.MODE_DRIVING) {
            p.camera.defaultArmLength = (p.camera.defaultArmLength > 1f) ? 0.05f : 3.2f;
        }
        if (input.consumeTap(MobileInputController.R_HORN)) {
            SoundSynth3D.play2D(SoundSynth3D.SOUND_HORN, 1f);
        }

        // Пик-наклон: тап = фиксация
        if (input.consumeTap(MobileInputController.R_PEEK_L)) p.setPeek(-1, true);
        if (input.consumeTap(MobileInputController.R_PEEK_R)) p.setPeek(1, true);
        // Удержание кнопки = временный наклон
        float holdLean = 0f;
        if (input.isDown(MobileInputController.R_PEEK_L)) holdLean = -1f;
        else if (input.isDown(MobileInputController.R_PEEK_R)) holdLean = 1f;
        p.leanHold = holdLean;

        // Парашют / прыжок из самолёта
        if (input.consumeTap(MobileInputController.R_CHUTE)) {
            if (p.moveMode == PUBGPlayer.MODE_IN_PLANE) {
                p.jumpFromPlane(map.planePos, 45f);
            } else {
                p.openParachute();
            }
        }

        // Граната
        if (input.consumeTap(MobileInputController.R_THROW)) {
            throwGrenade();
        }
    }

    private void throwGrenade() {
        if (!player.armGrenade()) return;
        float yaw = player.camera.yaw * Math3D.TO_RAD;
        float pitch = (player.camera.pitch + 28f) * Math3D.TO_RAD;
        float sp = 19f;
        float dx = (float) Math.sin(yaw) * (float) Math.cos(pitch);
        float dy = (float) Math.sin(pitch);
        float dz = (float) Math.cos(yaw) * (float) Math.cos(pitch);
        particles.projectiles.add(new ParticleSystem.Projectile(1,
                player.camera.eye.x + dx, player.pos.y + 1.5f, player.camera.eye.z + dz,
                dx * sp, dy * sp + 3.5f, dz * sp, 145f, 8.5f, -1));
        SoundSynth3D.play2D(SoundSynth3D.SOUND_PAN, 0.4f);
    }

    private void applyExplosionDamage(ParticleSystem.ExplosionEvent e) {
        for (PUBGBot bot : bots) {
            if (bot.isDead) continue;
            float d = Math3D.dist(bot.pos.x, bot.pos.y, bot.pos.z, e.x, e.y, e.z);
            if (d < e.radius) {
                float fall = 1f - d / e.radius;
                bot.lastHitByPlayer = (e.ownerId == -1);
                bot.takeDamage(e.damage * fall * fall, e.ownerId == -1 ? player : null, player);
            }
        }
        float dp = Math3D.dist(player.pos.x, player.pos.y, player.pos.z, e.x, e.y, e.z);
        if (dp < e.radius && e.ownerId != -1) {
            float fall = 1f - dp / e.radius;
            player.takeDamage(e.damage * fall * 0.6f, new Math3D.Vec3(e.x, e.y, e.z));
        } else if (dp < e.radius * 0.55f && e.ownerId == -1) {
            // и своя граната может припечатать
            float fall = 1f - dp / (e.radius * 0.55f);
            player.takeDamage(e.damage * fall * 0.35f, new Math3D.Vec3(e.x, e.y, e.z));
        }
    }

    private void handleShooting() {
        Weapon wep = player.getActiveWeapon();
        if (wep == null) return;

        // Авто-перезарядка при пустом магазине и удержании огня
        if (wep.ammoInMag <= 0 && wep.reloadTimer <= 0 && wep.ammoReserve > 0 && !wep.isMelee) {
            wep.startReload();
            return;
        }
        if (!wep.canFire()) return;

        wep.fireTimer = wep.fireInterval;
        if (!wep.isMelee) wep.ammoInMag--;

        // Отдача и спрей
        player.recoil.applyShot(wep, player.motor.currentStance, player.motor.isMoving, player.isAiming);
        player.camera.punch(wep.isMelee ? 0.1f : 0.28f);

        // Звук
        if (wep.isSilenced()) {
            SoundSynth3D.play2D(SoundSynth3D.SOUND_AKM, 0.35f);
        } else {
            int soundId = SoundSynth3D.SOUND_M416;
            if (wep.id == Weapon.ID_AKR) soundId = SoundSynth3D.SOUND_AKM;
            else if (wep.id == Weapon.ID_AWM) soundId = SoundSynth3D.SOUND_AWM;
            else if (wep.id == Weapon.ID_SHOTGUN) soundId = SoundSynth3D.SOUND_SHOTGUN;
            else if (wep.id == Weapon.ID_DEAGLE) soundId = SoundSynth3D.SOUND_DEAGLE;
            else if (wep.id == Weapon.ID_MP5) soundId = SoundSynth3D.SOUND_M416;
            else if (wep.id == Weapon.ID_KNIFE) soundId = SoundSynth3D.SOUND_PAN;
            SoundSynth3D.play2D(soundId, 1.0f);
        }

        // Направление выстрела с учётом отдачи и пика-наклона
        float leanRad = player.leanAngle * 0.35f;
        float currentYaw = player.camera.yaw + player.recoil.currentYawOffset + leanRad;
        float currentPitch = player.camera.pitch + player.recoil.currentPitchOffset;

        float radYaw = currentYaw * Math3D.TO_RAD;
        float radPitch = currentPitch * Math3D.TO_RAD;

        float dirX = (float) Math.sin(radYaw) * (float) Math.cos(radPitch);
        float dirY = (float) Math.sin(radPitch);
        float dirZ = (float) Math.cos(radYaw) * (float) Math.cos(radPitch);

        float muzzleX = player.pos.x + dirX * 0.6f;
        float muzzleY = player.pos.y + (player.isCrouching ? 1.05f : 1.45f);
        float muzzleZ = player.pos.z + dirZ * 0.6f;

        if (!wep.hideFlash()) {
            particles.triggerMuzzleFlash(muzzleX, muzzleY, muzzleZ);
        }

        ballistics.spawnBullet(muzzleX, muzzleY, muzzleZ, dirX, dirY, dirZ, wep, true, player);
    }

    // =========================================================================
    // 2D HUD / экраны
    // =========================================================================
    public void render2D(Canvas c, int w, int h) {
        touchHUD.resize(w, h);

        if (state == STATE_PLAYING) {
            touchHUD.render(c, w, h, this);
        } else if (state == STATE_LOBBY || state == STATE_MATCHING) {
            lobbyUI.render(c, w, h, player, matchingTimer);
        } else if (state == STATE_VICTORY) {
            renderVictory(c, w, h);
        } else if (state == STATE_DEFEAT) {
            renderDefeat(c, w, h);
        }
    }

    private void renderVictory(Canvas c, int w, int h) {
        c.drawColor(0xEE090E14);
        float cx = w / 2f;

        pPaint.setColor(0xFFFFD700);
        pPaint.setTextSize(52);
        pPaint.setFakeBoldText(true);
        String winText = "WINNER WINNER CHICKEN DINNER!";
        c.drawText(winText, cx - pPaint.measureText(winText) / 2f, h * 0.30f, pPaint);

        pPaint.setColor(0xFF00E676);
        pPaint.setTextSize(32);
        String ruText = "#1 / 100 • ПОБЕДА!";
        c.drawText(ruText, cx - pPaint.measureText(ruText) / 2f, h * 0.40f, pPaint);

        pPaint.setColor(0xFFFFFFFF);
        pPaint.setTextSize(24);
        c.drawText("KILLS: " + player.kills + "   |   RATING: +38 RANK PTS", cx - 180, h * 0.52f, pPaint);

        pPaint.setColor(0xFFFFB300);
        rect.set(cx - 150, h * 0.65f, cx + 150, h * 0.65f + 65);
        c.drawRoundRect(rect, 8, 8, pPaint);

        pPaint.setColor(0xFF000000);
        pPaint.setTextSize(28);
        c.drawText("CONTINUE (В ЛОББИ)", cx - 130, h * 0.65f + 42, pPaint);
    }

    private void renderDefeat(Canvas c, int w, int h) {
        c.drawColor(0xEE090E14);
        float cx = w / 2f;

        pPaint.setColor(0xFFFF1744);
        pPaint.setTextSize(48);
        pPaint.setFakeBoldText(true);
        String text = "BETTER LUCK NEXT TIME!";
        c.drawText(text, cx - pPaint.measureText(text) / 2f, h * 0.35f, pPaint);

        pPaint.setColor(0xFFFFFFFF);
        pPaint.setTextSize(24);
        c.drawText("RANK: #" + aliveCount + " / 100   |   KILLS: " + player.kills, cx - 160, h * 0.48f, pPaint);

        pPaint.setColor(0xFFFFB300);
        rect.set(cx - 150, h * 0.62f, cx + 150, h * 0.62f + 65);
        c.drawRoundRect(rect, 8, 8, pPaint);

        pPaint.setColor(0xFF000000);
        pPaint.setTextSize(28);
        c.drawText("RETURN TO LOBBY", cx - 120, h * 0.62f + 42, pPaint);
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (state == STATE_PLAYING) {
            return touchHUD.onTouchEvent(event, player, map);
        }

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            int w = touchHUD.input.screenWidth;
            int h = touchHUD.input.screenHeight;

            if (state == STATE_MATCHING) {
                float cx = w / 2f, cy = h / 2f;
                if (x >= cx - 60 && x <= cx + 60 && y >= cy + 48 && y <= cy + 88) {
                    state = STATE_LOBBY;
                    matchingTimer = 0;
                    return true;
                }
            }

            if (state == STATE_LOBBY) {
                return lobbyUI.handleClick(x, y, w, h, this);
            } else if (state == STATE_VICTORY || state == STATE_DEFEAT) {
                float cx = w / 2f;
                if (x > cx - 150 && x < cx + 150 && y > h * 0.60f && y < h * 0.75f) {
                    state = STATE_LOBBY;
                }
            }
        }
        return true;
    }
}
