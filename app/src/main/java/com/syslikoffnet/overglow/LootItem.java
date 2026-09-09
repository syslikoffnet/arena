package com.syslikoffnet.overglow;

/**
 * Лут на полу в PUBG Mobile: Оружие, модули/обвесы, патроны, аптечки, энергетики, броня, шлемы, рюкзаки, сковорода.
 */
public final class LootItem {

    // Типы предметов
    public static final int TYPE_WEAPON = 0;
    public static final int TYPE_AMMO = 1;
    public static final int TYPE_MEDKIT = 2;
    public static final int TYPE_ENERGY_DRINK = 3;
    public static final int TYPE_HELMET = 4;
    public static final int TYPE_VEST = 5;
    public static final int TYPE_BACKPACK = 6;
    public static final int TYPE_PAN = 7;
    public static final int TYPE_ATTACHMENT = 8;
    public static final int TYPE_AIRDROP = 9;

    public final int id;
    public final int type;
    public final String name;
    public final int subId; // ID оружия / уровень брони / ID обвеса
    public final int count;

    public float x, y, z;
    public float rotY = 0;
    public boolean isTaken = false;

    public LootItem(int id, int type, String name, int subId, int count, float x, float y, float z) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.subId = subId;
        this.count = count;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static LootItem createWeapon(int id, int weaponId, String name, float x, float y, float z) {
        return new LootItem(id, TYPE_WEAPON, name, weaponId, 1, x, y, z);
    }

    public static LootItem createAttachment(int id, int attachId, String name, float x, float y, float z) {
        return new LootItem(id, TYPE_ATTACHMENT, name, attachId, 1, x, y, z);
    }

    public static LootItem createAmmo(int id, String name, int count, float x, float y, float z) {
        return new LootItem(id, TYPE_AMMO, name, 0, count, x, y, z);
    }

    public static LootItem createMedkit(int id, String name, float x, float y, float z) {
        return new LootItem(id, TYPE_MEDKIT, name, 0, 1, x, y, z);
    }

    public static LootItem createDrink(int id, String name, float x, float y, float z) {
        return new LootItem(id, TYPE_ENERGY_DRINK, name, 0, 1, x, y, z);
    }

    public static LootItem createArmor(int id, int level, boolean isHelmet, float x, float y, float z) {
        return new LootItem(id, isHelmet ? TYPE_HELMET : TYPE_VEST,
                (isHelmet ? "Helmet Lv." : "Vest Lv.") + level, level, 1, x, y, z);
    }

    public static LootItem createBackpack(int id, int level, float x, float y, float z) {
        return new LootItem(id, TYPE_BACKPACK, "Backpack Lv." + level, level, 1, x, y, z);
    }

    public static LootItem createPan(int id, float x, float y, float z) {
        return new LootItem(id, TYPE_PAN, "Pan (Сковорода)", 0, 1, x, y, z);
    }
}
