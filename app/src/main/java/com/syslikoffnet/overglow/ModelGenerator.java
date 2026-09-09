package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * Высокодетализированный процедурный 3D генератор моделей для PUBG Mobile:
 * - Персонаж: Голова, Шлем 3 уровня (Алтын с забралом), Бронежилет 3 ур., Рюкзак 3 ур., Сковорода на поясе
 * - Транспорт: Багги с трубчатым каркасом и 4 отдельными колесами, УАЗ 4х4
 * - Самолет C-130: Фюзеляж, крылья, 4 двигателя, хвост
 * - Аирдроп: Деревянный ящик с синим брезентом и куполом парашюта
 * - Дома: Стены, двускатные крыши, окна, крыльцо, деревья с кроной, камни, заборы
 * - Оружие: M416, AKM, AWM, Kar98k, Сковорода, Аптечка, Энергетик
 */
public final class ModelGenerator {

    private ModelGenerator() {}

    // =========================================================================
    // Билдер 3D мешей
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

            addQuad(x0, y0, z1,  x1, y0, z1,  x1, y1, z1,  x0, y1, z1,   0, 0, 1,   uTile, vTile);
            addQuad(x1, y0, z0,  x0, y0, z0,  x0, y1, z0,  x1, y1, z0,   0, 0, -1,  uTile, vTile);
            addQuad(x0, y1, z1,  x1, y1, z1,  x1, y1, z0,  x0, y1, z0,   0, 1, 0,   uTile, vTile);
            addQuad(x0, y0, z0,  x1, y0, z0,  x1, y0, z1,  x0, y0, z1,   0, -1, 0,  uTile, vTile);
            addQuad(x1, y0, z1,  x1, y0, z0,  x1, y1, z0,  x1, y1, z1,   1, 0, 0,   uTile, vTile);
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
            addVert(x0, y0, z0, nx, ny, nz, 0, vTile);
            addVert(x1, y1, z1, nx, ny, nz, uTile, vTile);
            addVert(x2, y2, z2, nx, ny, nz, uTile, 0);
            addVert(x3, y3, z3, nx, ny, nz, 0, 0);

