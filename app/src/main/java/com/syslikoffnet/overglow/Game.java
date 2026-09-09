package com.syslikoffnet.overglow;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Ядро игры: стейт-машина экранов, связка мир+директор+рендер+звук,
 * мета-прогресс, кнопки. Один активный World на забег.
 */
public final class Game {

    // Состояния
    public static final int ST_TITLE = 0, ST_CHARS = 1, ST_SHOP = 2, ST_SETTINGS = 3,
            ST_STATS = 4, ST_HOWTO = 5, ST_PLAY = 6, ST_LEVELUP = 7, ST_PAUSE = 8,
            ST_DEAD = 9, ST_WIN = 10, ST_ACH = 11;

    // Кнопки
    public static final int B_PLAY = 1, B_CHARS = 2, B_SHOP = 3, B_SETTINGS = 4,
            B_STATS = 5, B_BACK = 6, B_CHAR_0 = 10, B_META_0 = 20, B_T_SOUND = 30,
            B_T_MUSIC = 31, B_T_VIB = 32, B_T_LANG = 33, B_RESET = 34, B_HOWTO = 35,
            B_CARD_0 = 40, B_RESUME = 50, B_RESTART = 51, B_MENU = 52,
            B_ENDLESS = 53, B_ACH = 54;

    public final Context ctx;
    public final Input input = new Input();
    public final World world;
    public final Director director;
    public final Screens screens = new Screens(this);

    public int state = ST_TITLE;
    public int[] meta;                 // уровни мета-усилений
    public int charId = 0;             // текущий герой
    public boolean newBest;
    public int achReward;             // золото за ачивки последнего забега
    public boolean achNewest;

    private float deadTimer;           // задержка перед экраном смерти
    private float titleTime;
    private Vibrator vibrator;

    /** Размер вьюпорта (обновляется в render). */
    public volatile int viewW, viewH;

    /** Дистанция спавна за краем экрана. */
    public float spawnDist() {
        float scale = Render.zoom();
        float half = Math.max(viewW, viewH) / 2f / Math.max(0.01f, scale);
        return half + 220f;
    }

    public Game(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        world = new World(this);
        director = new Director(world, this);
        meta = S.meta(this.ctx);
        charId = S.selectedChar(this.ctx);
        try {
            vibrator = (Vibrator) this.ctx.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable t) {
            vibrator = null;
        }
    }

    // ------------------------------------------------------------- вибрация

