package com.syslikoffnet.overglow;

/**
 * Фаза 2: Система анатомических хитбоксов и расчет урона (Hitbox System):
 * - Зоны попадания: Голова, Шея, Грудь, Живот, Руки/Кисти, Ноги/Ступни
 * - Бронезащита: Шлемы 1-3 уровней (-30%, -40%, -55%), Бронежилеты 1-3 уровней (-30%, -40%, -55%)
 * - Пробитие транспорта и повреждение колес
 * - 0 GC Alloc
 */
public final class HitboxSystem {

    public static final int ZONE_HEAD = 0;
    public static final int ZONE_NECK = 1;
    public static final int ZONE_CHEST = 2;
    public static final int ZONE_STOMACH = 3;
    public static final int ZONE_ARMS = 4;
    public static final int ZONE_LEGS = 5;

    public static final float MULT_HEAD = 2.50f;
    public static final float MULT_NECK = 1.50f;
    public static final float MULT_CHEST = 1.00f;
    public static final float MULT_STOMACH = 0.90f;
    public static final float MULT_ARMS = 0.60f;
    public static final float MULT_LEGS = 0.50f;

    // Редукция урона броней (Lv 0 = 0%, Lv 1 = 30%, Lv 2 = 40%, Lv 3 = 55%)
    public static final float[] ARMOR_REDUCTION = { 0.0f, 0.30f, 0.40f, 0.55f };

    public static final class HitResult {
        public boolean isHit = false;
        public int hitZone = ZONE_CHEST;
        public float hitDistance = 0f;
        public float finalDamage = 0f;
        public boolean isHeadshot = false;
        public final Math3D.Vec3 hitPoint = new Math3D.Vec3();

        public void reset() {
            isHit = false;
            hitZone = ZONE_CHEST;
            hitDistance = 0;
            finalDamage = 0;
            isHeadshot = false;
        }
    }

    /**
     * Расчет точного урона с учетом зоны попадания, дистанции и надетой экипировки
     */
    public static float calculateDamage(float baseDmg, int zone, float distance,
                                        int helmetLevel, int vestLevel) {
        float zoneMultiplier;
        float armorReduction = 0f;

        switch (zone) {
            case ZONE_HEAD:
                zoneMultiplier = MULT_HEAD;
                if (helmetLevel >= 1 && helmetLevel <= 3) {
                    armorReduction = ARMOR_REDUCTION[helmetLevel];
                }
                break;
            case ZONE_NECK:
                zoneMultiplier = MULT_NECK;
                if (helmetLevel >= 1 && helmetLevel <= 3) {
                    armorReduction = ARMOR_REDUCTION[helmetLevel] * 0.5f;
                }
                break;
            case ZONE_CHEST:
                zoneMultiplier = MULT_CHEST;
                if (vestLevel >= 1 && vestLevel <= 3) {
                    armorReduction = ARMOR_REDUCTION[vestLevel];
                }
                break;
            case ZONE_STOMACH:
                zoneMultiplier = MULT_STOMACH;
                if (vestLevel >= 1 && vestLevel <= 3) {
                    armorReduction = ARMOR_REDUCTION[vestLevel];
                }
                break;
            case ZONE_ARMS:
                zoneMultiplier = MULT_ARMS;
                // Бронежилет не защищает конечности в PUBG
                armorReduction = 0f;
                break;
            case ZONE_LEGS:
                zoneMultiplier = MULT_LEGS;
                armorReduction = 0f;
                break;
            default:
                zoneMultiplier = 1.0f;
                break;
        }

        // Падение урона на сверхдальних дистанциях (> 200м)
        float distanceDrop = 1.0f;
        if (distance > 150f) {
            distanceDrop = Math.max(0.65f, 1.0f - (distance - 150f) * 0.0009f);
        }

        float effectiveDmg = baseDmg * zoneMultiplier * distanceDrop * (1.0f - armorReduction);
        return Math.max(1.0f, effectiveDmg);
    }
}
