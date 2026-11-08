package app.currencyexchange.widget;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Settings screen, styled like SAB Monitor (graphite cards, amber accent, Jakarta + Mono type). */
public class MainActivity extends Activity {
    private final EditText[] city = new EditText[4];
    private final EditText[] from = new EditText[4];
    private EditText to, fee, home, sbbTz;
    private String sbbMode;
    private final TextView[] modeBtn = new TextView[3];
    private static final String[] MODES = {"smooth", "second", "minute"};
    private static final String[] MODE_LABELS = {"Smooth", "Every second", "Minute only"};
    private TextView banner, calStatus, calButton;
    private LinearLayout statusBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Theme.BG);
        sv.setFillViewport(true);
        LinearLayout col = vbox();
        col.setPadding(dp(16), dp(20), dp(16), dp(28));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- header: amber badge + title (like SAB's drawer header) ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text("FX", 13, Theme.monoBold(this), Theme.AMBER);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Theme.box(this, 0x26DF8B35, 0x66DF8B35, 12));
        head.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout titles = vbox();
        titles.setPadding(dp(12), 0, 0, 0);
        titles.addView(text("Currency Exchange", 19, Theme.uiBold(this), Theme.TEXT));
        titles.addView(text("World clocks · rates · weather", 12, Theme.uiMedium(this), Theme.MUTED));
        head.addView(titles);
        col.addView(head);

        // ---- world clocks ----
        LinearLayout clocks = card(col, "WORLD CLOCKS");
        grid(clocks, city, new String[]{Store.city(this, 0), Store.city(this, 1), Store.city(this, 2), Store.city(this, 3)},
                InputType.TYPE_CLASS_TEXT, false, "City");

        // ---- currency rows ----
        LinearLayout rates = card(col, "CURRENCY ROWS");
        grid(rates, from, new String[]{Store.from(this, 0), Store.from(this, 1), Store.from(this, 2), Store.from(this, 3)},
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true, "Code");
        LinearLayout conv = hbox();
        to = input(Store.to(this), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, true, "Code");
        fee = input(Store.feeText(this), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, true, "Fee");
        addWeighted(conv, labeled("CONVERT TO", to), 0);
        addWeighted(conv, labeled("VISA FEE %", fee), 8);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(12);
        rates.addView(conv, cp);

        // ---- weather location ----
        LinearLayout wx = card(col, "WEATHER & SUN LOCATION");
        home = input(Store.home(this), InputType.TYPE_CLASS_TEXT, false, "Home city (optional)");
        wx.addView(home, matchWrap(0));
        TextView hint = text("Leave empty to use your device location.", 12, Theme.uiMedium(this), Theme.DIM);
        hint.setPadding(dp(2), dp(6), 0, dp(10));
        wx.addView(hint);
        wx.addView(secondaryButton("Use my location", v -> useLocation()), matchWrap(0));

        // ---- SBB clock ----
        LinearLayout sbb = card(col, "SBB CLOCK");
        sbbTz = input(Store.sbbTz(this), InputType.TYPE_CLASS_TEXT, true, "Time zone, e.g. Europe/Zurich (optional)");
        sbb.addView(sbbTz, matchWrap(0));
        TextView sbbHint = text("Leave empty to follow your device. Second hand: runs a lap in 58.5 s, waits at 12 and the "
                + "minute hand steps on the minute, like the real station clock.", 12, Theme.uiMedium(this), Theme.DIM);
        sbbHint.setPadding(dp(2), dp(6), 0, dp(10));
        sbb.addView(sbbHint);
        sbbMode = Store.sbbMode(this);
        LinearLayout modes = hbox();
        for (int i = 0; i < 3; i++) {
            final String m = MODES[i];
            modeBtn[i] = text(MODE_LABELS[i], 13, Theme.uiSemi(this), Theme.TEXT_SOFT);
            modeBtn[i].setGravity(Gravity.CENTER);
            modeBtn[i].setPadding(dp(6), dp(11), dp(6), dp(11));
            modeBtn[i].setClickable(true);
            modeBtn[i].setOnClickListener(v -> { sbbMode = m; paintModes(); });
            addWeighted(modes, modeBtn[i], i == 0 ? 0 : 8);
        }
        sbb.addView(modes, matchWrap(0));
        paintModes();
        TextView modeHint = text("Smooth and Every second need a small background service while the screen is on (an "
                + "ongoing notification appears). Minute only uses no service.", 12, Theme.uiMedium(this), Theme.DIM);
        modeHint.setPadding(dp(2), dp(8), 0, 0);
        sbb.addView(modeHint);

        // ---- calendar ----
        LinearLayout cal = card(col, "CALENDAR");
        calStatus = text("", 13, Theme.uiSemi(this), Theme.TEXT);
        cal.addView(calStatus);
        calButton = secondaryButton("Allow calendar access", v ->
                requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, 2));
        cal.addView(calButton, matchWrap(10));
        updateCalendarCard();

        // ---- actions ----
        col.addView(primaryButton("Save & refresh", v -> save()), matchWrap(18));
        col.addView(secondaryButton("Add Currency widget", v -> pin(WidgetProvider.class)), matchWrap(10));
        col.addView(secondaryButton("Add SBB clock widget", v -> pin(SbbWidgetProvider.class)), matchWrap(8));
        col.addView(secondaryButton("Add Calendar widget", v -> pin(CalendarWidgetProvider.class)), matchWrap(8));

        // ---- status ----
        LinearLayout st = card(col, "STATUS");
        banner = text("", 13, Theme.uiSemi(this), Theme.AMBER);
        banner.setVisibility(View.GONE);
        banner.setPadding(0, 0, 0, dp(8));
        st.addView(banner);
        statusBox = vbox();
        st.addView(statusBox);
        TextView foot = text("World clocks update every minute. Rates and weather refresh about every 30 minutes.",
                12, Theme.uiMedium(this), Theme.DIM);
        foot.setPadding(0, dp(10), 0, 0);
        st.addView(foot);
        updateStatus();

        setContentView(sv);
    }

    // ------------------------------------------------------------------ actions

    private void save() {
        SharedPreferences.Editor e = Store.sp(this).edit();
        for (int i = 0; i < 4; i++) {
            e.putString("city" + i, city[i].getText().toString().trim());
            e.putString("from" + i, from[i].getText().toString().trim().toUpperCase(Locale.ROOT));
        }
        e.putString("to", to.getText().toString().trim().toUpperCase(Locale.ROOT));
        String feeIn = Util.cleanNum(fee.getText().toString());
        if (!Double.isNaN(Util.parseNum(feeIn))) e.putString("feeTxt", feeIn);
        e.putString("home", home.getText().toString().trim());
        String tz = sbbTz.getText().toString().trim();
        boolean tzOk = true;
        if (!tz.isEmpty()) {
            try { java.time.ZoneId.of(tz); } catch (java.time.DateTimeException ex) { tzOk = false; }
        }
        e.putString("sbbTz", tzOk ? tz : "");
        e.putString("sbbMode", sbbMode);
        e.apply();
        if (!tzOk) Toast.makeText(this, "Unknown time zone \"" + tz + "\" - using the device's.", Toast.LENGTH_LONG).show();
        if (Build.VERSION.SDK_INT >= 33 && !"minute".equals(sbbMode)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
        }
        refreshNow("Saved. Refreshing…");
    }

    private void refreshNow(String msg) {
        final Context app = getApplicationContext();
        WidgetProvider.updateAll(app); // instant repaint with whatever is cached
        SbbWidgetProvider.updateAll(app);
        CalendarWidgetProvider.updateAll(app);
        SbbService.ensure(app);
        banner.setText(msg);
        banner.setVisibility(View.VISIBLE);
        new Thread(() -> {
            Fetcher.refreshAll(app, true);
            WidgetProvider.updateAll(app);
            runOnUiThread(this::updateStatus);
        }).start();
    }

    private void updateStatus() {
        banner.setVisibility(View.GONE);
        statusBox.removeAllViews();

        long t = Store.lastFetch(this);
        String last = t == 0 ? null
                : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(t));
        statusRow("Data refresh", last != null, last == null ? "Not refreshed yet" : "Last successful: " + last);

        String visaErr = Store.sp(this).getString("visaErr", "");
        statusRow("Visa rate", visaErr.isEmpty(),
                visaErr.isEmpty() ? "Live rate from Visa" : visaErr);

        String wxErr = Store.sp(this).getString("wxErr", "");
        statusRow("Weather", wxErr.isEmpty() && Store.weather(this) != null,
                !wxErr.isEmpty() ? wxErr : (Store.weather(this) == null ? "Waiting for first update" : "Open-Meteo"));

        String geoErr = Store.sp(this).getString("geoErr", "");
        statusRow("Cities", geoErr.isEmpty(), geoErr.isEmpty() ? "All cities found" : geoErr);
    }

    // ---- location ----

    private void useLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            return;
        }
        fetchLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCalendarCard();
        SbbService.ensure(getApplicationContext()); // the app is in front, so Android lets the service start
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 2) {
            updateCalendarCard();
            CalendarWidgetProvider.updateAll(getApplicationContext());
            return;
        }
        if (code == 3) return; // notification permission: only affects whether the notification is visible
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) fetchLocation();
        else Toast.makeText(this, "Permission denied - type a home city instead.", Toast.LENGTH_LONG).show();
    }

    private void fetchLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) { }
        if (best != null) {
            saveLocation(best);
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                String prov = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                        ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
                lm.getCurrentLocation(prov, null, getMainExecutor(), loc -> {
                    if (loc != null) saveLocation(loc);
                    else toastNoLocation();
                });
                return;
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        toastNoLocation();
    }

    private void toastNoLocation() {
        Toast.makeText(this, "Couldn't get a location. Turn on device location, or type a home city.",
                Toast.LENGTH_LONG).show();
    }

    private void saveLocation(Location l) {
        Store.setDeviceLocation(this, l.getLatitude(), l.getLongitude());
        refreshNow(String.format(Locale.US, "Location saved (%.2f, %.2f). Refreshing…", l.getLatitude(), l.getLongitude()));
    }

    // ---- pin widget ----

    private void pin(Class<?> provider) {
        AppWidgetManager awm = getSystemService(AppWidgetManager.class);
        if (awm != null && awm.isRequestPinAppWidgetSupported()) {
            awm.requestPinAppWidget(new ComponentName(this, provider), null, null);
        } else {
            Toast.makeText(this, "Long-press your home screen > Widgets > Currency Exchange.", Toast.LENGTH_LONG).show();
        }
    }

    private void paintModes() {
        for (int i = 0; i < 3; i++) {
            boolean on = MODES[i].equals(sbbMode);
            modeBtn[i].setTextColor(on ? 0xFFFFFFFF : Theme.TEXT_SOFT);
            modeBtn[i].setBackground(on
                    ? Theme.amberGradient(this, 14, false)
                    : Theme.box(this, Theme.SURFACE, Theme.BORDER, 14));
        }
    }

    private void updateCalendarCard() {
        boolean ok = CalendarData.allowed(this);
        calStatus.setText(ok ? "Calendar access allowed - showing your next 5 events."
                : "Needs permission to read your calendar. Events never leave your phone.");
        calStatus.setTextColor(ok ? Theme.TEXT : Theme.AMBER);
        calButton.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ UI building blocks

    private int dp(int v) { return Math.round(Theme.dp(this, v)); }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams matchWrap(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private TextView text(String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        return t;
    }

    /** SAB-style panel: #11141e fill, thin slate border, 16dp corners, small caps section title. */
    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout c = vbox();
        c.setBackground(Theme.box(this, Theme.PANEL, Theme.BORDER_SOFT, 16));
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        TextView t = text(title, 11, Theme.uiBold(this), Theme.MUTED);
        t.setLetterSpacing(0.1f);
        t.setPadding(dp(2), 0, 0, dp(10));
        c.addView(t);
        parent.addView(c, matchWrap(14));
        return c;
    }

    /** Dark input with slate border that turns amber on focus. */
    private EditText input(String value, int inputType, boolean mono, String hint) {
        final EditText e = new EditText(this);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTypeface(mono ? Theme.monoMedium(this) : Theme.uiMedium(this));
        e.setTextColor(Theme.TEXT);
        e.setHintTextColor(Theme.DIM);
        e.setHighlightColor(0x55DF8B35);
        e.setPadding(dp(12), dp(11), dp(12), dp(11));
        e.setBackground(Theme.box(this, Theme.SURFACE, Theme.BORDER, 12));
        e.setOnFocusChangeListener((v, focused) ->
                e.setBackground(Theme.box(this, Theme.SURFACE, focused ? Theme.AMBER : Theme.BORDER, 12)));
        return e;
    }

    /** Two inputs per row. */
    private void grid(LinearLayout card, EditText[] out, String[] values, int type, boolean mono, String hintPrefix) {
        for (int i = 0; i < out.length; i += 2) {
            LinearLayout row = hbox();
            for (int j = 0; j < 2 && i + j < out.length; j++) {
                out[i + j] = input(values[i + j], type, mono, hintPrefix + " " + (i + j + 1));
                addWeighted(row, out[i + j], j == 0 ? 0 : 8);
            }
            card.addView(row, matchWrap(i == 0 ? 0 : 8));
        }
    }

    private void addWeighted(LinearLayout row, View v, int leftMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(leftMarginDp);
        row.addView(v, lp);
    }

    private LinearLayout labeled(String label, EditText e) {
        LinearLayout l = vbox();
        TextView t = text(label, 10, Theme.uiBold(this), Theme.DIM);
        t.setLetterSpacing(0.1f);
        t.setPadding(dp(2), 0, 0, dp(4));
        l.addView(t);
        l.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    /** SAB's active-tab look: orange gradient, white bold text. */
    private TextView primaryButton(String label, View.OnClickListener l) {
        TextView b = text(label, 15, Theme.uiBold(this), 0xFFFFFFFF);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(Theme.pressable(Theme.amberGradient(this, 16, false), Theme.amberGradient(this, 16, true)));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** SAB's inactive-tab look: #151922 with slate border. */
    private TextView secondaryButton(String label, View.OnClickListener l) {
        TextView b = text(label, 14, Theme.uiSemi(this), Theme.TEXT_SOFT);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(13), dp(16), dp(13));
        b.setBackground(Theme.pressable(Theme.box(this, Theme.SURFACE, Theme.BORDER, 16),
                Theme.box(this, Theme.SURFACE_HI, Theme.BORDER, 16)));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** Colored dot + name + detail. Green = OK, amber = needs attention. */
    private void statusRow(String name, boolean ok, String detail) {
        LinearLayout row = hbox();
        row.setPadding(0, dp(6), 0, dp(6));
        TextView dot = text("●", 12, Typeface.DEFAULT, ok ? Theme.GREEN : Theme.AMBER);
        dot.setPadding(0, dp(3), dp(10), 0);
        row.addView(dot);
        LinearLayout txt = vbox();
        txt.addView(text(name, 14, Theme.uiSemi(this), Theme.TEXT));
        TextView d = text(detail, 12, Theme.uiMedium(this), Theme.MUTED);
        d.setPadding(0, dp(1), 0, 0);
        txt.addView(d);
        row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusBox.addView(row);
    }
}
