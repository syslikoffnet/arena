package com.syslikoffnet.overglow;

import java.util.HashMap;

/** Локализация: русский / английский. Все строки игры — здесь. */
public final class L {

    private L() {}

    public static boolean ru = true;

    private static final HashMap<String, String[]> D = new HashMap<>();

    private static void add(String k, String ru, String en) {
        D.put(k, new String[]{ru, en});
    }

    static {
        add("play", "ИГРАТЬ", "PLAY");
        add("shop", "МАГАЗИН", "SHOP");
        add("stats", "СТАТИСТИКА", "STATS");
        add("settings", "НАСТРОЙКИ", "SETTINGS");
        add("howto", "КАК ИГРАТЬ", "HOW TO PLAY");
        add("chars", "ВЫБОР ГЕРОЯ", "CHOOSE HERO");
        add("start", "В БОЙ", "START");
        add("back", "НАЗАД", "BACK");
        add("resume", "ПРОДОЛЖИТЬ", "RESUME");
        add("restart", "ЗАНОВО", "RESTART");
        add("toMenu", "В МЕНЮ", "TO MENU");
        add("paused", "ПАУЗА", "PAUSED");
        add("levelup", "НОВЫЙ УРОВЕНЬ!", "LEVEL UP!");
        add("choose", "Выбери усиление", "Choose an upgrade");
        add("gameover", "СВЕТ ПОГАС", "LIGHTS OUT");
        add("survived", "Ты продержался", "You survived");
        add("victory", "ПОБЕДА!", "VICTORY!");
        add("victorySub", "Король глитчей повержен", "The Glitch King is defeated");
        add("endless", "БЕСКОНЕЧНЫЙ РЕЖИМ", "ENDLESS MODE");
        add("kills", "убийств", "kills");
        add("level", "уровень", "level");
        add("goldEarned", "золота заработано", "gold earned");
        add("best", "лучшее время", "best time");
        add("bossIncoming", "!!! БОСС ПРИБЛИЖАЕТСЯ !!!", "!!! BOSS INCOMING !!!");
        add("newWeapon", "НОВОЕ ОРУЖИЕ", "NEW WEAPON");
        add("weaponLvl", "ОРУЖИЕ +УР.", "WEAPON UP");
        add("passiveLvl", "ПАССИВКА +УР.", "PASSIVE UP");
        add("maxed", "МАКСИМУМ", "MAXED");
        add("locked", "ЗАКРЫТО", "LOCKED");
        add("buy", "КУПИТЬ", "BUY");
        add("select", "ВЫБРАТЬ", "SELECT");
        add("selected", "ВЫБРАН", "SELECTED");
        add("sound", "Звуки", "Sound FX");
        add("music", "Музыка", "Music");
        add("vibrate", "Вибрация", "Vibration");
        add("language", "Язык", "Language");
        add("resetAsk", "Сбросить весь прогресс?", "Reset all progress?");
        add("reset", "СБРОС ПРОГРЕССА", "RESET PROGRESS");
        add("yes", "Да", "Yes");
        add("no", "Нет", "No");
        add("htMove", "Веди пальцем в любом месте экрана — это джойстик", "Drag your finger anywhere — that's the joystick");
        add("htAuto", "Оружие стреляет само, твоя задача — выживать и собирать опыт", "Weapons fire automatically, just survive and grab XP");
        add("htLevel", "Собирай кристаллы опыта, повышай уровень и выбирай усиления", "Collect XP crystals, level up and pick upgrades");
        add("htBoss", "Боссы приходят на 10-й и 20-й минуте. Продержись 20 минут — победа", "Bosses arrive at 10:00 and 20:00. Survive 20 minutes to win");
        add("htGold", "Золото остаётся после смерти — трать его в магазине", "Gold persists after death — spend it in the shop");
        add("timeSurvived", "время", "time");
        add("totalKills", "всего убийств", "total kills");
        add("runs", "забегов", "runs");
        add("bestLvl", "лучший уровень", "best level");
        add("totalGold", "всего золота собрано", "total gold collected");
        add("hintMove", "Веди пальцем отсюда", "Drag from here");
        add("combo", "КОМБО", "COMBO");
        add("crit", "КРИТ!", "CRIT!");
        add("maxWeapons", "Слоты оружия заполнены", "Weapons slots are full");
        add("metaHp", "Здоровье", "Max Health");
        add("metaDmg", "Урон", "Damage");
        add("metaSpd", "Скорость", "Speed");
        add("metaMag", "Магнит", "Magnet");
        add("metaGreed", "Жадность", "Greed");
        add("shopTitle", "УСИЛЕНИЯ НАВСЕГДА", "PERMANENT UPGRADES");
        add("gold", "золото", "gold");
        add("lvl", "ур.", "lv");
        add("notEnough", "Не хватает золота", "Not enough gold");
        add("bought", "Куплено!", "Purchased!");
        add("newBest", "НОВЫЙ РЕКОРД!", "NEW RECORD!");
        // Оружие
        add("w_pulse", "Импульсная винтовка", "Pulse Rifle");
        add("w_chain", "Цепная молния", "Chain Lightning");
        add("w_blades", "Орбитальные клинки", "Orbit Blades");
        add("w_nova", "Взрыв Новы", "Nova Burst");
        add("w_spheres", "Сферы Бездны", "Void Spheres");
        add("w_laser", "Лазерный луч", "Laser Beam");
        add("w_missiles", "Ракеты", "Homing Missiles");
        add("w_frost", "Ледяные осколки", "Frost Shards");
        add("wd_pulse", "Скорострельные импульсы в ближайшего врага", "Rapid pulses at the nearest enemy");
        add("wd_chain", "Молния прыгает между врагами", "Lightning jumps between enemies");
        add("wd_blades", "Клинки вращаются вокруг тебя", "Blades orbit around you");
        add("wd_nova", "Кольцо энергии отталкивает толпу", "Energy ring blasts the horde");
        add("wd_spheres", "Ищущие сферы медленно плавят всех", "Seeking spheres slowly melt everyone");
        add("wd_laser", "Пробивной луч сквозь строй врагов", "Piercing beam through enemy lines");
        add("wd_missiles", "Самонаводящиеся ракеты с взрывом", "Homing missiles with splash");
        add("wd_frost", "Веер осколков замедляет врагов", "Shard fan slows enemies");
        add("w_boomerang", "Бумеранг-диски", "Boomerang Discs");
        add("w_storm", "Гроза", "Thunderstorm");
        add("w_aura", "Плазменная аура", "Plasma Aura");
        add("w_blackhole", "Чёрная дыра", "Black Hole");
        add("wd_boomerang", "Диски летят сквозь врагов и возвращаются", "Discs pierce enemies and come back");
        add("wd_storm", "Молнии бьют с неба по толпе", "Bolts strike the horde from above");
        add("wd_aura", "Постоянный ожог вокруг тебя", "Constant burn all around you");
        add("wd_blackhole", "Затягивает и сжимает толпу", "Pulls in and crushes the crowd");
        // События
        // Реликвии
        add("r_vamp", "КРОВАВЫЙ КРИСТАЛЛ", "BLOOD CRYSTAL");
        add("r_double", "СДВОЕННЫЙ ЭМИТТЕР", "TWIN EMITTER");
        add("r_glass", "СТЕКЛЯННОЕ СЕРДЦЕ", "GLASS HEART");
        add("r_chrono", "ХРОНОСФЕРА", "CHRONOSPHERE");
        add("r_magnet", "РЕЗОНАТОР", "RESONATOR");
        add("r_aegis", "ЭГИДА", "AEGIS");
        add("r_greed", "ЗОЛОТАЯ ЖИЛА", "GOLDEN VEIN");
        add("r_fury", "ЯДРО ЯРОСТИ", "FURY CORE");
        add("r_titan", "ТИТАНОВАЯ ОБОЛОЧКА", "TITAN SHELL");
        add("r_luck", "КЛЕВЕР НЕОНА", "NEON CLOVER");
        add("rd_vamp", "10% убийств лечат 2 HP", "10% of kills heal 2 HP");
        add("rd_double", "+1 снаряд ко всему оружию", "+1 projectile to all weapons");
        add("rd_glass", "+40% урона, −25% HP", "+40% damage, −25% HP");
        add("rd_chrono", "−20% перезарядки", "−20% cooldowns");
        add("rd_magnet", "+60% радиус магнита", "+60% magnet radius");
        add("rd_aegis", "+12% брони", "+12% armor");
        add("rd_greed", "+50% золота", "+50% gold");
        add("rd_fury", "+15% скорость и атака", "+15% speed and attack");
        add("rd_titan", "+30% HP и лечит", "+30% HP and heals");
        add("rd_luck", "+10% крит, +30% крит-урон", "+10% crit, +30% crit damage");
        // Пробуждение
        add("awaken", "ПРОБУЖДЕНИЕ", "AWAKENING");
        add("awakenDesc", "★ урон x1.6 навсегда", "★ damage x1.6 forever");
        // Killstreak
        add("ks1", "РАЗГРОМ!", "RAMPAGE!");
        add("ks2", "БЕЗУМИЕ!", "MAYHEM!");
        add("ks3", "БОЖЕСТВЕННО!", "GODLIKE!");
        add("ks4", "НЕОНОВЫЙ АПОКАЛИПСИС!", "NEON APOCALYPSE!");
        // Ачивки
        add("ach", "ДОСТИЖЕНИЯ", "ACHIEVEMENTS");
        add("ach0", "Первая кровь — первый забег", "First blood — first run");
        add("ach1", "Сотня — 100 убийств за забег", "Century — 100 kills in a run");
        add("ach2", "Мясорубка — 1000 убийств за забег", "Meat grinder — 1000 kills in a run");
        add("ach3", "Выживальщик — дожить до 10:00", "Survivor — reach 10:00");
        add("ach4", "Победитель — одолеть Королеву Роя", "Victor — defeat the Swarm Queen");
        add("ach5", "Убийца боссов — убить босса на 10:00", "Boss slayer — kill the 10:00 boss");
        add("ach6", "Мастер — 20-й уровень за забег", "Master — level 20 in a run");
        add("ach7", "Охотник — 5 элит за забег", "Hunter — 5 elites in a run");
        add("ach8", "Ловец удачи — убить жадину", "Lucky catch — kill the greedling");
        add("ach9", "Вечность — войти в endless", "Eternity — enter endless mode");
        add("achGot", "ДОСТИЖЕНИЕ:", "ACHIEVEMENT:");
        // Экран билда в паузе
        add("build", "БИЛД", "BUILD");
        add("relics", "Реликвии", "Relics");
        add("weapons", "Оружие", "Weapons");
        add("passives", "Пассивки", "Passives");
        add("noRelics", "пока нет", "none yet");
        // События
        add("ev_greed", "✦ ЗОЛОТОЙ ЖАДИНА ✦", "✦ GOLDEN GREED ✦");
        add("ev_meteor", "✦ МЕТЕОРИТНЫЙ ДОЖДЬ ✦", "✦ METEOR SHOWER ✦");
        add("ev_swarm", "✦ ОРДА ✦", "✦ THE SWARM ✦");
        // Пассивки
        add("p_dmg", "Ярость", "Fury");
        add("p_cd", "Перегрузка", "Overdrive");
        add("p_hp", "Стойкость", "Toughness");
        add("p_spd", "Скороход", "Fleetfoot");
        add("p_magnet", "Притяжение", "Attraction");
        add("p_armor", "Броня", "Armor");
        add("p_xp", "Мудрость", "Wisdom");
        add("p_regen", "Регенерация", "Regeneration");
        add("p_crit", "Точность", "Precision");
        add("pd_dmg", "+10% урона всему", "+10% all damage");
        add("pd_cd", "-8% перезарядки", "-8% cooldown");
        add("pd_hp", "+20 макс. HP и лечит", "+20 max HP and heals");
        add("pd_spd", "+8% скорости", "+8% speed");
        add("pd_magnet", "+30% радиус магнита", "+30% magnet radius");
        add("pd_armor", "+5% брони", "+5% armor");
        add("pd_xp", "+10% опыта", "+10% XP gain");
        add("pd_regen", "+0.8 HP/сек", "+0.8 HP/sec");
        add("pd_crit", "+5% шанс крита", "+5% crit chance");
        // Герои
        add("c0", "ПУЛЬС", "PULSE");
        add("c1", "ТЕСЛА", "TESLA");
        add("c2", "БАСТИОН", "BASTION");
        add("c3", "ВЕДЬМА БЕЗДНЫ", "VOID WITCH");
        add("cd0", "Баланс. Импульсная винтовка", "Balanced. Pulse Rifle");
        add("cd1", "Стеклянная пушка. Молния, криты", "Glass cannon. Lightning, crits");
        add("cd2", "Танк. Клинки, броня, HP", "Tank. Blades, armor, HP");
        add("cd3", "Сферы Бездны. Магнит и опыт", "Void Spheres. Magnet and XP");
    }

    /** Получить строку по ключу на текущем языке. */
    public static String t(String k) {
        String[] v = D.get(k);
        if (v == null) return k;
        return ru ? v[0] : v[1];
    }
}
