package com.syslikoffnet.overglow;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

/**
 * Аппаратно-ускоренный оверлей для 2D тактического интерфейса (HUD),
 * миникарты, прицелов и обработки мультикасаний.
 */
public final class HUDOverlayView extends View {

    public final FPSGame game;

    public HUDOverlayView(Context context, FPSGame game) {
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
        invalidate(); // плавные 60/120 кадров оверлея
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return game.onTouchEvent(event);
    }
}
