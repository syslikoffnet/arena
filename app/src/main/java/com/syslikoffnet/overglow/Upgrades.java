package com.syslikoffnet.overglow;

import java.util.ArrayList;
import java.util.Random;

/**
 * Усиления при левел-апе: 3 случайные карточки.
 * Новое оружие (до 4 слотов), уровень оружия (до 8), пассивки (до 5).
 */
public final class Upgrades {

    private Upgrades() {}

    // Пассивки
    public static final int P_DMG = 0, P_CD = 1, P_HP = 2, P_SPD = 3, P_MAGNET = 4,
            P_ARMOR = 5, P_XP = 6, P_REGEN = 7, P_CRIT = 8;

    public static final int PASSIVES = 9;
    public static final int MAX_PASSIVE = 5;
    public static final int MAX_WEAPONS = 6;

    /** Стартовое оружие героев. */
    public static final int[] CHAR_WEAPON = {
            Weapons.W_PULSE, Weapons.W_CHAIN, Weapons.W_BLADES, Weapons.W_SPHERES
    };

    /** Карточка выбора. */
    public static final class Card {
        public int kind;   // 0 новое оружие, 1 уровень оружия, 2 пассивка, 3 пробуждение
        public int id;     // id оружия / пассивки
        public String title;
        public String sub;

        Card(int kind, int id, String title, String sub) {
            this.kind = kind;
            this.id = id;
            this.title = title;
            this.sub = sub;
        }
    }

    private static final Random RND = new Random();

    /** Уровни пассивок текущего забега. */
    public static int[] passiveLvls = new int[PASSIVES];

    /** Цена мета-усиления следующего уровня (0..4). */
    public static int metaPrice(int lvl) {
        switch (lvl) {
            case 0: return 120;
            case 1: return 260;
            case 2: return 420;
            case 3: return 640;
            default: return 900;
        }
    }

    /** Цена открытия героя. */
    public static int charPrice(int i) {
        switch (i) {
            case 2: return 800;
            case 3: return 1500;
            default: return 0;
        }
    }

    public static void reset() {
        passiveLvls = new int[PASSIVES];
    }

    /** Имена реликвий. */
    public static String relicName(int id) {
        switch (id) {
            case World.R_VAMP: return L.t("r_vamp");
            case World.R_DOUBLE: return L.t("r_double");
            case World.R_GLASS: return L.t("r_glass");
            case World.R_CHRONO: return L.t("r_chrono");
            case World.R_MAGNET: return L.t("r_magnet");
            case World.R_AEGIS: return L.t("r_aegis");
            case World.R_GREED: return L.t("r_greed");
            case World.R_FURY: return L.t("r_fury");
            case World.R_TITAN: return L.t("r_titan");
            case World.R_LUCK: return L.t("r_luck");
            default: return "?";
        }
    }

    public static String relicDesc(int id) {
        switch (id) {
            case World.R_VAMP: return L.t("rd_vamp");
            case World.R_DOUBLE: return L.t("rd_double");
            case World.R_GLASS: return L.t("rd_glass");
            case World.R_CHRONO: return L.t("rd_chrono");
            case World.R_MAGNET: return L.t("rd_magnet");
            case World.R_AEGIS: return L.t("rd_aegis");
            case World.R_GREED: return L.t("rd_greed");
            case World.R_FURY: return L.t("rd_fury");
            case World.R_TITAN: return L.t("rd_titan");
            case World.R_LUCK: return L.t("rd_luck");
            default: return "";
        }
    }

    public static int relicColor(int id) {
        switch (id) {
            case World.R_VAMP: return 0xFFFF4D6D;
            case World.R_DOUBLE: return Art.PLAYER;
            case World.R_GLASS: return 0xFF8AF5FF;
            case World.R_CHRONO: return 0xFF9DFF6B;
            case World.R_MAGNET: return 0xFFB388FF;
            case World.R_AEGIS: return 0xFF9BA8B8;
            case World.R_GREED: return Art.GOLD;
            case World.R_FURY: return 0xFFFF8A3D;
            case World.R_TITAN: return 0xFFB8C6D9;
            case World.R_LUCK: return 0xFFFFE066;
            default: return Art.WHITE;
        }
    }

