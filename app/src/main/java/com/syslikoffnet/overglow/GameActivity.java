package com.syslikoffnet.overglow;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Главная Активити: полноэкранный режим (Immersive Fullscreen),
 * интеграция 3D OpenGL ES рендерера и 2D тактического HUD оверлея.
 */
public class GameActivity extends Activity {

    private FPSGame game;
    private GLView glView;
    private HUDOverlayView hudView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        game = new FPSGame(this);
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
    }

    @Override
    protected void onPause() {
        if (glView != null) glView.onPause();
        if (game != null && game.state == FPSGame.STATE_PLAYING) {
            game.state = FPSGame.STATE_PAUSE;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
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

        if (game.state == FPSGame.STATE_PLAYING) {
            game.state = FPSGame.STATE_PAUSE;
        } else if (game.state == FPSGame.STATE_PAUSE ||
                   game.state == FPSGame.STATE_INVENTORY ||
                   game.state == FPSGame.STATE_SETTINGS ||
                   game.state == FPSGame.STATE_END) {
            game.state = FPSGame.STATE_MENU;
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
