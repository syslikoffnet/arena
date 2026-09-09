package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * Огромная карта Королевской Битвы (Erangel Island 600x600):
 * - Остров ожидания (Spawn Island), Починки, Военная база, Мосты
 * - Дома с открывающимися дверями, склады, сторожевые вышки
 * - Раскиданный по полу лут, спавны транспорта
 * - Динамическая сужающаяся Зона (Белый круг безопасности + Синяя зона радиации)
 */
public final class PUBGMap {

    public static final class Obstacle {
        public final Math3D.Box box;
        public final int texture;
        public final float r, g, b;

        public Obstacle(float x, float y, float z, float w, float h, float d, int texture, float r, float g, float b) {
            this.box = new Math3D.Box(x - w / 2f, y - h / 2f, z - d / 2f,
                                      x + w / 2f, y + h / 2f, z + d / 2f);
            this.texture = texture;
            this.r = r; this.g = g; this.b = b;
        }

        public float getCenterX() { return (box.minX + box.maxX) / 2f; }
        public float getCenterY() { return (box.minY + box.maxY) / 2f; }
        public float getCenterZ() { return (box.minZ + box.maxZ) / 2f; }
        public float getWidth() { return box.maxX - box.minX; }
        public float getHeight() { return box.maxY - box.minY; }
        public float getDepth() { return box.maxZ - box.minZ; }
    }

    public final ArrayList<Obstacle> obstacles = new ArrayList<>();
    public final ArrayList<InteractiveDoor> doors = new ArrayList<>();
    public final ArrayList<LootItem> loot = new ArrayList<>();
    public final ArrayList<Vehicle3D> vehicles = new ArrayList<>();

    // Траектория самолета
    public final Math3D.Vec3 planeStart = new Math3D.Vec3(-280, 180, -280);
    public final Math3D.Vec3 planeEnd = new Math3D.Vec3(280, 180, 280);
    public final Math3D.Vec3 planePos = new Math3D.Vec3();
    public float planeProgress = 0f;
    public float planeSpeed = 0.04f; // скорость полета через карту

    // Зона безопасности (Safe Zone) и Синяя зона
    public float whiteZoneX = 0f;
    public float whiteZoneZ = 0f;
    public float whiteZoneRadius = 260f;

    public float blueZoneX = 0f;
    public float blueZoneZ = 0f;
    public float blueZoneRadius = 300f;
    public float zoneShrinkTimer = 60f; // время до сужения
    public int zonePhase = 1;

    // Аирдроп
    public final Math3D.Vec3 airdropPos = new Math3D.Vec3(0, -100, 0);
    public boolean airdropActive = false;
    public boolean airdropLanded = false;

    public PUBGMap() {
        buildErangel();
    }

    public void startPlaneFlight() {
        planeProgress = 0f;
        planePos.set(planeStart);
        whiteZoneRadius = 220f;
        blueZoneRadius = 300f;
        zonePhase = 1;
        zoneShrinkTimer = 60f;
    }

    public void update(float dt) {
        // Движение самолета
        if (planeProgress < 1.0f) {
            planeProgress += planeSpeed * dt;
            planePos.x = Math3D.lerp(planeStart.x, planeEnd.x, planeProgress);
            planePos.y = Math3D.lerp(planeStart.y, planeEnd.y, planeProgress);
            planePos.z = Math3D.lerp(planeStart.z, planeEnd.z, planeProgress);
        }

        // Обновление дверей
        for (InteractiveDoor door : doors) {
            door.update(dt);
        }

        // Сужение синей зоны к белому кругу
        zoneShrinkTimer -= dt;
        if (zoneShrinkTimer <= 0) {
            blueZoneRadius = Math.max(whiteZoneRadius, blueZoneRadius - dt * 4.5f);
            blueZoneX = Math3D.lerp(blueZoneX, whiteZoneX, dt * 0.05f);
            blueZoneZ = Math3D.lerp(blueZoneZ, whiteZoneZ, dt * 0.05f);

            if (blueZoneRadius <= whiteZoneRadius + 1f) {
                // Следующая фаза зоны
                zonePhase++;
                zoneShrinkTimer = 45f;
                whiteZoneRadius = Math.max(30f, whiteZoneRadius * 0.60f);
                whiteZoneX += (float) ((Math.random() - 0.5) * 40);
                whiteZoneZ += (float) ((Math.random() - 0.5) * 40);

                // Сброс Аирдропа
                spawnAirdrop(whiteZoneX + (float) ((Math.random() - 0.5) * 50),
                             whiteZoneZ + (float) ((Math.random() - 0.5) * 50));
            }
        }

        // Падение Аирдропа с парашютом
        if (airdropActive && !airdropLanded) {
            airdropPos.y -= 12f * dt;
            if (airdropPos.y <= 0) {
                airdropPos.y = 0;
                airdropLanded = true;
                // Спавн легендарного лута в ящике
                loot.add(LootItem.createWeapon(loot.size(), Weapon.ID_AWM, "★ AWM Sniper", airdropPos.x, 0.4f, airdropPos.z));
                loot.add(LootItem.createArmor(loot.size(), 3, true, airdropPos.x + 1f, 0.4f, airdropPos.z));
                loot.add(LootItem.createArmor(loot.size(), 3, false, airdropPos.x - 1f, 0.4f, airdropPos.z));
            }
        }
    }

