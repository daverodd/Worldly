package app.currencyexchange.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

public class WidgetProvider extends AppWidgetProvider {
    static final String ACTION_TICK = "app.currencyexchange.widget.TICK";
    private static final int JOB_PERIODIC = 1, JOB_NOW = 2;
    private static final long STALE_MS = 40L * 60_000L;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        tick(ctx);
    }

    @Override
    public void onEnabled(Context ctx) {
        requestRefresh(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(tickIntent(ctx));
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        js.cancel(JOB_PERIODIC);
        js.cancel(JOB_NOW);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent); // dispatches APPWIDGET_* actions
        String a = intent.getAction();
        if (ACTION_TICK.equals(a)
                || Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            tick(ctx);
        }
    }

    /** Redraw from cache, queue a network refresh if stale, and arm the next per-minute redraw. */
    private static void tick(Context ctx) {
        updateAll(ctx);
        if (widgetIds(ctx).length == 0) return;
        ensurePeriodicJob(ctx);
        if (System.currentTimeMillis() - Store.lastFetch(ctx) > STALE_MS) requestRefresh(ctx);
        scheduleNextTick(ctx);
    }

    static int[] widgetIds(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, WidgetProvider.class));
    }

    static void updateAll(Context ctx) {
        int[] ids = widgetIds(ctx);
        if (ids.length == 0) return;
        Bitmap bmp = Renderer.render(ctx);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        rv.setImageViewBitmap(R.id.img, bmp);
        PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.root, open);
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, rv);
    }

    private static PendingIntent tickIntent(Context ctx) {
        Intent i = new Intent(ctx, WidgetProvider.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void scheduleNextTick(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        long next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L + 500L; // just after the minute turns
        am.setAndAllowWhileIdle(AlarmManager.RTC, next, tickIntent(ctx));
    }

    private static void ensurePeriodicJob(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js.getPendingJob(JOB_PERIODIC) != null) return;
        js.schedule(new JobInfo.Builder(JOB_PERIODIC, new ComponentName(ctx, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(30L * 60_000L)
                .setPersisted(true)
                .build());
    }

    /** One-off refresh as soon as the network is available. */
    static void requestRefresh(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        js.schedule(new JobInfo.Builder(JOB_NOW, new ComponentName(ctx, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build());
    }
}
