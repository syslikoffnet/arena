package com.syslikoffnet.overglow;

import android.view.MotionEvent;

/**
 * Мобильный ввод в духе классических BR-шутеров (собственная реализация):
 * - ЛЕВАЯ зона: плавающий стик движения (появляется под пальцем). Полный вперёд = авто-спринт,
 *   отпускание в верхнем секторе = блокировка спринта (sprint lock).
 * - ПРАВАЯ зона: pad обзора камеры (drag). Чувствительность гасится при ADS/оптике.
 * - Кластеры кнопок по фазе матча: пеший бой (очного/факел, ADS, прыжок/присед/лёжа, пик L/R,
 *   перезарядка, слоты, аптечка/буст, граната, контекстное взаимодействие),
 *   полёт (прыжок из самолёта, раскрытие купола),驾驶 (газ/тормоз/ручник/клаксон/выход/камера).
 * - Каждый палец владеет ролью (pointer id → role), поэтому жест обзора НЕ отменяет удержание огня.
 * - Тап vs удержание: пик L/R — удерживать = наклон, тап = фиксация наклона.
 */
public final class MobileInputController {

    // =========================================================================
    // Режимы и роли
    // =========================================================================
    public static final int MODE_ONFOOT = 0;
    public static final int MODE_DRIVING = 1;
    public static final int MODE_SKYDIVING = 2;
    public static final int MODE_PLANE = 3;
    public int mode = MODE_ONFOOT;

    public static final int R_FIRE = 0;
    public static final int R_FIRE_L = 1;
    public static final int R_ADS = 2;
    public static final int R_JUMP = 3;
    public static final int R_CROUCH = 4;
    public static final int R_PRONE = 5;
    public static final int R_PEEK_L = 6;
    public static final int R_PEEK_R = 7;
    public static final int R_RELOAD = 8;
    public static final int R_SLOT1 = 9;
    public static final int R_SLOT2 = 10;
    public static final int R_SLOT3 = 11;
    public static final int R_HEAL = 12;
    public static final int R_BOOST = 13;
    public static final int R_THROW = 14;
    public static final int R_INTERACT = 15;
    public static final int R_CHUTE = 16;
    public static final int R_SHOULDER = 17;
    public static final int R_FPP = 18;
    public static final int R_ZERO = 19;
    public static final int R_GAS = 20;
    public static final int R_BRAKE = 21;
    public static final int R_HB = 22;
    public static final int R_HORN = 23;
    public static final int R_EXIT = 24;
    public static final int R_CAMSW = 25;
    public static final int NUM_ROLES = 26;

    // Геометрия кнопок (читается HUD для отрисовки)
    public final float[] bx = new float[NUM_ROLES];
    public final float[] by = new float[NUM_ROLES];
    public final float[] br = new float[NUM_ROLES];
    public final boolean[] visible = new boolean[NUM_ROLES];
    public final boolean[] pressed = new boolean[NUM_ROLES];
    private final boolean[] tapped = new boolean[NUM_ROLES];

    // Контекстная видимость
    public boolean showInteract = false;
    public boolean showChute = false;
    public boolean inAds = false;

    // Настройки чувствительности (лобби → сюда)
    public float camSens = 0.30f;        // градусов на пиксель
    public float adsMul = 1f;            // множитель от FOV (игра считает)
    public boolean invertY = false;
    public float buttonScale = 1f;

    // Выход движения
    public float moveX = 0f, moveZ = 0f;
    public boolean sprintLocked = false;
    public boolean stickActive = false;

    public int screenWidth = 1280, screenHeight = 720;

    // Привязка указателей
    private static final int MAX_POINTERS = 10;
    private static final int ROLE_NONE = -1, ROLE_STICK = -2, ROLE_LOOK = -3;
    private final int[] pointerRole = new int[MAX_POINTERS];
    private final float[] pLastX = new float[MAX_POINTERS];
    private final float[] pLastY = new float[MAX_POINTERS];
    private final long[] pDownTime = new long[MAX_POINTERS];
    private final float[] pDownX = new float[MAX_POINTERS];
    private final float[] pDownY = new float[MAX_POINTERS];

    // Плавающий стик
    private int stickPointer = -1;
    private float stickOX, stickOY;
    public float stickRadius = 120f;
    public float knobX, knobY;

    public MobileInputController() {
        for (int i = 0; i < MAX_POINTERS; i++) pointerRole[i] = ROLE_NONE;
    }

    public boolean hasActiveStick() { return stickPointer >= 0; }
    public float getStickStartX() { return stickOX; }
    public float getStickStartY() { return stickOY; }
    public float getStickCurX() { return knobX; }
    public float getStickCurY() { return knobY; }

    public boolean isFiring() { return pressed[R_FIRE] || pressed[R_FIRE_L]; }
    public boolean isDown(int role) { return pressed[role]; }

    public boolean consumeTap(int role) {
        boolean v = tapped[role];
        tapped[role] = false;
        return v;
    }

