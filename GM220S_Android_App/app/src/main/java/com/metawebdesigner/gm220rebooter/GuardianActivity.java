package com.metawebdesigner.gm220rebooter;

import android.app.Activity;
import android.content.Intent;
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
    private LinearLayout shell;
    protected boolean dark;
    protected static final int TAB_DASHBOARD = 0, TAB_ROUTER = 1, TAB_HISTORY = 2, TAB_SETTINGS = 3;
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
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(background);
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
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(shell);
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
    protected LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
    protected Button actionButton(LinearLayout parent, int icon, String title, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(title); button.setAllCaps(false); button.setTextSize(14); button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0); button.setCompoundDrawablePadding(dp(6));
        button.setTextColor(primary ? (dark ? Color.parseColor("#10382E") : Color.WHITE) : ink);
        button.setBackground(rounded(primary ? accent : background, 14)); button.setMinHeight(dp(76));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        parent.addView(button, lp); button.setOnClickListener(v -> action.run()); return button;
    }
    protected void navigation(int selected) {
        if (shell == null) return;
        LinearLayout nav = horizontal();
        nav.setPadding(dp(8), dp(6), dp(8), dp(8)); nav.setBackgroundColor(surface);
        addNav(nav, android.R.drawable.ic_menu_view, "Dashboard", TAB_DASHBOARD, selected, MainActivity.class);
        addNav(nav, android.R.drawable.ic_menu_manage, "Router", TAB_ROUTER, selected, RouterActivity.class);
        addNav(nav, android.R.drawable.ic_menu_recent_history, "History", TAB_HISTORY, selected, HistoryActivity.class);
        addNav(nav, android.R.drawable.ic_menu_preferences, "Settings", TAB_SETTINGS, selected, SettingsActivity.class);
        shell.addView(nav, new LinearLayout.LayoutParams(-1, -2));
    }
    private void addNav(LinearLayout nav, int icon, String title, int tab, int selected, Class<?> target) {
        Button item = new Button(this);
        item.setText(title); item.setAllCaps(false); item.setTextSize(11); item.setGravity(Gravity.CENTER);
        item.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0); item.setCompoundDrawablePadding(dp(2));
        item.setTextColor(tab == selected ? accent : muted);
        item.setBackgroundColor(Color.TRANSPARENT); item.setMinHeight(dp(58)); item.setPadding(0, dp(3), 0, dp(2));
        nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
        if (tab != selected) item.setOnClickListener(v -> openTab(target));
    }
    protected void openTab(Class<?> target) {
        Intent intent = new Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (target == MainActivity.class) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
    protected void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
