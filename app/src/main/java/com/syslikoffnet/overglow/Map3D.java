package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * 3D Карты для Standoff 2 и PUBG режимов:
 * - Sandstone (Тактическая арена с Plant A / B, мидом и тоннелями)
 * - Cyber Bunker (Военный ангар с контейнерами)
 * - Battle Island (Королевская битва с зоной радиации)
 */
public final class Map3D {

    public static final int MAP_SANDSTONE = 0;
    public static final int MAP_CYBER_BUNKER = 1;
    public static final int MAP_BATTLE_ISLAND = 2;

    public final int id;
    public final String name;
    public final ArrayList<Obstacle> obstacles = new ArrayList<>();
    public final ArrayList<Math3D.Vec3> spawnsT = new ArrayList<>();
    public final ArrayList<Math3D.Vec3> spawnsCT = new ArrayList<>();

    // Точки закладки бомбы
    public final Math3D.Vec3 plantSiteA = new Math3D.Vec3();
    public final Math3D.Vec3 plantSiteB = new Math3D.Vec3();

    // Зона для Королевской битвы (Battle Royale)
    public float zoneCenterX = 0;
    public float zoneCenterZ = 0;
    public float zoneRadius = 180f;
    public float zoneTargetRadius = 180f;
    public float zoneShrinkSpeed = 2.5f;

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

    public Map3D(int id) {
        this.id = id;
        switch (id) {
            case MAP_SANDSTONE:
                this.name = "Sandstone Arena";
                buildSandstone();
                break;
            case MAP_CYBER_BUNKER:
                this.name = "Cyber Hangar";
                buildCyberBunker();
                break;
            case MAP_BATTLE_ISLAND:
                this.name = "Battle Island";
                buildBattleIsland();
                break;
            default:
                this.name = "Sandstone Arena";
                buildSandstone();
                break;
        }
    }

    public void updateZone(float dt) {
        if (id == MAP_BATTLE_ISLAND) {
            if (zoneRadius > zoneTargetRadius) {
                zoneRadius = Math.max(zoneTargetRadius, zoneRadius - zoneShrinkSpeed * dt);
            }
        }
    }

    private void addWall(float x, float y, float z, float w, float h, float d, int tex) {
        obstacles.add(new Obstacle(x, y, z, w, h, d, tex, 1f, 1f, 1f));
    }

    private void buildSandstone() {
        // Внешние стены арены 120х120
        int sand = GLUtil.texSandstone;
        int crate = GLUtil.texCrate;
        int conc = GLUtil.texConcrete;

        addWall(0, 4, 60, 120, 8, 2, sand);
        addWall(0, 4, -60, 120, 8, 2, sand);
        addWall(60, 4, 0, 2, 8, 120, sand);
        addWall(-60, 4, 0, 2, 8, 120, sand);

        // Центральный тоннель (Mid)
        addWall(0, 3.5f, 10, 8, 7, 24, sand);
        addWall(0, 3.5f, -10, 8, 7, 24, sand);

        // Длинный проход на A-Site (Long A)
        addWall(35, 3, 20, 30, 6, 2, sand);
        addWall(20, 3, 35, 2, 6, 30, sand);

        // Плент А (Plant Site A)
        plantSiteA.set(40, 0, 40);
        addWall(40, 1, 40, 2.5f, 2f, 2.5f, crate);
        addWall(43, 1, 38, 2.5f, 2f, 2.5f, crate);
        addWall(38, 1.5f, 43, 2.5f, 3f, 2.5f, crate);

        // Тоннели на B-Site (B-Apartments & Dark)
        addWall(-35, 3, 20, 30, 6, 2, sand);
        addWall(-20, 3, 35, 2, 6, 30, sand);

        // Плент B (Plant Site B)
        plantSiteB.set(-40, 0, 40);
        addWall(-40, 1, 40, 3f, 2f, 3f, crate);
        addWall(-44, 1.2f, 42, 2.5f, 2.4f, 2.5f, crate);
        addWall(-38, 1, 44, 2f, 2f, 2f, crate);

        // Укрытия на миду и спавнах
        addWall(12, 1, 0, 3, 2, 3, crate);
        addWall(-12, 1, 0, 3, 2, 3, crate);
        addWall(0, 1, 28, 4, 2, 4, conc);
        addWall(0, 1, -28, 4, 2, 4, conc);

        // Спавны T (Террористы - низ карты)
        spawnsT.add(new Math3D.Vec3(0, 0, -48));
        spawnsT.add(new Math3D.Vec3(-10, 0, -50));
        spawnsT.add(new Math3D.Vec3(10, 0, -50));
        spawnsT.add(new Math3D.Vec3(-20, 0, -48));
        spawnsT.add(new Math3D.Vec3(20, 0, -48));

        // Спавны CT (Спецназ - верх карты)
        spawnsCT.add(new Math3D.Vec3(0, 0, 48));
        spawnsCT.add(new Math3D.Vec3(-10, 0, 50));
        spawnsCT.add(new Math3D.Vec3(10, 0, 50));
        spawnsCT.add(new Math3D.Vec3(-20, 0, 48));
        spawnsCT.add(new Math3D.Vec3(20, 0, 48));
    }

