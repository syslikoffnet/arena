package com.syslikoffnet.overglow;

/**
 * Режиссёр спавна: расписание волн по времени, элиты, боссы.
 * Сложность растёт: HP врагов +35%/мин, плотность — по кривой.
 */
public final class Director {

    private final World world;
    private final Game game;

    private float spawnT;        // таймер до следующего спавна
    private float eliteT = 90f;  // таймер элиты
    private boolean boss1Done, boss2Done, miniDone;
    public float bossWarnT;      // предупреждение «БОСС ПРИБЛИЖАЕТСЯ»
    private float warnShown = -1;

    // Сюрж-наплывы
    public float surgeWarnT;     // предупреждение (стрелка)
    private float surgeT = 42f;  // до следующего сюржа
    private int surgeWavesLeft;  // волн осталось
    private float surgeWaveT;    // до следующей волны
    public float surgeAngle;     // направление наплыва

    // События
    private float eventT = 55f;
    public float bannerT;        // показ баннера события
    public String bannerText;

    // Метеоры {x, y, t}
    public final java.util.ArrayList<float[]> meteors = new java.util.ArrayList<>();


    public Director(World world, Game game) {
        this.world = world;
        this.game = game;
    }

    public void reset() {
        spawnT = 0;
        eliteT = 90f;
        boss1Done = false;
        boss2Done = false;
        miniDone = false;
        bossWarnT = 0;
        warnShown = -1;
        surgeT = 42f;
        surgeWarnT = 0;
        surgeWavesLeft = 0;
        bannerT = 0;
        bannerText = null;
        meteors.clear();
    }

    public void update(float dt) {
        float t = world.time;
        if (world.dead) return;

        // --- Волны ---
        // Интервал спавна: от 1.1с до 0.25с
        spawnT -= dt;
        if (spawnT <= 0) {
            float progress = Math.min(1f, t / 1200f);
            spawnT = 0.95f - 0.55f * progress;
            // Размер пачки
            int batch = 2 + (int) (t / 30f);
            batch = Math.min(batch, 18);
            spawnWave(batch);
        }

        // --- Элита каждые 90 секунд ---
        eliteT -= dt;
        if (eliteT <= 0) {
            eliteT = 90f;
            float[] pt = world.offscreenPoint(game.spawnDist() + 120, new float[2]);
            int type = pickEliteType(t);
            world.spawnEnemy(type, pt[0], pt[1], true);
            world.ring(pt[0], pt[1], 0.8f, 20, 300, Art.ELITE);
        }

        // --- Мини-босс на 5:00 ---
        if (!miniDone && t >= 300f) {
            miniDone = true;
            world.spawnBoss(1);
            world.shake = 14;
            Sound.play(Sound.BOSS);
            game.vibrate(200);
        }

        // --- Боссы: 10:00 и 20:00 ---
        if (!boss1Done && t >= 600f) {
            boss1Done = true;
            world.spawnBoss(0);
        }
        if (!boss2Done && t >= 1200f) {
            boss2Done = true;
            if (!world.endless) world.spawnBoss(2); // Королева Роя
        }

        // Предупреждение перед боссом (за 5 секунд)
        if (warnShown < 0) {
            float nextBoss = !miniDone ? 300f
                    : (!boss1Done ? 600f : (!boss2Done && !world.endless ? 1200f : -1));
            if (nextBoss > 0 && t >= nextBoss - 5f && t < nextBoss) {
                warnShown = t;
                bossWarnT = 5f;
                Sound.play(Sound.BOSS);
                game.vibrate(300);
            }
        }
        if (bossWarnT > 0) {
            bossWarnT -= dt;
        } else {
            warnShown = -1; // готовность к следующему предупреждению
        }

        updateSurge(dt);
        updateEvents(dt);
        updateMeteors(dt);
    }

    // ============================== Сюржи

    private void updateSurge(float dt) {
        if (surgeWavesLeft > 0) {
            // Идёт наплыв
            surgeWaveT -= dt;
            if (surgeWaveT <= 0) {
                surgeWaveT = 0.55f;
                surgeWavesLeft--;
                spawnSurgeWave();
            }
            return;
        }
        if (surgeWarnT > 0) {
            surgeWarnT -= dt;
            if (surgeWarnT <= 0) {
                surgeWavesLeft = 4;
                surgeWaveT = 0;
            }
            return;
        }
        surgeT -= dt;
        if (surgeT <= 0) {
            surgeT = 40f + world.rnd.nextFloat() * 15f;
            surgeWarnT = 2.2f;
            surgeAngle = world.rnd.nextFloat() * (float) (Math.PI * 2);
        }
    }

    private void spawnSurgeWave() {
        float d = game.spawnDist() + 140;
        float[] pt = world.offscreenPoint(1, new float[2]);
        // Точка строго по направлению
        pt[0] = World.clamp(world.px + (float) Math.cos(surgeAngle) * d, 20, World.ARENA - 20);
        pt[1] = World.clamp(world.py + (float) Math.sin(surgeAngle) * d, 20, World.ARENA - 20);
        int[] pool = wavePool(world.time);
        int n = 10 + (int) (world.time / 90f);
        n = Math.min(n, 22);
        for (int i = 0; i < n; i++) {
            int type = pool[world.rnd.nextInt(pool.length)];
            float along = (world.rnd.nextFloat() - 0.5f) * 700;
            float ox = -(float) Math.sin(surgeAngle) * along;
            float oy = (float) Math.cos(surgeAngle) * along;
            world.spawnEnemy(type,
                    World.clamp(pt[0] + ox, 20, World.ARENA - 20),
                    World.clamp(pt[1] + oy, 20, World.ARENA - 20), false);
        }
    }

