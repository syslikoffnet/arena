package com.syslikoffnet.overglow;

import java.util.ArrayList;
import java.util.Random;

/**
 * Карта Эрангеля 600×600 — процедурная, всё генерируется кодом:
 * - Холмистый террейн (аналитическая функция высот, рендерится сеткой 161×161)
 * - Починок: дома со штукатуркой/черепицей, окнами, дверями на петлях, внутренним полом
 * - Военная база: ангары (открытые_front_, лут доступен), вышка, песчаные мешки
 * - Порт Джорэ: штабеля рифлёных контейнеров, бочки, ящики
 * - Дороги, деревья/камни/кусты (детерминированный seed), лут по всему миру
 * Препятствия ссылаются на ИНДЕКСЫ материалов TextureForge (не GL id!) — GL-id'ы
 * в конструкторе карты были бы нулями до инициализации текстур (старый баг исправлен).
 */
public final class PUBGMap {

    public static final class Obstacle {
        public final Math3D.Box box;
        public final int material;
        public final float r, g, b;

        public Obstacle(float x, float y, float z, float w, float h, float d, int material, float r, float g, float b) {
            this.box = new Math3D.Box(x - w / 2f, y - h / 2f, z - d / 2f,
                                      x + w / 2f, y + h / 2f, z + d / 2f);
            this.material = material;
            this.r = r; this.g = g; this.b = b;
        }

        public float getCenterX() { return (box.minX + box.maxX) / 2f; }
        public float getCenterY() { return (box.minY + box.maxY) / 2f; }
        public float getCenterZ() { return (box.minZ + box.maxZ) / 2f; }
        public float getWidth() { return box.maxX - box.minX; }
        public float getHeight() { return box.maxY - box.minY; }
        public float getDepth() { return box.maxZ - box.minZ; }
    }

    /** Неколлидируемая декорация (крыши, окна, деревья, пропы) */
    public static final class Decor {
        public static final int BOX = 0, ROOF = 1, TRUNK = 2, CANOPY = 3, BUSH = 4,
                ROCK = 5, BARREL = 6, CRATE = 7, WINDOW = 8, GLASS = 9, SANDBAG = 10, TOWER = 11;

        public final int kind;
        public final int material;
        public final float x, y, z, sx, sy, sz, rotY;

        public Decor(int kind, int material, float x, float y, float z, float sx, float sy, float sz, float rotY) {
            this.kind = kind; this.material = material;
            this.x = x; this.y = y; this.z = z;
            this.sx = sx; this.sy = sy; this.sz = sz; this.rotY = rotY;
        }
    }

    public final ArrayList<Obstacle> obstacles = new ArrayList<>();
    public final ArrayList<Decor> decors = new ArrayList<>();
    public final ArrayList<float[]> roads = new ArrayList<>(); // x0,z0,x1,z1,width
    public final ArrayList<InteractiveDoor> doors = new ArrayList<>();
    public final ArrayList<LootItem> loot = new ArrayList<>();
    public final ArrayList<Vehicle3D> vehicles = new ArrayList<>();

    // Траектория самолета
    public final Math3D.Vec3 planeStart = new Math3D.Vec3(-280, 180, -280);
    public final Math3D.Vec3 planeEnd = new Math3D.Vec3(280, 180, 280);
    public final Math3D.Vec3 planePos = new Math3D.Vec3();
    public float planeProgress = 0f;
    public float planeSpeed = 0.04f;

    // Зона безопасности (Safe Zone) и Синяя зона
    public float whiteZoneX = 0f;
    public float whiteZoneZ = 0f;
    public float whiteZoneRadius = 260f;

    public float blueZoneX = 0f;
    public float blueZoneZ = 0f;
    public float blueZoneRadius = 300f;
    public float zoneShrinkTimer = 60f;
    public int zonePhase = 1;

    // Аирдроп
    public final Math3D.Vec3 airdropPos = new Math3D.Vec3(0, -100, 0);
    public boolean airdropActive = false;
    public boolean airdropLanded = false;

    public PUBGMap() {
        buildErangel();
    }

