package app.currencyexchange.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Week strip + next-event card. Reads the device calendar; refreshes every 15 minutes and at midnight. */
public class CalendarWidgetProvider extends AppWidgetProvider {
    static final String ACTION_TICK = "app.currencyexchange.widget.CAL_TICK";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        tick(ctx);
    }

    @Override
    public void onEnabled(Context ctx) {
        tick(ctx);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager awm, int id, Bundle opts) {
        updateAll(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        Alarms.cancel(ctx, tickIntent(ctx));
        if (!LiveService.wanted(ctx)) LiveService.stop(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        if (ACTION_TICK.equals(a)
                || Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)
                || Intent.ACTION_DATE_CHANGED.equals(a)) {
            tick(ctx);
        }
    }

    static void tick(Context ctx) {
        if (widgetIds(ctx).length == 0) return;
        try {
            long now = System.currentTimeMillis();
            long midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 2000L;
            Alarms.at(ctx, Math.min(now + 15L * 60_000L, midnight), tickIntent(ctx));
        } catch (Exception ignored) { }
        try { updateAll(ctx); } catch (Throwable ignored) { }
        LiveService.ensure(ctx);
    }

    static int[] widgetIds(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, CalendarWidgetProvider.class));
    }

    private static final int[] LAYOUTS = {R.layout.cal_k0, R.layout.cal_k1, R.layout.cal_k2, R.layout.cal_k3,
            R.layout.cal_k4, R.layout.cal_k5, R.layout.cal_k6, R.layout.cal_k7, R.layout.cal_k8};

    /** Height (dp) from which the variant with k event rows fits. */
    private static int heightFor(int k) { return k == 0 ? 110 : 140 + 46 * k; }

    static void updateAll(Context ctx) {
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        int[] ids = widgetIds(ctx);
        if (ids.length == 0) return;
        boolean allowed = CalendarData.allowed(ctx);
        List<CalendarData.Event> events = CalendarData.upcoming(ctx);

        PendingIntent settings = PendingIntent.getActivity(ctx, 20, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent calendar = settings;
        PendingIntent add = settings;
        if (allowed) {
            calendar = settings;
            Intent ci = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR);
            if (ci.resolveActivity(ctx.getPackageManager()) != null) {
                calendar = PendingIntent.getActivity(ctx, 21, ci, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            }
            add = calendar;
            Intent ai = new Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI);
            if (ai.resolveActivity(ctx.getPackageManager()) != null) {
                add = PendingIntent.getActivity(ctx, 25, ai, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }
        PendingIntent open = allowed ? calendar : settings;
        PendingIntent addPi = allowed ? add : settings;

        for (int id : ids) {
            RemoteViews rv;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                // The launcher picks the variant that fits the real widget size and re-picks when it is resized.
                java.util.Map<android.util.SizeF, RemoteViews> map = new java.util.LinkedHashMap<>();
                for (int k = 0; k < LAYOUTS.length; k++) {
                    map.put(new android.util.SizeF(110f, heightFor(k)), build(ctx, k, events, allowed, open, addPi));
                }
                rv = new RemoteViews(map);
            } else {
                Bundle o = awm.getAppWidgetOptions(id);
                boolean land = ctx.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                int hdp = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                        : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 208);
                int k = 0;
                for (int i = 0; i < LAYOUTS.length; i++) if (heightFor(i) <= hdp) k = i;
                rv = build(ctx, k, events, allowed, open, addPi);
            }
            awm.updateAppWidget(id, rv);
        }
    }

    private static int rid(Context ctx, String name) {
        return ctx.getResources().getIdentifier(name, "id", ctx.getPackageName());
    }

    private static RemoteViews build(Context ctx, int k, List<CalendarData.Event> events, boolean allowed,
                                     PendingIntent open, PendingIntent add) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), LAYOUTS[k]);
        LocalDate today = LocalDate.now();
        long now = System.currentTimeMillis();
        java.util.Locale loc = java.util.Locale.getDefault();

        rv.setTextViewText(R.id.cal_day, String.valueOf(today.getDayOfMonth()));
        rv.setTextColor(R.id.cal_day, Theme.TEXT);
        rv.setTextViewText(R.id.cal_month, today.format(java.time.format.DateTimeFormatter.ofPattern("MMMM", loc)));
        rv.setTextColor(R.id.cal_month, Theme.TEXT);
        int week = today.get(java.time.temporal.WeekFields.of(java.time.DayOfWeek.SUNDAY, 1).weekOfYear());
        rv.setTextViewText(R.id.cal_sub, "Today, " + today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", loc))
                + "  \u00B7  Week " + week);
        rv.setTextColor(R.id.cal_sub, Theme.MUTED);

        // week row, Sunday first
        LocalDate sunday = today.minusDays(today.getDayOfWeek().getValue() % 7);
        for (int i = 0; i < 7; i++) {
            LocalDate d = sunday.plusDays(i);
            boolean isToday = d.equals(today);
            int lab = rid(ctx, "wk_label_" + i), num = rid(ctx, "wk_num_" + i);
            rv.setTextViewText(lab, d.format(java.time.format.DateTimeFormatter.ofPattern("EEE", loc)).toUpperCase(loc));
            rv.setTextColor(lab, isToday ? Theme.AMBER_LIGHT : Theme.MUTED);
            rv.setTextViewText(num, String.valueOf(d.getDayOfMonth()));
            rv.setTextColor(num, isToday ? 0xFFFFFFFF : Theme.TEXT_SOFT);
            rv.setInt(num, "setBackgroundResource", isToday ? R.drawable.cal_today : 0);
        }

        // events
        int shown = Math.min(k, Math.max(events.size(), (k > 0 && (events.isEmpty() || !allowed)) ? 1 : 0));
        java.time.format.DateTimeFormatter tf = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT);
        for (int i = 0; i < k; i++) {
            int row = rid(ctx, "cal_row_" + i), bar = rid(ctx, "cal_bar_" + i), time = rid(ctx, "cal_time_" + i),
                    hint = rid(ctx, "cal_hint_" + i), title = rid(ctx, "cal_title_" + i);
            if (i >= shown) { rv.setViewVisibility(row, android.view.View.GONE); continue; }
            rv.setViewVisibility(row, android.view.View.VISIBLE);
            if (!allowed || events.isEmpty()) {
                rv.setViewVisibility(bar, android.view.View.INVISIBLE);
                rv.setTextViewText(title, allowed ? "No upcoming events" : "Calendar access needed");
                rv.setTextColor(title, Theme.TEXT);
                rv.setTextViewText(time, allowed ? "Nothing in the next 120 days" : "Tap to open settings");
                rv.setTextColor(time, Theme.MUTED);
                rv.setTextViewText(hint, "");
                continue;
            }
            CalendarData.Event ev = events.get(i);
            rv.setViewVisibility(bar, android.view.View.VISIBLE);
            rv.setInt(bar, "setColorFilter", ev.color != 0 ? (0xFF000000 | ev.color) : Theme.AMBER);
            String t;
            if (ev.allDay) t = "All day";
            else {
                java.time.ZoneId z = java.time.ZoneId.systemDefault();
                t = java.time.Instant.ofEpochMilli(ev.begin).atZone(z).format(tf) + " - "
                        + java.time.Instant.ofEpochMilli(ev.end).atZone(z).format(tf);
            }
            if (!ev.date.equals(today)) {
                t = ev.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", loc)) + "  \u00B7  " + t;
            }
            rv.setTextViewText(time, t);
            rv.setTextColor(time, Theme.MUTED);
            String h = "";
            if (!ev.allDay && ev.date.equals(today)) {
                if (ev.begin <= now) h = "now";
                else {
                    long mins = (ev.begin - now + 59_999L) / 60_000L;
                    if (mins <= 90) h = "in " + mins + (mins == 1 ? " minute" : " minutes");
                }
            }
            rv.setTextViewText(hint, h);
            rv.setTextColor(hint, Theme.AMBER_LIGHT);
            rv.setTextViewText(title, Util.capWords(ev.title));
            rv.setTextColor(title, Theme.TEXT);
        }
        int hidden = allowed ? events.size() - k : 0;
        if (k > 0 && hidden > 0) {
            rv.setViewVisibility(R.id.cal_more, android.view.View.VISIBLE);
            rv.setTextViewText(R.id.cal_more, "+" + hidden + " more");
            rv.setTextColor(R.id.cal_more, Theme.MUTED);
        } else {
            rv.setViewVisibility(R.id.cal_more, android.view.View.GONE);
        }
        rv.setOnClickPendingIntent(R.id.panel, open);
        rv.setOnClickPendingIntent(R.id.btn_add, add);
        return rv;
    }

    private static PendingIntent broadcast(Context ctx, String action, int code) {
        Intent i = new Intent(ctx, CalendarWidgetProvider.class).setAction(action);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent tickIntent(Context ctx) {
        return broadcast(ctx, ACTION_TICK, 24);
    }
}
