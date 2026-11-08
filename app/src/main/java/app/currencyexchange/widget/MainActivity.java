package app.currencyexchange.widget;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Settings screen: everything that used to be a KWGT global is editable here. */
public class MainActivity extends Activity {
    private final EditText[] city = new EditText[4];
    private final EditText[] from = new EditText[4];
    private EditText to, fee, home, geoKey, wxKey;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Currency Exchange");

        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        col.setPadding(pad, pad, pad, pad);
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(col, "World clocks - 4 cities");
        for (int i = 0; i < 4; i++) city[i] = edit(col, Store.city(this, i), InputType.TYPE_CLASS_TEXT);

        label(col, "Currencies to convert FROM (3-letter codes)");
        for (int i = 0; i < 4; i++) {
            from[i] = edit(col, Store.from(this, i), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        }

        label(col, "Convert TO (your currency)");
        to = edit(col, Store.to(this), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        label(col, "Visa fee (%)");
        fee = edit(col, String.valueOf(Store.fee(this)), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        label(col, "Weather / sunrise location: home city (leave empty to use device location)");
        home = edit(col, Store.home(this), InputType.TYPE_CLASS_TEXT);

        label(col, "API keys (Geoapify = city lookup, Tomorrow.io = weather)");
        geoKey = edit(col, Store.geoKey(this), InputType.TYPE_CLASS_TEXT);
        geoKey.setHint("Geoapify key");
        wxKey = edit(col, Store.wxKey(this), InputType.TYPE_CLASS_TEXT);
        wxKey.setHint("Tomorrow.io key");

        button(col, "Save & refresh", v -> save());
        button(col, "Use my location for weather", v -> useLocation());
        button(col, "Add widget to home screen", v -> pin());

        status = new TextView(this);
        status.setPadding(0, dp(12), 0, 0);
        col.addView(status);
        updateStatus();

        setContentView(sv);
    }

    private void save() {
        SharedPreferences.Editor e = Store.sp(this).edit();
        for (int i = 0; i < 4; i++) {
            e.putString("city" + i, city[i].getText().toString().trim());
            e.putString("from" + i, from[i].getText().toString().trim().toUpperCase(Locale.ROOT));
        }
        e.putString("to", to.getText().toString().trim().toUpperCase(Locale.ROOT));
        try {
            e.putFloat("fee", Float.parseFloat(fee.getText().toString().trim()));
        } catch (NumberFormatException ignored) { }
        e.putString("home", home.getText().toString().trim());
        e.putString("geoKey", geoKey.getText().toString().trim());
        e.putString("wxKey", wxKey.getText().toString().trim());
        e.apply();
        refreshNow("Saved. Refreshing...");
    }

    private void refreshNow(String msg) {
        final Context app = getApplicationContext();
        WidgetProvider.updateAll(app); // instant repaint with whatever is cached
        status.setText(msg);
        new Thread(() -> {
            Fetcher.refreshAll(app);
            WidgetProvider.updateAll(app);
            runOnUiThread(this::updateStatus);
        }).start();
    }

    private void updateStatus() {
        long t = Store.lastFetch(this);
        String last = t == 0 ? "never"
                : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(t));
        status.setText("Last successful data refresh: " + last
                + "\nData also refreshes automatically about every 30 minutes; clocks update every minute.");
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
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
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
        refreshNow(String.format(Locale.US, "Location saved (%.2f, %.2f). Refreshing...", l.getLatitude(), l.getLongitude()));
    }

    // ---- pin widget ----

    private void pin() {
        AppWidgetManager awm = getSystemService(AppWidgetManager.class);
        if (awm != null && awm.isRequestPinAppWidgetSupported()) {
            awm.requestPinAppWidget(new ComponentName(this, WidgetProvider.class), null, null);
        } else {
            Toast.makeText(this, "Long-press your home screen > Widgets > Currency Exchange.", Toast.LENGTH_LONG).show();
        }
    }

    // ---- tiny UI helpers ----

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void label(LinearLayout p, String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setPadding(0, dp(14), 0, dp(2));
        p.addView(t);
    }

    private EditText edit(LinearLayout p, String value, int inputType) {
        EditText e = new EditText(this);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setText(value);
        p.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private void button(LinearLayout p, String s, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(s);
        bt.setOnClickListener(l);
        p.addView(bt, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