    /**
     * Аналитическая функция высот: плоские Почки в центре, мягкие холмы дальше.
     * Используется террейном, коллизиями, пропами — всё всегда «приклеено» к земле.
     */
    public float getTerrainHeight(float x, float z) {
        float distToCenter = (float) Math.sqrt(x * x + z * z);
        float flat = Math3D.smoothstep(65f, 130f, distToCenter);
        float h1 = (float) Math.sin(x * 0.019f) * (float) Math.cos(z * 0.023f) * 5.2f;
        float h2 = (float) Math.sin(x * 0.055f + 1.2f) * (float) Math.cos(z * 0.041f - 0.7f) * 2.1f;
        float h3 = (float) Math.sin((x + z) * 0.11f) * 0.6f;
        float h = Math.max(0f, h1 + h2 + h3);
        // Пологий спуск к океану на границах
        float edge = Math.max(Math.abs(x), Math.abs(z));
        float edgeFall = 1f - Math3D.smoothstep(250f, 296f, edge) * 0.55f;
        return h * flat * edgeFall;
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
        if (planeProgress < 1.0f) {
            planeProgress += planeSpeed * dt;
            planePos.x = Math3D.lerp(planeStart.x, planeEnd.x, planeProgress);
            planePos.y = Math3D.lerp(planeStart.y, planeEnd.y, planeProgress);
            planePos.z = Math3D.lerp(planeStart.z, planeEnd.z, planeProgress);
        }

        for (InteractiveDoor door : doors) {
            door.update(dt);
        }

        zoneShrinkTimer -= dt;
        if (zoneShrinkTimer <= 0) {
            blueZoneRadius = Math.max(whiteZoneRadius, blueZoneRadius - dt * 4.5f);
            blueZoneX = Math3D.lerp(blueZoneX, whiteZoneX, dt * 0.05f);
            blueZoneZ = Math3D.lerp(blueZoneZ, whiteZoneZ, dt * 0.05f);

            if (blueZoneRadius <= whiteZoneRadius + 1f) {
                zonePhase++;
                zoneShrinkTimer = 45f;
                whiteZoneRadius = Math.max(30f, whiteZoneRadius * 0.60f);
                whiteZoneX += (float) ((Math.random() - 0.5) * 40);
                whiteZoneZ += (float) ((Math.random() - 0.5) * 40);

                spawnAirdrop(whiteZoneX + (float) ((Math.random() - 0.5) * 50),
                             whiteZoneZ + (float) ((Math.random() - 0.5) * 50));
            }
        }

        if (airdropActive && !airdropLanded) {
            airdropPos.y -= 12f * dt;
            float gHeight = getTerrainHeight(airdropPos.x, airdropPos.z);
            if (airdropPos.y <= gHeight) {
                airdropPos.y = gHeight;
                airdropLanded = true;
                loot.add(LootItem.createWeapon(loot.size(), Weapon.ID_AWM, "AWM Sniper", airdropPos.x, gHeight + 0.4f, airdropPos.z));
                loot.add(LootItem.createArmor(loot.size(), 3, true, airdropPos.x + 1f, gHeight + 0.4f, airdropPos.z));
                loot.add(LootItem.createArmor(loot.size(), 3, false, airdropPos.x - 1f, gHeight + 0.4f, airdropPos.z));
                loot.add(LootItem.createAmmo(loot.size(), ".300 Magnum Ammo", 30, airdropPos.x, gHeight + 0.4f, airdropPos.z + 1f));
            }
        }
    }

    public void spawnAirdrop(float x, float z) {
        airdropPos.set(x, 150f, z);
        airdropActive = true;
        airdropLanded = false;
        SoundSynth3D.play2D(SoundSynth3D.SOUND_RPG, 0.8f);
    }

    private void addWall(float x, float y, float z, float w, float h, float d, int mat) {
        obstacles.add(new Obstacle(x, y, z, w, h, d, mat, 1f, 1f, 1f));
    }

    private void addWallTinted(float x, float y, float z, float w, float h, float d, int mat, float r, float g, float b) {
        obstacles.add(new Obstacle(x, y, z, w, h, d, mat, r, g, b));
    }

