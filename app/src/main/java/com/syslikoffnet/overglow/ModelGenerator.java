package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * Процедурный генератор 3D моделей: ящики, здания, оружие (AKR, M4, AWM, Deagle, Shotgun, RPG, Knife),
 * гранаты, персонажи (T / CT с анимацией шага и прицеливания).
 */
public final class ModelGenerator {

    private ModelGenerator() {}

    // =========================================================================
    // Билдер для объединения полигонов в единый Mesh
    // =========================================================================
    public static final class Builder {
        private final ArrayList<Float> vList = new ArrayList<>();
        private final ArrayList<Float> nList = new ArrayList<>();
        private final ArrayList<Float> uList = new ArrayList<>();
        private final ArrayList<Short> iList = new ArrayList<>();
        private short vOffset = 0;

        public Builder addBox(float x, float y, float z, float w, float h, float d, float uTile, float vTile) {
            float hw = w / 2f, hh = h / 2f, hd = d / 2f;
            float x0 = x - hw, x1 = x + hw;
            float y0 = y - hh, y1 = y + hh;
            float z0 = z - hd, z1 = z + hd;

            // 6 граней: Front, Back, Top, Bottom, Left, Right
            // Front (+Z)
            addQuad(x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1,   0, 0, 1,   uTile, vTile);
            // Back (-Z)
            addQuad(x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0,   0, 0, -1,  uTile, vTile);
            // Top (+Y)
            addQuad(x0, y1, z1,  x1, y1, z1,  x1, y1, z0,  x0, y1, z0,   0, 1, 0,   uTile, vTile);
            // Bottom (-Y)
            addQuad(x0, y0, z0,  x1, y0, z0,  x1, y0, z1,  x0, y0, z1,   0, -1, 0,  uTile, vTile);
            // Right (+X)
            addQuad(x1, y0, z1,  x1, y0, z0,  x1, y1, z0,  x1, y1, z1,   1, 0, 0,   uTile, vTile);
            // Left (-X)
            addQuad(x0, y0, z0,  x0, y0, z1,  x0, y1, z1,  x0, y1, z0,  -1, 0, 0,   uTile, vTile);

            return this;
        }

        public Builder addQuad(float x0, float y0, float z0,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               float x3, float y3, float z3,
                               float nx, float ny, float nz,
                               float uTile, float vTile) {
            short base = vOffset;

            // Вершины
            addVert(x0, y0, z0, nx, ny, nz, 0, vTile);
            addVert(x1, y1, z1, nx, ny, nz, uTile, vTile);
            addVert(x2, y2, z2, nx, ny, nz, uTile, 0);
            addVert(x3, y3, z3, nx, ny, nz, 0, 0);

            // Индексы двух треугольников
            iList.add(base);
            iList.add((short) (base + 1));
            iList.add((short) (base + 2));

            iList.add(base);
            iList.add((short) (base + 2));
            iList.add((short) (base + 3));

            vOffset += 4;
            return this;
        }

        public Builder addCylinder(float cx, float cy, float cz, float radius, float height, int segments) {
            float hh = height / 2f;
            float step = (float) (Math.PI * 2 / segments);
            for (int i = 0; i < segments; i++) {
                float a0 = i * step;
                float a1 = (i + 1) * step;

                float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
                float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);

                float x0 = cx + cos0 * radius, z0 = cz + sin0 * radius;
                float x1 = cx + cos1 * radius, z1 = cz + sin1 * radius;

                // Сторона цилиндра
                addQuad(x0, cy - hh, z0,
                        x1, cy - hh, z1,
                        x1, cy + hh, z1,
                        x0, cy + hh, z0,
                        cos0, 0, sin0,
                        1f, 1f);
            }
            return this;
        }

        private void addVert(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
            vList.add(x); vList.add(y); vList.add(z);
            nList.add(nx); nList.add(ny); nList.add(nz);
            uList.add(u); uList.add(v);
        }

