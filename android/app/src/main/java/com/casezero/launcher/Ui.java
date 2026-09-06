package com.casezero.launcher;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int INK = 0xff101316, CARD = 0xff1b2026, WHITE = 0xfff3f0e9,
        MUTED = 0xffacb3bc, ACCENT = 0xffeeb34c;
    static int dp(Context c, float n) { return (int)(n * c.getResources().getDisplayMetrics().density + .5f); }
    static TextView text(Context c, String text, float size, int color) {
        TextView v = new TextView(c); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setPadding(0, dp(c, 4), 0, dp(c, 8)); return v;
    }
    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    static LinearLayout card(Context c, LinearLayout parent, int title) {
        LinearLayout box = column(c); int p = dp(c, 18); box.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(CARD); bg.setCornerRadius(dp(c, 16)); box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(c, 8), 0, dp(c, 8));
        parent.addView(box, lp);
        TextView heading = text(c, c.getString(title), 12, ACCENT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD); heading.setLetterSpacing(.12f); box.addView(heading);
        return box;
    }
    static Button button(Context c, LinearLayout box, int label, Runnable click) {
        Button b = new Button(c); b.setText(label); b.setAllCaps(false); b.setMinHeight(dp(c, 48));
        b.setTextColor(WHITE); b.setBackgroundTintList(ColorStateList.valueOf(0xff303842));
        b.setOnClickListener(v -> click.run()); box.addView(b, new LinearLayout.LayoutParams(-1, -2)); return b;
    }
    static Switch toggle(Context c, LinearLayout box, int label, boolean checked, java.util.function.Consumer<Boolean> action) {
        Switch s = new Switch(c); s.setText(label); s.setTextColor(WHITE); s.setMinHeight(dp(c, 52));
        s.setPadding(0, dp(c, 6), 0, dp(c, 6)); s.setChecked(checked);
        s.setOnCheckedChangeListener((view, on) -> action.accept(on)); box.addView(s); return s;
    }
    static void choose(Activity c, LinearLayout box, int label, String[] values, int selected, java.util.function.IntConsumer action) {
        box.addView(text(c, c.getString(label), 14, MUTED));
        Spinner s = new Spinner(c); ArrayAdapter<String> adapter = new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, values);
        s.setAdapter(adapter); s.setSelection(Math.max(0, selected), false); s.setMinimumHeight(dp(c, 48)); box.addView(s);
        s.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            int previous = Math.max(0, selected);
            public void onItemSelected(AdapterView<?> p, View v, int index, long id) {
                if (index != previous) { previous = index; action.accept(index); }
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
    }
    static void error(Activity activity, Throwable error) {
        if (!activity.isFinishing()) new AlertDialog.Builder(activity).setTitle(R.string.error)
            .setMessage(error.getMessage()).setPositiveButton(R.string.close, null).show();
    }
    static void confirm(Activity a, int message, Runnable yes) {
        new AlertDialog.Builder(a).setMessage(message).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.continue_action, (d, w) -> yes.run()).show();
    }
    private Ui() {}
}