    private void addDecor(int kind, int mat, float x, float y, float z, float sx, float sy, float sz, float rotY) {
        decors.add(new Decor(kind, mat, x, y, z, sx, sy, sz, rotY));
    }

    // =========================================================================
    // Генерация мира
    // =========================================================================
    private void buildErangel() {
        int grass = TextureForge.MAT_GRASS;
        int dirt = TextureForge.MAT_DIRT;
        int road = TextureForge.MAT_ROAD;
        int concrete = TextureForge.MAT_CONCRETE;
        int plaster = TextureForge.MAT_PLASTER;
        int brick = TextureForge.MAT_BRICK;
        int roof = TextureForge.MAT_ROOF;
        int wood = TextureForge.MAT_WOOD;
        int crate = TextureForge.MAT_CRATE;
        int metal = TextureForge.MAT_METAL;
        int corr = TextureForge.MAT_CORRUGATED;
        int sand = TextureForge.MAT_SAND;
        int rock = TextureForge.MAT_ROCK;

        // ---- Границы острова (пляжный песок) ----
        addWall(0, 8, 300, 600, 16, 6, sand);
        addWall(0, 8, -300, 600, 16, 6, sand);
        addWall(300, 8, 0, 6, 16, 600, sand);
        addWall(-300, 8, 0, 6, 16, 600, sand);

        // ---- Дороги: центральные крест + объездные ----
        roads.add(new float[]{-150, 20, 150, 20, 9});
        roads.add(new float[]{40, -140, 40, 140, 8});
        roads.add(new float[]{-140, -100, -140, 120, 7});
        roads.add(new float[]{-120, -160, 120, -160, 9});   // подъезд к базе
        roads.add(new float[]{120, -160, 120, 160, 8});      // объезд на порт
        roads.add(new float[]{-120, 180, 120, 180, 9});      // портовая дамба

        // ---- Починки: 8 домов around две улицы ----
        float[][] pochinkiHouses = {
                {-40, -40}, {0, -40}, {40, -40},
                {-40, 0}, {40, 0},
                {-40, 40}, {0, 40}, {40, 40}
        };

        int doorId = 0;
        int lootId = 0;
        Random rnd = new Random(40771);

        for (int hi = 0; hi < pochinkiHouses.length; hi++) {
            float hx = pochinkiHouses[hi][0], hz = pochinkiHouses[hi][1];
            float hy = getTerrainHeight(hx, hz);
            lootId = buildHouse(hx, hy, hz, doorId, lootId, rnd, hi);
            doorId++;

            // двор: ящики/бочки/дерево у дома
            if (rnd.nextFloat() < 0.8f) {
                float cxx = hx + (hi % 2 == 0 ? 9.5f : -9.5f), czz = hz + 6;
                addWall(cxx, hy + 0.45f, czz, 0.95f, 0.9f, 0.95f, crate);
                addDecor(Decor.CRATE, crate, cxx, hy, czz, 1, 1, 1, rnd.nextFloat() * 90f);
            }
            if (rnd.nextFloat() < 0.6f) {
                float bxx = hx - 8.5f, bzz = hz - 7.5f;
                addWall(bxx, hy + 0.45f, bzz, 0.7f, 0.9f, 0.7f, metal);
                addDecor(Decor.BARREL, metal, bxx, hy, bzz, 1, 1, 1, 0);
            }
        }

        // ---- Военная база Сосновка: 3 ангара с открытым фронтом ----
        for (int x = -100; x <= 100; x += 100) {
            float y = getTerrainHeight(x, -180);
            buildHangar(x, y, -180, concrete, metal, corr);
            lootId = buildBaseLoot(x, y, -180, lootId, rnd);
        }

        // Вышка контроля полётов (декорация, коллизию дают только ножки-блоки по углам — проходимая)
        float twy = getTerrainHeight(0, -215);
        addDecor(Decor.TOWER, metal, 0, twy, -215, 1, 1, 1, 0);

        // Песчаные редуты перед ангарами
        for (int x = -100; x <= 100; x += 50) {
            float y = getTerrainHeight(x, -158);
            addDecor(Decor.SANDBAG, sand, x, y, -158, 1, 1, 1, 0);
            addWall(x, y + 0.55f, -158, 4.6f, 1.1f, 0.9f, sand);
        }

        // ---- Порт: штабеля контейнеров ----
        for (int i = -80; i <= 80; i += 20) {
            float y = getTerrainHeight(i, 180);
            addWall(i, y + 1.5f, 180, 6, 3, 14, corr);
            addDecor(Decor.BOX, corr, i, y, 180, 6, 3, 14, 0);
            if (rnd.nextFloat() < 0.45f) { // второй ярус
                addWall(i, y + 4.5f, 180, 6, 3, 14, corr);
                addDecor(Decor.BOX, corr, i, y + 3f, 180, 6, 3, 14, 0);
                // лут на крыше нижнего контейнера рядом
                loot.add(LootItem.createArmor(lootId++, 2, false, i, y + 3.15f, 176));
            }
            if (rnd.nextFloat() < 0.5f) {
                float z2 = 200 + rnd.nextFloat() * 8;
                addWall(i + 6, getTerrainHeight(i + 6, z2) + 1.5f, z2, 6, 3, 14, corr);
                addDecor(Decor.BOX, corr, i + 6, getTerrainHeight(i + 6, z2), z2, 6, 3, 14, 0);
            }
            // ящики и бочки между контейнерами
            addWall(i + 10, y + 0.45f, 190, 0.95f, 0.9f, 0.95f, crate);
            addDecor(Decor.CRATE, crate, i + 10, y, 190, 1, 1, 1, rnd.nextFloat() * 90f);
            if (rnd.nextFloat() < 0.7f) {
                addDecor(Decor.BARREL, corr, i - 9, y, 196, 1, 1, 1, 0);
                addWall(i - 9, y + 0.45f, 196, 0.7f, 0.9f, 0.7f, corr);
            }
            if (rnd.nextFloat() < 0.4f) {
                loot.add(LootItem.createWeapon(lootId++, rnd.nextBoolean() ? Weapon.ID_M4 : Weapon.ID_MP5,
                        rnd.nextBoolean() ? "M416" : "MP5K", i, y + 0.25f, 192));
            }
            if (rnd.nextFloat() < 0.35f) {
                loot.add(LootItem.createWeapon(lootId++, Weapon.ID_GRENADE, "Grenade", i + 3, y + 0.25f, 186));
            }
        }

        // ---- Природа: деревья, камни, кусты (сеянный ГСЧ — стабильный мир) ----
        int trees = 0, guard = 0;
        while (trees < 150 && guard++ < 4000) {
            float x = (rnd.nextFloat() * 2 - 1) * 275f;
            float z = (rnd.nextFloat() * 2 - 1) * 275f;
            if (nearTownOrBase(x, z)) continue;
            if (nearRoad(x, z, 7f)) continue;
            float s = 0.8f + rnd.nextFloat() * 0.7f;
            float y = getTerrainHeight(x, z);
            addDecor(Decor.TRUNK, TextureForge.MAT_BARK, x, y, z, s, s, s, 0);
            addDecor(Decor.CANOPY, TextureForge.MAT_LEAVES, x, y, z, s * (0.85f + rnd.nextFloat() * 0.4f), s, s * (0.85f + rnd.nextFloat() * 0.4f), 0);
            addWall(x, y + 3f * s, z, 0.7f * s, 6f * s, 0.7f * s, TextureForge.MAT_BARK);
            trees++;
        }
        int rocks = 0; guard = 0;
        while (rocks < 90 && guard++ < 3000) {
            float x = (rnd.nextFloat() * 2 - 1) * 285f;
            float z = (rnd.nextFloat() * 2 - 1) * 285f;
            if (nearTownOrBase(x, z)) continue;
            float s = 0.7f + rnd.nextFloat() * 1.5f;
            float y = getTerrainHeight(x, z);
            addDecor(Decor.ROCK, rock, x, y - 0.1f, z, s * 1.3f, s, s * 1.3f, rnd.nextFloat() * 360f);
            addWall(x, y + s * 0.45f, z, s * 1.5f, s * 1.05f, s * 1.5f, rock);
            rocks++;
        }
        int bushes = 0; guard = 0;
        while (bushes < 190 && guard++ < 4000) {
            float x = (rnd.nextFloat() * 2 - 1) * 288f;
            float z = (rnd.nextFloat() * 2 - 1) * 288f;
            if (nearTownOrBase(x, z)) continue;
            float s = 0.7f + rnd.nextFloat() * 0.8f;
            addDecor(Decor.BUSH, TextureForge.MAT_LEAVES, x, getTerrainHeight(x, z), z, s, s * 0.9f, s, rnd.nextFloat() * 360f);
            bushes++;
        }

        // ---- Транспорт ----
        vehicles.add(new Vehicle3D(0, Vehicle3D.TYPE_BUGGY, -20, getTerrainHeight(-20, -60), -60, 0));
        vehicles.add(new Vehicle3D(1, Vehicle3D.TYPE_UAZ, 30, getTerrainHeight(30, 60), 60, 90));
        vehicles.add(new Vehicle3D(2, Vehicle3D.TYPE_BUGGY, -120, getTerrainHeight(-120, 0), 0, 45));
        vehicles.add(new Vehicle3D(3, Vehicle3D.TYPE_UAZ, 120, getTerrainHeight(120, -120), -120, 180));
        vehicles.add(new Vehicle3D(4, Vehicle3D.TYPE_UAZ, -60, getTerrainHeight(-60, 160), 160, 0));
    }

