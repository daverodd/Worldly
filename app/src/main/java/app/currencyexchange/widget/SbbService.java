package app.currencyexchange.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;

import java.time.ZoneId;

/**
 * Keeps the red second hand moving. A widget cannot animate itself, so while the screen is on this service
 * pushes a fresh picture of the clock about 10 times a second ("smooth") or once a second. When the screen
 * turns off it stops drawing completely (no battery use); the per-minute alarm keeps the minute step exact.
 */
public class SbbService extends Service {
    private static final String CHANNEL = "sbb_clock";
    private static final int NOTIF_ID = 42;

    private HandlerThread thread;
    private Handler handler;
    private BroadcastReceiver screen;
    private Bitmap buf;
    private boolean looping;

    // ------------------------------------------------------------------ control

    /** Start (or keep) the service when a clock widget exists and live mode is on; stop it otherwise. */
    static void ensure(Context ctx) {
        if (!wanted(ctx)) {
            stop(ctx);
            return;
        }
        try {
            Intent i = new Intent(ctx, SbbService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception ignored) {
            // The system refused a background start (rare). The per-minute alarm still keeps the clock correct,
            // and opening the app starts live mode again.
        }
    }

    static void stop(Context ctx) {
        try { ctx.stopService(new Intent(ctx, SbbService.class)); } catch (Exception ignored) { }
    }

    static boolean wanted(Context ctx) {
        return SbbWidgetProvider.widgetIds(ctx).length > 0 && !"minute".equals(Store.sbbMode(ctx));
    }

    // ------------------------------------------------------------------ service

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
        thread = new HandlerThread("sbb-clock");
        thread.start();
        handler = new Handler(thread.getLooper());
        screen = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) startLoop();
                else stopLoop();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!wanted(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm.isInteractive()) startLoop();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopLoop();
        try { unregisterReceiver(screen); } catch (Exception ignored) { }
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "SBB clock second hand", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 12, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_clock)
                .setContentTitle("SBB clock running")
                .setContentText("Moves the second hand while the screen is on")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    // ------------------------------------------------------------------ frame loop

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!looping) return;
            Context ctx = getApplicationContext();
            int[] ids = SbbWidgetProvider.widgetIds(ctx);
            if (ids.length == 0 || "minute".equals(Store.sbbMode(ctx))) {
                stopSelf();
                return;
            }
            try {
                long now = System.currentTimeMillis();
                ZoneId zone = SbbWidgetProvider.zone(ctx);
                buf = SbbRenderer.render(now, zone, SbbRenderer.SIZE, buf);
                SbbWidgetProvider.pushFrame(ctx, ids, buf);
            } catch (Throwable ignored) { }
            long now = System.currentTimeMillis();
            boolean smooth = "smooth".equals(Store.sbbMode(ctx));
            long next = smooth ? (now / 100L + 1) * 100L + 5L : (now / 1000L + 1) * 1000L + 20L;
            handler.postDelayed(this, Math.max(15L, next - now));
        }
    };

    private void startLoop() {
        if (looping) return;
        looping = true;
        handler.post(frame);
    }

    private void stopLoop() {
        looping = false;
        if (handler != null) handler.removeCallbacks(frame);
    }
}
