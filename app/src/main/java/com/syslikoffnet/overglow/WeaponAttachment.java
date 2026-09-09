package com.syslikoffnet.overglow;

/**
 * Фаза 2: Модули и обвесы на оружие (Weapon Attachments):
 * - Дуло (Muzzle): Глушитель, Компенсатор, Пламегаситель
 * - Рукоятка (Grip): Вертикальная, Угловая, Облегченная
 * - Магазин (Magazine): Увеличенный, Быстрой перезарядки, Расширенный быстросъемный
 * - Прицел (Scope): Коллиматор (Red Dot), 2x, 4x ACOG, 8x Снайперский
 * - Приклад (Stock): Тактический приклад, Подщечник
 * - 0 GC Alloc
 */
public final class WeaponAttachment {

    public static final int SLOT_MUZZLE = 0;
    public static final int SLOT_GRIP = 1;
    public static final int SLOT_MAGAZINE = 2;
    public static final int SLOT_SCOPE = 3;
    public static final int SLOT_STOCK = 4;
    public static final int TOTAL_SLOTS = 5;

    // Идентификаторы обвесов
    public static final int ID_NONE = 0;

    // Дуло
    public static final int ID_MUZZLE_COMPENSATOR = 101;
    public static final int ID_MUZZLE_SUPPRESSOR = 102;
    public static final int ID_MUZZLE_FLASH_HIDER = 103;

    // Рукоятки
    public static final int ID_GRIP_VERTICAL = 201;
    public static final int ID_GRIP_ANGLED = 202;
    public static final int ID_GRIP_LIGHT = 203;

    // Магазины
    public static final int ID_MAG_EXTENDED = 301;
    public static final int ID_MAG_QUICKDRAW = 302;
    public static final int ID_MAG_EXT_QUICKDRAW = 303;

    // Прицелы
    public static final int ID_SCOPE_RED_DOT = 401;
    public static final int ID_SCOPE_2X = 402;
    public static final int ID_SCOPE_4X = 403;
    public static final int ID_SCOPE_8X = 404;

    // Приклады
    public static final int ID_STOCK_TACTICAL = 501;
    public static final int ID_STOCK_CHEEKPAD = 502;

    public final int id;
    public final int slot;
    public final String name;

    // Модификаторы характеристик
    public float recoilVertMul = 1.0f;     // Множитель вертикальной отдачи
    public float recoilHorizMul = 1.0f;    // Множитель горизонтальной отдачи
    public float recoilRecoveryMul = 1.0f; // Ускорение стабилизации ствола
    public float reloadTimeMul = 1.0f;     // Множитель времени перезарядки
    public int magCapacityBonus = 0;       // Дополнительные патроны в магазине
    public float adsSpeedMul = 1.0f;       // Скорость входа в прицел
    public float adsZoom = 1.0f;           // Кратность увеличения оптики
    public boolean isSilenced = false;     // Глушение звука выстрела
    public boolean hideFlash = false;      // Скрытие вспышки выстрела

    public WeaponAttachment(int id, int slot, String name) {
        this.id = id;
        this.slot = slot;
        this.name = name;
    }

    public static WeaponAttachment create(int id) {
        WeaponAttachment att;
        switch (id) {
            // --- ДУЛЬНЫЕ НАСАДКИ ---
            case ID_MUZZLE_COMPENSATOR:
                att = new WeaponAttachment(id, SLOT_MUZZLE, "AR Compensator");
                att.recoilVertMul = 0.75f;   // -25% вертикальной отдачи
                att.recoilHorizMul = 0.80f;  // -20% горизонтального увода
                return att;

            case ID_MUZZLE_SUPPRESSOR:
                att = new WeaponAttachment(id, SLOT_MUZZLE, "Suppressor (Глушитель)");
                att.isSilenced = true;
                att.hideFlash = true;
                att.recoilVertMul = 0.95f;   // -5% отдачи
                return att;

            case ID_MUZZLE_FLASH_HIDER:
                att = new WeaponAttachment(id, SLOT_MUZZLE, "Flash Hider (Пламегаситель)");
                att.hideFlash = true;
                att.recoilVertMul = 0.90f;
                att.recoilHorizMul = 0.90f;
                return att;

            // --- РУКОЯТКИ ---
            case ID_GRIP_VERTICAL:
                att = new WeaponAttachment(id, SLOT_GRIP, "Vertical Foregrip");
                att.recoilVertMul = 0.80f;   // -20% вертикальной отдачи
                return att;

            case ID_GRIP_ANGLED:
                att = new WeaponAttachment(id, SLOT_GRIP, "Angled Foregrip");
                att.recoilHorizMul = 0.78f;  // -22% горизонтальной отдачи
                att.adsSpeedMul = 1.20f;     // +20% к скорости вскидки
                return att;

            case ID_GRIP_LIGHT:
                att = new WeaponAttachment(id, SLOT_GRIP, "Light Grip");
                att.recoilRecoveryMul = 1.25f; // +25% скорость восстановления
                return att;

            // --- МАГАЗИНЫ ---
            case ID_MAG_EXTENDED:
                att = new WeaponAttachment(id, SLOT_MAGAZINE, "Extended Mag (+10)");
                att.magCapacityBonus = 10;
                return att;

            case ID_MAG_QUICKDRAW:
                att = new WeaponAttachment(id, SLOT_MAGAZINE, "Quickdraw Mag");
                att.reloadTimeMul = 0.70f;   // -30% времени перезарядки
                return att;

            case ID_MAG_EXT_QUICKDRAW:
                att = new WeaponAttachment(id, SLOT_MAGAZINE, "Ext. Quickdraw Mag");
                att.magCapacityBonus = 10;
                att.reloadTimeMul = 0.70f;
                return att;

            // --- ПРИЦЕЛЫ ---
            case ID_SCOPE_RED_DOT:
                att = new WeaponAttachment(id, SLOT_SCOPE, "Red Dot Sight");
                att.adsZoom = 1.30f;
                att.adsSpeedMul = 1.15f;
                return att;

            case ID_SCOPE_2X:
                att = new WeaponAttachment(id, SLOT_SCOPE, "2x Aimpoint");
                att.adsZoom = 2.0f;
                return att;

            case ID_SCOPE_4X:
                att = new WeaponAttachment(id, SLOT_SCOPE, "4x ACOG Scope");
                att.adsZoom = 4.0f;
                return att;

            case ID_SCOPE_8X:
                att = new WeaponAttachment(id, SLOT_SCOPE, "8x CQBSS Sniper");
                att.adsZoom = 8.0f;
                return att;

            // --- ПРИКЛАДЫ ---
            case ID_STOCK_TACTICAL:
                att = new WeaponAttachment(id, SLOT_STOCK, "Tactical Stock");
                att.recoilRecoveryMul = 1.20f;
                att.recoilHorizMul = 0.85f;
                return att;

            case ID_STOCK_CHEEKPAD:
                att = new WeaponAttachment(id, SLOT_STOCK, "Cheek Pad (Sniper)");
                att.recoilVertMul = 0.80f;
                att.recoilRecoveryMul = 1.25f;
                return att;

            default:
                return null;
        }
    }
}