    public void vibrate(int ms) {
        if (!S.vibrate(ctx) || vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms,
                        VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------- забег

    public void startRun() {
        Upgrades.reset();
        world.start(charId);
        director.reset();
        world.camX = world.px;
        world.camY = world.py;
        deadTimer = 0;
        newBest = false;
        achReward = 0;
        achNewest = false;
        state = ST_PLAY;
        input.reset();
        Sound.startMusic();
    }

    public void toMenu() {
        state = ST_TITLE;
        input.reset();
        Sound.stopMusic();
    }

    public int charGlowColor() {
        switch (charId) {
            case 1: return 0xFFFFE066;
            case 2: return 0xFFB8C6D9;
            case 3: return 0xFFB388FF;
            default: return Art.PLAYER;
        }
    }

    // ------------------------------------------------------------- апдейт

    public void update(float dt) {
        switch (state) {
            case ST_TITLE:
                titleTime += dt;
                break;
            case ST_PLAY: {
                // Хит-стоп на критах: короткая пауза «удара»
                if (world.hitStop > 0) {
                    world.hitStop -= dt;
                    world.update(0, input);
                    break;
                }
                // Замедление мира (сок-эффекты)
                float scale = 1f;
                if (world.slowmo > 0) {
                    world.slowmo -= dt;
                    scale = 0.35f;
                }
                world.update(dt * scale, input);
                director.update(dt * scale);

                // Левел-ап
                if (world.levelUpReady() && state == ST_PLAY) {
                    world.pvx = 0;
                    world.pvy = 0;
                    screens.cards = Upgrades.roll3(world);
                    screens.cardAnimT = 0;
                    state = ST_LEVELUP;
                    input.reset();
                    world.ring(world.px, world.py, 0.8f, 30, 600, Art.GOLD);
                    world.burst(world.px, world.py, 26, Art.GOLD, 260, 0.7f, 9);
                    Sound.play(Sound.LEVELUP);
                }

                // Смерть → экран после slow-mo
                if (world.dead) {
                    deadTimer += dt;
                    if (deadTimer > 1.6f) finishRun(false);
                }

                // Победа
                if (world.victory && !world.dead) {
                    world.victory = false;
                    finishRun(true);
                }
                break;
            }
            case ST_LEVELUP:
                screens.cardAnimT += dt;
                break;
            case ST_PAUSE:
            case ST_DEAD:
            case ST_WIN:
                // Мир «дышит» (частицы) но не апдейтится
                break;
            default:
                break;
        }
    }

    private void finishRun(boolean win) {
        int bestBefore = S.stats(ctx).optInt("bestTime", 0);
        S.addRun(ctx, (int) world.time, world.kills, world.level, world.goldRun);
        int achGold = checkAchievements(win);
        S.addGold(ctx, world.goldRun + achGold);
        newBest = (int) world.time > bestBefore;
        Sound.stopMusic();
        input.reset();
        state = win ? ST_WIN : ST_DEAD;
    }

    /** Проверить ачивки забега. Возвращает суммарную награду золотом. */
    private int checkAchievements(boolean win) {
        int got = 0;
        if (S.setAchievement(ctx, 0)) got += S.ACH_REWARD[0];
        if (world.kills >= 100 && S.setAchievement(ctx, 1)) got += S.ACH_REWARD[1];
        if (world.kills >= 1000 && S.setAchievement(ctx, 2)) got += S.ACH_REWARD[2];
        if (world.time >= 600 && S.setAchievement(ctx, 3)) got += S.ACH_REWARD[3];
        if (win && S.setAchievement(ctx, 4)) got += S.ACH_REWARD[4];
        if (world.bossesKilled > 0 && S.setAchievement(ctx, 5)) got += S.ACH_REWARD[5];
        if (world.level >= 20 && S.setAchievement(ctx, 6)) got += S.ACH_REWARD[6];
        if (world.elitesKilled >= 5 && S.setAchievement(ctx, 7)) got += S.ACH_REWARD[7];
        if (world.greedKilled > 0 && S.setAchievement(ctx, 8)) got += S.ACH_REWARD[8];
        achReward = got;
        achNewest = got > 0;
        return got;
    }

    // ------------------------------------------------------------- рендер

    public void render(android.graphics.Canvas c, int w, int h) {
        viewW = w;
        viewH = h;
        if (state == ST_TITLE) {
            Art.P_FILL.setStyle(android.graphics.Paint.Style.FILL);
            Art.P_FILL.setColor(Art.BG);
            c.drawRect(0, 0, w, h, Art.P_FILL);
            // Фоновая «аура» логотипа
            android.graphics.RectF dst = new android.graphics.RectF(
                    w / 2f - w * 0.6f, h * 0.02f, w / 2f + w * 0.6f, h * 0.55f);
            c.drawBitmap(Art.glow(0x227DE2FF), null, dst, Art.P_BMP);
            screens.drawTitle(c, w, h, titleTime);
            Art.vignette(c, w, h);
            return;
        }

        // Мир рисуем во всех игровых состояниях
        Render.drawWorld(c, w, h, world, this, input, 1 / 60f);

        switch (state) {
            case ST_CHARS: screens.drawChars(c, w, h); break;
            case ST_SHOP: screens.drawShop(c, w, h); break;
            case ST_SETTINGS: screens.drawSettings(c, w, h); break;
            case ST_STATS: screens.drawStats(c, w, h); break;
            case ST_ACH: screens.drawAch(c, w, h); break;
            case ST_HOWTO: screens.drawHowTo(c, w, h); break;
            case ST_LEVELUP: screens.drawLevelUp(c, w, h, world); break;
            case ST_PAUSE: screens.drawPause(c, w, h, world); break;
            case ST_DEAD: screens.drawDead(c, w, h, world); break;
            case ST_WIN: screens.drawWin(c, w, h, world); break;
            default: break;
        }
    }

    // ------------------------------------------------------------- ввод

    public void onTouch(android.view.MotionEvent e) {
        if (state == ST_PLAY) {
            input.onTouch(e);
            // Короткий тап без движения = пауза
            float[] tap = new float[2];
            if (input.consumeTap(tap)) {
                state = ST_PAUSE;
                input.reset();
            }
            return;
        }
        // В меню — тапы по кнопкам (сначала фиксируем тап в Input)
        input.onTouch(e);
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
            float[] tap = new float[2];
            if (input.consumeTap(tap)) {
                screens.onTap(tap[0], tap[1]);
            }
        }
    }

    // ------------------------------------------------------------- кнопки

    public void onButton(int id) {
        switch (id) {
            case B_PLAY:
                startRun();
                break;
            case B_CHARS:
                state = ST_CHARS;
                break;
            case B_SHOP:
                state = ST_SHOP;
                break;
            case B_SETTINGS:
                state = ST_SETTINGS;
                break;
            case B_STATS:
                state = ST_STATS;
                break;
            case B_ACH:
                state = ST_ACH;
                break;
            case B_HOWTO:
                state = ST_HOWTO;
                break;
            case B_BACK:
                state = ST_TITLE;
                break;
            case B_RESUME:
                state = ST_PLAY;
                input.reset();
                break;
            case B_RESTART:
                startRun();
                break;
            case B_MENU:
                toMenu();
                break;
            case B_ENDLESS:
                if (S.setAchievement(ctx, 9)) {
                    S.addGold(ctx, S.ACH_REWARD[9]);
                }
                world.endless = true;
                world.victory = false;
                world.hp = Math.max(world.hp, world.hpMax * 0.5f);
                world.dead = false;
                state = ST_PLAY;
                input.reset();
                Sound.startMusic();
                break;
            default:
                onOtherButton(id);
        }
    }

    private void onOtherButton(int id) {
        // Выбор героя
        if (id >= B_CHAR_0 && id < B_CHAR_0 + 4) {
            int i = id - B_CHAR_0;
            boolean[] unlocked = S.chars(ctx);
            if (unlocked[i]) {
                S.selectedChar(ctx, i);
                charId = i;
            } else {
                int price = Upgrades.charPrice(i);
                if (S.gold(ctx) >= price) {
                    S.addGold(ctx, -price);
                    S.unlockChar(ctx, i);
                    S.selectedChar(ctx, i);
                    charId = i;
                    Sound.play(Sound.LEVELUP);
                } else {
                    Sound.play(Sound.HURT);
                }
            }
            return;
        }
        // Мета-усиления
        if (id >= B_META_0 && id < B_META_0 + 5) {
            int i = id - B_META_0;
            int lvl = meta[i];
            if (lvl < 5) {
                int price = Upgrades.metaPrice(lvl);
                if (S.gold(ctx) >= price) {
                    S.addGold(ctx, -price);
                    meta[i] = lvl + 1;
                    S.setMeta(ctx, meta);
                    Sound.play(Sound.LEVELUP);
                } else {
                    Sound.play(Sound.HURT);
                }
            }
            return;
        }
        // Карточки левел-апа
        if (id >= B_CARD_0 && id < B_CARD_0 + 3) {
            if (screens.cards != null && id - B_CARD_0 < screens.cards.length) {
                Upgrades.apply(world, screens.cards[id - B_CARD_0]);
                world.doLevelUp();
                if (world.levelUpReady()) {
                    screens.cards = Upgrades.roll3(world);
                    screens.cardAnimT = 0;
                } else {
                    screens.cards = null;
                    state = ST_PLAY;
                    input.reset();
                }
            }
            return;
        }
        // Тумблеры
        switch (id) {
            case B_T_SOUND:
                S.sound(ctx, !S.sound(ctx));
                Sound.applySettings(ctx);
                break;
            case B_T_MUSIC:
                S.music(ctx, !S.music(ctx));
                Sound.applySettings(ctx);
                break;
            case B_T_VIB:
                S.vibrate(ctx, !S.vibrate(ctx));
                break;
            case B_T_LANG:
                S.lang(ctx, L.ru ? "en" : "ru");
                break;
            case B_RESET:
                S.reset(ctx);
                S.applyLang(ctx);
                meta = S.meta(ctx);
                charId = 0;
                break;
            default:
                break;
        }
    }
}
