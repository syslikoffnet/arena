package com.syslikoffnet.overglow;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Главная Активити PUBG Mobile: полноэкранный ландшафтный режим,
 * 3D рендерер OpenGL ES, 2D оверлей интерфейса и аппаратный гироскоп.
 */
public class GameActivity extends Activity {

    private PUBGGame game;
    private GLView glView;
    private HUDOverlayView hudView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        game = new PUBGGame(this);
        glView = new GLView(this, game);
        hudView = new HUDOverlayView(this, game);

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(glView);
        root.addView(hudView);

        setContentView(root);
        hideBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        if (glView != null) glView.onResume();
        if (game != null && game.gyroscope != null) {
            game.gyroscope.start();
        }
    }

    @Override
    protected void onPause() {
        if (game != null && game.gyroscope != null) {
            game.gyroscope.stop();
        }
        if (glView != null) glView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (game != null && game.gyroscope != null) {
            game.gyroscope.stop();
        }
        SoundSynth3D.destroy();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    public void onBackPressed() {
        if (game == null) {
            super.onBackPressed();
            return;
        }

        if (game.state == PUBGGame.STATE_PLAYING) {
            game.state = PUBGGame.STATE_LOBBY;
        } else {
            super.onBackPressed();
        }
    }

    private void hideBars() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
}