    public void spawnAirdrop(float x, float z) {
        airdropPos.set(x, 150f, z);
        airdropActive = true;
        airdropLanded = false;
        SoundSynth3D.play2D(SoundSynth3D.SOUND_RPG, 0.8f);
    }

    private void addWall(float x, float y, float z, float w, float h, float d, int tex) {
        obstacles.add(new Obstacle(x, y, z, w, h, d, tex, 1f, 1f, 1f));
    }

    private void buildErangel() {
        int conc = GLUtil.texConcrete;
        int crate = GLUtil.texCrate;
        int metal = GLUtil.texMetal;
        int sand = GLUtil.texSandstone;

        // Внешние границы острова 600х600
        addWall(0, 8, 300, 600, 16, 6, sand);
        addWall(0, 8, -300, 600, 16, 6, sand);
        addWall(300, 8, 0, 6, 16, 600, sand);
        addWall(-300, 8, 0, 6, 16, 600, sand);

        // Починки (Центральный город с домами и улицами)
        float[][] pochinkiHouses = {
                {-40, -40}, {0, -40}, {40, -40},
                {-40, 0}, {40, 0},
                {-40, 40}, {0, 40}, {40, 40}
        };

        int doorId = 0;
        int lootId = 0;

        for (float[] hPos : pochinkiHouses) {
            float hx = hPos[0], hz = hPos[1];
            // Стены дома 12х12 с дверным проемом
            addWall(hx, 3f, hz + 6, 12, 6, 1, conc); // задняя
            addWall(hx - 6, 3f, hz, 1, 6, 12, conc); // левая
            addWall(hx + 6, 3f, hz, 1, 6, 12, conc); // правая

            // Передняя стена с проемом для двери
            addWall(hx - 3.5f, 3f, hz - 6, 5, 6, 1, conc);
            addWall(hx + 3.5f, 3f, hz - 6, 5, 6, 1, conc);
            addWall(hx, 4.5f, hz - 6, 2, 3, 1, conc); // перемычка над дверью

            // Интерактивная открывающаяся дверь
            doors.add(new InteractiveDoor(doorId++, hx - 0.8f, 0, hz - 6, 0));

            // Лут внутри дома на полу
            loot.add(LootItem.createWeapon(lootId++, (doorId % 2 == 0 ? Weapon.ID_M4 : Weapon.ID_AKR),
                    (doorId % 2 == 0 ? "M416" : "AKM"), hx - 2, 0.2f, hz));
            loot.add(LootItem.createAmmo(lootId++, "5.56mm Ammo", 60, hx + 2, 0.2f, hz));
            loot.add(LootItem.createMedkit(lootId++, "First Aid Kit", hx, 0.2f, hz + 2));
            loot.add(LootItem.createDrink(lootId++, "Energy Drink", hx - 2, 0.2f, hz + 2));
            loot.add(LootItem.createArmor(lootId++, 2, true, hx + 2, 0.2f, hz - 2));
            loot.add(LootItem.createArmor(lootId++, 2, false, hx, 0.2f, hz - 3));
            loot.add(LootItem.createBackpack(lootId++, 2, hx + 3, 0.2f, hz + 3));
        }

        // Военная база Сосновка (Юг острова: ангары, вышки, контейнеры)
        for (int x = -100; x <= 100; x += 50) {
            addWall(x, 4f, -180, 24, 8, 16, metal);
            // Лут в ангарах
            loot.add(LootItem.createWeapon(lootId++, Weapon.ID_AWM, "AWM Sniper", x, 0.2f, -180));
            loot.add(LootItem.createWeapon(lootId++, Weapon.ID_DEAGLE, "Desert Eagle", x - 4, 0.2f, -180));
            loot.add(LootItem.createPan(lootId++, x + 4, 0.2f, -180));
            loot.add(LootItem.createArmor(lootId++, 3, true, x, 0.2f, -184));
        }

        // Спавны транспорта (Багги и УАЗ на дорогах)
        vehicles.add(new Vehicle3D(0, Vehicle3D.TYPE_BUGGY, -20, 0, -60, 0));
        vehicles.add(new Vehicle3D(1, Vehicle3D.TYPE_UAZ, 30, 0, 60, 90));
        vehicles.add(new Vehicle3D(2, Vehicle3D.TYPE_BUGGY, -120, 0, 0, 45));
        vehicles.add(new Vehicle3D(3, Vehicle3D.TYPE_UAZ, 120, 0, -120, 180));

        // Контейнерный порт (Джорджополь)
        for (int i = -80; i <= 80; i += 20) {
            addWall(i, 1.5f, 180, 6, 3, 14, metal);
            addWall(i, 1.5f, 210, 6, 3, 14, crate);
        }
    }
}