    private void buildCyberBunker() {
        int metal = GLUtil.texMetal;
        int conc = GLUtil.texConcrete;
        int crate = GLUtil.texCrate;

        // Внешний ангар 140x140
        addWall(0, 6, 70, 140, 12, 2, metal);
        addWall(0, 6, -70, 140, 12, 2, metal);
        addWall(70, 6, 0, 2, 12, 140, metal);
        addWall(-70, 6, 0, 2, 12, 140, metal);

        // Контейнерные ряды
        for (int i = -40; i <= 40; i += 25) {
            addWall(i, 2, 15, 6, 4, 14, metal);
            addWall(i, 2, -15, 6, 4, 14, metal);
        }

        // Центральная наблюдательная вышка
        addWall(0, 3, 0, 16, 6, 16, conc);
        addWall(0, 7, 0, 12, 2, 12, metal);

        plantSiteA.set(45, 0, 45);
        plantSiteB.set(-45, 0, 45);

        addWall(45, 1.5f, 45, 4, 3, 4, crate);
        addWall(-45, 1.5f, 45, 4, 3, 4, crate);

        spawnsT.add(new Math3D.Vec3(0, 0, -58));
        spawnsT.add(new Math3D.Vec3(-15, 0, -58));
        spawnsT.add(new Math3D.Vec3(15, 0, -58));

        spawnsCT.add(new Math3D.Vec3(0, 0, 58));
        spawnsCT.add(new Math3D.Vec3(-15, 0, 58));
        spawnsCT.add(new Math3D.Vec3(15, 0, 58));
    }

    private void buildBattleIsland() {
        int conc = GLUtil.texConcrete;
        int crate = GLUtil.texCrate;
        int sand = GLUtil.texSandstone;

        // Огромная территория 300х300
        addWall(0, 8, 150, 300, 16, 4, sand);
        addWall(0, 8, -150, 300, 16, 4, sand);
        addWall(150, 8, 0, 4, 16, 300, sand);
        addWall(-150, 8, 0, 4, 16, 300, sand);

        // Несколько деревень и военных баз на острове
        float[][] compounds = {
                {-60, -60}, {60, -60}, {-60, 60}, {60, 60},
                {0, 0}, {0, -80}, {0, 80}, {-80, 0}, {80, 0}
        };

        for (float[] comp : compounds) {
            float cx = comp[0], cz = comp[1];
            // Двухэтажные здания
            addWall(cx - 10, 4, cz, 12, 8, 14, conc);
            addWall(cx + 10, 4, cz, 12, 8, 14, conc);
            // Ящики с лутом
            addWall(cx, 1, cz + 6, 3, 2, 3, crate);
            addWall(cx, 1, cz - 6, 3, 2, 3, crate);
        }

        // Разбросанные точки спавна игроков вокруг острова
        for (int i = 0; i < 16; i++) {
            float angle = (float) (i * Math.PI * 2 / 16);
            float dist = 110f;
            spawnsT.add(new Math3D.Vec3((float) Math.cos(angle) * dist, 0, (float) Math.sin(angle) * dist));
            spawnsCT.add(new Math3D.Vec3((float) Math.cos(angle) * dist, 0, (float) Math.sin(angle) * dist));
        }

        zoneRadius = 160f;
        zoneTargetRadius = 30f;
        zoneShrinkSpeed = 1.8f;
    }
}
