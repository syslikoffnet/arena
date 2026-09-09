package com.syslikoffnet.overglow;

import java.util.Random;

/**
 * Оружие: 8 видов, каждый качается до 8 уровня. Стреляет автоматически.
 * Уровни влияют на урон, количество, кулдаун, площадь.
 */
public final class Weapons {

    // ID оружия
    public static final int W_PULSE = 0, W_CHAIN = 1, W_BLADES = 2, W_NOVA = 3,
            W_SPHERES = 4, W_LASER = 5, W_MISSILES = 6, W_FROST = 7,
            W_BOOMERANG = 8, W_STORM = 9, W_AURA = 10, W_BLACKHOLE = 11;

    public static final int COUNT = 12;
    public static final int MAX_LVL = 8;

    public final int id;
    public int lvl = 1;
    public boolean awakened; // пробуждение (эволюция на макс. уровне)
    public float timer;      // до следующего выстрела
    public float angle;      // для клинков
    public float pulseSide;  // чередование стволов

    public Weapons(int id) {
        this.id = id;
        this.timer = 0.3f;
    }

    // --- Таблицы: урон, кулдаун, количество по уровню ---
    private static final float[][] DMG = {
            /* PULSE   */ {10, 13, 17, 22, 28, 35, 44, 55},
            /* CHAIN   */ {14, 18, 23, 29, 36, 45, 56, 70},
            /* BLADES  */ {8, 10, 13, 16, 20, 25, 31, 38},
            /* NOVA    */ {12, 16, 21, 27, 34, 42, 52, 64},
            /* SPHERES */ {9, 12, 15, 19, 24, 30, 37, 46},
            /* LASER   */ {7, 9, 12, 15, 19, 24, 30, 37},
            /* MISSILES*/ {18, 24, 31, 39, 49, 61, 76, 95},
            /* FROST   */ {7, 9, 12, 15, 19, 24, 30, 37},
            /* BOOMRNG */ {12, 16, 20, 26, 32, 40, 50, 62},
            /* STORM   */ {16, 21, 27, 34, 42, 52, 64, 80},
            /* AURA    */ {4, 5, 6, 8, 10, 12, 15, 18},
            /* BLACKHL */ {6, 8, 10, 13, 16, 20, 25, 31},
    };
    private static final float[][] CD = {
            /* PULSE   */ {0.55f, 0.52f, 0.48f, 0.45f, 0.42f, 0.38f, 0.35f, 0.30f},
            /* CHAIN   */ {2.2f, 2.0f, 1.9f, 1.7f, 1.6f, 1.4f, 1.3f, 1.1f},
            /* BLADES  */ {0.0f, 0, 0, 0, 0, 0, 0, 0},
            /* NOVA    */ {3.4f, 3.2f, 3.0f, 2.7f, 2.5f, 2.3f, 2.0f, 1.8f},
            /* SPHERES */ {2.8f, 2.6f, 2.5f, 2.3f, 2.1f, 1.9f, 1.8f, 1.6f},
            /* LASER   */ {1.6f, 1.5f, 1.4f, 1.3f, 1.2f, 1.1f, 1.0f, 0.9f},
            /* MISSILES*/ {2.6f, 2.4f, 2.3f, 2.1f, 2.0f, 1.8f, 1.7f, 1.5f},
            /* FROST   */ {1.5f, 1.4f, 1.3f, 1.2f, 1.1f, 1.0f, 0.95f, 0.85f},
            /* BOOMRNG */ {1.7f, 1.6f, 1.5f, 1.4f, 1.3f, 1.2f, 1.1f, 1.0f},
            /* STORM   */ {2.4f, 2.2f, 2.1f, 1.9f, 1.8f, 1.6f, 1.5f, 1.3f},
            /* AURA    */ {0.45f, 0.43f, 0.41f, 0.39f, 0.37f, 0.35f, 0.32f, 0.30f},
            /* BLACKHL */ {4.2f, 4.0f, 3.7f, 3.5f, 3.2f, 3.0f, 2.8f, 2.5f},
    };
    /** Количество снарядов/целей/клинков по уровню. */
    private static final int[][] N = {
            /* PULSE   */ {1, 1, 2, 2, 3, 3, 4, 4},
            /* CHAIN   */ {2, 3, 3, 4, 4, 5, 5, 6},
            /* BLADES  */ {2, 2, 3, 3, 4, 4, 5, 6},
            /* NOVA    */ {1, 1, 1, 1, 2, 2, 2, 3},
            /* SPHERES */ {1, 1, 2, 2, 2, 3, 3, 4},
            /* LASER   */ {1, 1, 1, 2, 2, 2, 3, 3},
            /* MISSILES*/ {1, 2, 2, 3, 3, 4, 4, 5},
            /* FROST   */ {3, 4, 4, 5, 5, 6, 7, 8},
            /* BOOMRNG */ {1, 1, 2, 2, 2, 3, 3, 3},
            /* STORM   */ {1, 2, 2, 2, 3, 3, 3, 4},
            /* AURA    */ {1, 1, 1, 1, 1, 1, 1, 1},
            /* BLACKHL */ {1, 1, 1, 1, 2, 2, 2, 2},
    };
    /** Радиус действия по уровню (новa/spheres/laser). */
    private static final float[][] RANGE = {
            /* PULSE   */ {500, 500, 520, 520, 540, 540, 560, 560},
            /* CHAIN   */ {320, 340, 360, 380, 400, 420, 440, 460},
            /* BLADES  */ {92, 96, 100, 104, 108, 114, 118, 124},
            /* NOVA    */ {130, 140, 150, 160, 170, 180, 195, 210},
            /* SPHERES */ {60, 62, 64, 66, 68, 70, 72, 75},
            /* LASER   */ {520, 540, 560, 580, 600, 620, 640, 660},
            /* MISSILES */ {0, 0, 0, 0, 0, 0, 0, 0},
            /* FROST   */ {0, 0, 0, 0, 0, 0, 0, 0},
            /* BOOMRNG */ {0, 0, 0, 0, 0, 0, 0, 0},
            /* STORM   */ {850, 870, 890, 910, 930, 950, 970, 1000},
            /* AURA    */ {120, 132, 144, 156, 170, 184, 200, 220},
            /* BLACKHL */ {0, 0, 0, 0, 0, 0, 0, 0},
    };