    /** Три случайные карточки для текущего мира. */
    public static Card[] roll3(World world) {
        ArrayList<Card> pool = new ArrayList<>();

        // Новое оружие
        if (world.weapons.size() < MAX_WEAPONS) {
            for (int wid = 0; wid < Weapons.COUNT; wid++) {
                if (world.weapon(wid) == null) {
                    pool.add(new Card(0, wid,
                            Weapons.name(wid) + " — " + L.t("newWeapon"),
                            Weapons.desc(wid, 1)));
                }
            }
        }
        // Прокачка своего оружия
        for (Weapons w : world.weapons) {
            if (w.lvl < Weapons.MAX_LVL) {
                pool.add(new Card(1, w.id,
                        Weapons.name(w.id) + " — " + L.t("weaponLvl"),
                        Weapons.desc(w.id, w.lvl + 1)));
            }
        }
        // Пробуждение оружия (на макс. уровне)
        for (Weapons w : world.weapons) {
            if (w.lvl >= Weapons.MAX_LVL && !w.awakened) {
                pool.add(new Card(3, w.id,
                        "★ " + Weapons.name(w.id) + " — " + L.t("awaken"),
                        L.t("awakenDesc")));
            }
        }
        // Пассивки
        for (int p = 0; p < PASSIVES; p++) {
            if (passiveLvls[p] < MAX_PASSIVE) {
                pool.add(new Card(2, p, passiveName(p) + " — " + L.t("passiveLvl"),
                        passiveDesc(p)));
            }
        }

        // Выбор 3 разных
        Card[] out = new Card[3];
        int n = Math.min(3, pool.size());
        for (int i = 0; i < n; i++) {
            int idx = RND.nextInt(pool.size());
            out[i] = pool.remove(idx);
        }
        // Если пул кончился — чем заполнить
        for (int i = n; i < 3; i++) {
            out[i] = new Card(2, P_HP, L.t("pd_hp"), "+20 HP");
        }
        return out;
    }

    /** Применить карточку. */
    public static void apply(World world, Card card) {
        switch (card.kind) {
            case 0:
                world.addWeapon(card.id);
                break;
            case 1: {
                Weapons w = world.weapon(card.id);
                if (w != null && w.lvl < Weapons.MAX_LVL) w.lvl++;
                break;
            }
            case 2:
                applyPassive(world, card.id);
                break;
            case 3: {
                Weapons w = world.weapon(card.id);
                if (w != null && !w.awakened) {
                    w.awakened = true;
                    world.ring(world.px, world.py, 0.9f, 40, 700,
                            Upgrades.relicColor(2));
                    world.burst(world.px, world.py, 40, Art.GOLD, 320, 0.8f, 10);
                    Sound.play(Sound.LEVELUP);
                }
                break;
            }
            default:
                break;
        }
    }

    private static void applyPassive(World world, int p) {
        passiveLvls[p]++;
        switch (p) {
            case P_DMG: world.dmgMul += 0.10f; break;
            case P_CD: world.cdMul = Math.max(0.4f, world.cdMul - 0.08f); break;
            case P_HP:
                world.hpMax += 20;
                world.hp = Math.min(world.hpMax, world.hp + 20);
                break;
            case P_SPD: world.speed *= 1.08f; break;
            case P_MAGNET: world.magnetR *= 1.3f; break;
            case P_ARMOR: world.armor = Math.min(0.6f, world.armor + 0.05f); break;
            case P_XP: world.xpMul += 0.10f; break;
            case P_REGEN: world.regen += 0.8f; break;
            case P_CRIT: world.critCh = Math.min(0.6f, world.critCh + 0.05f); break;
            default: break;
        }
    }

    public static String passiveName(int p) {
        switch (p) {
            case P_DMG: return L.t("p_dmg");
            case P_CD: return L.t("p_cd");
            case P_HP: return L.t("p_hp");
            case P_SPD: return L.t("p_spd");
            case P_MAGNET: return L.t("p_magnet");
            case P_ARMOR: return L.t("p_armor");
            case P_XP: return L.t("p_xp");
            case P_REGEN: return L.t("p_regen");
            case P_CRIT: return L.t("p_crit");
            default: return "?";
        }
    }

    public static String passiveDesc(int p) {
        switch (p) {
            case P_DMG: return L.t("pd_dmg");
            case P_CD: return L.t("pd_cd");
            case P_HP: return L.t("pd_hp");
            case P_SPD: return L.t("pd_spd");
            case P_MAGNET: return L.t("pd_magnet");
            case P_ARMOR: return L.t("pd_armor");
            case P_XP: return L.t("pd_xp");
            case P_REGEN: return L.t("pd_regen");
            case P_CRIT: return L.t("pd_crit");
            default: return "";
        }
    }

    public static int passiveColor(int p) {
        switch (p) {
            case P_DMG: return 0xFFFF6B6B;
            case P_CD: return 0xFFF5C542;
            case P_HP: return Art.HEAL;
            case P_SPD: return 0xFF6BD4FF;
            case P_MAGNET: return 0xFFB388FF;
            case P_ARMOR: return 0xFF9BA8B8;
            case P_XP: return Art.XP;
            case P_REGEN: return 0xFF34D399;
            case P_CRIT: return 0xFFFF8A3D;
            default: return Art.WHITE;
        }
    }
}
