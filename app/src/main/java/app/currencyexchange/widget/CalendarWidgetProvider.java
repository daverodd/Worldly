package app.currencyexchange.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Week strip + next-event card. Reads the device calendar; refreshes every 15 minutes and at midnight. */
public class CalendarWidgetProvider extends AppWidgetProvider {
    static final String ACTION_TICK = "app.currencyexchange.widget.CAL_TICK";
    static final String ACTION_PREV = "app.currencyexchange.widget.CAL_PREV";
    static final String ACTION_NEXT = "app.currencyexchange.widget.CAL_NEXT";

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
        if (ACTION_PREV.equals(a) || ACTION_NEXT.equals(a)) {
            int n = Math.max(1, CalendarData.upcoming(ctx).size());
            int i = Store.calIdx(ctx) + (ACTION_NEXT.equals(a) ? 1 : -1);
            Store.setCalIdx(ctx, ((i % n) + n) % n); // wraps around like the preset's list switch
            updateAll(ctx);
        } else if (ACTION_TICK.equals(a)
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

    static void updateAll(Context ctx) {
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        int[] ids = widgetIds(ctx);
        if (ids.length == 0) return;
        boolean allowed = CalendarData.allowed(ctx);
        List<CalendarData.Event> events = CalendarData.upcoming(ctx);
        int idx = events.isEmpty() ? 0 : Math.max(0, Math.min(Store.calIdx(ctx), events.size() - 1));
        float density = ctx.getResources().getDisplayMetrics().density;

        PendingIntent settings = PendingIntent.getActivity(ctx, 20, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent calendar = settings;
        if (allowed) {
            Intent ci = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR);
            if (ci.resolveActivity(ctx.getPackageManager()) != null) {
                calendar = PendingIntent.getActivity(ctx, 21, ci, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }

        for (int id : ids) {
            Bundle o = awm.getAppWidgetOptions(id);
            boolean land = ctx.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            int wdp = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300);
            int hdp = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180);
            int w = clamp(Math.round(wdp * density), 300, 1100);
            int h = clamp(Math.round(hdp * density), 180, 1800);
            Bitmap bmp = CalendarRenderer.render(ctx, w, h, events, idx, allowed);
            RemoteViews rv;
            if (CalendarRenderer.compact(ctx, w, h)) {
                rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_calendar);
                rv.setImageViewBitmap(R.id.img, bmp);
                rv.setOnClickPendingIntent(R.id.open_top, calendar);
                rv.setOnClickPendingIntent(R.id.open_card, allowed ? calendar : settings);
                if (allowed && !events.isEmpty()) {
                    rv.setOnClickPendingIntent(R.id.btn_prev, broadcast(ctx, ACTION_PREV, 22));
                    rv.setOnClickPendingIntent(R.id.btn_next, broadcast(ctx, ACTION_NEXT, 23));
                } else {
                    rv.setOnClickPendingIntent(R.id.btn_prev, settings);
                    rv.setOnClickPendingIntent(R.id.btn_next, settings);
                }
            } else {
                rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_calendar_list);
                rv.setImageViewBitmap(R.id.img, bmp);
                rv.setOnClickPendingIntent(R.id.open_all, allowed ? calendar : settings);
            }
            awm.updateAppWidget(id, rv);
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static PendingIntent broadcast(Context ctx, String action, int code) {
        Intent i = new Intent(ctx, CalendarWidgetProvider.class).setAction(action);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent tickIntent(Context ctx) {
        return broadcast(ctx, ACTION_TICK, 24);
    }
}
