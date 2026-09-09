package com.syslikoffnet.overglow;

import android.content.Context;
import android.opengl.GLSurfaceView;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * GLSurfaceView для 3D графики PUBG Mobile (OpenGL ES 2.0).
 */
public final class GLView extends GLSurfaceView implements GLSurfaceView.Renderer {

    public final PUBGGame game;
    public final PUBGRenderer renderer3D;

    private long lastTimeNanos;

    public GLView(Context context, PUBGGame game) {
        super(context);
        this.game = game;
        this.renderer3D = new PUBGRenderer();

        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        renderer3D.init();
        lastTimeNanos = System.nanoTime();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        renderer3D.resize(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = (now - lastTimeNanos) / 1_000_000_000f;
        lastTimeNanos = now;
        if (dt > 0.1f) dt = 0.1f;

        game.update(dt);
        renderer3D.render(game.player, game.map, game.bots, game.particles);
    }
}
