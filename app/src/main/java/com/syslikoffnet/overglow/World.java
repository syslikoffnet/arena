package com.syslikoffnet.overglow;

import java.util.ArrayList;
import java.util.Random;

/**
 * Игровой мир: игрок, враги, снаряды, пикапы, частицы, коллизии.
 * Координаты — «мировые юниты», арена 3600×3600. fixed timestep 60 Гц.
 */
public final class World {

    public static final float ARENA = 3600f;
    public static final float CELL = 120f;
    public static final int GRID_N = (int) (ARENA / CELL); // 30

    // ===================== Сущности =====================

    /** Типы врагов. */
    public static final int
            E_MITE = 0, E_RUNNER = 1, E_BRUTE = 2, E_SPITTER = 3, E_SPLITTER = 4,
            E_SHIELDED = 5, E_BOMBER = 6, E_BAT = 7, E_BOSS = 8, E_GREED = 9;

    /** Таблица врагов: hp, speed, dmg, r, цвет, xp-вес, шанс золота. */
    public static final float[][] ETAB = {
            /* MITE     */ {8f, 95f, 5f, 16f, 0xFFFF4D6D, 1f, 0.02f},
            /* RUNNER   */ {14f, 160f, 7f, 18f, 0xFFFF6B9D, 1.5f, 0.03f},
            /* BRUTE    */ {60f, 70f, 16f, 30f, 0xFFFF8A3D, 4f, 0.10f},
            /* SPITTER  */ {26f, 90f, 9f, 22f, 0xFFB388FF, 3f, 0.08f},
            /* SPLITTER */ {40f, 85f, 10f, 26f, 0xFF9DFF6B, 3f, 0.08f},
            /* SHIELDED */ {90f, 75f, 14f, 28f, 0xFF6BD4FF, 5f, 0.14f},
            /* BOMBER   */ {30f, 120f, 26f, 24f, 0xFFF5C542, 3f, 0.10f},
            /* BAT      */ {10f, 190f, 6f, 14f, 0xFFC86BFF, 1.5f, 0.02f},
            /* BOSS     */ {4200f, 70f, 30f, 96f, 0xFFFF2E63, 250f, 1f},
            /* GREED    */ {70f, 200f, 0f, 20f, 0xFFF5C542, 6f, 1f},
    };

    public static final class Enemy {
        public int type;
        public float x, y, vx, vy;
        public float hp, hpMax, speed, dmg, r;
        public int color;
        public float hitFlash;      // вспышка при уроне
        public float slowT;         // замедление (Frost)
        public float bladeCd;       // кулдаун урона от клинков
        public float t;             // таймер AI
        public int aiState;         // состояние AI (босс/плевун)
        public boolean elite;
        public float shootCd;
        public boolean mini;      // мини-босс
        public boolean queen;     // Королева Роя (финальный босс)
        public float spawnT;      // анимация появления
        public boolean deadFlag; // умрёт в конце кадра
    }

    public static final class Bullet {
        public boolean active;
        public boolean enemy;       // вражеский снаряд?
        public int kind;            // 0 пульс 1 сфера 2 ракета 3 осколок 4 плевок
        public float x, y, vx, vy;
        public float r, dmg, life;
        public boolean pierce;
        public float slow;          // замедление цели
        public float aoe;           // урон по площади при попадании
        public float homing;        // сила самонаведения
        public float spin;          // вращение осколков
        public boolean boomerang;   // возвращается к игроку
        public float turnAt;        // порог жизни для разворота бумеранга
        public float hitT;          // окно неуязвимости после удара (пробой)
        public float tickT;         // таймер тиков (чёрная дыра)
    }

    /** Пикапы. */
    public static final int PU_XP1 = 0, PU_XP2 = 1, PU_XP3 = 2, PU_GOLD = 3,
            PU_HEAL = 4, PU_MAGNET = 5, PU_BOMB = 6, PU_CHEST = 7,
            PU_SHIELD = 8, PU_GOLD2 = 9, PU_RELIC = 10;

    public static final class Pickup {
        public boolean active;
        public int kind;
        public float x, y, vx, vy, t;
        public int val;
        public boolean mag; // притянутся к игроку?
    }

    public static final class Part {
        public boolean active;
        public float x, y, vx, vy;
        public float life, maxLife, r0, r1;
        public int color;
        public boolean ring; // расширяющееся кольцо
    }

    public static final class DText {
        public boolean active;
        public float x, y, life;
        public String text;
        public int color;
        public float size;
    }

    // ===================== Состояние =====================

    // Игрок
    public float px, py, pvx, pvy;
    public float hp, hpMax;
    public float speed = 220f;
    public float magnetR = 100f;
    public float dmgMul = 1f, cdMul = 1f, areaMul = 1f, projSpdMul = 1f;
    public float armor = 0f, regen = 0f;
    public float critCh = 0.05f, critMul = 1.8f;
    public float xpMul = 1f, goldMul = 1f;
    public float hurtCd;         // неуязвимость после урона
    public float faceX = 0, faceY = 1;
    public int level = 1, xp = 0;
    public int kills = 0, goldRun = 0;
    public boolean dead;

    // Комбо
    public int comboN;
    public float comboT;         // остаток окна
    public int comboBest;

    // Killstreak (длинные цепочки)
    public int streakN;
    public float streakT;
    public int streakShown;      // последний показанный порог

    // Реликвии (10 слотов)
    public boolean[] relics = new boolean[10];
    public int relicCount;

    // Щит и бусты
    public boolean shield;
    public float goldBoostT;     // x2 золото

    // Статистика забега для ачивок
    public int elitesKilled;
    public int greedKilled;
    public int bossesKilled;

    // Время выживания (секунды)
    public float time;
    public boolean bossAlive;
    public boolean victory;
    public boolean endless;

    // Сок
    public float shake;          // сила тряски
    public float slowmo;         // замедление мира (сек оставшихся)
    public float hitStop;        // микро-пауза
    public float flashScreen;    // красная вспышка при уроне

    // Камера
    public float camX, camY;

    // Трейл игрока (кольцевой буфер позиций)
    public final float[] trailX = new float[24];
    public final float[] trailY = new float[24];
    public int trailPtr;
    public float trailTimer;

    // Коллекции
    public final ArrayList<Enemy> enemies = new ArrayList<>(256);
    public final ArrayList<Weapons> weapons = new ArrayList<>(8);
    public final Bullet[] bullets = new Bullet[700];
    public final Pickup[] pickups = new Pickup[400];
    public final Part[] parts = new Part[800];
    public final DText[] dtexts = new DText[96];
    public int bulletPtr, pickupPtr, partPtr, dtextPtr;