    /** Дом Починок: 4 стены со штукатуркой, черепичная крыша, окна, крыльцо, лут. Возвращает новый lootId */
    private int buildHouse(float hx, float hy, float hz, int doorIdx, int lootId, Random rnd, int hi) {
        int plaster = TextureForge.MAT_PLASTER;
        int roof = TextureForge.MAT_ROOF;
        int wood = TextureForge.MAT_WOOD;
        int brick = TextureForge.MAT_BRICK;
        int concrete = TextureForge.MAT_CONCRETE;
        int glass = TextureForge.MAT_METAL; // стекло: тёмный металл с бликом (дешевый читаемый материал)

        float wallT = 0.8f, wallH = 5.6f, wallY = hy + wallH / 2f;
        // Фронт (z - 6) с проёмом двери 2.4м
        addWall(hx - 3.9f, wallY, hz - 6, 4.2f, wallH, wallT, plaster);
        addWall(hx + 3.9f, wallY, hz - 6, 4.2f, wallH, wallT, plaster);
        addWall(hx, hy + wallH + 0.7f, hz - 6, 3.6f, 1.4f, wallT, plaster); // перемычка
        // Задняя и боковые
        addWall(hx, wallY, hz + 6, 12, wallH, wallT, plaster);
        addWall(hx - 6, wallY, hz, wallT, wallH, 12, plaster);
        addWall(hx + 6, wallY, hz, wallT, wallH, 12, plaster);
        // Цоколь из кирпича по периметру
        addWallTinted(hx, hy + 0.3f, hz - 6.02f, 12.4f, 0.6f, 0.5f, brick, 0.9f, 0.85f, 0.8f);
        addWallTinted(hx, hy + 0.3f, hz + 6.02f, 12.4f, 0.6f, 0.5f, brick, 0.9f, 0.85f, 0.8f);
        // Перекрытие + двускатная крыша (база скатов над верхом стен/перемычки)
        addWall(hx, hy + wallH + 0.15f, hz, 12.8f, 0.3f, 12.8f, wood);
        addDecor(Decor.ROOF, roof, hx, hy + 7.05f, hz, 1.06f, 1f, 1.06f, 0);
        // Дымоход
        addWall(hx + 4, hy + wallH + 2.6f, hz + 2.5f, 1f, 3.2f, 1f, brick);
        // Внутренний бетонный пол
        addWall(hx, hy + 0.04f, hz, 11.9f, 0.1f, 11.9f, concrete);
        // Окна: 2 на фасаде по бокам двери, по 1 на стенах
        addDecor(Decor.WINDOW, wood, hx - 3.9f, hy + 3.2f, hz - 6.45f, 1, 1, 1, 0);
        addDecor(Decor.GLASS, glass, hx - 3.9f, hy + 3.2f, hz - 6.42f, 1, 1, 1, 0);
        addDecor(Decor.WINDOW, wood, hx + 3.9f, hy + 3.2f, hz - 6.45f, 1, 1, 1, 0);
        addDecor(Decor.GLASS, glass, hx + 3.9f, hy + 3.2f, hz - 6.42f, 1, 1, 1, 0);
        addDecor(Decor.WINDOW, wood, hx - 6.45f, hy + 3.2f, hz, 1, 1, 1, 90);
        addDecor(Decor.GLASS, glass, hx - 6.42f, hy + 3.2f, hz, 1, 1, 1, 90);
        addDecor(Decor.WINDOW, wood, hx + 6.45f, hy + 3.2f, hz, 1, 1, 1, 90);
        addDecor(Decor.GLASS, glass, hx + 6.42f, hy + 3.2f, hz, 1, 1, 1, 90);
        // Крыльцо
        addWall(hx, hy + 0.12f, hz - 7.2f, 3f, 0.24f, 1.6f, concrete);

        // Дверь на петлях (левой край проёма)
        doors.add(new InteractiveDoor(doorIdx, hx - 1.2f, hy, hz - 6, 0));

        // ---- Лут внутри ----
        int gun = (hi % 2 == 0) ? Weapon.ID_M4 : Weapon.ID_AKR;
        loot.add(LootItem.createWeapon(lootId++, gun, (gun == Weapon.ID_M4 ? "M416" : "AKM"), hx - 2, hy + 0.25f, hz));
        if (rnd.nextFloat() < 0.5f) {
            loot.add(LootItem.createWeapon(lootId++, Weapon.ID_MP5, "MP5K", hx + 2, hy + 0.25f, hz + 1.5f));
        }
        loot.add(LootItem.createAttachment(lootId++, (hi % 2 == 0 ? WeaponAttachment.ID_MUZZLE_COMPENSATOR : WeaponAttachment.ID_MUZZLE_SUPPRESSOR),
                (hi % 2 == 0 ? "Compensator" : "Suppressor"), hx - 3.4f, hy + 0.25f, hz + 1));
        if (rnd.nextFloat() < 0.7f) {
            loot.add(LootItem.createAttachment(lootId++, rnd.nextBoolean() ? WeaponAttachment.ID_SCOPE_4X : WeaponAttachment.ID_GRIP_VERTICAL,
                    "Optic/Grip", hx + 3.4f, hy + 0.25f, hz - 1));
        }
        loot.add(LootItem.createAmmo(lootId++, "Ammo", 60, hx + 2, hy + 0.25f, hz));
        loot.add(LootItem.createAmmo(lootId++, "Ammo", 30, hx - 2, hy + 0.25f, hz - 2.2f));
        loot.add(LootItem.createMedkit(lootId++, "First Aid Kit", hx, hy + 0.25f, hz + 2.2f));
        if (rnd.nextFloat() < 0.65f) loot.add(LootItem.createMedkit(lootId++, "Painkiller", hx - 1.2f, hy + 0.25f, hz + 2.6f));
        loot.add(LootItem.createDrink(lootId++, "Energy Drink", hx - 2.2f, hy + 0.25f, hz + 2));
        if (rnd.nextFloat() < 0.85f) loot.add(LootItem.createArmor(lootId++, 1 + rnd.nextInt(2), true, hx + 2.2f, hy + 0.25f, hz - 2));
        if (rnd.nextFloat() < 0.85f) loot.add(LootItem.createArmor(lootId++, 1 + rnd.nextInt(2), false, hx, hy + 0.25f, hz - 3.2f));
        if (rnd.nextFloat() < 0.55f) loot.add(LootItem.createBackpack(lootId++, 1 + rnd.nextInt(2), hx + 3.4f, hy + 0.25f, hz + 3));
        if (rnd.nextFloat() < 0.2f) loot.add(LootItem.createPan(lootId++, hx - 3.4f, hy + 0.25f, hz - 3));
        if (rnd.nextFloat() < 0.45f) loot.add(LootItem.createWeapon(lootId++, Weapon.ID_GRENADE, "Grenade", hx - 1, hy + 0.25f, hz + 3.4f));
        return lootId;
    }

