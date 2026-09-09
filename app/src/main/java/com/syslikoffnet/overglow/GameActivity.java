package com.syslikoffnet.overglow;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/**
 * Единственная активити: фуллскрин, без гашения экрана, immersive.
 */
public class GameActivity extends Activity {

    private GameView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        S.applyLang(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new GameView(this);
        setContentView(view);
        hideBars();
        Sound.init(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        view.onResume();
    }

    @Override
    protected void onPause() {
        view.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Sound.destroy();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    public void onBackPressed() {
        if (view == null || view.game == null) {
            super.onBackPressed();
            return;
        }
        int st = view.game.state;
        if (st == Game.ST_PLAY) {
            view.game.state = Game.ST_PAUSE;
            view.game.input.reset();
        } else if (st == Game.ST_LEVELUP) {
            // выбор усиления обязателен — не выходим
        } else if (st != Game.ST_TITLE) {
            view.game.onButton(Game.B_BACK);
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