    // Визуальные «молнии» и «лучи» (x1,y1,x2,y2,t)
    public final ArrayList<float[]> zaps = new ArrayList<>();
    public final ArrayList<float[]> beams = new ArrayList<>();

    // Пространственная сетка врагов
    public final int[] gridHead = new int[GRID_N * GRID_N];
    public final int[] gridNext = new int[6144];

    public final Random rnd = new Random();

    public final Game game;

    public World(Game game) {
        this.game = game;
        px = py = camX = camY = ARENA / 2f; // камера в центре до первого забега
        for (int i = 0; i < bullets.length; i++) bullets[i] = new Bullet();
        for (int i = 0; i < pickups.length; i++) pickups[i] = new Pickup();
        for (int i = 0; i < parts.length; i++) parts[i] = new Part();
        for (int i = 0; i < dtexts.length; i++) dtexts[i] = new DText();
    }

    // ===================== Старт забега =====================

    public void start(int charId) {
        enemies.clear();
        weapons.clear();
        zaps.clear();
        beams.clear();
        for (Bullet b : bullets) b.active = false;
        for (Pickup p : pickups) p.active = false;
        for (Part p : parts) p.active = false;
        for (DText d : dtexts) d.active = false;

        px = py = ARENA / 2f;
        pvx = pvy = 0;
        time = 0;
        kills = 0;
        goldRun = 0;
        level = 1;
        xp = 0;
        dead = false;
        victory = false;
        endless = false;
        bossAlive = false;
        comboN = 0;
        comboT = 0;
        comboBest = 0;
        streakN = 0;
        streakT = 0;
        streakShown = 0;
        java.util.Arrays.fill(relics, false);
        relicCount = 0;
        shield = false;
        goldBoostT = 0;
        elitesKilled = 0;
        greedKilled = 0;
        bossesKilled = 0;
        shake = 0;
        slowmo = 0;
        flashScreen = 0;
        hurtCd = 0;
        camX = px;
        camY = py;

        // Мета-усиления + персонаж
        int[] meta = game.meta;
        hpMax = 100 + meta[0] * 20;
        dmgMul = 1f + meta[1] * 0.08f;
        speed = 220f * (1f + meta[2] * 0.03f);
        magnetR = 100f * (1f + meta[3] * 0.15f);
        goldMul = 1f + meta[4] * 0.10f;
        armor = 0;
        regen = 0;
        areaMul = 1;
        cdMul = 1;
        projSpdMul = 1;
        critCh = 0.05f;
        critMul = 1.8f;
        xpMul = 1;
        hp = hpMax;

        switch (charId) {
            case 1: // Tesla
                hpMax = 80;
                speed = 240;
                critCh = 0.15f;
                break;
            case 2: // Bastion
                hpMax = 140;
                speed = 200;
                armor = 0.10f;
                break;
            case 3: // Void Witch
                hpMax = 90;
                magnetR = 150;
                xpMul = 1.25f;
                break;
            default: // Pulse
                break;
        }
        hp = hpMax;

        // Стартовое оружие персонажа
        addWeapon(Upgrades.CHAR_WEAPON[charId]);
    }

    public void addWeapon(int wid) {
        for (Weapons w : weapons) if (w.id == wid) return;
        weapons.add(new Weapons(wid));
    }

    public Weapons weapon(int wid) {
        for (Weapons w : weapons) if (w.id == wid) return w;
        return null;
    }

    // ===================== XP / уровень =====================

    public int xpNext() {
        int l = level;
        return 5 + (l - 1) * 10 + (l - 1) * (l - 1) * 2;
    }

    public void addXp(int v) {
        xp += Math.round(v * xpMul);
    }

    public static final int R_VAMP = 0, R_DOUBLE = 1, R_GLASS = 2, R_CHRONO = 3,
            R_MAGNET = 4, R_AEGIS = 5, R_GREED = 6, R_FURY = 7, R_TITAN = 8,
            R_LUCK = 9;
    public static final int RELIC_COUNT = 10;

    /** Есть ли реликвия. */
    public boolean hasRelic(int id) {
        return relics[id];
    }

    /** Выдать реликвию (одноразово). Возвращает true, если новая. */
    public boolean giveRelic(int id) {
        if (relics[id]) return false;
        relics[id] = true;
        relicCount++;
        switch (id) {
            case R_GLASS:
                dmgMul *= 1.4f;
                hpMax = Math.max(30, hpMax * 0.75f);
                hp = Math.min(hp, hpMax);
                break;
            case R_CHRONO: cdMul = Math.max(0.35f, cdMul * 0.8f); break;
            case R_MAGNET: magnetR *= 1.6f; break;
            case R_AEGIS: armor = Math.min(0.7f, armor + 0.12f); break;
            case R_GREED: goldMul *= 1.5f; break;
            case R_FURY:
                cdMul = Math.max(0.35f, cdMul * 0.85f);
                speed *= 1.15f;
                break;
            case R_TITAN:
                hpMax *= 1.3f;
                hp += hpMax * 0.3f;
                break;
            case R_LUCK:
                critCh = Math.min(0.75f, critCh + 0.10f);
                critMul += 0.3f;
                break;
            default:
                break; // VAMP и DOUBLE проверяются на лету
        }
        return true;
    }

    /** Случайная невыданная реликвия или -1. */
    public int randomMissingRelic() {
        int missing = RELIC_COUNT - relicCount;
        if (missing <= 0) return -1;
        int pick = rnd.nextInt(missing);
        for (int i = 0; i < RELIC_COUNT; i++) {
            if (!relics[i]) {
                if (pick == 0) return i;
                pick--;
            }
        }
        return -1;
    }

    /** Комбо-множитель золота: до ×2.5 при 100+. */
    public float comboGoldMul() {
        return 1f + Math.min(comboN, 100) / 66f;
    }

    /** Множитель x2-золота (пикап). */
    public float goldBoost() {
        return goldBoostT > 0 ? 2f : 1f;
    }

    /** Готов к левел-апу? */
    public boolean levelUpReady() {
        return xp >= xpNext() && !dead;
    }

    /** Применить левел-ап (после выбора карточки). */
    public void doLevelUp() {
        xp -= xpNext();
        level++;
    }

    // ===================== Спавн =====================

    public void spawnEnemy(int type, float x, float y, boolean elite) {
        if (enemies.size() >= 550) return;
        Enemy e = new Enemy();
        float[] tab = ETAB[type];
        float minute = time / 60f;
        float scale = 1f + minute * 0.28f;
        if (type == E_BOSS) scale = 1f;
        e.type = type;
        e.x = clamp(x, 40, ARENA - 40);
        e.y = clamp(y, 40, ARENA - 40);
        e.hpMax = tab[0] * scale * (elite ? 8f : 1f);
        if (type == E_BOSS) e.hpMax = tab[0] * (1f + minute * 0.12f);
        e.hp = e.hpMax;
        e.speed = tab[1] * (elite ? 0.85f : 1f);
        e.dmg = tab[2] * (1f + minute * 0.06f);
        e.r = tab[3] * (elite ? 1.7f : 1f);
        e.color = (int) tab[4];
        e.elite = elite;
        e.shootCd = 1f + rnd.nextFloat();
        e.spawnT = 0.45f;
        enemies.add(e);
    }