    /** Газ -1..1 с учётом педалей (для vehicle.update) */
    public float vehicleThrottle() {
        if (pressed[R_GAS]) return 1f;
        if (pressed[R_BRAKE]) return -0.7f;
        return 0f;
    }

    public float vehicleSteer() { return moveX; }

    public boolean vehicleHandbrake() { return pressed[R_HB]; }

    // =========================================================================
    // Раскладка под текущий режим
    // =========================================================================
    public void setScreenSize(int w, int h) {
        screenWidth = w;
        screenHeight = h;
        stickRadius = h * 0.145f * buttonScale;
        applyLayout();
    }

    private void circle(int role, float fx, float fy, float rr) {
        bx[role] = fx * screenWidth;
        by[role] = fy * screenHeight;
        br[role] = rr * screenHeight * buttonScale;
    }

    void applyLayout() {
        for (int i = 0; i < NUM_ROLES; i++) visible[i] = false;

        if (mode == MODE_ONFOOT) {
            circle(R_FIRE, 0.885f, 0.60f, 0.115f);
            circle(R_FIRE_L, 0.085f, 0.42f, 0.052f);
            circle(R_ADS, 0.740f, 0.62f, 0.068f);
            circle(R_RELOAD, 0.740f, 0.44f, 0.055f);
            circle(R_JUMP, 0.950f, 0.415f, 0.055f);
            circle(R_CROUCH, 0.828f, 0.85f, 0.050f);
            circle(R_PRONE, 0.925f, 0.855f, 0.050f);
            circle(R_PEEK_L, 0.790f, 0.235f, 0.048f);
            circle(R_PEEK_R, 0.872f, 0.235f, 0.048f);
            circle(R_HEAL, 0.650f, 0.235f, 0.048f);
            circle(R_BOOST, 0.650f, 0.345f, 0.048f);
            circle(R_THROW, 0.732f, 0.30f, 0.050f);
            circle(R_SLOT1, 0.105f, 0.865f, 0.050f);
            circle(R_SLOT2, 0.190f, 0.865f, 0.050f);
            circle(R_SLOT3, 0.275f, 0.865f, 0.050f);
            circle(R_FPP, 0.955f, 0.135f, 0.042f);
            circle(R_SHOULDER, 0.700f, 0.135f, 0.042f);
            circle(R_ZERO, 0.740f, 0.765f, 0.048f);
            circle(R_INTERACT, 0.615f, 0.52f, 0.062f);
            visible[R_FIRE] = visible[R_FIRE_L] = visible[R_ADS] = visible[R_RELOAD] = true;
            visible[R_JUMP] = visible[R_CROUCH] = visible[R_PRONE] = true;
            visible[R_PEEK_L] = visible[R_PEEK_R] = true;
            visible[R_HEAL] = visible[R_BOOST] = visible[R_THROW] = true;
            visible[R_SLOT1] = visible[R_SLOT2] = visible[R_SLOT3] = true;
            visible[R_FPP] = visible[R_SHOULDER] = true;
            visible[R_INTERACT] = showInteract;
            visible[R_ZERO] = inAds;
        } else if (mode == MODE_DRIVING) {
            circle(R_GAS, 0.900f, 0.615f, 0.095f);
            circle(R_BRAKE, 0.900f, 0.355f, 0.066f);
            circle(R_HB, 0.775f, 0.845f, 0.052f);
            circle(R_HORN, 0.955f, 0.145f, 0.044f);
            circle(R_EXIT, 0.620f, 0.30f, 0.058f);
            circle(R_CAMSW, 0.700f, 0.135f, 0.044f);
            circle(R_FPP, 0.955f, 0.855f, 0.044f);
            visible[R_GAS] = visible[R_BRAKE] = visible[R_HB] = visible[R_HORN] = true;
            visible[R_EXIT] = visible[R_CAMSW] = visible[R_FPP] = true;
        } else if (mode == MODE_SKYDIVING) {
            circle(R_CHUTE, 0.500f, 0.815f, 0.078f);
            circle(R_FPP, 0.955f, 0.135f, 0.042f);
            visible[R_CHUTE] = showChute;
            visible[R_FPP] = true;
        } else { // MODE_PLANE — тап в нижней части = прыжок
            circle(R_CHUTE, 0.500f, 0.80f, 0.10f);
            visible[R_CHUTE] = true;
        }
    }

    public void setMode(int m) {
        if (mode == m) {
            // контекстные видимость может меняться — пересобрать раскладку дёшево
            applyLayout();
            return;
        }
        mode = m;
        releaseAll();
        applyLayout();
    }

    private void releaseAll() {
        for (int i = 0; i < NUM_ROLES; i++) {
            pressed[i] = false;
            tapped[i] = false;
        }
        for (int i = 0; i < MAX_POINTERS; i++) pointerRole[i] = ROLE_NONE;
        stickPointer = -1;
        moveX = 0f; moveZ = 0f;
        sprintLocked = false;
    }

    /** Сброс входного состояния при старте нового матча */
    public void releaseAllForNewMatch() {
        releaseAll();
        mode = MODE_ONFOOT;
        applyLayout();
    }

