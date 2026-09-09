package com.syslikoffnet.overglow;

import android.view.MotionEvent;

/**
 * Ввод: виртуальный стик — палец в любом месте экрана задаёт направление.
 * Второй палец не мешает. Тапы (короткие касания без движения) для UI.
 */
public final class Input {

    private static final float DEAD = 0.14f;

    public float stickX, stickY;      // нормализованное направление
    public float stickStartX, stickStartY; // центр стика (экран)
    public float stickCurX, stickCurY;     // текущий палец
    public boolean stickActive;
    public int stickPointer = -1;

    /** Пришёл тап (для кнопок). Заполняется в consumeTap(). */
    private float tapX, tapY;
    private boolean hasTap;

    /** Обработать событие. Возвращает true, если событие съедено стиком. */
    public boolean onTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (stickPointer == -1) {
                    int i = e.getActionIndex();
                    stickPointer = e.getPointerId(i);
                    stickStartX = stickCurX = e.getX(i);
                    stickStartY = stickCurY = e.getY(i);
                    stickActive = true;
                    stickX = 0;
                    stickY = 0;
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                if (stickPointer != -1) {
                    int i = e.findPointerIndex(stickPointer);
                    if (i >= 0) {
                        stickCurX = e.getX(i);
                        stickCurY = e.getY(i);
                        float dx = stickCurX - stickStartX;
                        float dy = stickCurY - stickStartY;
                        float len = (float) Math.sqrt(dx * dx + dy * dy);
                        float maxR = 220f;
                        if (len > maxR) {
                            // Тянем базу за пальцем — «плавающий» стик
                            stickStartX = stickCurX - dx / len * maxR;
                            stickStartY = stickCurY - dy / len * maxR;
                        }
                        if (len > 12f) {
                            stickX = dx / (len == 0 ? 1 : len);
                            stickY = dy / (len == 0 ? 1 : len);
                        } else {
                            stickX = 0;
                            stickY = 0;
                        }
                        return true;
                    }
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                if (e.getPointerId(i) == stickPointer) {
                    // Если короткое касание почти без движения — это тап
                    float dx = Math.abs(stickCurX - stickStartX);
                    float dy = Math.abs(stickCurY - stickStartY);
                    long dt = e.getEventTime() - e.getDownTime();
                    if (dx < 18 && dy < 18 && dt < 260) {
                        tapX = stickCurX;
                        tapY = stickCurY;
                        hasTap = true;
                    }
                    stickPointer = -1;
                    stickActive = false;
                    stickX = 0;
                    stickY = 0;
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_CANCEL: {
                stickPointer = -1;
                stickActive = false;
                stickX = 0;
                stickY = 0;
                return false;
            }
            default:
                return false;
        }
    }

    /** Плавный вектор движения с мёртвой зоной. */
    public float moveX() {
        float l = (float) Math.sqrt(stickX * stickX + stickY * stickY);
        return l < DEAD ? 0 : stickX;
    }

    public float moveY() {
        float l = (float) Math.sqrt(stickX * stickX + stickY * stickY);
        return l < DEAD ? 0 : stickY;
    }

    /** Забрать тап (одноразово). */
    public boolean consumeTap(float[] out) {
        if (!hasTap) return false;
        hasTap = false;
        out[0] = tapX;
        out[1] = tapY;
        return true;
    }

    public void reset() {
        stickPointer = -1;
        stickActive = false;
        stickX = 0;
        stickY = 0;
        hasTap = false;
    }
}