        public Mesh build() {
            float[] v = new float[vList.size()];
            for (int i = 0; i < v.length; i++) v[i] = vList.get(i);

            float[] n = new float[nList.size()];
            for (int i = 0; i < n.length; i++) n[i] = nList.get(i);

            float[] u = new float[uList.size()];
            for (int i = 0; i < u.length; i++) u[i] = uList.get(i);

            short[] idx = new short[iList.size()];
            for (int i = 0; i < idx.length; i++) idx[i] = iList.get(i);

            return new Mesh(v, n, u, idx);
        }
    }

    // =========================================================================
    // Готовые статические меши
    // =========================================================================
    public static Mesh boxMesh;
    public static Mesh barrelMesh;
    public static Mesh groundMesh;
    public static Mesh skyboxMesh;
    public static Mesh quad2DMesh;

    // Оружие
    public static Mesh akrMesh;
    public static Mesh m4Mesh;
    public static Mesh awmMesh;
    public static Mesh deagleMesh;
    public static Mesh mp5Mesh;
    public static Mesh shotgunMesh;
    public static Mesh rpgMesh;
    public static Mesh knifeMesh;
    public static Mesh grenadeMesh;

    // Персонаж (части тела для анимации)
    public static Mesh charHeadMesh;
    public static Mesh charTorsoMesh;
    public static Mesh charArmMesh;
    public static Mesh charLegMesh;

    public static void init() {
        // Базовый ящик 1x1x1
        boxMesh = new Builder().addBox(0, 0, 0, 1, 1, 1, 1, 1).build();

        // Бочка
        barrelMesh = new Builder().addCylinder(0, 0, 0, 0.45f, 1.2f, 12).build();

        // Земля арены
        groundMesh = new Builder().addQuad(
                -250, 0,  250,
                 250, 0,  250,
                 250, 0, -250,
                -250, 0, -250,
                0, 1, 0,
                50, 50).build();

        // Небесный купол
        skyboxMesh = new Builder().addBox(0, 0, 0, 600, 600, 600, 1, 1).build();

        // 2D Квадрат для UI / частиц
        quad2DMesh = new Builder().addQuad(
                -0.5f, -0.5f, 0,
                 0.5f, -0.5f, 0,
                 0.5f,  0.5f, 0,
                -0.5f,  0.5f, 0,
                0, 0, 1,
                1, 1).build();

        // Генерация оружия
        akrMesh = buildAKR();
        m4Mesh = buildM4();
        awmMesh = buildAWM();
        deagleMesh = buildDeagle();
        mp5Mesh = buildMP5();
        shotgunMesh = buildShotgun();
        rpgMesh = buildRPG();
        knifeMesh = buildKnife();
        grenadeMesh = buildGrenade();

        // Генерация частей персонажа
        charHeadMesh = new Builder().addBox(0, 0, 0, 0.36f, 0.36f, 0.36f, 1, 1).build();
        charTorsoMesh = new Builder().addBox(0, 0, 0, 0.52f, 0.72f, 0.34f, 1, 1).build();
        charArmMesh = new Builder().addBox(0, 0, 0, 0.18f, 0.56f, 0.18f, 1, 1).build();
        charLegMesh = new Builder().addBox(0, 0, 0, 0.20f, 0.68f, 0.20f, 1, 1).build();
    }

    // ------------------------------------------------------------- 3D Оружие

    private static Mesh buildAKR() {
        Builder b = new Builder();
        // Ствольная коробка
        b.addBox(0, 0, 0, 0.08f, 0.12f, 0.48f, 1, 1);
        // Ствол
        b.addBox(0, 0.02f, 0.36f, 0.04f, 0.04f, 0.38f, 1, 1);
        // Газовая трубка и цевье
        b.addBox(0, 0, 0.24f, 0.07f, 0.10f, 0.22f, 1, 1);
        // Магазин рожок
        b.addBox(0, -0.14f, 0.08f, 0.06f, 0.20f, 0.10f, 1, 1);
        // Приклад
        b.addBox(0, -0.02f, -0.32f, 0.07f, 0.14f, 0.24f, 1, 1);
        // Рукоятка
        b.addBox(0, -0.12f, -0.10f, 0.06f, 0.14f, 0.07f, 1, 1);
        return b.build();
    }

    private static Mesh buildM4() {
        Builder b = new Builder();
        // Корпус M4
        b.addBox(0, 0, 0, 0.07f, 0.11f, 0.44f, 1, 1);
        // Ствол с глушителем
        b.addBox(0, 0.01f, 0.38f, 0.05f, 0.05f, 0.42f, 1, 1);
        // Цевье RIS планка
        b.addBox(0, 0.01f, 0.22f, 0.08f, 0.09f, 0.20f, 1, 1);
        // Прямой магазин
        b.addBox(0, -0.13f, 0.06f, 0.05f, 0.18f, 0.08f, 1, 1);
        // Телескопический приклад
        b.addBox(0, 0.01f, -0.30f, 0.06f, 0.12f, 0.22f, 1, 1);
        // Тактическая рукоятка
        b.addBox(0, -0.12f, -0.08f, 0.05f, 0.14f, 0.06f, 1, 1);
        return b.build();
    }

    private static Mesh buildAWM() {
        Builder b = new Builder();
        // Длинный тяжелый ствол
        b.addBox(0, 0.02f, 0.45f, 0.05f, 0.05f, 0.65f, 1, 1);
        // Зеленое ложе AWM
        b.addBox(0, -0.01f, 0.05f, 0.08f, 0.12f, 0.58f, 1, 1);
        // Оптический прицел (трубка + линзы)
        b.addBox(0, 0.10f, 0.05f, 0.06f, 0.06f, 0.30f, 1, 1);
        b.addBox(0, 0.10f, 0.20f, 0.08f, 0.08f, 0.06f, 1, 1);
        b.addBox(0, 0.10f, -0.10f, 0.08f, 0.08f, 0.06f, 1, 1);
        // Приклад с вырезом под палец
        b.addBox(0, -0.02f, -0.36f, 0.07f, 0.16f, 0.28f, 1, 1);
        // Магазин
        b.addBox(0, -0.11f, 0.04f, 0.06f, 0.10f, 0.10f, 1, 1);
        return b.build();
    }

    private static Mesh buildDeagle() {
        Builder b = new Builder();
        // Массивный затвор пистолета
        b.addBox(0, 0.04f, 0.06f, 0.06f, 0.08f, 0.26f, 1, 1);
        // Рамка и рукоятка
        b.addBox(0, -0.06f, -0.02f, 0.05f, 0.14f, 0.08f, 1, 1);
        // Спусковая скоба
        b.addBox(0, -0.03f, 0.06f, 0.03f, 0.04f, 0.06f, 1, 1);
        return b.build();
    }

    private static Mesh buildMP5() {
        Builder b = new Builder();
        b.addBox(0, 0, 0, 0.06f, 0.09f, 0.34f, 1, 1);
        b.addBox(0, 0.01f, 0.22f, 0.03f, 0.03f, 0.16f, 1, 1);
        // Изогнутый магазин
        b.addBox(0, -0.12f, 0.05f, 0.04f, 0.16f, 0.06f, 1, 1);
        // Приклад
        b.addBox(0, 0, -0.22f, 0.05f, 0.08f, 0.16f, 1, 1);
        return b.build();
    }

    private static Mesh buildShotgun() {
        Builder b = new Builder();
        // Ствольная коробка
        b.addBox(0, 0, 0, 0.07f, 0.10f, 0.38f, 1, 1);
        // Два ствола (основной + трубчатый магазин)
        b.addBox(0, 0.03f, 0.32f, 0.04f, 0.04f, 0.36f, 1, 1);
        b.addBox(0, -0.02f, 0.28f, 0.04f, 0.04f, 0.30f, 1, 1);
        // Подвижное цевье-помпа
        b.addBox(0, -0.02f, 0.22f, 0.07f, 0.07f, 0.14f, 1, 1);
        // Приклад
        b.addBox(0, -0.03f, -0.26f, 0.06f, 0.13f, 0.20f, 1, 1);
        return b.build();
    }

    private static Mesh buildRPG() {
        Builder b = new Builder();
        // Труба гранатомета
        b.addBox(0, 0, 0, 0.09f, 0.09f, 0.85f, 1, 1);
        // Коническая ракета на конце
        b.addBox(0, 0, 0.48f, 0.18f, 0.18f, 0.20f, 1, 1);
        b.addBox(0, 0, 0.62f, 0.08f, 0.08f, 0.14f, 1, 1);
        // Оптический прицел ПГО-7
        b.addBox(0.08f, 0.08f, 0.05f, 0.05f, 0.08f, 0.14f, 1, 1);
        // Две рукоятки
        b.addBox(0, -0.12f, 0.10f, 0.05f, 0.14f, 0.05f, 1, 1);
        b.addBox(0, -0.12f, -0.15f, 0.05f, 0.14f, 0.05f, 1, 1);
        return b.build();
    }

    private static Mesh buildKnife() {
        Builder b = new Builder();
        // Рукоятка керамбита с кольцом
        b.addBox(0, 0, -0.06f, 0.03f, 0.05f, 0.14f, 1, 1);
        b.addBox(0, 0, -0.15f, 0.04f, 0.06f, 0.05f, 1, 1);
        // Изогнутое лезвие
        b.addBox(0, 0.03f, 0.05f, 0.01f, 0.06f, 0.12f, 1, 1);
        b.addBox(0, 0.07f, 0.12f, 0.01f, 0.04f, 0.08f, 1, 1);
        return b.build();
    }

    private static Mesh buildGrenade() {
        Builder b = new Builder();
        // Корпус "лимонка" Ф-1
        b.addBox(0, 0, 0, 0.10f, 0.14f, 0.10f, 1, 1);
        // Запал и чека
        b.addBox(0, 0.10f, 0, 0.04f, 0.06f, 0.04f, 1, 1);
        b.addBox(0.03f, 0.09f, 0, 0.03f, 0.05f, 0.02f, 1, 1);
        return b.build();
    }
}