    // =========================================================================
    // Обработка касаний
    // =========================================================================
    public void onTouchEvent(MotionEvent ev, PUBGPlayer player, PUBGMap map, SpringArmCamera camera) {
        int action = ev.getActionMasked();
        int idx = ev.getActionIndex();
        int pid = ev.getPointerId(idx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleDown(pid, ev.getX(idx), ev.getY(idx), player, camera);
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    handleMove(ev.getPointerId(i), ev.getX(i), ev.getY(i), camera);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                handleUp(pid, player);
                break;
        }
    }

    private int roleAt(float x, float y) {
        for (int r = 0; r < NUM_ROLES; r++) {
            if (!visible[r]) continue;
            float dx = x - bx[r], dy = y - by[r];
            float rr = br[r] * 1.18f;
            if (dx * dx + dy * dy <= rr * rr) return r;
        }
        return ROLE_NONE;
    }

    private void handleDown(int pid, float x, float y, PUBGPlayer player, SpringArmCamera camera) {
        if (pid >= MAX_POINTERS) return;
        pDownTime[pid] = System.currentTimeMillis();
        pDownX[pid] = x; pDownY[pid] = y;
        pLastX[pid] = x; pLastY[pid] = y;

        // Самолёт: тап по широкой зоне JUMP (press/release → tap-событие в handleUp)
        if (mode == MODE_PLANE) {
            if (x > screenWidth * 0.25f && x < screenWidth * 0.75f && y > screenHeight * 0.55f) {
                pointerRole[pid] = R_CHUTE;
                pressed[R_CHUTE] = true;
                return;
            }
            pointerRole[pid] = ROLE_LOOK; // всё остальное — свободный обзор
            return;
        }

        int r = roleAt(x, y);
        if (r != ROLE_NONE) {
            pointerRole[pid] = r;
            pressed[r] = true;
            return;
        }

        boolean leftSide = x < screenWidth * 0.45f && y > screenHeight * 0.24f;
        if (leftSide && stickPointer < 0) {
            stickPointer = pid;
            pointerRole[pid] = ROLE_STICK;
            stickOX = x; stickOY = y;
            knobX = x; knobY = y;
            stickActive = true;
            sprintLocked = false; // новое касание стика снимает авто-спринт
        } else {
            boolean lookBusy = false;
            for (int i = 0; i < MAX_POINTERS; i++) if (pointerRole[i] == ROLE_LOOK) lookBusy = true;
            pointerRole[pid] = lookBusy ? ROLE_NONE : ROLE_LOOK;
        }
    }

    private void handleMove(int pid, float x, float y, SpringArmCamera camera) {
        if (pid >= MAX_POINTERS) return;
        float dx = x - pLastX[pid];
        float dy = y - pLastY[pid];
        pLastX[pid] = x; pLastY[pid] = y;

        int role = pointerRole[pid];
        if (role == ROLE_STICK) {
            float ox = x - stickOX, oy = y - stickOY;
            float len = (float) Math.sqrt(ox * ox + oy * oy);
            float r = stickRadius;
            if (len > r) { ox = ox / len * r; oy = oy / len * r; }
            knobX = stickOX + ox;
            knobY = stickOY + oy;
            float nx = ox / r, ny = -oy / r;
            // мёртвая зона
            float mag = (float) Math.sqrt(nx * nx + ny * ny);
            if (mag < 0.14f) { nx = 0f; ny = 0f; }
            else if (mag > 1f) { nx /= mag; ny /= mag; }
            moveX = nx;
            moveZ = ny;
        } else if (role == ROLE_LOOK && camera != null) {
            float sens = camSens * adsMul;
            // Двиг вперёд = наклон вперёд: yaw+ по конвенции движка — это поворот ВЛЕВО,
            // поэтому свайп вправо (dx>0) уменьшает yaw.
            camera.yaw -= dx * sens;
            camera.pitch += (invertY ? dy : -dy) * sens * 0.9f;
            if (camera.pitch > 85f) camera.pitch = 85f;
            if (camera.pitch < -85f) camera.pitch = -85f;
        }
    }

    private void handleUp(int pid, PUBGPlayer player) {
        if (pid >= MAX_POINTERS) return;
        int role = pointerRole[pid];
        pointerRole[pid] = ROLE_NONE;

        if (role == ROLE_STICK) {
            // отпускание в верхнем секторе = фиксация спринта (sprint lock)
            if (moveZ > 0.82f && Math.abs(moveX) < 0.35f) {
                sprintLocked = true;
            }
            stickPointer = -1;
            stickActive = false;
            moveX = 0f; moveZ = 0f;
        } else if (role >= 0) {
            pressed[role] = false;
            long dur = System.currentTimeMillis() - pDownTime[pid];
            float mx = pLastX[pid] - pDownX[pid], my = pLastY[pid] - pDownY[pid];
            boolean isTap = dur < 260 && mx * mx + my * my < 36f * 36f;
            if (isTap) tapped[role] = true;
        }
    }
}
