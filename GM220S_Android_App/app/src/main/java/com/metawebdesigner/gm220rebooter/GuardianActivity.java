package com.metawebdesigner.gm220rebooter;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Small shared visual system for accessible, scrollable native screens. */
public abstract class GuardianActivity extends Activity {
    protected SecurePrefs prefs;
    protected int background, surface, ink, muted, accent, border;
    protected LinearLayout root;
    protected boolean dark;
    @Override protected void onCreate(Bundle state) {
        prefs = new SecurePrefs(this);
        String theme = prefs.raw().getString("theme", "System");
        dark = theme.equals("Dark") || (theme.equals("System") && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark ? R.style.GuardianDark : R.style.AppTheme);
        super.onCreate(state);
        background = Color.parseColor(dark ? "#101A20" : "#F1F5F7");
        surface = Color.parseColor(dark ? "#192830" : "#FFFFFF");
        ink = Color.parseColor(dark ? "#EDF5F6" : "#172F3B");
        muted = Color.parseColor(dark ? "#AFBFC5" : "#526975");
        accent = Color.parseColor(dark ? "#7BE2CA" : "#006B59");
        border = Color.parseColor(dark ? "#334852" : "#D9E4E8");
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }
    protected int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    protected void page(String title, String subtitle) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.addView(label(title, 29, ink, true));
        TextView sub = label(subtitle, 14, muted, false);
        sub.setPadding(0, dp(5), 0, dp(22));
        root.addView(sub);
        setContentView(scroll);
    }
    protected TextView label(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setLineSpacing(dp(3), 1f);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }
    protected GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), border);
        return drawable;
    }
    protected LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(surface, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(16);
        root.addView(card, lp);
        TextView heading = label(title, 18, ink, true); heading.setPadding(0, 0, 0, dp(12)); card.addView(heading);
        return card;
    }
    protected TextView detail(LinearLayout card, String value) {
        TextView text = label(value, 14, muted, false); text.setPadding(0, dp(5), 0, dp(9)); card.addView(text); return text;
    }
    protected Button button(LinearLayout parent, String title, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(title); button.setAllCaps(false); button.setTextSize(15);
        button.setTextColor(primary ? (dark ? Color.parseColor("#10382E") : Color.WHITE) : ink);
        button.setBackground(rounded(primary ? accent : background, 12));
        button.setMinHeight(dp(52)); button.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8);
        parent.addView(button, lp); button.setOnClickListener(v -> action.run()); return button;
    }
    protected void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
