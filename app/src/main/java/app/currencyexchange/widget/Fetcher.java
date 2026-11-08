package app.currencyexchange.widget;

import android.content.Context;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Pulls every piece of data the original KWGT formulas fetched, with the same sources:
 *  - Geoapify geocoding (city -> lat/lon/timezone/country)
 *  - fawazahmed0 currency-api (market rate)
 *  - Visa FX endpoint (rate incl. fee)
 *  - Tomorrow.io (feels-like + humidity)
 * Results are cached in {@link Store}; every call is independent so one failure never blocks the rest.
 * Must run on a background thread.
 */
final class Fetcher {
    private Fetcher() {}

    private static final long WEATHER_MIN_GAP_MS = 25L * 60_000L; // Tomorrow.io free plan is rate limited

    static void refreshAll(Context ctx) {
        refreshAll(ctx, false);
    }

    /** @param force true when the user asked for it (Save & refresh): ignores the weather rate-limit gap */
    static void refreshAll(Context ctx, boolean force) {
        boolean any = false;
        String to = Store.to(ctx).trim();

        // Cities: geocode (cached forever per name) + flags
        for (int i = 0; i < 4; i++) {
            try {
                String city = Store.city(ctx, i);
                if (Store.geo(ctx, city) == null) geocode(ctx, city);
                JSONObject g = Store.geo(ctx, city);
                if (g != null) {
                    Flags.ensureFlag(ctx, g.optString("cc", ""));
                    any = true;
                }
            } catch (Exception ignored) { }
        }
        String home = Store.home(ctx).trim();
        if (!home.isEmpty() && Store.geo(ctx, home) == null) {
            try { geocode(ctx, home); } catch (Exception ignored) { }
        }

        // Currency rows
        Flags.ensureFlag(ctx, Util.countryOfCurrency(to));
        Flags.ensureVisa(ctx);
        for (int i = 0; i < 4; i++) {
            String from = Store.from(ctx, i).trim();
            Flags.ensureFlag(ctx, Util.countryOfCurrency(from));
            try {
                Store.putRate(ctx, from, to, marketRate(from, to));
                any = true;
            } catch (Exception ignored) { }
            try {
                double v = visaRate(ctx, from, to, Store.fee(ctx));
                if (!Double.isNaN(v)) Store.putVisa(ctx, from, to, v);
            } catch (Exception ignored) { }
        }

        // Weather
        try {
            double[] ll = Store.weatherLatLon(ctx);
            String key = Store.wxKey(ctx);
            if (Double.isNaN(ll[0])) {
                Store.sp(ctx).edit().putString("wxErr", "no location yet (tap 'Use my location' or type a home city)").apply();
            } else if (key.isEmpty()) {
                Store.sp(ctx).edit().putString("wxErr", "Tomorrow.io API key is empty").apply();
            } else if (!force && Store.weather(ctx) != null
                    && System.currentTimeMillis() - Store.sp(ctx).getLong("wxTs", 0L) < WEATHER_MIN_GAP_MS) {
                any = true; // fresh enough: do not spend another API call
            } else {
                Store.sp(ctx).edit().putLong("wxTs", System.currentTimeMillis()).apply(); // counts even if it fails
                weather(ctx, ll[0], ll[1], key);
                Store.sp(ctx).edit().remove("wxErr").apply();
                any = true;
            }
        } catch (Exception e) {
            String m = e.getMessage() == null ? e.toString() : e.getMessage();
            Store.sp(ctx).edit().putString("wxErr", m.replaceAll("apikey=[^&\\s]+", "apikey=***")).apply();
        }

        if (any) Store.setLastFetch(ctx);
    }

