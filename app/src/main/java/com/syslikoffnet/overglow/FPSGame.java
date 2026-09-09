package com.syslikoffnet.overglow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.ArrayList;

/**
 * Главный игровой движок:
 * - Режимы: Team Deathmatch (TDM 5v5), Battle Royale (Королевская битва PUBG), Закладка бомбы
 * - Меню, Инвентарь скинов, Настройки, Подсчет статистики и хедшотов
 */
public final class FPSGame {

    // Состояния игры
    public static final int STATE_MENU = 0;
    public static final int STATE_PLAYING = 1;
    public static final int STATE_PAUSE = 2;
    public static final int STATE_END = 3;
    public static final int STATE_INVENTORY = 4;
    public static final int STATE_SETTINGS = 5;

    // Режимы игры
    public static final int MODE_TDM = 0;
    public static final int MODE_BATTLE_ROYALE = 1;
    public static final int MODE_DEFUSE = 2;
    public static final int MODE_TRAINING = 3;

    public final Context context;
    public int state = STATE_MENU;
    public int gameMode = MODE_TDM;
    public int selectedMapId = Map3D.MAP_SANDSTONE;

    public Player player = new Player();
    public Map3D map = new Map3D(Map3D.MAP_SANDSTONE);
    public final ArrayList<BotAI> bots = new ArrayList<>();
    public final ParticleSystem particles = new ParticleSystem();
    public final TouchControls touch = new TouchControls();
    public final HUD2D hud = new HUD2D();

    // Счет матча
    public int scoreT = 0;
    public int scoreCT = 0;
    public float matchTimer = 300f; // 5 минут на раунд
    public String matchResultText = "";

    // Отрисовка 2D меню
    private final Paint pMenu = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF btnRect = new RectF();

    public FPSGame(Context ctx) {
        this.context = ctx;
        SoundSynth3D.init(ctx);
    }

    public void startMatch(int mode, int mapId) {
        this.gameMode = mode;
        this.selectedMapId = mapId;
        this.map = new Map3D(mapId);
        this.bots.clear();
        this.particles.particles.clear();
        this.particles.tracers.clear();
        this.particles.projectiles.clear();

        scoreT = 0;
        scoreCT = 0;
        matchTimer = (mode == MODE_BATTLE_ROYALE) ? 420f : 300f;

        // Спавн игрока
        Math3D.Vec3 pSpawn = (player.team == 0) ? map.spawnsT.get(0) : map.spawnsCT.get(0);
        player.spawn(pSpawn.x, pSpawn.y, pSpawn.z, (player.team == 0) ? 0 : 180);

        // Спавн ботов
        int botCount = (mode == MODE_BATTLE_ROYALE) ? 12 : 9; // 5v5 в TDM (4 бота в нашей команде + 5 во вражеской)
        for (int i = 0; i < botCount; i++) {
            int team = (mode == MODE_BATTLE_ROYALE) ? (i + 2) : (i < 4 ? player.team : (1 - player.team));
            String name = "Bot_" + (i + 1);
            int wepId = (i % 5 == 0) ? Weapon.ID_AWM : ((i % 2 == 0) ? Weapon.ID_AKR : Weapon.ID_M4);

            BotAI bot = new BotAI(i, name, team, wepId);
            Math3D.Vec3 bSpawn;
            if (team == 0 && !map.spawnsT.isEmpty()) {
                bSpawn = map.spawnsT.get((i % map.spawnsT.size()));
            } else if (team == 1 && !map.spawnsCT.isEmpty()) {
                bSpawn = map.spawnsCT.get((i % map.spawnsCT.size()));
            } else {
                bSpawn = new Math3D.Vec3((float) ((Math.random() - 0.5) * 60), 0, (float) ((Math.random() - 0.5) * 60));
            }
            bot.spawn(bSpawn.x, bSpawn.y, bSpawn.z, (float) (Math.random() * 360));
            bots.add(bot);
        }

        state = STATE_PLAYING;
    }

