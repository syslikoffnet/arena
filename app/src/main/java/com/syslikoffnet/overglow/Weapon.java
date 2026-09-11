package com.syslikoffnet.overglow;

/**
 * Фаза 2: Оружие с физическими характеристиками, баллистикой и слотами обвесов.
 * - Учет начальной скорости пули (Muzzle Velocity: м/с)
 * - Расчет эффективных характеристик с учетом установленных насадок
 * - Поддержка пристрелки (Zeroing Distance: 100м, 200м, 300м, 400м, 500м)
 * - 0 GC Alloc во время стрельбы
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

    public static final int SKIN_DEFAULT = 0;
    public static final int SKIN_GOLD = 1;
    public static final int SKIN_DRAGON = 2;

    public final int id;
    public final String name;
    public final float baseDamage;
    public final float headMultiplier;
    public final float fireInterval;
    public final int baseMaxMag;
    public final int maxReserve;
    public final float baseReloadTime;
    public final float baseRecoilPitch;
    public final float baseRecoilYaw;
    public final float baseRecoilRecovery;
    public final float baseAdsZoom;
    public final boolean isAutomatic;
    public final boolean isMelee;
    public final boolean isProjectile;

    // Баллистические параметры
    public final float muzzleVelocity; // Начальная скорость пули (м/с)
    public final float bulletDrag;      // Коэффициент сопротивления воздуха
    public final float bulletMass;      // Масса пули (грамм)
    public final int pelletCount;       // Количество дробинок (для дробовика)
    public final float spreadRadius;    // Базовый конус разброса

    // Пристрелка (Zeroing Distance в метрах)
    public int zeroingDistance = 100;

    // Слоты модулей
    public final WeaponAttachment[] attachments = new WeaponAttachment[WeaponAttachment.TOTAL_SLOTS];

    // Текущее состояние
    public int ammoInMag;
    public int ammoReserve;
    public float fireTimer = 0;
    public float reloadTimer = 0;
    public int skin = SKIN_DEFAULT;

    public Weapon(int id, String name, float baseDamage, float headMultiplier, float fireInterval,
                  int baseMaxMag, int maxReserve, float baseReloadTime, float baseRecoilPitch,
                  float baseRecoilYaw, float baseRecoilRecovery, float baseAdsZoom,
                  float muzzleVelocity, float bulletDrag, float bulletMass, int pelletCount, float spreadRadius,
                  boolean isAutomatic, boolean isMelee, boolean isProjectile) {
        this.id = id;
        this.name = name;
        this.baseDamage = baseDamage;
        this.headMultiplier = headMultiplier;
        this.fireInterval = fireInterval;
        this.baseMaxMag = baseMaxMag;
        this.maxReserve = maxReserve;
        this.baseReloadTime = baseReloadTime;
        this.baseRecoilPitch = baseRecoilPitch;
        this.baseRecoilYaw = baseRecoilYaw;
        this.baseRecoilRecovery = baseRecoilRecovery;
        this.baseAdsZoom = baseAdsZoom;
        this.muzzleVelocity = muzzleVelocity;
        this.bulletDrag = bulletDrag;
        this.bulletMass = bulletMass;
        this.pelletCount = pelletCount;
        this.spreadRadius = spreadRadius;
        this.isAutomatic = isAutomatic;
        this.isMelee = isMelee;
        this.isProjectile = isProjectile;

        this.ammoInMag = baseMaxMag;
        this.ammoReserve = maxReserve;
    }

    public static Weapon create(int id) {
        switch (id) {
            case ID_M4: // M416 (5.56mm NATO)
                Weapon m4 = new Weapon(ID_M4, "M416 Tactical", 41f, 2.35f, 0.086f,
                        30, 150, 2.1f, 1.35f, 0.65f, 7.8f, 1.25f,
                        880f, 0.0018f, 4.0f, 1, 0.015f, true, false, false);
                m4.attach(WeaponAttachment.create(WeaponAttachment.ID_SCOPE_RED_DOT));
                return m4;

            case ID_AKR: // AKM (7.62x39mm)
                return new Weapon(ID_AKR, "AKM Heavy", 48f, 2.35f, 0.100f,
                        30, 150, 2.3f, 1.95f, 0.95f, 5.8f, 1.20f,
                        715f, 0.0022f, 7.9f, 1, 0.022f, true, false, false);

            case ID_AWM: // AWM (.300 Winchester Magnum)
                Weapon awm = new Weapon(ID_AWM, "AWM Magnum", 132f, 2.50f, 1.35f,
                        5, 25, 3.2f, 4.8f, 1.4f, 3.2f, 8.0f,
                        910f, 0.0012f, 19.0f, 1, 0.001f, false, false, false);
                awm.attach(WeaponAttachment.create(WeaponAttachment.ID_SCOPE_8X));
                awm.attach(WeaponAttachment.create(WeaponAttachment.ID_STOCK_CHEEKPAD));
                return awm;

            case ID_DEAGLE: // Desert Eagle (.45 ACP / .50 AE)
                return new Weapon(ID_DEAGLE, "Desert Eagle", 62f, 2.2f, 0.22f,
                        7, 35, 1.8f, 3.2f, 1.2f, 4.5f, 1.15f,
                        470f, 0.0035f, 15.0f, 1, 0.035f, false, false, false);

            case ID_MP5: // MP5K (9mm Luger)
                return new Weapon(ID_MP5, "MP5K Submachine", 33f, 1.8f, 0.068f,
                        30, 180, 1.8f, 0.95f, 0.45f, 8.5f, 1.20f,
                        400f, 0.0028f, 8.0f, 1, 0.025f, true, false, false);

            case ID_SHOTGUN: // SPAS-12 (12 Gauge Buckshot - 9 дробинок по 22 урона)
                return new Weapon(ID_SHOTGUN, "S1897 Shotgun", 24f, 1.5f, 0.75f,
                        5, 30, 2.8f, 4.5f, 2.5f, 3.8f, 1.10f,
                        360f, 0.0065f, 32.0f, 9, 0.085f, false, false, false);

            case ID_RPG: // RPG-7 Rocket
                return new Weapon(ID_RPG, "RPG-7 Rocket", 240f, 1.0f, 2.2f,
                        1, 4, 3.8f, 6.5f, 2.0f, 2.5f, 1.3f,
                        115f, 0.0005f, 2500f, 1, 0.010f, false, false, true);

            case ID_KNIFE: // Pan / Melee
                return new Weapon(ID_KNIFE, "Crowbar / Pan", 80f, 2.0f, 0.45f,
                        1, 1, 0.1f, 0.5f, 0.2f, 10f, 1.0f,
                        0f, 0f, 0f, 1, 0f, false, true, false);

            case ID_GRENADE: // HE Frag
                return new Weapon(ID_GRENADE, "Frag Grenade", 180f, 1.0f, 1.2f,
                        1, 3, 1.0f, 1.0f, 0.5f, 8f, 1.0f,
                        28f, 0.004f, 400f, 1, 0f, false, false, true);

            default:
                return create(ID_M4);
        }
    }

    public void attach(WeaponAttachment att) {
        if (att != null && att.slot >= 0 && att.slot < WeaponAttachment.TOTAL_SLOTS) {
            attachments[att.slot] = att;
        }
    }

    public void detach(int slot) {
        if (slot >= 0 && slot < WeaponAttachment.TOTAL_SLOTS) {
            attachments[slot] = null;
        }
    }

    public int getMaxMag() {
        int cap = baseMaxMag;
        if (attachments[WeaponAttachment.SLOT_MAGAZINE] != null) {
            cap += attachments[WeaponAttachment.SLOT_MAGAZINE].magCapacityBonus;
        }
        return cap;
    }

    public float getReloadTime() {
        float t = baseReloadTime;
        if (attachments[WeaponAttachment.SLOT_MAGAZINE] != null) {
            t *= attachments[WeaponAttachment.SLOT_MAGAZINE].reloadTimeMul;
        }
        return t;
    }

    public float getRecoilPitch() {
        float p = baseRecoilPitch;
        for (WeaponAttachment a : attachments) {
            if (a != null) p *= a.recoilVertMul;
        }
        return p;
    }

    public float getRecoilYaw() {
        float y = baseRecoilYaw;
        for (WeaponAttachment a : attachments) {
            if (a != null) y *= a.recoilHorizMul;
        }
        return y;
    }

    public float getRecoilRecovery() {
        float r = baseRecoilRecovery;
        for (WeaponAttachment a : attachments) {
            if (a != null) r *= a.recoilRecoveryMul;
        }
        return r;
    }

    public float getAdsZoom() {
        if (attachments[WeaponAttachment.SLOT_SCOPE] != null) {
            return attachments[WeaponAttachment.SLOT_SCOPE].adsZoom;
        }
        return baseAdsZoom;
    }

    public boolean isSilenced() {
        return attachments[WeaponAttachment.SLOT_MUZZLE] != null &&
               attachments[WeaponAttachment.SLOT_MUZZLE].isSilenced;
    }

    public boolean hideFlash() {
        return attachments[WeaponAttachment.SLOT_MUZZLE] != null &&
               attachments[WeaponAttachment.SLOT_MUZZLE].hideFlash;
    }

    public void cycleZeroing() {
        if (zeroingDistance == 100) zeroingDistance = 200;
        else if (zeroingDistance == 200) zeroingDistance = 300;
        else if (zeroingDistance == 300) zeroingDistance = 400;
        else if (zeroingDistance == 400) zeroingDistance = 500;
        else zeroingDistance = 100;
    }

    public void update(float dt) {
        if (fireTimer > 0) fireTimer -= dt;
        if (reloadTimer > 0) {
            reloadTimer -= dt;
            if (reloadTimer <= 0) {
                int needed = getMaxMag() - ammoInMag;
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
        if (isMelee || reloadTimer > 0 || ammoInMag >= getMaxMag() || ammoReserve <= 0) return;
        reloadTimer = getReloadTime();
        SoundSynth3D.play2D(SoundSynth3D.SOUND_RELOAD, 0.8f);
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
            default: return ModelGenerator.m4Mesh;
        }
    }

    /** Индекс материала TextureForge (скины оружия — собственные процедурные материалы) */
    public int getMaterial() {
        if (skin == SKIN_GOLD) return TextureForge.MAT_GOLD;
        if (skin == SKIN_DRAGON) return TextureForge.MAT_DRAGON;
        return TextureForge.MAT_GUN;
    }
}
