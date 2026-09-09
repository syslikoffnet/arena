package com.syslikoffnet.overglow;

/**
 * Характеристики, баллистика, отдача, перезарядка и анимации оружия.
 */
public final class Weapon {

    public static final int ID_AKR = 0;
    public static final int ID_M4 = 1;
    public static final int ID_AWM = 2;
    public static final int ID_DEAGLE = 3;
    public static final int ID_MP5 = 4;
    public static final int ID_SHOTGUN = 5;
    public static final int ID_RPG = 6;
    public static final int ID_KNIFE = 7;
    public static final int ID_GRENADE = 8;
    public static final int TOTAL_WEAPONS = 9;

    // Скины
    public static final int SKIN_DEFAULT = 0;
    public static final int SKIN_GOLD = 1;
    public static final int SKIN_DRAGON = 2;

    public final int id;
    public final String name;
    public final float damage;
    public final float headMultiplier;
    public final float fireInterval;      // секунды между выстрелами
    public final int maxMag;
    public final int maxReserve;
    public final float reloadTime;
    public final float recoilPitch;       // подброс ствола вверх
    public final float recoilYaw;         // разброс по горизонтали
    public final float recoilRecovery;    // скорость стабилизации
    public final float adsZoom;           // кратность прицела (1.0 = без зума, 8.0 = AWM)
    public final boolean isAutomatic;
    public final boolean isMelee;
    public final boolean isProjectile;

    // Текущее состояние
    public int ammoInMag;
    public int ammoReserve;
    public float fireTimer = 0;
    public float reloadTimer = 0;
    public int skin = SKIN_DEFAULT;

    public Weapon(int id, String name, float damage, float headMultiplier, float fireInterval,
                  int maxMag, int maxReserve, float reloadTime, float recoilPitch,
                  float recoilYaw, float recoilRecovery, float adsZoom,
                  boolean isAutomatic, boolean isMelee, boolean isProjectile) {
        this.id = id;
        this.name = name;
        this.damage = damage;
        this.headMultiplier = headMultiplier;
        this.fireInterval = fireInterval;
        this.maxMag = maxMag;
        this.maxReserve = maxReserve;
        this.reloadTime = reloadTime;
        this.recoilPitch = recoilPitch;
        this.recoilYaw = recoilYaw;
        this.recoilRecovery = recoilRecovery;
        this.adsZoom = adsZoom;
        this.isAutomatic = isAutomatic;
        this.isMelee = isMelee;
        this.isProjectile = isProjectile;

        this.ammoInMag = maxMag;
        this.ammoReserve = maxReserve;
    }

    public static Weapon create(int id) {
        switch (id) {
            case ID_AKR:
                return new Weapon(ID_AKR, "AKR-12", 36f, 3.5f, 0.10f,
                        30, 120, 2.2f, 1.8f, 0.9f, 6.0f, 1.3f, true, false, false);
            case ID_M4:
                return new Weapon(ID_M4, "M4A1-S", 32f, 3.3f, 0.088f,
                        30, 120, 2.0f, 1.2f, 0.6f, 7.5f, 1.35f, true, false, false);
            case ID_AWM:
                return new Weapon(ID_AWM, "AWM Dragon", 120f, 4.0f, 1.25f,
                        5, 30, 3.0f, 4.5f, 1.5f, 3.5f, 6.0f, false, false, false);
            case ID_DEAGLE:
                return new Weapon(ID_DEAGLE, "Desert Eagle", 54f, 3.8f, 0.22f,
                        7, 35, 1.8f, 3.0f, 1.2f, 5.0f, 1.2f, false, false, false);
            case ID_MP5:
                return new Weapon(ID_MP5, "MP5 Tactical", 24f, 3.0f, 0.072f,
                        30, 150, 1.7f, 1.0f, 0.5f, 8.0f, 1.25f, true, false, false);
            case ID_SHOTGUN:
                return new Weapon(ID_SHOTGUN, "SPAS-12", 18f /* x8 дробинок = 144 */, 2.5f, 0.65f,
                        6, 36, 2.6f, 4.0f, 2.2f, 4.0f, 1.15f, false, false, false);
            case ID_RPG:
                return new Weapon(ID_RPG, "RPG-7", 220f, 1.0f, 1.8f,
                        1, 5, 3.4f, 6.0f, 2.0f, 3.0f, 1.4f, false, false, true);
            case ID_KNIFE:
                return new Weapon(ID_KNIFE, "Karambit Gold", 75f, 2.0f, 0.40f,
                        1, 1, 0.1f, 0.5f, 0.2f, 10f, 1.0f, false, true, false);
            case ID_GRENADE:
                return new Weapon(ID_GRENADE, "HE Grenade", 160f, 1.0f, 1.0f,
                        1, 4, 1.2f, 1.0f, 0.5f, 8f, 1.0f, false, false, true);
            default:
                return create(ID_AKR);
        }
    }

    public void update(float dt) {
        if (fireTimer > 0) fireTimer -= dt;
        if (reloadTimer > 0) {
            reloadTimer -= dt;
            if (reloadTimer <= 0) {
                // Завершение перезарядки
                int needed = maxMag - ammoInMag;
                int take = Math.min(needed, ammoReserve);
                ammoInMag += take;
                ammoReserve -= take;
            }
        }
    }

    public boolean canFire() {
        if (isMelee) return fireTimer <= 0;
        return fireTimer <= 0 && reloadTimer <= 0 && ammoInMag > 0;
    }

    public void startReload() {
        if (isMelee || reloadTimer > 0 || ammoInMag >= maxMag || ammoReserve <= 0) return;
        reloadTimer = reloadTime;
    }

    public Mesh getMesh() {
        switch (id) {
            case ID_AKR: return ModelGenerator.akrMesh;
            case ID_M4: return ModelGenerator.m4Mesh;
            case ID_AWM: return ModelGenerator.awmMesh;
            case ID_DEAGLE: return ModelGenerator.deagleMesh;
            case ID_MP5: return ModelGenerator.mp5Mesh;
            case ID_SHOTGUN: return ModelGenerator.shotgunMesh;
            case ID_RPG: return ModelGenerator.rpgMesh;
            case ID_KNIFE: return ModelGenerator.knifeMesh;
            case ID_GRENADE: return ModelGenerator.grenadeMesh;
            default: return ModelGenerator.akrMesh;
        }
    }

    public int getTexture() {
        if (skin == SKIN_GOLD) return GLUtil.texWeaponGold;
        if (skin == SKIN_DRAGON) return GLUtil.texWeaponDragon;
        return GLUtil.texWeaponDark;
    }
}