    public void update(float dt) {
        if (state != STATE_PLAYING) return;

        matchTimer -= dt;
        map.updateZone(dt);
        hud.update(dt);

        // Обновление управления и игрока
        touch.update(player, dt);
        player.update(dt, map, touch.moveX, touch.moveZ);

        // Стрельба игрока
        if (touch.isShooting()) {
            handlePlayerShooting();
        }

        // Обновление ботов
        for (BotAI bot : bots) {
            bot.update(dt, map, player, bots, particles);
            // Респавн бота в TDM
            if (bot.isDead && bot.respawnTimer <= 0 && gameMode == MODE_TDM) {
                Math3D.Vec3 s = (bot.team == 0) ? map.spawnsT.get(0) : map.spawnsCT.get(0);
                bot.spawn(s.x, s.y, s.z, 0);
            }
        }

        // Респавн игрока в TDM
        if (player.isDead && gameMode == MODE_TDM) {
            Math3D.Vec3 s = (player.team == 0) ? map.spawnsT.get(0) : map.spawnsCT.get(0);
            player.spawn(s.x, s.y, s.z, (player.team == 0) ? 0 : 180);
        }

        // Обновление частиц
        particles.update(dt, map);

        // Проверка условий победы
        checkMatchEnd();
    }

    private void handlePlayerShooting() {
        Weapon wep = player.getActiveWeapon();
        if (wep == null || !wep.canFire()) return;

        wep.fireTimer = wep.fireInterval;
        if (!wep.isMelee) wep.ammoInMag--;

        // Отдача оружия
        player.recoilPitchOffset += wep.recoilPitch * (player.isAiming ? 0.4f : 0.8f);
        player.recoilYawOffset += (float) ((Math.random() - 0.5) * wep.recoilYaw);

        // Звук выстрела
        int soundId = SoundSynth3D.SOUND_AKR;
        if (wep.id == Weapon.ID_M4) soundId = SoundSynth3D.SOUND_M4;
        else if (wep.id == Weapon.ID_AWM) soundId = SoundSynth3D.SOUND_AWM;
        else if (wep.id == Weapon.ID_DEAGLE) soundId = SoundSynth3D.SOUND_DEAGLE;
        else if (wep.id == Weapon.ID_SHOTGUN) soundId = SoundSynth3D.SOUND_SHOTGUN;
        else if (wep.id == Weapon.ID_RPG) soundId = SoundSynth3D.SOUND_RPG;
        else if (wep.id == Weapon.ID_KNIFE) soundId = SoundSynth3D.SOUND_KNIFE;

        SoundSynth3D.play2D(soundId, 1.0f);

        // Направление выстрела из камеры
        float radYaw = (player.yaw + player.recoilYawOffset) * Math3D.TO_RAD;
        float radPitch = (player.pitch + player.recoilPitchOffset) * Math3D.TO_RAD;

        float dirX = -(float) Math.sin(radYaw) * (float) Math.cos(radPitch);
        float dirY = (float) Math.sin(radPitch);
        float dirZ = (float) Math.cos(radYaw) * (float) Math.cos(radPitch);

        Math3D.Vec3 rayOrigin = new Math3D.Vec3(player.pos.x, player.pos.y + player.eyeHeight, player.pos.z);
        Math3D.Vec3 rayDir = new Math3D.Vec3(dirX, dirY, dirZ).normalize();

        particles.triggerMuzzleFlash(rayOrigin.x + dirX * 0.8f, rayOrigin.y + dirY * 0.8f, rayOrigin.z + dirZ * 0.8f);

        // Граната или RPG ракета
        if (wep.isProjectile) {
            float spd = (wep.id == Weapon.ID_RPG) ? 45f : 20f;
            particles.projectiles.add(new ParticleSystem.Projectile(
                    wep.id == Weapon.ID_RPG ? 0 : 1,
                    rayOrigin.x + dirX * 0.6f, rayOrigin.y + dirY * 0.6f, rayOrigin.z + dirZ * 0.6f,
                    dirX * spd, dirY * spd + (wep.id == Weapon.ID_GRENADE ? 4f : 0f), dirZ * spd,
                    wep.damage, 8f, 0));
            return;
        }

        // Raycast попадания пуль (Hitscan)
        float maxDist = wep.isMelee ? 2.8f : 250f;
        float closestHit = maxDist;
        BotAI hitBot = null;
        boolean hitHead = false;

        for (BotAI bot : bots) {
            if (bot.isDead || (bot.team == player.team && gameMode != MODE_BATTLE_ROYALE)) continue;

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

        // Проверка препятствий (стены блокируют выстрел)
        for (Map3D.Obstacle obs : map.obstacles) {
            float tObs = obs.box.raycast(rayOrigin, rayDir);
            if (tObs > 0 && tObs < closestHit) {
                closestHit = tObs;
                hitBot = null; // Попадание в стену
            }
        }

        float endX = rayOrigin.x + rayDir.x * closestHit;
        float endY = rayOrigin.y + rayDir.y * closestHit;
        float endZ = rayOrigin.z + rayDir.z * closestHit;

        // Трейсер пули
        particles.addTracer(rayOrigin.x, rayOrigin.y - 0.15f, rayOrigin.z, endX, endY, endZ);

        if (hitBot != null) {
            float finalDmg = hitHead ? (wep.damage * wep.headMultiplier) : wep.damage;
            hitBot.takeDamage(finalDmg, null, player);

            particles.spawnBlood(endX, endY, endZ, hitHead ? 18 : 10);
            player.hitMarkerTimer = 0.22f;
            player.hitMarkerHeadshot = hitHead;

            if (hitHead) SoundSynth3D.play2D(SoundSynth3D.SOUND_HEADSHOT, 1.0f);
            else SoundSynth3D.play2D(SoundSynth3D.SOUND_HIT, 0.6f);

            if (hitBot.isDead) {
                player.kills++;
                player.score += hitHead ? 150 : 100;
                if (player.team == 0) scoreT++;
                else scoreCT++;

                hud.addKill("You", hitBot.name, wep.name, hitHead);
                SoundSynth3D.play2D(SoundSynth3D.SOUND_KILL, 1.0f);
            }
        } else {
            particles.spawnSparks(endX, endY, endZ, 6);
        }
    }

    private void checkMatchEnd() {
        if (gameMode == MODE_TDM) {
            if (scoreT >= 40 || scoreCT >= 40 || matchTimer <= 0) {
                state = STATE_END;
                boolean won = (player.team == 0 && scoreT >= scoreCT) || (player.team == 1 && scoreCT >= scoreT);
                matchResultText = won ? "VICTORY! (ПОБЕДА)" : "DEFEAT (ПОРАЖЕНИЕ)";
                SoundSynth3D.play2D(won ? SoundSynth3D.SOUND_VICTORY : SoundSynth3D.SOUND_DEFEAT, 1.0f);
            }
        } else if (gameMode == MODE_BATTLE_ROYALE) {
            int aliveBots = 0;
            for (BotAI b : bots) if (!b.isDead) aliveBots++;
            if (player.isDead) {
                state = STATE_END;
                matchResultText = "BETTER LUCK NEXT TIME!";
            } else if (aliveBots == 0) {
                state = STATE_END;
                matchResultText = "WINNER WINNER CHICKEN DINNER!";
                SoundSynth3D.play2D(SoundSynth3D.SOUND_VICTORY, 1.0f);
            }
        }
    }

    // =========================================================================
    // Отрисовка Меню и Интерфейса
    // =========================================================================
    public void render2D(Canvas c, int w, int h) {
        touch.resize(w, h);

        if (state == STATE_PLAYING) {
            hud.render(c, w, h, player, touch, map, bots, scoreT, scoreCT, matchTimer);
            if (touch.btnPause) {
                state = STATE_PAUSE;
                touch.btnPause = false;
            }
        } else if (state == STATE_MENU) {
            renderMainMenu(c, w, h);
        } else if (state == STATE_INVENTORY) {
            renderInventoryMenu(c, w, h);
        } else if (state == STATE_SETTINGS) {
            renderSettingsMenu(c, w, h);
        } else if (state == STATE_PAUSE) {
            renderPauseMenu(c, w, h);
        } else if (state == STATE_END) {
            renderEndMenu(c, w, h);
        }
    }

    private void renderMainMenu(Canvas c, int w, int h) {
        c.drawColor(0xDD090E14);

        float cx = w / 2f;

        // Логотип игры
        pMenu.setColor(0xFFFF9800);
        pMenu.setTextSize(54);
        pMenu.setFakeBoldText(true);
        String title = "STANDOFF : OVERGLOW 3D";
        float tw = pMenu.measureText(title);
        c.drawText(title, cx - tw / 2f, h * 0.22f, pMenu);

        pMenu.setColor(0xFF00E5FF);
        pMenu.setTextSize(24);
        String sub = "TACTICAL FPS & BATTLE ROYALE";
        c.drawText(sub, cx - pMenu.measureText(sub) / 2f, h * 0.28f, pMenu);

        // Кнопка ИГРАТЬ (TDM)
        drawMenuButton(c, cx - 180, h * 0.38f, 360, 65, "▶ TEAM DEATHMATCH (5v5)", 0xFFFF9800);

        // Кнопка BATTLE ROYALE
        drawMenuButton(c, cx - 180, h * 0.50f, 360, 65, "🏆 BATTLE ROYALE (PUBG)", 0xFF00E5FF);

        // Кнопка ИНВЕНТАРЬ / СКИНЫ
        drawMenuButton(c, cx - 180, h * 0.62f, 360, 65, "🔫 WEAPONS & SKINS", 0xFF9C27B0);

        // Кнопка НАСТРОЙКИ
        drawMenuButton(c, cx - 180, h * 0.74f, 360, 65, "⚙ SETTINGS", 0xFF607D8B);
    }

    private void renderInventoryMenu(Canvas c, int w, int h) {
        c.drawColor(0xEE090E14);
        float cx = w / 2f;

        pMenu.setColor(0xFFFF9800);
        pMenu.setTextSize(42);
        pMenu.setFakeBoldText(true);
        c.drawText("WEAPON SKINS (ИНВЕНТАРЬ)", cx - 220, 70, pMenu);

        // Скины AKR
        drawMenuButton(c, cx - 260, 140, 520, 60, "AKR-12: " + (player.inventory[0].skin == Weapon.SKIN_GOLD ? "★ GOLD" : "DEFAULT"), 0xFFFFD700);

        // Скины AWM
        drawMenuButton(c, cx - 260, 220, 520, 60, "AWM: " + (player.inventory[0].skin == Weapon.SKIN_DRAGON ? "★ DRAGON" : "DEFAULT"), 0xFFFF3333);

        // Скины Ножа
        drawMenuButton(c, cx - 260, 300, 520, 60, "KARAMBIT: ★ GOLDEN BLADE", 0xFF00E5FF);

        // Назад
        drawMenuButton(c, cx - 140, h - 90, 280, 60, "← BACK TO MENU", 0xFF78909C);
    }

    private void renderSettingsMenu(Canvas c, int w, int h) {
        c.drawColor(0xEE090E14);
        float cx = w / 2f;

        pMenu.setColor(0xFF00E5FF);
        pMenu.setTextSize(42);
        pMenu.setFakeBoldText(true);
        c.drawText("SETTINGS (НАСТРОЙКИ)", cx - 180, 70, pMenu);

        drawMenuButton(c, cx - 240, 150, 480, 60, "SENSITIVITY: " + (int)(touch.sensitivityX * 100), 0xFF00E5FF);
        drawMenuButton(c, cx - 240, 230, 480, 60, "AUDIO: " + (SoundSynth3D.enabled ? "ON" : "OFF"), 0xFF4CAF50);
        drawMenuButton(c, cx - 140, h - 90, 280, 60, "← BACK TO MENU", 0xFF78909C);
    }

    private void renderPauseMenu(Canvas c, int w, int h) {
        c.drawColor(0xCC000000);
        float cx = w / 2f;

        pMenu.setColor(0xFFFFFFFF);
        pMenu.setTextSize(48);
        pMenu.setFakeBoldText(true);
        c.drawText("PAUSE (ПАУЗА)", cx - 140, h * 0.28f, pMenu);

        drawMenuButton(c, cx - 160, h * 0.40f, 320, 65, "▶ RESUME", 0xFF00E676);
        drawMenuButton(c, cx - 160, h * 0.54f, 320, 65, "🚪 QUIT TO MENU", 0xFFFF1744);
    }

    private void renderEndMenu(Canvas c, int w, int h) {
        c.drawColor(0xEE090E14);
        float cx = w / 2f;

        pMenu.setColor(matchResultText.contains("VICTORY") || matchResultText.contains("CHICKEN") ? 0xFF00E676 : 0xFFFF1744);
        pMenu.setTextSize(46);
        pMenu.setFakeBoldText(true);
        c.drawText(matchResultText, cx - pMenu.measureText(matchResultText) / 2f, h * 0.28f, pMenu);

        pMenu.setColor(0xFFFFFFFF);
        pMenu.setTextSize(26);
        c.drawText("KILLS: " + player.kills + "   |   DEATHS: " + player.deaths + "   |   SCORE: " + player.score, cx - 190, h * 0.40f, pMenu);

        drawMenuButton(c, cx - 160, h * 0.54f, 320, 65, "🔄 PLAY AGAIN", 0xFFFF9800);
        drawMenuButton(c, cx - 160, h * 0.68f, 320, 65, "🚪 MAIN MENU", 0xFF78909C);
    }

    private void drawMenuButton(Canvas c, float x, float y, float w, float h, String text, int color) {
        btnRect.set(x, y, x + w, y + h);
        pMenu.setStyle(Paint.Style.FILL);
        pMenu.setColor(0x33FFFFFF);
        c.drawRoundRect(btnRect, 10, 10, pMenu);

        pMenu.setStyle(Paint.Style.STROKE);
        pMenu.setColor(color);
        pMenu.setStrokeWidth(3f);
        c.drawRoundRect(btnRect, 10, 10, pMenu);

        pMenu.setStyle(Paint.Style.FILL);
        pMenu.setColor(0xFFFFFFFF);
        pMenu.setTextSize(22);
        pMenu.setFakeBoldText(true);
        float tw = pMenu.measureText(text);
        c.drawText(text, x + (w - tw) / 2f, y + h * 0.62f, pMenu);
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (state == STATE_PLAYING) {
            return touch.onTouchEvent(event, player);
        }

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            handleMenuClick(x, y);
        }
        return true;
    }

