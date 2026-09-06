package com.casezero.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public final class LayoutActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Ui.INK);
        TouchControlsView controls = new TouchControlsView(this, true, null); root.addView(controls);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER);
        Button reset = new Button(this); reset.setText(R.string.reset); reset.setOnClickListener(v -> controls.resetLayout()); top.addView(reset);
        Button save = new Button(this); save.setText(R.string.save_layout); save.setOnClickListener(v -> { controls.saveLayout(); finish(); }); top.addView(save);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.addView(top, lp);
        setContentView(root);
        Toast.makeText(this, R.string.layout_hint, Toast.LENGTH_LONG).show();
    }
}
