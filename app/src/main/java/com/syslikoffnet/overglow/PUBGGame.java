package com.syslikoffnet.overglow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.ArrayList;

/**
 * Главный игровой контроллер PUBG Mobile (Battle Royale):
 * - Лобби с 1-в-1 интерфейсом, выбором карт (Erangel, Miramar, Sanhok, Livik) и Royale Pass
 * - Полноценный матч: Остров ожидания -> Полет на самолете -> Прыжок и парашют ->
 *   Сбор лута в домах -> Открытие дверей -> Вождение транспорта -> Сужение зоны -> Победа #1!
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
    public final PUBGTouchHUD touchHUD = new PUBGTouchHUD();
    public final PUBGLobbyUI lobbyUI = new PUBGLobbyUI();

    public float matchingTimer = 0f;
    public float matchTimer = 0f;
    public int aliveCount = 100;

    private final Paint pPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public PUBGGame(Context ctx) {
        this.context = ctx;
        SoundSynth3D.init(ctx);
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

        aliveCount = 100;
        matchTimer = 0;

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
        player.update(dt, map, touchHUD.moveX, touchHUD.moveZ);

        if (player.moveMode == PUBGPlayer.MODE_DRIVING && player.currentVehicle != null) {
            player.currentVehicle.update(dt, map, bots, player,
                    touchHUD.vehicleThrottle, touchHUD.vehicleSteer, touchHUD.vehicleHandbrake);
        }

        if (touchHUD.isShooting() && player.moveMode == PUBGPlayer.MODE_ON_FOOT) {
            handleShooting();
        }

        int alive = 1;
        for (PUBGBot bot : bots) {
            bot.update(dt, map, player, bots, particles);
            if (!bot.isDead) alive++;
        }
        aliveCount = alive;

        particles.update(dt, null);

        if (player.isDead) {
            state = STATE_DEFEAT;
        } else if (aliveCount == 1 && player.moveMode == PUBGPlayer.MODE_ON_FOOT && matchTimer > 20f) {
            state = STATE_VICTORY;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_VICTORY, 1.0f);
        }
    }

    private void handleShooting() {
        Weapon wep = player.getActiveWeapon();
        if (wep == null || !wep.canFire()) return;

        wep.fireTimer = wep.fireInterval;
        wep.ammoInMag--;

        player.recoilPitch += wep.recoilPitch * 0.7f;
        player.recoilYaw += (float) ((Math.random() - 0.5) * wep.recoilYaw);

        int soundId = SoundSynth3D.SOUND_M416;
        if (wep.id == Weapon.ID_AKR) soundId = SoundSynth3D.SOUND_AKM;
        else if (wep.id == Weapon.ID_AWM) soundId = SoundSynth3D.SOUND_AWM;
        else if (wep.id == Weapon.ID_SHOTGUN) soundId = SoundSynth3D.SOUND_SHOTGUN;
        else if (wep.id == Weapon.ID_DEAGLE) soundId = SoundSynth3D.SOUND_DEAGLE;
        else if (wep.id == Weapon.ID_KNIFE) soundId = SoundSynth3D.SOUND_PAN;

        SoundSynth3D.play2D(soundId, 1.0f);

        float radYaw = (player.yaw + player.recoilYaw) * Math3D.TO_RAD;
        float radPitch = (player.pitch + player.recoilPitch) * Math3D.TO_RAD;

        float dirX = -(float) Math.sin(radYaw) * (float) Math.cos(radPitch);
        float dirY = (float) Math.sin(radPitch);
        float dirZ = (float) Math.cos(radYaw) * (float) Math.cos(radPitch);

        Math3D.Vec3 rayOrigin = new Math3D.Vec3(player.pos.x, player.pos.y + 1.6f, player.pos.z);
        Math3D.Vec3 rayDir = new Math3D.Vec3(dirX, dirY, dirZ).normalize();

        particles.triggerMuzzleFlash(rayOrigin.x + dirX * 0.8f, rayOrigin.y + dirY * 0.8f, rayOrigin.z + dirZ * 0.8f);

        float closestHit = 250f;
        PUBGBot hitBot = null;
        boolean hitHead = false;

        for (PUBGBot bot : bots) {
            if (bot.isDead || bot.isParachuting) continue;
            float tHead = bot.getHeadBox().raycast(rayOrigin, rayDir);
            if (tHead > 0 && tHead < closestHit) {
                closestHit = tHead;
                hitBot = bot;
                hitHead = true;
            }
            float tBody = bot.getBodyBox().raycast(rayOrigin, rayDir);
            if (tBody > 0 && tBody < closestHit) {
                closestHit = tBody;
                hitBot = bot;
                hitHead = false;
            }
        }

        float endX = rayOrigin.x + rayDir.x * closestHit;
        float endY = rayOrigin.y + rayDir.y * closestHit;
        float endZ = rayOrigin.z + rayDir.z * closestHit;

        particles.addTracer(rayOrigin.x, rayOrigin.y - 0.2f, rayOrigin.z, endX, endY, endZ);

        if (hitBot != null) {
            float dmg = hitHead ? wep.damage * 3.5f : wep.damage;
            hitBot.takeDamage(dmg, null, player);
            particles.spawnBlood(endX, endY, endZ, hitHead ? 15 : 8);
            if (hitHead) SoundSynth3D.play2D(SoundSynth3D.SOUND_HEADSHOT_HELMET, 1.0f);
            else SoundSynth3D.play2D(SoundSynth3D.SOUND_BODY_HIT, 0.8f);
        } else {
            particles.spawnSparks(endX, endY, endZ, 4);
        }
    }

    public void render2D(Canvas c, int w, int h) {
        touchHUD.resize(w, h);

        if (state == STATE_PLAYING) {
            touchHUD.render(c, w, h, player, map, bots, aliveCount, matchTimer);
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
            int w = touchHUD.screenW;
            int h = touchHUD.screenH;

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