    // ============================== События

    private void updateEvents(float dt) {
        if (bannerT > 0) bannerT -= dt;
        eventT -= dt;
        if (eventT > 0) return;
        eventT = 55f + world.rnd.nextFloat() * 25f;
        if (world.time < 75f) return; // дать освоиться

        int roll = world.rnd.nextInt(3);
        switch (roll) {
            case 0: { // Золотой жадина
                float[] pt = world.offscreenPoint(game.spawnDist() * 0.8f, new float[2]);
                world.spawnEnemy(World.E_GREED, pt[0], pt[1], false);
                banner(L.t("ev_greed"), 3f);
                Sound.play(Sound.PICKUP);
                break;
            }
            case 1: { // Метеоритный дождь
                banner(L.t("ev_meteor"), 3f);
                for (int i = 0; i < 8; i++) {
                    float a = world.rnd.nextFloat() * (float) (Math.PI * 2);
                    float d = world.rnd.nextFloat() * 480;
                    meteors.add(new float[]{
                            World.clamp(world.px + (float) Math.cos(a) * d, 60, World.ARENA - 60),
                            World.clamp(world.py + (float) Math.sin(a) * d, 60, World.ARENA - 60),
                            -i * 0.55f // задержка старта
                    });
                }
                Sound.play(Sound.BOSS);
                break;
            }
            default: { // Орда мышей
                banner(L.t("ev_swarm"), 3f);
                surgeAngle = world.rnd.nextFloat() * (float) (Math.PI * 2);
                float d = game.spawnDist() + 100;
                for (int i = 0; i < 26; i++) {
                    world.spawnEnemy(World.E_BAT,
                            World.clamp(world.px + (float) Math.cos(surgeAngle) * d
                                    + (world.rnd.nextFloat() - 0.5f) * 500, 20, World.ARENA - 20),
                            World.clamp(world.py + (float) Math.sin(surgeAngle) * d
                                    + (world.rnd.nextFloat() - 0.5f) * 500, 20, World.ARENA - 20),
                            false);
                }
                break;
            }
        }
    }

    private void banner(String text, float t) {
        bannerText = text;
        bannerT = t;
    }

    private void updateMeteors(float dt) {
        for (int i = meteors.size() - 1; i >= 0; i--) {
            float[] m = meteors.get(i);
            m[2] += dt;
            if (m[2] >= 1.25f) {
                // Удар
                world.explosion(m[0], m[1], 130, 0xFFFF8A3D);
                world.damageArea(m[0], m[1], 125, 70, 0);
                float dx = world.px - m[0], dy = world.py - m[1];
                if (dx * dx + dy * dy < 125 * 125) world.hurtPlayer(24);
                world.shake = Math.max(world.shake, 10);
                Sound.play(Sound.BOOM);
                meteors.remove(i);
            }
        }
    }

    /** Спавн пачки обычных врагов. */
    /** Пул типов врагов по времени. */
    private int[] wavePool(float t) {
        // 0:00 mite; 0:40 runner; 1:30 spitter; 2:30 splitter; 3:30 brute;
        // 5:00 bat; 6:00 bomber; 7:30 shielded
        if (t < 40) return new int[]{World.E_MITE};
        if (t < 90) return new int[]{World.E_MITE, World.E_RUNNER};
        if (t < 150) return new int[]{World.E_MITE, World.E_RUNNER, World.E_SPITTER};
        if (t < 210) return new int[]{World.E_MITE, World.E_RUNNER, World.E_SPITTER, World.E_SPLITTER};
        if (t < 300) return new int[]{World.E_RUNNER, World.E_SPITTER, World.E_SPLITTER, World.E_BRUTE};
        if (t < 360) return new int[]{World.E_RUNNER, World.E_SPITTER, World.E_SPLITTER, World.E_BRUTE, World.E_BAT};
        if (t < 450) return new int[]{World.E_RUNNER, World.E_SPITTER, World.E_SPLITTER, World.E_BRUTE, World.E_BAT, World.E_BOMBER};
        return new int[]{World.E_RUNNER, World.E_SPITTER, World.E_SPLITTER, World.E_BRUTE, World.E_BAT, World.E_BOMBER, World.E_SHIELDED};
    }

    private void spawnWave(int batch) {
        int[] pool = wavePool(world.time);
        float sd = game.spawnDist();
        float[] pt = world.offscreenPoint(sd, new float[2]);
        for (int i = 0; i < batch; i++) {
            int type = pool[world.rnd.nextInt(pool.length)];
            float x = pt[0] + (world.rnd.nextFloat() - 0.5f) * Math.min(500, sd * 0.6f);
            float y = pt[1] + (world.rnd.nextFloat() - 0.5f) * Math.min(500, sd * 0.6f);
            world.spawnEnemy(type, x, y, false);
        }
    }

    private int pickEliteType(float t) {
        if (t > 420) return World.E_SHIELDED;
        if (t > 240) return World.E_BRUTE;
        if (t > 120) return World.E_SPITTER;
        return World.E_RUNNER;
    }
}