    /** Спавн босса: mini, обычный или Королева Роя. */
    public void spawnBoss(int kind) { // 0 обычный, 1 мини, 2 королева
        float[] pt = offscreenPoint(900, new float[2]);
        spawnEnemy(E_BOSS, pt[0], pt[1], false);
        Enemy b = enemies.get(enemies.size() - 1);
        if (kind == 1) {
            b.mini = true;
            b.hpMax *= 0.42f;
            b.hp = b.hpMax;
            b.r *= 0.58f;
            b.dmg *= 0.7f;
            b.speed = 95f;
        } else if (kind == 2) {
            b.queen = true;
            b.hpMax *= 1.25f;
            b.hp = b.hpMax;
            b.r *= 1.15f;
            b.speed = 85f;
            b.color = 0xFFB388FF;
        }
        bossAlive = true;
    }

    /** Точка за краем экрана вокруг игрока. */
    public float[] offscreenPoint(float dist, float[] out) {
        float a = rnd.nextFloat() * (float) (Math.PI * 2);
        out[0] = clamp(px + (float) Math.cos(a) * dist, 20, ARENA - 20);
        out[1] = clamp(py + (float) Math.sin(a) * dist, 20, ARENA - 20);
        return out;
    }

    public void spawnPickup(int kind, float x, float y, int val) {
        for (int i = 0; i < pickups.length; i++) {
            Pickup p = pickups[(pickupPtr + i) % pickups.length];
            if (!p.active) {
                pickupPtr = (pickupPtr + i + 1) % pickups.length;
                p.active = true;
                p.kind = kind;
                p.x = x;
                p.y = y;
                p.vx = (rnd.nextFloat() - 0.5f) * 60;
                p.vy = (rnd.nextFloat() - 0.5f) * 60;
                p.t = 0;
                p.val = val;
                p.mag = false;
                return;
            }
        }
    }

    // ===================== Частицы / текст =====================

    public void part(float x, float y, float vx, float vy, float life,
                     float r0, float r1, int color) {
        for (int i = 0; i < parts.length; i++) {
            Part p = parts[(partPtr + i) % parts.length];
            if (!p.active) {
                partPtr = (partPtr + i + 1) % parts.length;
                p.active = true;
                p.ring = false;
                p.x = x; p.y = y; p.vx = vx; p.vy = vy;
                p.life = p.maxLife = life;
                p.r0 = r0; p.r1 = r1;
                p.color = color;
                return;
            }
        }
    }

    public void ring(float x, float y, float life, float r0, float r1, int color) {
        for (int i = 0; i < parts.length; i++) {
            Part p = parts[(partPtr + i) % parts.length];
            if (!p.active) {
                partPtr = (partPtr + i + 1) % parts.length;
                p.active = true;
                p.ring = true;
                p.x = x; p.y = y; p.vx = p.vy = 0;
                p.life = p.maxLife = life;
                p.r0 = r0; p.r1 = r1;
                p.color = color;
                return;
            }
        }
    }

    /** Мощный взрыв: вспышка, ударная волна, искры. */
    public void explosion(float x, float y, float r, int color) {
        // Вспышка
        for (Part p : parts) {
            if (!p.active) {
                p.active = true;
                p.ring = false;
                p.x = x; p.y = y; p.vx = p.vy = 0;
                p.life = p.maxLife = 0.22f;
                p.r0 = r * 1.1f;
                p.r1 = 0;
                p.color = 0xFFFFFFFF;
                break;
            }
        }
        // Ударная волна (двойная)
        ring(x, y, 0.5f, r * 0.2f, r * 1.5f, color);
        ring(x, y, 0.35f, r * 0.1f, r * 0.9f, 0xFFFFFFFF);
        // Искры — длинные быстрые
        for (int i = 0; i < 18; i++) {
            float a = rnd.nextFloat() * (float) (Math.PI * 2);
            float sp = 240 + rnd.nextFloat() * 380;
            part(x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    0.3f + rnd.nextFloat() * 0.35f, 6 + rnd.nextFloat() * 5, 0,
                    rnd.nextFloat() < 0.5f ? color : 0xFFFFFFFF);
        }
    }

    public void burst(float x, float y, int n, int color, float spd, float life, float r) {
        for (int i = 0; i < n; i++) {
            float a = rnd.nextFloat() * (float) (Math.PI * 2);
            float s = spd * (0.3f + rnd.nextFloat() * 0.7f);
            part(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s,
                    life * (0.6f + rnd.nextFloat() * 0.6f),
                    r * (0.5f + rnd.nextFloat()), 0, color);
        }
    }

    public void dtext(float x, float y, String text, int color, float size) {
        for (int i = 0; i < dtexts.length; i++) {
            DText d = dtexts[(dtextPtr + i) % dtexts.length];
            if (!d.active) {
                dtextPtr = (dtextPtr + i + 1) % dtexts.length;
                d.active = true;
                d.x = x;
                d.y = y;
                d.life = 0.8f;
                d.text = text;
                d.color = color;
                d.size = size;
                return;
            }
        }
    }

    public Bullet bullet(boolean enemy, int kind, float x, float y,
                         float vx, float vy, float r, float dmg, float life) {
        for (int i = 0; i < bullets.length; i++) {
            Bullet b = bullets[(bulletPtr + i) % bullets.length];
            if (!b.active) {
                bulletPtr = (bulletPtr + i + 1) % bullets.length;
                b.active = true;
                b.enemy = enemy;
                b.kind = kind;
                b.x = x; b.y = y; b.vx = vx; b.vy = vy;
                b.r = r; b.dmg = dmg; b.life = life;
                b.pierce = false;
                b.slow = 0;
                b.aoe = 0;
                b.homing = 0;
                b.spin = 0;
                b.boomerang = false;
                b.turnAt = 0;
                b.hitT = 0;
                b.tickT = 0;
                return b;
            }
        }
        return null;
    }

    // ===================== Урон =====================

