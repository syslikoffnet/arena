package com.syslikoffnet.overglow;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

/**
 * Аппаратно-ускоренный 2D оверлей интерфейса PUBG Mobile (HUD, Лобби, Кнопки).
 */
public final class HUDOverlayView extends View {

    public final PUBGGame game;

    public HUDOverlayView(Context context, PUBGGame game) {
        super(context);
        this.game = game;
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
            game.render2D(canvas, w, h);
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return game.onTouchEvent(event);
    }
}