    private static void geocode(Context ctx, String city) throws Exception {
        String key = Store.geoKey(ctx);
        if (key.isEmpty() || city.trim().isEmpty()) return;
        String url = "https://api.geoapify.com/v1/geocode/search?text=" + URLEncoder.encode(city.trim(), "UTF-8")
                + "&lang=en&limit=1&type=city&format=json&apiKey=" + key;
        JSONObject r = new JSONObject(Net.get(url)).getJSONArray("results").getJSONObject(0);
        JSONObject o = new JSONObject();
        o.put("lat", r.getDouble("lat"));
        o.put("lon", r.getDouble("lon"));
        o.put("tz", r.getJSONObject("timezone").getString("name"));
        o.put("cc", r.optString("country_code", ""));
        Store.putGeo(ctx, city, o);
    }

    /** Same source/path as the preset: .{from}.{to} inside {from}.json */
    private static double marketRate(String from, String to) throws Exception {
        String f = from.toLowerCase(Locale.ROOT), t = to.toLowerCase(Locale.ROOT);
        String[] bases = {
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/",
                "https://latest.currency-api.pages.dev/v1/currencies/"
        };
        Exception last = null;
        for (String b : bases) {
            try {
                return new JSONObject(Net.get(b + f + ".json")).getJSONObject(f).getDouble(t);
            } catch (Exception e) { last = e; }
        }
        throw last;
    }

    /**
     * Same endpoint as the preset (fromCurr=TARGET, toCurr=SOURCE), but sends browser-like headers,
     * tries a second Visa domain, and falls back to the previous 3 days (Visa publishes rates with a
     * delay, and not on weekends). The last error is stored so the settings screen can show it.
     */
    private static double visaRate(Context ctx, String from, String to, float fee) {
        String[] hosts = {"https://www.visa.com.au", "https://usa.visa.com"};
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US);
        String err = "no response";
        for (int back = 0; back <= 4; back++) {
            boolean retryOlder = false; // true when the server answered but has no rate for this date
            String d = LocalDate.now().minusDays(back).format(fmt);
            for (String host : hosts) {
                try {
                    String url = host + "/cmsapi/fx/rates?amount=1&fee=" + fee
                            + "&utcConvertedDate=" + URLEncoder.encode(d, "UTF-8")
                            + "&exchangedate=" + URLEncoder.encode(d, "UTF-8")
                            + "&fromCurr=" + to.toUpperCase(Locale.ROOT)
                            + "&toCurr=" + from.toUpperCase(Locale.ROOT);
                    String body = Net.get(url, host + "/support/consumer/travel-support/exchange-rate-calculator.html");
                    JSONObject o = new JSONObject(body).optJSONObject("originalValues");
                    if (o == null) {
                        retryOlder = true;
                        err = from + ": no rate for " + d + " (" + body.substring(0, Math.min(100, body.length())) + ")";
                        continue;
                    }
                    double v = Util.parseNum(o.optString("toAmountWithAdditionalFee", ""));
                    if (!Double.isNaN(v)) {
                        Store.sp(ctx).edit().remove("visaErr").apply();
                        return v;
                    }
                    retryOlder = true;
                    err = from + ": empty value for " + d;
                } catch (Exception e) {
                    String m = String.valueOf(e.getMessage());
                    err = from + " " + host.replace("https://", "") + " " + d + ": " + m;
                    // 400/404 = Visa has no rate for that date (weekends, publication delay): try an older date.
                    // 403/429/timeouts = blocked or offline: older dates will not help.
                    if (m.startsWith("HTTP 404") || m.startsWith("HTTP 400")) retryOlder = true;
                }
            }
            if (!retryOlder) break;
        }
        Store.sp(ctx).edit().putString("visaErr", err).apply();
        return Double.NaN;
    }

    private static void weather(Context ctx, double lat, double lon, String key) throws Exception {
        String url = "https://api.tomorrow.io/v4/timelines?location="
                + String.format(Locale.US, "%.4f,%.4f", lat, lon)
                + "&fields=temperatureApparent,humidity&timesteps=current&units=metric&apikey=" + key;
        JSONObject v = new JSONObject(Net.get(url)).getJSONObject("data").getJSONArray("timelines")
                .getJSONObject(0).getJSONArray("intervals").getJSONObject(0).getJSONObject("values");
        Store.putWeather(ctx, v.getDouble("temperatureApparent"), v.getDouble("humidity"));
    }
}