    public void damageEnemy(Enemy e, float dmg, float kbX, float kbY) {
        if (e.deadFlag || e.hp <= 0) return;
        // Крит
        boolean crit = rnd.nextFloat() < critCh;
        float d = dmg * dmgMul * (crit ? critMul : 1f);
        e.hp -= d;
        e.hitFlash = 0.12f;
        if (kbX != 0 || kbY != 0) {
            float k = e.type == E_BOSS || e.elite ? 2f : 14f;
            e.x += kbX * k;
            e.y += kbY * k;
        }
        if (crit) {
            dtext(e.x, e.y - e.r, String.valueOf((int) d), Art.GOLD, 34);
            hitStop = Math.max(hitStop, 0.035f); // микро-панч удара
            burst(e.x, e.y, 4, 0xFFFFE066, 200, 0.3f, 5);
        } else if (rnd.nextInt(3) == 0) {
            dtext(e.x, e.y - e.r, String.valueOf((int) d), 0xCCFFFFFF, 24);
        }
        if (e.hp <= 0) killEnemy(e);
    }

    public void damageArea(float x, float y, float r, float dmg, int color) {
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.deadFlag) continue;
            float dx = e.x - x, dy = e.y - y;
            float rr = r + e.r;
            if (dx * dx + dy * dy <= rr * rr) {
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                float nx = len > 0 ? dx / len : 0, ny = len > 0 ? dy / len : 0;
                damageEnemy(e, dmg, nx, ny);
            }
        }
        if (color != 0) ring(x, y, 0.35f, r * 0.3f, r * 1.1f, color);
    }

    /** Урон по линии (лазер). Возвращает число задетых. */
    public int damageLine(float x0, float y0, float x1, float y1, float width, float dmg) {
        float dx = x1 - x0, dy = y1 - y0;
        float len2 = dx * dx + dy * dy;
        if (len2 < 1) return 0;
        float len = (float) Math.sqrt(len2);
        float nx = dx / len, ny = dy / len;
        int hits = 0;
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.deadFlag) continue;
            float ex = e.x - x0, ey = e.y - y0;
            float t = ex * nx + ey * ny;               // проекция
            if (t < -e.r || t > len + e.r) continue;
            float cx = Math.max(0, Math.min(len, t));
            float ddx = ex - cx * nx, ddy = ey - cx * ny;
            float rr = width + e.r;
            if (ddx * ddx + ddy * ddy <= rr * rr) {
                damageEnemy(e, dmg, nx, ny);
                hits++;
            }
        }
        return hits;
    }

    public void killEnemy(Enemy e) {
        if (e.deadFlag || e.hp > 0) return;
        e.deadFlag = true;
        kills++;
        comboN++;
        comboT = 2.5f;
        if (comboN > comboBest) comboBest = comboN;
        // Killstreak
        streakN++;
        streakT = 4f;
        // Вампиризм-реликвия
        if (relics[R_VAMP] && rnd.nextInt(10) == 0) {
            hp = Math.min(hpMax, hp + 2);
            part(e.x, e.y, 0, -60, 0.5f, 6, 0, Art.HEAL);
        }
        if (e.elite) elitesKilled++;
        if (e.type == E_GREED) greedKilled++;
        if (e.type == E_BOSS && !e.mini) bossesKilled++;

        float[] tab = ETAB[e.type];
        int color = e.elite ? Art.ELITE : (int) tab[4];

        // Взрыв частиц
        if (e.elite || e.type == E_BOSS) {
            explosion(e.x, e.y, e.r * 2.4f, color);
        }
        burst(e.x, e.y, e.elite ? 26 : (e.type == E_BOSS ? 60 : 10),
                color, e.type == E_BOSS ? 400 : 160, 0.5f, e.r * 0.5f);
        Sound.play(e.type == E_BOSS ? Sound.BOOM : Sound.KILL);
        if (e.type == E_BOSS || e.elite) shake = Math.max(shake, e.type == E_BOSS ? 26 : 10);

        // Дроп
        float xpVal = tab[5] * (e.elite ? 10f : 1f);
        int nGems = Math.min(6, 1 + (int) (xpVal / 8f));
        int per = Math.max(1, Math.round(xpVal / nGems));
        for (int i = 0; i < nGems; i++) {
            int kind = per >= 12 ? PU_XP3 : (per >= 4 ? PU_XP2 : PU_XP1);
            spawnPickup(kind, e.x + (rnd.nextFloat() - 0.5f) * e.r,
                    e.y + (rnd.nextFloat() - 0.5f) * e.r, per);
        }
        float goldCh = tab[6] * (e.elite ? 4f : 1f);
        if (rnd.nextFloat() < goldCh || e.elite || e.type == E_BOSS) {
            int g = e.elite ? 25 : (e.type == E_BOSS ? (e.mini ? 150 : 250) : 2);
            spawnPickup(PU_GOLD, e.x + (rnd.nextFloat() - 0.5f) * e.r,
                    e.y + (rnd.nextFloat() - 0.5f) * e.r, g);
        }
        // Комбо-бонус золота
        if (comboN >= 20 && rnd.nextFloat() < 0.30f) {
            spawnPickup(PU_GOLD, e.x, e.y, 2);
        }
        // Жадина сыплет золотом
        if (e.type == E_GREED) {
            for (int i = 0; i < 6; i++) {
                spawnPickup(PU_GOLD, e.x + (rnd.nextFloat() - 0.5f) * 60,
                        e.y + (rnd.nextFloat() - 0.5f) * 60, 12);
            }
        }
        // Редкие дропы
        if (rnd.nextFloat() < 0.012f) spawnPickup(PU_HEAL, e.x, e.y, 30);
        if (rnd.nextFloat() < 0.006f) spawnPickup(PU_MAGNET, e.x, e.y, 0);
        if (rnd.nextFloat() < 0.004f) spawnPickup(PU_BOMB, e.x, e.y, 0);
        if (rnd.nextFloat() < 0.005f) spawnPickup(PU_SHIELD, e.x, e.y, 0);
        if (rnd.nextFloat() < 0.004f) spawnPickup(PU_GOLD2, e.x, e.y, 0);

        // Особые смерти
        if (e.type == E_SPLITTER) {
            for (int i = 0; i < 2; i++) {
                spawnEnemy(E_MITE, e.x + (i == 0 ? -20 : 20), e.y, false);
            }
        }
        if (e.type == E_BOMBER) {
            // Взрыв: урон игроку если рядом
            ring(e.x, e.y, 0.4f, 20, 130, Art.GOLD);
            burst(e.x, e.y, 20, Art.GOLD, 260, 0.5f, 10);
            float dx = px - e.x, dy = py - e.y;
            if (dx * dx + dy * dy < 150 * 150) hurtPlayer(e.dmg);
            Sound.play(Sound.BOOM);
        }
        if (e.elite) {
            spawnPickup(PU_CHEST, e.x, e.y, 0);
        }
        if (e.type == E_BOSS && !e.mini) {
            // Полноценный босс всегда дарит реликвию
            int rid = randomMissingRelic();
            if (rid >= 0) spawnPickup(PU_RELIC, e.x, e.y, rid);
        }
        if (e.type == E_BOSS) {
            bossAlive = false;
            slowmo = e.mini ? 0.8f : 1.6f;
            shake = e.mini ? 16 : 30;
            explosion(e.x, e.y, 200, Art.GOLD);
            if (!e.mini && !endless && time >= 1150f) victory = true;
        }
    }

    public void hurtPlayer(float dmg) {
        if (dead || hurtCd > 0) return;
        if (shield) {
            shield = false;
            hurtCd = 0.5f;
            ring(px, py, 0.4f, 60, 20, 0xFF6BD4FF);
            Sound.play(Sound.HURT);
            return;
        }
        float d = dmg * (1f - Math.min(0.75f, armor));
        hp -= d;
        hurtCd = 0.35f;
        flashScreen = 0.5f;
        shake = Math.max(shake, 8);
        Sound.play(Sound.HURT);
        game.vibrate(60);
        if (hp <= 0) {
            hp = 0;
            dead = true;
            slowmo = 2.2f;
            shake = 24;
            burst(px, py, 60, Art.PLAYER, 320, 1f, 14);
            Sound.play(Sound.BOOM);
            game.vibrate(250);
        }
    }

    // ===================== Сетка =====================

    private void buildGrid() {
        java.util.Arrays.fill(gridHead, -1);
        if (gridNext.length < enemies.size()) return;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            int cx = (int) clamp(e.x / CELL, 0, GRID_N - 1);
            int cy = (int) clamp(e.y / CELL, 0, GRID_N - 1);
            int idx = cy * GRID_N + cx;
            gridNext[i] = gridHead[idx];
            gridHead[idx] = i;
        }
    }

    // ===================== Апдейт =====================

    private final float[] tmp = new float[2];

    public void update(float dt, Input input) {
        if (dead) {
            // После смерти — только частицы и эффекты
            updateParticles(dt);
            shake = Math.max(0, shake - dt * 30);
            return;
        }

        time += dt;

        // ---- Игрок ----
        float mx = input.moveX(), my = input.moveY();
        float ml = (float) Math.sqrt(mx * mx + my * my);
        if (ml > 1) {
            mx /= ml;
            my /= ml;
        }
        if (ml > 0.01f) {
            faceX = mx;
            faceY = my;
        }
        pvx += (mx * speed - pvx) * Math.min(1, dt * 12);
        pvy += (my * speed - pvy) * Math.min(1, dt * 12);
        px = clamp(px + pvx * dt, 30, ARENA - 30);
        py = clamp(py + pvy * dt, 30, ARENA - 30);

        if (regen > 0) hp = Math.min(hpMax, hp + regen * dt);
        if (hurtCd > 0) hurtCd -= dt;
        if (flashScreen > 0) flashScreen -= dt * 2;

        // Трейл
        trailTimer -= dt;
        if (trailTimer <= 0 && (Math.abs(pvx) > 30 || Math.abs(pvy) > 30)) {
            trailTimer = 0.03f;
            trailX[trailPtr] = px;
            trailY[trailPtr] = py;
            trailPtr = (trailPtr + 1) % trailX.length;
        }

        // Комбо
        if (comboT > 0) {
            comboT -= dt;
            if (comboT <= 0) comboN = 0;
        }
        // Killstreak
        if (streakT > 0) {
            streakT -= dt;
            if (streakT <= 0) {
                streakN = 0;
                streakShown = 0;
            }
        }
        if (goldBoostT > 0) goldBoostT -= dt;

        // ---- Камера ----
        camX += (px - camX) * Math.min(1, dt * 6);
        camY += (py - camY) * Math.min(1, dt * 6);

        buildGrid();

        // ---- Оружие ----
        for (int i = 0; i < weapons.size(); i++) {
            weapons.get(i).update(dt, this);
        }

        // ---- Враги ----
        updateEnemies(dt);

        // ---- Снаряды ----
        updateBullets(dt);

        // ---- Уборка погибших ----
        sweepDead();

        // ---- Пикапы ----
        updatePickups(dt);

        // ---- Частицы ----
        updateParticles(dt);

        // ---- Эффекты ----
        shake = Math.max(0, shake - dt * 30);
        for (int i = zaps.size() - 1; i >= 0; i--) {
            float[] z = zaps.get(i);
            z[4] -= dt;
            if (z[4] <= 0) zaps.remove(i);
        }
        for (int i = beams.size() - 1; i >= 0; i--) {
            float[] b = beams.get(i);
            b[4] -= dt;
            if (b[4] <= 0) beams.remove(i);
        }

        // Контактный урон врагов
        contactDamage();
    }

    /** Удалить погибших (swap-remove), сохраняя индексы сетки до конца кадра. */
    private void sweepDead() {
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.deadFlag || e.hp <= 0) {
                if (!e.deadFlag) {
                    e.deadFlag = true;
                }
                enemies.set(i, enemies.get(enemies.size() - 1));
                enemies.remove(enemies.size() - 1);
            }
        }
    }

    private void updateEnemies(float dt) {
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.deadFlag) continue;
            if (e.spawnT > 0) {
                e.spawnT -= dt;
                continue; // материализуется — не двигается и неуязвим? бьётся, но стоит
            }
            if (e.hitFlash > 0) e.hitFlash -= dt;
            if (e.slowT > 0) e.slowT -= dt;
            if (e.bladeCd > 0) e.bladeCd -= dt;

            float dx = px - e.x, dy = py - e.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float nx = dist > 0 ? dx / dist : 0, ny = dist > 0 ? dy / dist : 0;
            float spd = e.speed * (e.slowT > 0 ? 0.5f : 1f);

            switch (e.type) {
                case E_SPITTER: {
                    // Держит дистанцию и стреляет
                    e.shootCd -= dt;
                    if (dist > 320) {
                        e.x += nx * spd * dt;
                        e.y += ny * spd * dt;
                    } else if (dist < 220) {
                        e.x -= nx * spd * 0.7f * dt;
                        e.y -= ny * spd * 0.7f * dt;
                    }
                    if (e.shootCd <= 0 && dist < 520) {
                        e.shootCd = 2.2f;
                        float bs = 240;
                        Bullet b = bullet(true, 4, e.x, e.y, nx * bs, ny * bs, 9,
                                e.dmg, 3f);
                        if (b != null) b.spin = 0;
                    }
                    break;
                }
                case E_BAT: {
                    // Пикирует с виражами
                    e.t += dt;
                    float wob = (float) Math.sin(e.t * 6f + e.x * 0.01f) * 0.8f;
                    e.x += (nx + -ny * wob) * spd * dt;
                    e.y += (ny + nx * wob) * spd * dt;
                    break;
                }
                case E_GREED: {
                    // Убегает от игрока с виляниями, исчезает через 14с
                    e.t += dt;
                    if (e.t > 14f) {
                        e.deadFlag = true; // сбежал — без дропа
                        break;
                    }
                    float wob = (float) Math.sin(e.t * 5f) * 0.7f;
                    float fx = -nx + (-ny) * wob, fy = -ny + nx * wob;
                    float fl = (float) Math.sqrt(fx * fx + fy * fy);
                    e.x += fx / fl * spd * dt;
                    e.y += fy / fl * spd * dt;
                    // Сыпит монетками
                    if (rnd.nextFloat() < dt * 3f) {
                        spawnPickup(PU_GOLD, e.x, e.y, 1);
                    }
                    break;
                }
                case E_BOSS: {
                    updateBoss(e, dt, nx, ny, dist);
                    break;
                }
                default: {
                    e.x += nx * spd * dt;
                    e.y += ny * spd * dt;
                    break;
                }
            }

            // Разлипание соседей (через сетку, дёшево)
            if (e.type != E_BOSS) {
                int cx = (int) clamp(e.x / CELL, 0, GRID_N - 1);
                int cy = (int) clamp(e.y / CELL, 0, GRID_N - 1);
                int x0 = Math.max(0, cx - 1), x1 = Math.min(GRID_N - 1, cx + 1);
                int y0 = Math.max(0, cy - 1), y1 = Math.min(GRID_N - 1, cy + 1);
                for (int gy = y0; gy <= y1; gy++) {
                    for (int gx = x0; gx <= x1; gx++) {
                        int j = gridHead[gy * GRID_N + gx];
                        int guard = 0;
                        while (j != -1 && guard++ < 64) {
                            if (j != i) {
                                Enemy o = enemies.get(j);
                                if (o.deadFlag) { j = gridNext[j]; continue; }
                                float ox = e.x - o.x, oy = e.y - o.y;
                                float rr = e.r + o.r;
                                float d2 = ox * ox + oy * oy;
                                if (d2 > 0.01f && d2 < rr * rr) {
                                    float d = (float) Math.sqrt(d2);
                                    float push = (rr - d) * 0.5f;
                                    e.x += ox / d * push * 0.5f;
                                    e.y += oy / d * push * 0.5f;
                                }
                            }
                            j = gridNext[j];
                        }
                    }
                }
            }

            e.x = clamp(e.x, 10, ARENA - 10);
            e.y = clamp(e.y, 10, ARENA - 10);
        }
    }

    private void updateBoss(Enemy e, float dt, float nx, float ny, float dist) {
        e.t += dt;
        e.shootCd -= dt;

        // --- Королева Роя: свои паттерны ---
        if (e.queen) {
            updateQueen(e, dt, nx, ny, dist);
            if (dist < e.r + 24) hurtPlayer(e.dmg * dt * 2.5f);
            return;
        }

        if (e.aiState == 0) {
            // Преследование
            e.x += nx * e.speed * dt;
            e.y += ny * e.speed * dt;
            if (e.shootCd <= 0) {
                e.shootCd = 2.6f;
                e.aiState = 1 + (rnd.nextInt(3)); // 1..3
                e.t = 0;
            }
        } else if (e.aiState == 1) {
            // Кольцо пуль
            if (e.t > 0.7f) {
                int n = 18;
                float off = rnd.nextFloat() * 0.3f;
                for (int k = 0; k < n; k++) {
                    float a = (float) (Math.PI * 2) * k / n + off;
                    Bullet b = bullet(true, 4, e.x, e.y,
                            (float) Math.cos(a) * 200, (float) Math.sin(a) * 200,
                            11, e.dmg * 0.5f, 6f);
                }
                Sound.play(Sound.LASER);
                e.aiState = 0;
            }
        } else if (e.aiState == 2) {
            // Рывок в игрока
            if (e.t < 0.5f) {
                e.x -= nx * 60 * dt; // откат
                e.y -= ny * 60 * dt;
            } else if (e.t < 1.1f) {
                e.x += nx * 620 * dt;
                e.y += ny * 620 * dt;
                burst(e.x, e.y, 2, BOSS_COL(), 120, 0.3f, 12);
            } else {
                shake = Math.max(shake, 12);
                ring(e.x, e.y, 0.45f, 40, 170, BOSS_COL());
                burst(e.x, e.y, 24, BOSS_COL(), 320, 0.6f, 10);
                // Урон игроку в зоне
                float dx = px - e.x, dy = py - e.y;
                if (dx * dx + dy * dy < 160 * 160) hurtPlayer(e.dmg);
                e.aiState = 0;
            }
        } else {
            // Саммоны
            if (e.t > 0.6f) {
                for (int k = 0; k < 5; k++) {
                    float a = rnd.nextFloat() * (float) (Math.PI * 2);
                    spawnEnemy(rnd.nextFloat() < 0.5f ? E_MITE : E_BAT,
                            e.x + (float) Math.cos(a) * 150,
                            e.y + (float) Math.sin(a) * 150, false);
                }
                ring(e.x, e.y, 0.4f, 40, 200, BOSS_COL());
                e.aiState = 0;
            }
        }
        // Контактный урон босса
        if (dist < e.r + 24) hurtPlayer(e.dmg * dt * 2.5f);
    }

    private int BOSS_COL() {
        return (int) ETAB[E_BOSS][4];
    }

    private static final int QUEEN_COL = 0xFFB388FF;

    /** Королева Роя: спираль пуль и телепорт-рывки. */
    private void updateQueen(Enemy e, float dt, float nx, float ny, float dist) {
        switch (e.aiState) {
            case 0: {
                // Плавное преследование
                e.x += nx * e.speed * dt;
                e.y += ny * e.speed * dt;
                // След из частиц
                if (rnd.nextFloat() < dt * 20) {
                    part(e.x + (rnd.nextFloat() - 0.5f) * e.r,
                            e.y + (rnd.nextFloat() - 0.5f) * e.r,
                            0, -40, 0.5f, 8, 0, QUEEN_COL);
                }
                if (e.shootCd <= 0) {
                    e.shootCd = 3.0f;
                    e.aiState = rnd.nextFloat() < 0.55f ? 4 : 5;
                    e.t = 0;
                }
                break;
            }
            case 4: {
                // Спиральный обстрел: 3 ствола вращаются
                if (e.t < 2.4f) {
                    if (e.shootCd <= 0) {
                        e.shootCd = 0.07f;
                        float base = e.t * 5.5f;
                        for (int k = 0; k < 3; k++) {
                            float a = base + k * (float) (Math.PI * 2 / 3);
                            bullet(true, 4, e.x, e.y,
                                    (float) Math.cos(a) * 210,
                                    (float) Math.sin(a) * 210, 9,
                                    e.dmg * 0.4f, 5f);
                        }
                    }
                    e.shootCd -= dt;
                } else {
                    e.aiState = 0;
                    e.shootCd = 1.2f;
                }
                break;
            }
            case 5: {
                // Телепорт за спину игрока + кольцо
                if (e.t < 0.6f) {
                    // Заряд: сжимается
                    burst(e.x, e.y, 2, QUEEN_COL, 100, 0.3f, 8);
                } else if (e.t < 0.7f) {
                    // Скачок
                    burst(e.x, e.y, 20, QUEEN_COL, 260, 0.5f, 10);
                    ring(e.x, e.y, 0.5f, 30, 220, QUEEN_COL);
                    float a = rnd.nextFloat() * (float) (Math.PI * 2);
                    e.x = clamp(px + (float) Math.cos(a) * 260, 60, ARENA - 60);
                    e.y = clamp(py + (float) Math.sin(a) * 260, 60, ARENA - 60);
                    e.t = 0.7f;
                    shake = Math.max(shake, 8);
                } else if (e.t < 1.0f) {
                    // Появление
                    ring(e.x, e.y, 0.4f, 200, 40, QUEEN_COL);
                    e.t = 1.5f;
                } else {
                    // Кольцо пуль
                    int n = 14;
                    for (int k = 0; k < n; k++) {
                        float a = (float) (Math.PI * 2) * k / n;
                        bullet(true, 4, e.x, e.y,
                                (float) Math.cos(a) * 230,
                                (float) Math.sin(a) * 230, 10,
                                e.dmg * 0.4f, 5f);
                    }
                    Sound.play(Sound.LASER);
                    e.aiState = 0;
                    e.shootCd = 1.6f;
                }
                break;
            }
            default:
                e.aiState = 0;
                break;
        }
    }

    private void contactDamage() {
        float pr = 22;
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.type == E_BOSS || e.type == E_GREED || e.deadFlag) continue;
            float dx = px - e.x, dy = py - e.y;
            float rr = pr + e.r;
            if (dx * dx + dy * dy < rr * rr) {
                hurtPlayer(e.dmg);
            }
        }
    }

    private void updateBullets(float dt) {
        for (Bullet b : bullets) {
            if (!b.active) continue;
            b.life -= dt;
            if (b.hitT > 0) b.hitT -= dt;
            if (b.life <= 0) {
                b.active = false;
                continue;
            }

            // Чёрная дыра: стоит на месте, тянет и жмёт
            if (b.kind == 6) {
                b.tickT -= dt;
                if (b.tickT <= 0) {
                    b.tickT = 0.3f;
                    // Притяжение врагов
                    for (int i = 0; i < enemies.size(); i++) {
                        Enemy e = enemies.get(i);
                        if (e.deadFlag || e.type == E_BOSS) continue;
                        float dx = b.x - e.x, dy = b.y - e.y;
                        float d2 = dx * dx + dy * dy;
                        if (d2 < 320 * 320 && d2 > 4) {
                            float d = (float) Math.sqrt(d2);
                            e.x += dx / d * 110 * dt;
                            e.y += dy / d * 110 * dt;
                        }
                    }
                    damageArea(b.x, b.y, 170, b.dmg, 0);
                }
                // Спиральные частицы
                float a = time * 4f;
                part(b.x + (float) Math.cos(a) * 40, b.y + (float) Math.sin(a) * 40,
                        (float) Math.cos(a + 1.57f) * 90, (float) Math.sin(a + 1.57f) * 90,
                        0.4f, 5, 0, 0xFFB388FF);
                part(b.x - (float) Math.cos(a) * 40, b.y - (float) Math.sin(a) * 40,
                        -(float) Math.cos(a + 1.57f) * 90, -(float) Math.sin(a + 1.57f) * 90,
                        0.4f, 5, 0, 0xFF6BD4FF);
            }

            // Бумеранг: после половины жизни возвращается к игроку
            if (b.boomerang) {
                if (b.life < b.turnAt) {
                    float dx = px - b.x, dy = py - b.y;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < 46) {
                        b.active = false;
                        continue;
                    }
                    float sp = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
                    float k = Math.min(1, dt * 6f);
                    b.vx += (dx / d * sp - b.vx) * k;
                    b.vy += (dy / d * sp - b.vy) * k;
                    float ns = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
                    if (ns > 0) {
                        b.vx = b.vx / ns * sp;
                        b.vy = b.vy / ns * sp;
                    }
                }
            }

            // Самонаведение (ракеты/сферы)
            if (b.homing > 0 && !b.enemy) {
                Enemy tgt = nearestEnemy(b.x, b.y, 700);
                if (tgt != null) {
                    float dx = tgt.x - b.x, dy = tgt.y - b.y;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d > 1) {
                        float sp = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
                        b.vx += (dx / d * sp - b.vx) * Math.min(1, b.homing * dt);
                        b.vy += (dy / d * sp - b.vy) * Math.min(1, b.homing * dt);
                        float ns = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
                        b.vx = b.vx / ns * sp;
                        b.vy = b.vy / ns * sp;
                    }
                }
            }
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            if (b.x < -50 || b.x > ARENA + 50 || b.y < -50 || b.y > ARENA + 50) {
                b.active = false;
                continue;
            }

            if (b.enemy) {
                // Попадание в игрока
                float dx = px - b.x, dy = py - b.y;
                float rr = 20 + b.r;
                if (dx * dx + dy * dy < rr * rr) {
                    hurtPlayer(b.dmg);
                    b.active = false;
                    continue;
                }
            } else {
                // Попадание во врага (через сетку)
                int cx = (int) clamp(b.x / CELL, 0, GRID_N - 1);
                int cy = (int) clamp(b.y / CELL, 0, GRID_N - 1);
                int x0 = Math.max(0, cx - 1), x1 = Math.min(GRID_N - 1, cx + 1);
                int y0 = Math.max(0, cy - 1), y1 = Math.min(GRID_N - 1, cy + 1);
                boolean dead = false;
                for (int gy = y0; gy <= y1 && !dead; gy++) {
                    for (int gx = x0; gx <= x1 && !dead; gx++) {
                        int j = gridHead[gy * GRID_N + gx];
                        int guard = 0;
                        while (j != -1 && guard++ < 64) {
                            if (b.hitT > 0) break; // перезарядка после удара
                            Enemy e = enemies.get(j);
                            if (e.deadFlag) { j = gridNext[j]; continue; }
                            float dx = e.x - b.x, dy = e.y - b.y;
                            float rr = e.r + b.r;
                            if (dx * dx + dy * dy < rr * rr) {
                                float len = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
                                float kbx = len > 0 ? b.vx / len : 0;
                                float kby = len > 0 ? b.vy / len : 0;
                                if (b.slow > 0) e.slowT = Math.max(e.slowT, 1.5f);
                                damageEnemy(e, b.dmg, kbx, kby);
                                if (b.aoe > 0) {
                                    damageArea(b.x, b.y, b.aoe, b.dmg * 0.6f, 0);
                                    ring(b.x, b.y, 0.3f, b.aoe * 0.4f, b.aoe, Art.PLAYER);
                                    Sound.play(Sound.BOOM);
                                }
                                if (b.pierce || b.boomerang) {
                                    // Пробой: короткая «перезарядка» удара
                                    b.hitT = 0.16f;
                                    dead = true; // выходим из перебора клеток
                                } else {
                                    b.active = false;
                                    dead = true;
                                }
                            }
                            j = gridNext[j];
                        }
                    }
                }
                // Хвостик
                if (b.kind == 2 || b.kind == 1 || b.kind == 5) {
                    part(b.x, b.y, 0, 0, 0.25f, b.r * 0.8f, 0,
                            b.kind == 2 ? Art.PLAYER : (b.kind == 5 ? 0xFF7DE2FF : 0xFF9D6BFF));
                }
            }
        }
    }

    private void updatePickups(float dt) {
        float mag2 = magnetR * magnetR;
        for (Pickup p : pickups) {
            if (!p.active) continue;
            p.t += dt;
            float dx = px - p.x, dy = py - p.y;
            float d2 = dx * dx + dy * dy;
            boolean pullAll = p.mag;
            // Магнит: близко или глобально притянуты
            if (pullAll || d2 < mag2) {
                p.mag = true;
                float d = (float) Math.sqrt(d2);
                if (d > 1) {
                    float pull = 380 + p.t * 500;
                    p.vx += (dx / d) * pull * dt;
                    p.vy += (dy / d) * pull * dt;
                }
            } else {
                p.vx *= 0.92f;
                p.vy *= 0.92f;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            // Подбор
            float pr = 26 + (p.kind == PU_CHEST || p.kind == PU_RELIC ? 18 : 0);
            if (d2 < pr * pr) {
                collect(p);
                p.active = false;
            }
        }
    }

    private void collect(Pickup p) {
        switch (p.kind) {
            case PU_XP1:
            case PU_XP2:
            case PU_XP3:
                addXp(p.val);
                Sound.play(Sound.XP);
                break;
            case PU_GOLD: {
                int got = Math.round(p.val * goldMul * comboGoldMul() * goldBoost());
                goldRun += got;
                Sound.play(Sound.PICKUP);
                dtext(p.x, p.y - 10, "+" + got, Art.GOLD, 26);
                break;
            }
            case PU_HEAL:
                hp = Math.min(hpMax, hp + p.val);
                Sound.play(Sound.HEAL);
                dtext(p.x, p.y - 10, "+" + p.val + " HP", Art.HEAL, 28);
                burst(px, py, 14, Art.HEAL, 120, 0.6f, 8);
                break;
            case PU_MAGNET: {
                for (Pickup q : pickups) {
                    if (q.active && (q.kind == PU_XP1 || q.kind == PU_XP2 || q.kind == PU_XP3)) {
                        q.mag = true;
                    }
                }
                Sound.play(Sound.PICKUP);
                dtext(px, py - 40, "MAGNET", Art.XP, 30);
                break;
            }
            case PU_SHIELD:
                shield = true;
                Sound.play(Sound.HEAL);
                dtext(px, py - 40, "SHIELD", 0xFF6BD4FF, 30);
                break;
            case PU_GOLD2:
                goldBoostT = 20f;
                Sound.play(Sound.PICKUP);
                dtext(px, py - 40, "GOLD x2", Art.GOLD, 30);
                break;
            case PU_RELIC: {
                int rid = p.val;
                if (giveRelic(rid)) {
                    Sound.play(Sound.LEVELUP);
                    dtext(px, py - 40, Upgrades.relicName(rid), Upgrades.relicColor(rid), 30);
                    ring(px, py, 0.7f, 30, 400, Upgrades.relicColor(rid));
                }
                break;
            }
            case PU_BOMB: {
                // Зачистка экрана
                float r = 700;
                for (int i = enemies.size() - 1; i >= 0; i--) {
                    Enemy e = enemies.get(i);
                    if (e.type == E_BOSS) continue;
                    float dx = e.x - px, dy = e.y - py;
                    if (dx * dx + dy * dy < r * r) killEnemy(e);
                }
                ring(px, py, 0.7f, 50, r, Art.WHITE);
                shake = 20;
                slowmo = Math.max(slowmo, 0.5f);
                Sound.play(Sound.BOOM);
                break;
            }
            case PU_CHEST: {
                int g = 40 + rnd.nextInt(40);
                goldRun += Math.round(g * goldMul * goldBoost());
                // 40% шанс реликвии из сундука
                if (rnd.nextFloat() < 0.4f) {
                    int rid = randomMissingRelic();
                    if (rid >= 0 && giveRelic(rid)) {
                        dtext(px, py - 60, Upgrades.relicName(rid),
                                Upgrades.relicColor(rid), 30);
                        Sound.play(Sound.LEVELUP);
                    }
                }
                for (int i = 0; i < 5; i++) {
                    spawnPickup(PU_GOLD, p.x + (rnd.nextFloat() - 0.5f) * 80,
                            p.y + (rnd.nextFloat() - 0.5f) * 80, 8);
                }
                // Сундук может дать и уровень
                addXp(Math.max(6, xpNext() / 3));
                dtext(p.x, p.y - 20, "+" + g, Art.GOLD, 34);
                Sound.play(Sound.LEVELUP);
                break;
            }
            default:
                break;
        }
    }

    private void updateParticles(float dt) {
        for (Part p : parts) {
            if (!p.active) continue;
            p.life -= dt;
            if (p.life <= 0) {
                p.active = false;
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= 0.95f;
            p.vy *= 0.95f;
        }
        for (DText d : dtexts) {
            if (!d.active) continue;
            d.life -= dt;
            if (d.life <= 0) d.active = false;
            d.y -= dt * 60;
        }
    }

    // ===================== Поиск =====================

    public Enemy nearestEnemy(float x, float y, float maxR) {
        Enemy best = null;
        float bd = maxR * maxR;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (e.deadFlag) continue;
            float dx = e.x - x, dy = e.y - y;
            float d2 = dx * dx + dy * dy;
            if (d2 < bd) {
                bd = d2;
                best = e;
            }
        }
        return best;
    }

    public static float clamp(float v, float a, float b) {
        return v < a ? a : (v > b ? b : v);
    }

    /** Формат MM:SS. */
    public static String fmtTime(float t) {
        int m = (int) (t / 60);
        int s = (int) (t % 60);
        return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }
}