    /** Ангар: тыловая + боковые стены, широкий вход, покатая металлическая крыша */
    private void buildHangar(float hx, float hy, float hz, int concrete, int metal, int corr) {
        float w = 24f, d = 16f, h = 8f, t = 1f;
        addWall(hx, hy + h / 2f, hz + d / 2f, w, h, t, concrete);            // задняя
        addWall(hx - w / 2f, hy + h / 2f, hz, t, h, d, concrete);             // левая
        addWall(hx + w / 2f, hy + h / 2f, hz, t, h, d, concrete);             // правая
        addWall(hx - w / 4f - 3f, hy + h / 2f, hz - d / 2f, 6f, h, t, concrete); // фронт слева
        addWall(hx + w / 4f + 3f, hy + h / 2f, hz - d / 2f, 6f, h, t, concrete); // фронт справа
        addWall(hx, hy + h + 0.3f, hz - d / 2f, 13f, 1.4f, t, concrete);         // перемычка входа
        // Крыша: slight A-frame через два декоративных ската
        addDecor(Decor.BOX, corr, hx, hy + h + 0.6f, hz, w + 1.6f, 0.35f, d + 1.6f, 0);
        // Пол ангара
        addWall(hx, hy + 0.03f, hz, w, 0.06f, d, concrete);
    }