            iList.add(base); iList.add((short) (base + 1)); iList.add((short) (base + 2));
            iList.add(base); iList.add((short) (base + 2)); iList.add((short) (base + 3));
            vOffset += 4;
            return this;
        }

        public Builder addCylinder(float cx, float cy, float cz, float radius, float height, int seg, boolean vertical) {
            float hh = height / 2f;
            float step = (float) (Math.PI * 2 / seg);
            for (int i = 0; i < seg; i++) {
                float a0 = i * step, a1 = (i + 1) * step;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

                if (vertical) {
                    addQuad(cx + c0 * radius, cy - hh, cz + s0 * radius,
                            cx + c1 * radius, cy - hh, cz + s1 * radius,
                            cx + c1 * radius, cy + hh, cz + s1 * radius,
                            cx + c0 * radius, cy + hh, cz + s0 * radius,
                            c0, 0, s0, 1f, 1f);
                } else { // горизонтальный цилиндр (ось Z)
                    addQuad(cx + c0 * radius, cy + s0 * radius, cz - hh,
                            cx + c1 * radius, cy + s1 * radius, cz - hh,
                            cx + c1 * radius, cy + s1 * radius, cz + hh,
                            cx + c0 * radius, cy + s0 * radius, cz + hh,
                            c0, s0, 0, 1f, 1f);
                }
            }
            return this;
        }

        public Builder addHemisphere(float cx, float cy, float cz, float radius, int rings, int segs) {
            for (int r = 0; r < rings; r++) {
                float phi0 = (float) (r * Math.PI / 2 / rings);
                float phi1 = (float) ((r + 1) * Math.PI / 2 / rings);
                float y0 = cy + (float) Math.sin(phi0) * radius;
                float y1 = cy + (float) Math.sin(phi1) * radius;
                float r0 = (float) Math.cos(phi0) * radius;
                float r1 = (float) Math.cos(phi1) * radius;

                for (int s = 0; s < segs; s++) {
                    float theta0 = (float) (s * Math.PI * 2 / segs);
                    float theta1 = (float) ((s + 1) * Math.PI * 2 / segs);

                    float x00 = cx + (float) Math.cos(theta0) * r0, z00 = cz + (float) Math.sin(theta0) * r0;
                    float x10 = cx + (float) Math.cos(theta1) * r0, z10 = cz + (float) Math.sin(theta1) * r0;
                    float x11 = cx + (float) Math.cos(theta1) * r1, z11 = cz + (float) Math.sin(theta1) * r1;
                    float x01 = cx + (float) Math.cos(theta0) * r1, z01 = cz + (float) Math.sin(theta0) * r1;

                    addQuad(x00, y0, z00, x10, y0, z10, x11, y1, z11, x01, y1, z01, 0, 1, 0, 1, 1);
                }
            }
            return this;
        }

        public Builder addPrismRoof(float x, float y, float z, float w, float h, float d) {
            float hw = w / 2f, hd = d / 2f;
            // Левый скат крыши
            addQuad(x - hw, y, z - hd,  x, y + h, z - hd,  x, y + h, z + hd,  x - hw, y, z + hd,  -0.7f, 0.7f, 0, 2, 2);
            // Правый скат крыши
            addQuad(x, y + h, z - hd,  x + hw, y, z - hd,  x + hw, y, z + hd,  x, y + h, z + hd,   0.7f, 0.7f, 0, 2, 2);
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
    // Готовые 3D модели
    // =========================================================================
    public static Mesh boxMesh;
    public static Mesh groundMesh;
    public static Mesh quad2DMesh;

    // Персонаж PUBG
    public static Mesh soldierHeadMesh;
    public static Mesh helmet3Mesh;
    public static Mesh soldierTorsoMesh;
    public static Mesh vest3Mesh;
    public static Mesh backpack3Mesh;
    public static Mesh panMesh;
    public static Mesh soldierArmMesh;
    public static Mesh soldierLegMesh;

    // Транспорт
    public static Mesh buggyBodyMesh;
    public static Mesh uazBodyMesh;
    public static Mesh wheelMesh;

    // Окружение и мир
    public static Mesh housePochinkiMesh;
    public static Mesh roofMesh;
    public static Mesh treeMesh;
    public static Mesh cargoPlaneMesh;
    public static Mesh airdropBoxMesh;
    public static Mesh parachuteCanopyMesh;

    // Оружие и лут
    public static Mesh m416Mesh;
    public static Mesh akmMesh;
    public static Mesh awmMesh;
    public static Mesh firstAidMesh;
    public static Mesh energyDrinkMesh;

    public static void init() {
        boxMesh = new Builder().addBox(0, 0, 0, 1, 1, 1, 1, 1).build();
        groundMesh = new Builder().addQuad(-400, 0, 400, 400, 0, 400, 400, 0, -400, -400, 0, -400, 0, 1, 0, 80, 80).build();
        quad2DMesh = new Builder().addQuad(-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.5f, 0, 0, 0, 1, 1, 1).build();

        // 1. Детализированный Персонаж PUBG
        soldierHeadMesh = buildSoldierHead();
        helmet3Mesh = buildHelmetLevel3();
        soldierTorsoMesh = buildSoldierTorso();
        vest3Mesh = buildVestLevel3();
        backpack3Mesh = buildBackpackLevel3();
        panMesh = buildFryingPan();
        soldierArmMesh = new Builder().addBox(0, 0, 0, 0.16f, 0.60f, 0.16f, 1, 1).build();
        soldierLegMesh = buildSoldierLeg();

        // 2. Транспорт
        buggyBodyMesh = buildBuggy();
        uazBodyMesh = buildUAZ();
        wheelMesh = new Builder().addCylinder(0, 0, 0, 0.42f, 0.32f, 14, false).build();

        // 3. Мир и здания
        housePochinkiMesh = buildPochinkiHouse();
        roofMesh = new Builder().addPrismRoof(0, 0, 0, 13f, 3.5f, 13f).build();
        treeMesh = buildTree();
        cargoPlaneMesh = buildCargoPlane();
        airdropBoxMesh = buildAirdropBox();
        parachuteCanopyMesh = new Builder().addHemisphere(0, 0, 0, 4.5f, 6, 16).build();

        // 4. Оружие
        m416Mesh = buildM416();
        akmMesh = buildAKM();
        awmMesh = buildAWM();
        firstAidMesh = buildFirstAid();
        energyDrinkMesh = new Builder().addCylinder(0, 0, 0, 0.08f, 0.22f, 10, true).build();
    }

    // ------------------------------------------------------------- 3D Персонаж

    private static Mesh buildSoldierHead() {
        Builder b = new Builder();
        // Лицо / голова
        b.addBox(0, 0, 0, 0.28f, 0.32f, 0.30f, 1, 1);
        // Тактические очки / повязка
        b.addBox(0, 0.04f, 0.16f, 0.26f, 0.08f, 0.06f, 1, 1);
        return b.build();
    }

    private static Mesh buildHelmetLevel3() {
        Builder b = new Builder();
        // Купол шлема Алтын (черный металл)
        b.addBox(0, 0.04f, -0.02f, 0.34f, 0.36f, 0.36f, 1, 1);
        // Забрало со смотровой щелью
        b.addBox(0, -0.02f, 0.19f, 0.32f, 0.22f, 0.05f, 1, 1);
        b.addBox(0, 0.04f, 0.20f, 0.22f, 0.04f, 0.06f, 1, 1); // щель
        return b.build();
    }

    private static Mesh buildSoldierTorso() {
        Builder b = new Builder();
        // Торс в камуфляжной куртке
        b.addBox(0, 0, 0, 0.48f, 0.70f, 0.30f, 1, 1);
        // Воротник
        b.addBox(0, 0.36f, 0, 0.30f, 0.08f, 0.26f, 1, 1);
        // Ремень с пряжкой
        b.addBox(0, -0.32f, 0.02f, 0.50f, 0.08f, 0.32f, 1, 1);
        return b.build();
    }

    private static Mesh buildVestLevel3() {
        Builder b = new Builder();
        // Тяжелые бронепластины
        b.addBox(0, 0.04f, 0.05f, 0.52f, 0.58f, 0.28f, 1, 1);
        // Подсумки для магазинов на груди
        b.addBox(-0.14f, -0.08f, 0.20f, 0.10f, 0.16f, 0.08f, 1, 1);
        b.addBox(0, -0.08f, 0.20f, 0.10f, 0.16f, 0.08f, 1, 1);
        b.addBox(0.14f, -0.08f, 0.20f, 0.10f, 0.16f, 0.08f, 1, 1);
        // Рация на плече
        b.addBox(-0.20f, 0.24f, 0.12f, 0.07f, 0.14f, 0.06f, 1, 1);
        return b.build();
    }

    private static Mesh buildBackpackLevel3() {
        Builder b = new Builder();
        // Большой военный рюкзак 3 уровня на спине
        b.addBox(0, 0.05f, -0.25f, 0.42f, 0.58f, 0.28f, 1, 1);
        // Боковые карманы
        b.addBox(-0.22f, 0, -0.24f, 0.08f, 0.36f, 0.20f, 1, 1);
        b.addBox(0.22f, 0, -0.24f, 0.08f, 0.36f, 0.20f, 1, 1);
        // Скрученный спальный мешок сверху
        b.addBox(0, 0.36f, -0.24f, 0.40f, 0.12f, 0.16f, 1, 1);
        return b.build();
    }

    private static Mesh buildFryingPan() {
        Builder b = new Builder();
        // Круглая чаша сковороды (чугун)
        b.addCylinder(0, 0, 0, 0.22f, 0.06f, 16, false);
        // Длинная рукоятка
        b.addBox(0, 0.26f, 0, 0.05f, 0.24f, 0.04f, 1, 1);
        return b.build();
    }

    private static Mesh buildSoldierLeg() {
        Builder b = new Builder();
        // Бедро в штанах карго с карманом
        b.addBox(0, 0.12f, 0, 0.22f, 0.42f, 0.22f, 1, 1);
        b.addBox(0.11f, 0.12f, 0, 0.04f, 0.18f, 0.16f, 1, 1);
        // Наколенник
        b.addBox(0, -0.05f, 0.11f, 0.16f, 0.14f, 0.06f, 1, 1);
        // Армейский ботинок с подошвой
        b.addBox(0, -0.28f, 0.04f, 0.20f, 0.22f, 0.28f, 1, 1);
        b.addBox(0, -0.38f, 0.06f, 0.22f, 0.04f, 0.32f, 1, 1);
        return b.build();
    }

    // ------------------------------------------------------------- 3D Транспорт

    private static Mesh buildBuggy() {
        Builder b = new Builder();
        // Каркас и шасси
        b.addBox(0, 0.3f, 0, 1.4f, 0.35f, 3.4f, 1, 1);
        // Капот с фарами
        b.addBox(0, 0.55f, 1.1f, 1.1f, 0.35f, 1.2f, 1, 1);
        b.addBox(-0.45f, 0.65f, 1.7f, 0.18f, 0.18f, 0.10f, 1, 1); // фара левая
        b.addBox(0.45f, 0.65f, 1.7f, 0.18f, 0.18f, 0.10f, 1, 1);  // фара правая
        // Трубчатый каркас безопасности (Roll Cage)
        b.addBox(-0.55f, 1.1f, -0.1f, 0.08f, 1.0f, 0.08f, 1, 1);
        b.addBox(0.55f, 1.1f, -0.1f, 0.08f, 1.0f, 0.08f, 1, 1);
        b.addBox(0, 1.6f, -0.1f, 1.2f, 0.08f, 0.08f, 1, 1);
        // Задний двигатель
        b.addBox(0, 0.7f, -1.2f, 0.8f, 0.6f, 0.9f, 1, 1);
        return b.build();
    }

    private static Mesh buildUAZ() {
        Builder b = new Builder();
        // Основной кузов джипа
        b.addBox(0, 0.6f, 0, 1.8f, 0.8f, 3.8f, 1, 1);
        // Кабина с крышей и окнами
        b.addBox(0, 1.35f, -0.3f, 1.7f, 0.75f, 2.4f, 1, 1);
        // Капот
        b.addBox(0, 0.9f, 1.3f, 1.6f, 0.45f, 1.2f, 1, 1);
        // Решетка радиатора и бампер
        b.addBox(0, 0.85f, 1.95f, 1.5f, 0.4f, 0.1f, 1, 1);
        b.addBox(0, 0.4f, 2.05f, 1.9f, 0.18f, 0.2f, 1, 1);
        // Запасное колесо на задней двери
        b.addCylinder(0, 1.0f, -1.95f, 0.38f, 0.24f, 12, false);
        return b.build();
    }

    // ------------------------------------------------------------- 3D Окружение

    private static Mesh buildPochinkiHouse() {
        Builder b = new Builder();
        // 2-этажный дом в Починках 12х7х12 с дверным и оконными проемами
        b.addBox(0, 3.5f, 5.8f, 12f, 7f, 0.8f, 4, 3); // задняя стена
        b.addBox(-5.8f, 3.5f, 0, 0.8f, 7f, 12f, 4, 3); // левая стена
        b.addBox(5.8f, 3.5f, 0, 0.8f, 7f, 12f, 4, 3);  // правая стена
        // Фасадная стена
        b.addBox(-3.6f, 3.5f, -5.8f, 5.2f, 7f, 0.8f, 2, 3);
        b.addBox(3.6f, 3.5f, -5.8f, 5.2f, 7f, 0.8f, 2, 3);
        b.addBox(0, 5.5f, -5.8f, 2.4f, 3f, 0.8f, 1, 1); // над дверью
        // Межэтажное перекрытие и пол
        b.addBox(0, 3.5f, 0, 11.5f, 0.3f, 11.5f, 3, 3);
        return b.build();
    }

    private static Mesh buildTree() {
        Builder b = new Builder();
        // Ствол сосны
        b.addCylinder(0, 3.5f, 0, 0.45f, 7.0f, 8, true);
        // 3 яруса зеленой кроны
        b.addBox(0, 5.5f, 0, 3.6f, 2.4f, 3.6f, 1, 1);
        b.addBox(0, 7.5f, 0, 2.8f, 2.2f, 2.8f, 1, 1);
        b.addBox(0, 9.2f, 0, 1.8f, 1.8f, 1.8f, 1, 1);
        return b.build();
    }

    private static Mesh buildCargoPlane() {
        Builder b = new Builder();
        // Фюзеляж C-130
        b.addBox(0, 0, 0, 3.8f, 4.2f, 26.0f, 2, 6);
        // Нос кабины
        b.addBox(0, -0.2f, 14.0f, 3.2f, 3.2f, 3.5f, 1, 1);
        // Огромные крылья (размах 28м)
        b.addBox(0, 1.6f, 2.0f, 28.0f, 0.4f, 4.2f, 8, 1);
        // 4 турбовинтовых двигателя
        b.addBox(-8.0f, 0.8f, 2.0f, 1.4f, 1.4f, 4.0f, 1, 1);
        b.addBox(-4.0f, 0.8f, 2.0f, 1.4f, 1.4f, 4.0f, 1, 1);
        b.addBox(4.0f, 0.8f, 2.0f, 1.4f, 1.4f, 4.0f, 1, 1);
        b.addBox(8.0f, 0.8f, 2.0f, 1.4f, 1.4f, 4.0f, 1, 1);
        // Хвостовой киль
        b.addBox(0, 3.8f, -11.5f, 0.4f, 4.8f, 4.5f, 1, 1);
        b.addBox(0, 2.0f, -11.5f, 9.0f, 0.3f, 2.8f, 2, 1);
        return b.build();
    }

    private static Mesh buildAirdropBox() {
        Builder b = new Builder();
        // Красный деревянный ящик
        b.addBox(0, 0, 0, 1.8f, 1.8f, 1.8f, 1, 1);
        // Синий брезент сверху со складками
        b.addBox(0, 0.95f, 0, 1.9f, 0.22f, 1.9f, 1, 1);
        return b.build();
    }

    // ------------------------------------------------------------- 3D Оружие

    private static Mesh buildM416() {
        Builder b = new Builder();
        b.addBox(0, 0, 0, 0.07f, 0.12f, 0.48f, 1, 1);
        b.addBox(0, 0.02f, 0.38f, 0.04f, 0.04f, 0.36f, 1, 1); // ствол
        b.addBox(0, 0.01f, 0.22f, 0.08f, 0.09f, 0.22f, 1, 1); // RIS цевье
        b.addBox(0, 0.10f, 0.08f, 0.05f, 0.07f, 0.14f, 1, 1); // коллиматорный прицел
        b.addBox(0, -0.14f, 0.08f, 0.05f, 0.20f, 0.09f, 1, 1); // магазин
        b.addBox(0, -0.01f, -0.32f, 0.06f, 0.13f, 0.22f, 1, 1); // приклад
        b.addBox(0, -0.13f, -0.08f, 0.05f, 0.14f, 0.06f, 1, 1); // рукоять
        return b.build();
    }

    private static Mesh buildAKM() {
        Builder b = new Builder();
        b.addBox(0, 0, 0, 0.08f, 0.13f, 0.50f, 1, 1);
        b.addBox(0, 0.02f, 0.40f, 0.04f, 0.04f, 0.38f, 1, 1);
        b.addBox(0, 0, 0.24f, 0.07f, 0.10f, 0.22f, 1, 1); // деревянное цевье
        b.addBox(0, -0.16f, 0.08f, 0.06f, 0.22f, 0.11f, 1, 1); // изогнутый рожок
        b.addBox(0, -0.02f, -0.34f, 0.07f, 0.14f, 0.26f, 1, 1); // деревянный приклад
        return b.build();
    }

    private static Mesh buildAWM() {
        Builder b = new Builder();
        b.addBox(0, 0.02f, 0.50f, 0.05f, 0.05f, 0.75f, 1, 1); // длинный ствол
        b.addBox(0, -0.01f, 0.06f, 0.09f, 0.13f, 0.62f, 1, 1); // зеленое ложе
        b.addBox(0, 0.11f, 0.06f, 0.07f, 0.07f, 0.32f, 1, 1); // 8x оптический прицел
        b.addBox(0, -0.03f, -0.38f, 0.08f, 0.16f, 0.30f, 1, 1); // приклад
        return b.build();
    }

    private static Mesh buildFirstAid() {
        Builder b = new Builder();
        // Белый чемоданчик с красным крестом
        b.addBox(0, 0, 0, 0.32f, 0.24f, 0.14f, 1, 1);
        b.addBox(0, 0.15f, 0, 0.14f, 0.06f, 0.04f, 1, 1); // ручка
        return b.build();
    }
}
