package com.syslikoffnet.overglow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * SurfaceView + собственный поток рендера. Fixed timestep 60 Гц,
 * рендер по готовности поверхности. Поддержка «паузы» при потере фокуса.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private Thread thread;
    private volatile boolean running;
    private volatile boolean surfaceReady;

    public final Game game;

    private static final float STEP = 1f / 60f;
    private long lastNanos;
    private float acc;

    private int fpsFrames;
    private long fpsTimer;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        game = new Game(context);
        Art.init();
    }

    // ------------------------------------------------------------- цикл

    @Override
    public void run() {
        lastNanos = System.nanoTime();
        acc = 0;
        while (running) {
            if (!surfaceReady) {
                sleepQuiet(16);
                continue;
            }
            long now = System.nanoTime();
            float frame = (now - lastNanos) / 1_000_000_000f;
            lastNanos = now;
            if (frame > 0.25f) frame = 0.25f;
            acc += frame;

            boolean updated = false;
            while (acc >= STEP) {
                game.update(STEP);
                acc -= STEP;
                updated = true;
            }

            if (updated || true) {
                SurfaceHolder holder = getHolder();
                Canvas canvas = null;
                try {
                    canvas = holder.lockHardwareCanvas();
                    if (canvas == null) canvas = holder.lockCanvas();
                    if (canvas != null) {
                        int w = canvas.getWidth();
                        int h = canvas.getHeight();
                        // Чёрный фон под всё
                        canvas.drawColor(Color.BLACK);
                        game.render(canvas, w, h);
                    }
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            // FPS-статистика (для отладки производительности)
            fpsFrames++;
            if (now - fpsTimer > 1_000_000_000L) {
                fpsTimer = now;
                fpsFrames = 0;
            }

            // Плавный сон до следующего кадра
            long spent = System.nanoTime() - now;
            long sleepNs = 16_000_000 - spent;
            if (sleepNs > 1_000_000) sleepQuiet(sleepNs / 1_000_000);
        }
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    // ------------------------------------------------------------- цикл жизни

    public void startLoop() {
        if (thread != null) return;
        running = true;
        thread = new Thread(this, "overglow-render");
        thread.start();
    }

    public void stopLoop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(1000);
            } catch (InterruptedException ignored) {
            }
            thread = null;
        }
    }

    public void onPause() {
        stopLoop();
        Sound.pause();
        if (game.state == Game.ST_PLAY) {
            game.state = Game.ST_PAUSE;
            game.input.reset();
        }
    }

    public void onResume() {
        Sound.resume();
        startLoop();
    }

    // ------------------------------------------------------------- поверхность

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        surfaceReady = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
    }

    // ------------------------------------------------------------- ввод

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        game.onTouch(event);
        return true;
    }
}