    private void handleMenuClick(float x, float y) {
        int w = touch.screenW;
        int h = touch.screenH;
        float cx = w / 2f;

        if (state == STATE_MENU) {
            if (x > cx - 180 && x < cx + 180) {
                if (y > h * 0.38f && y < h * 0.38f + 65) startMatch(MODE_TDM, Map3D.MAP_SANDSTONE);
                else if (y > h * 0.50f && y < h * 0.50f + 65) startMatch(MODE_BATTLE_ROYALE, Map3D.MAP_BATTLE_ISLAND);
                else if (y > h * 0.62f && y < h * 0.62f + 65) state = STATE_INVENTORY;
                else if (y > h * 0.74f && y < h * 0.74f + 65) state = STATE_SETTINGS;
            }
        } else if (state == STATE_INVENTORY) {
            if (y > 140 && y < 200) player.inventory[0].skin = (player.inventory[0].skin == Weapon.SKIN_DEFAULT ? Weapon.SKIN_GOLD : Weapon.SKIN_DEFAULT);
            else if (y > 220 && y < 280) player.inventory[0].skin = (player.inventory[0].skin == Weapon.SKIN_DEFAULT ? Weapon.SKIN_DRAGON : Weapon.SKIN_DEFAULT);
            else if (y > h - 90 && y < h - 30) state = STATE_MENU;
        } else if (state == STATE_SETTINGS) {
            if (y > 150 && y < 210) touch.sensitivityX = (touch.sensitivityX > 0.4f ? 0.12f : touch.sensitivityX + 0.08f);
            else if (y > 230 && y < 290) SoundSynth3D.enabled = !SoundSynth3D.enabled;
            else if (y > h - 90 && y < h - 30) state = STATE_MENU;
        } else if (state == STATE_PAUSE) {
            if (y > h * 0.40f && y < h * 0.40f + 65) state = STATE_PLAYING;
            else if (y > h * 0.54f && y < h * 0.54f + 65) state = STATE_MENU;
        } else if (state == STATE_END) {
            if (y > h * 0.54f && y < h * 0.54f + 65) startMatch(gameMode, selectedMapId);
            else if (y > h * 0.68f && y < h * 0.68f + 65) state = STATE_MENU;
        }
    }
}