    private int buildBaseLoot(float hx, float hy, float hz, int lootId, Random rnd) {
        for (int i = 0; i < 3; i++) {
            float x = hx + (i - 1) * 6;
            int wep = rnd.nextInt(4);
            loot.add(LootItem.createWeapon(lootId++, wep == 0 ? Weapon.ID_AWM : (wep == 1 ? Weapon.ID_AKR : (wep == 2 ? Weapon.ID_DEAGLE : Weapon.ID_RPG)),
                    wep == 0 ? "AWM Sniper" : (wep == 1 ? "AKM" : (wep == 2 ? "Desert Eagle" : "RPG-7")),
                    x, hy + 0.25f, hz + 2f));
            loot.add(LootItem.createAmmo(lootId++, "Ammo", 60, x, hy + 0.25f, hz - 2f));
        }
        loot.add(LootItem.createArmor(lootId++, 3, true, hx - 8, hy + 0.25f, hz - 4));
        loot.add(LootItem.createArmor(lootId++, 3, false, hx + 8, hy + 0.25f, hz - 4));
        loot.add(LootItem.createMedkit(lootId++, "Med Kit", hx, hy + 0.25f, hz - 5));
        loot.add(LootItem.createPan(lootId++, hx + 5, hy + 0.25f, hz + 5));
        return lootId;
    }

    private boolean nearTownOrBase(float x, float z) {
        if (x * x + z * z < 78f * 78f) return true;                    // Почки
        if (Math.abs(x) < 130 && z < -140 && z > -235) return true;    // база
        if (Math.abs(x) < 115 && z > 155) return true;                 // порт
        return false;
    }

    private boolean nearRoad(float x, float z, float margin) {
        for (float[] r : roads) {
            if (distToSegment(x, z, r[0], r[1], r[2], r[3]) < r[4] / 2f + margin) return true;
        }
        return false;
    }

    private static float distToSegment(float px, float pz, float x0, float z0, float x1, float z1) {
        float dx = x1 - x0, dz = z1 - z0;
        float lenSq = dx * dx + dz * dz;
        float t = lenSq < 0.0001f ? 0f : Math3D.clamp(((px - x0) * dx + (pz - z0) * dz) / lenSq, 0f, 1f);
        float cx = x0 + dx * t - px, cz = z0 + dz * t - pz;
        return (float) Math.sqrt(cx * cx + cz * cz);
    }
}