    public float dmg() {
        return DMG[id][lvl - 1] * (awakened ? 1.6f : 1f);
    }

    public float cd() {
        return CD[id][lvl - 1];
    }

    public int n() {
        return N[id][lvl - 1];
    }

    /** Количество с учётом реликвии «Сдвоенный эмиттер». */
    public int nEff(World w) {
        int n = nEff(world);
        if (w.hasRelic(World.R_DOUBLE)
                && id != W_AURA && id != W_BLACKHOLE && id != W_BLADES) {
            n++;
        }
        return n;
    }

    public float range() {
        return RANGE[id][lvl - 1] * (w != null ? w.areaMul : 1f);
    }

    // Ссылка на мир — ставится при update
    private World w;

    // --------------------------------------------------------------- fire

    private static final Random RND = new Random();

    public void update(float dt, World world) {
        w = world;
        switch (id) {
            case W_BLADES:
                updateBlades(dt, world);
                return;
            default:
                break;
        }
        timer -= dt * world.cdMul;
        if (timer > 0) return;
        timer = cd();
        fire(world);
    }

    private void fire(World world) {
        switch (id) {
            case W_PULSE: {
                World.Enemy tgt = world.nearestEnemy(world.px, world.py, range() + 200);
                float dx, dy;
                if (tgt != null) {
                    dx = tgt.x - world.px;
                    dy = tgt.y - world.py;
                } else {
                    dx = world.faceX;
                    dy = world.faceY;
                }
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 1) {
                    dx = 0;
                    dy = 1;
                    d = 1;
                }
                dx /= d;
                dy /= d;
                int n = nEff(world);
                float spread = 0.14f;
                for (int i = 0; i < n; i++) {
                    float a = (float) Math.atan2(dy, dx)
                            + (i - (n - 1) / 2f) * spread;
                    float sp = 560 * world.projSpdMul;
                    world.bullet(false, 0, world.px, world.py,
                            (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                            8, dmg(), 1.4f);
                }
                Sound.play(Sound.SHOOT);
                break;
            }
            case W_CHAIN: {
                // Цепная молния: ближайший → прыжки
                World.Enemy first = world.nearestEnemy(world.px, world.py, range());
                if (first == null) {
                    timer = 0.2f;
                    return;
                }
                float x = world.px, y = world.py;
                float jumpR = range() * 0.8f;
                int jumps = n();
                java.util.HashSet<World.Enemy> hit = new java.util.HashSet<>();
                for (int i = 0; i < jumps; i++) {
                    World.Enemy tgt = i == 0 ? first
                            : nearestExcept(world, x, y, jumpR, hit);
                    if (tgt == null) break;
                    hit.add(tgt);
                    world.zaps.add(new float[]{x, y, tgt.x, tgt.y, 0.18f});
                    float d = dmg() * (1f - i * 0.12f);
                    world.damageEnemy(tgt, d, 0, 0);
                    world.burst(tgt.x, tgt.y, 4, Art.PLAYER, 120, 0.3f, 6);
                    x = tgt.x;
                    y = tgt.y;
                }
                Sound.play(Sound.LASER);
                break;
            }
            case W_NOVA: {
                // Кольцевой взрыв вокруг игрока
                int waves = n();
                for (int i = 0; i < waves; i++) {
                    float r = range() * (0.7f + i * 0.25f);
                    world.damageArea(world.px, world.py, r, dmg() / waves, Art.PLAYER);
                }
                world.ring(world.px, world.py, 0.5f, 40, range() * 1.1f, Art.PLAYER);
                world.shake = Math.max(world.shake, 4);
                Sound.play(Sound.BOOM);
                break;
            }
            case W_SPHERES: {
                // Медленные сферы в случайные стороны
                int n = nEff(world);
                for (int i = 0; i < n; i++) {
                    float a = RND.nextFloat() * (float) (Math.PI * 2);
                    float sp = 170 * world.projSpdMul;
                    World.Bullet b = world.bullet(false, 1, world.px, world.py,
                            (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                            range() * 0.35f, dmg(), 3.2f);
                    if (b != null) {
                        b.pierce = true;
                        b.homing = 1.2f;
                    }
                }
                Sound.play(Sound.SHOOT);
                break;
            }
            case W_LASER: {
                // Луч(и) через ближайшего врага
                int beamsN = n();
                for (int i = 0; i < beamsN; i++) {
                    World.Enemy tgt = (i == 0)
                            ? world.nearestEnemy(world.px, world.py, range())
                            : randomEnemy(world, range());
                    float a;
                    if (tgt != null) {
                        a = (float) Math.atan2(tgt.y - world.py, tgt.x - world.px);
                    } else {
                        a = RND.nextFloat() * (float) (Math.PI * 2);
                    }
                    float len = range();
                    float x1 = world.px + (float) Math.cos(a) * len;
                    float y1 = world.py + (float) Math.sin(a) * len;
                    world.damageLine(world.px, world.py, x1, y1, 14 * world.areaMul, dmg());
                    world.beams.add(new float[]{world.px, world.py, x1, y1, 0.14f});
                }
                Sound.play(Sound.LASER);
                break;
            }
            case W_MISSILES: {
                int n = nEff(world);
                for (int i = 0; i < n; i++) {
                    float a = RND.nextFloat() * (float) (Math.PI * 2);
                    World.Bullet b = world.bullet(false, 2, world.px, world.py,
                            (float) Math.cos(a) * 220, (float) Math.sin(a) * 220,
                            9, dmg(), 4.5f);
                    if (b != null) {
                        b.homing = 3.5f;
                        b.aoe = 70 * world.areaMul;
                    }
                }
                Sound.play(Sound.SHOOT);
                break;
            }
            case W_BOOMERANG: {
                World.Enemy tgt = world.nearestEnemy(world.px, world.py, 600);
                float baseA;
                if (tgt != null) {
                    baseA = (float) Math.atan2(tgt.y - world.py, tgt.x - world.px);
                } else {
                    baseA = (float) Math.atan2(world.faceY, world.faceX);
                }
                int n = nEff(world);
                for (int i = 0; i < n; i++) {
                    float a = baseA + (i - (n - 1) / 2f) * 0.45f;
                    float sp = 430;
                    World.Bullet b = world.bullet(false, 5, world.px, world.py,
                            (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                            13, dmg(), 1.8f);
                    if (b != null) {
                        b.boomerang = true;
                        b.pierce = true;
                        b.turnAt = b.life * 0.45f;
                    }
                }
                Sound.play(Sound.SHOOT);
                break;
            }
            case W_STORM: {
                // Гроза: молнии с «неба» по случайным врагам
                int n = nEff(world);
                int strikes = 0;
                for (int i = 0; i < n * 4 && strikes < n; i++) {
                    if (world.enemies.isEmpty()) break;
                    World.Enemy e = world.enemies.get(
                            RND.nextInt(world.enemies.size()));
                    if (e.deadFlag) continue;
                    float dx = e.x - world.px, dy = e.y - world.py;
                    if (dx * dx + dy * dy > range() * range()) continue;
                    // Вертикальная молния
                    world.zaps.add(new float[]{e.x, e.y - 640, e.x, e.y, 0.22f});
                    world.damageArea(e.x, e.y, 64, dmg(), 0);
                    world.burst(e.x, e.y, 8, 0xFF8AE8FF, 180, 0.35f, 7);
                    strikes++;
                }
                if (strikes > 0) {
                    Sound.play(Sound.LASER);
                    world.shake = Math.max(world.shake, 5);
                }
                break;
            }
            case W_AURA: {
                // Огненная аура: тик вокруг игрока
                world.damageArea(world.px, world.py, range(), dmg(), 0);
                world.ring(world.px, world.py, 0.42f, range() * 0.85f,
                        range() * 1.05f, 0xFFFF8A3D);
                break;
            }
            case W_BLACKHOLE: {
                // Чёрная дыра в скоплении врагов
                int n = nEff(world);
                for (int i = 0; i < n; i++) {
                    World.Enemy tgt = world.enemies.isEmpty() ? null
                            : world.enemies.get(RND.nextInt(world.enemies.size()));
                    float hx, hy;
                    if (tgt != null && !tgt.deadFlag) {
                        hx = tgt.x;
                        hy = tgt.y;
                    } else {
                        hx = world.px + (RND.nextFloat() - 0.5f) * 400;
                        hy = world.py + (RND.nextFloat() - 0.5f) * 400;
                    }
                    World.Bullet b = world.bullet(false, 6, hx, hy, 0, 0,
                            24, dmg(), 3.4f);
                    if (b != null) b.pierce = true;
                }
                Sound.play(Sound.LASER);
                break;
            }
            case W_FROST: {
                // Дробовик осколков вперёд по движению
                int n = nEff(world);
                float base = (float) Math.atan2(world.faceY, world.faceX);
                float spread = 0.85f;
                for (int i = 0; i < n; i++) {
                    float a = base + (i / (n - 1f) - 0.5f) * spread
                            + (RND.nextFloat() - 0.5f) * 0.1f;
                    float sp = 480 * world.projSpdMul;
                    World.Bullet b = world.bullet(false, 3, world.px, world.py,
                            (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                            7, dmg(), 0.9f);
                    if (b != null) {
                        b.slow = 1;
                        b.spin = a;
                    }
                }
                Sound.play(Sound.SHOOT);
                break;
            }
            default:
                break;
        }
    }

    private void updateBlades(float dt, World world) {
        angle += dt * (2.6f + lvl * 0.15f);
        int n = nEff(world);
        float R = range();
        for (int i = 0; i < n; i++) {
            float a = angle + (float) (Math.PI * 2) * i / n;
            float bx = world.px + (float) Math.cos(a) * R;
            float by = world.py + (float) Math.sin(a) * R;
            // Урон врагам в точке клинка
            int cx = (int) World.clamp(bx / World.CELL, 0, World.GRID_N - 1);
            int cy = (int) World.clamp(by / World.CELL, 0, World.GRID_N - 1);
            int x0 = Math.max(0, cx - 1), x1 = Math.min(World.GRID_N - 1, cx + 1);
            int y0 = Math.max(0, cy - 1), y1 = Math.min(World.GRID_N - 1, cy + 1);
            for (int gy = y0; gy <= y1; gy++) {
                for (int gx = x0; gx <= x1; gx++) {
                    int j = world.gridHead[gy * World.GRID_N + gx];
                    int guard = 0;
                    while (j != -1 && guard++ < 64) {
                        World.Enemy e = world.enemies.get(j);
                        if (!e.deadFlag && e.bladeCd <= 0) {
                            float dx = e.x - bx, dy = e.y - by;
                            float rr = 26 * world.areaMul + e.r;
                            if (dx * dx + dy * dy < rr * rr) {
                                e.bladeCd = 0.38f;
                                world.damageEnemy(e, dmg(), dx * 0.02f, dy * 0.02f);
                            }
                        }
                        j = world.gridNext[j];
                    }
                }
            }
        }
    }

    private static World.Enemy nearestExcept(World w, float x, float y, float r,
                                             java.util.HashSet<World.Enemy> exclude) {
        World.Enemy best = null;
        float bd = r * r;
        for (int i = 0; i < w.enemies.size(); i++) {
            World.Enemy e = w.enemies.get(i);
            if (e.deadFlag || exclude.contains(e)) continue;
            float dx = e.x - x, dy = e.y - y;
            float d2 = dx * dx + dy * dy;
            if (d2 < bd) {
                bd = d2;
                best = e;
            }
        }
        return best;
    }

    private static World.Enemy randomEnemy(World w, float r) {
        if (w.enemies.isEmpty()) return null;
        for (int tries = 0; tries < 6; tries++) {
            World.Enemy e = w.enemies.get(RND.nextInt(w.enemies.size()));
            if (e.deadFlag) continue;
            float dx = e.x - w.px, dy = e.y - w.py;
            if (dx * dx + dy * dy < r * r) return e;
        }
        return null;
    }

    // --------------------------------------------------------------- имена

    public static String name(int id) {
        switch (id) {
            case W_PULSE: return L.t("w_pulse");
            case W_CHAIN: return L.t("w_chain");
            case W_BLADES: return L.t("w_blades");
            case W_NOVA: return L.t("w_nova");
            case W_SPHERES: return L.t("w_spheres");
            case W_LASER: return L.t("w_laser");
            case W_MISSILES: return L.t("w_missiles");
            case W_FROST: return L.t("w_frost");
            case W_BOOMERANG: return L.t("w_boomerang");
            case W_STORM: return L.t("w_storm");
            case W_AURA: return L.t("w_aura");
            case W_BLACKHOLE: return L.t("w_blackhole");
            default: return "?";
        }
    }

    /** Короткое описание текущего уровня. */
    public static String desc(int id, int lvl) {
        String base;
        switch (id) {
            case W_PULSE: base = L.t("wd_pulse"); break;
            case W_CHAIN: base = L.t("wd_chain"); break;
            case W_BLADES: base = L.t("wd_blades"); break;
            case W_NOVA: base = L.t("wd_nova"); break;
            case W_SPHERES: base = L.t("wd_spheres"); break;
            case W_LASER: base = L.t("wd_laser"); break;
            case W_MISSILES: base = L.t("wd_missiles"); break;
            case W_FROST: base = L.t("wd_frost"); break;
            case W_BOOMERANG: base = L.t("wd_boomerang"); break;
            case W_STORM: base = L.t("wd_storm"); break;
            case W_AURA: base = L.t("wd_aura"); break;
            case W_BLACKHOLE: base = L.t("wd_blackhole"); break;
            default: base = ""; break;
        }
        return base + "  " + L.t("lvl") + " " + lvl + "/8";
    }
}
