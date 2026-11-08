package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Draws the Swiss railway clock. Geometry (in a 320-unit square, face radius 150) is taken from the
 * KWGT "SBB_Clock" preset: 12 hour bars 11 x 36, 48 minute ticks 3.5 x 12, tapered black hour and minute
 * hands with a tail, red second hand with a 31-unit disc. The motion comes from {@link SbbClock}.
 * The only liberty: the case ring uses the app's amber instead of the wallpaper colour (SAB look).
 */
final class SbbRenderer {
    private SbbRenderer() {}

    static final int SIZE = 440;

    private static Bitmap face;
    private static int faceSize;

    private static synchronized Bitmap face(int n) {
        if (face != null && faceSize == n) return face;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.translate(n / 2f, n / 2f);
        float s = n / 320f;
        c.scale(s, s);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(-160, -160, 160, 160, Theme.AMBER_LIGHT, Theme.AMBER_DEEP, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, 160, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(0, 0, 150, p);
        p.setColor(0xFF000000);
        for (int i = 0; i < 60; i++) {
            c.save();
            c.rotate(i * 6f);
            if (i % 5 == 0) c.drawRect(-5.5f, -141.5f, 5.5f, -105.5f, p);
            else c.drawRect(-1.75f, -141f, 1.75f, -129f, p);
            c.restore();
        }
        face = b;
        faceSize = n;
        return b;
    }

    /** @param reuse optional bitmap of the right size to draw into (avoids allocating at 10 frames a second) */
    static Bitmap render(long nowMs, ZoneId zone, int n, Bitmap reuse) {
        Bitmap out = (reuse != null && reuse.getWidth() == n && reuse.getHeight() == n && reuse.isMutable())
                ? reuse : Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        ZonedDateTime t = Instant.ofEpochMilli(nowMs).atZone(zone);
        double sec = t.getSecond() + Math.floorMod(nowMs, 1000L) / 1000.0;
        float secDeg = (float) SbbClock.secondAngle(sec);
        float minDeg = (float) SbbClock.minuteAngle(t.getMinute());
        float hourDeg = (float) SbbClock.hourAngle(t.getHour(), t.getMinute());

        Canvas c = new Canvas(out);
        c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
        c.drawBitmap(face(n), 0, 0, null);
        float s = n / 320f;
        c.translate(n / 2f, n / 2f);
        c.scale(s, s);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF000000);

        // hour hand: 91 units forward, 33 tail, 17 -> 14 wide
        hand(c, p, hourDeg, 8.6f, 7.0f, 33f, 91f);
        // minute hand: 135 forward, 32 tail, 14.7 -> 10.2 wide
        hand(c, p, minDeg, 7.35f, 5.1f, 32f, 135f);

        // second hand: red stick (5 wide, 46 tail, 137.5 forward) and 31-unit disc
        p.setColor(0xFFEB0000);
        p.setShadowLayer(3f * s, 1.5f * s, 1.5f * s, 0x66000000);
        c.save();
        c.rotate(secDeg);
        c.drawRect(-2.5f, -137.5f, 2.5f, 46f, p);
        c.drawCircle(0, -137.5f, 15.5f, p);
        c.restore();
        p.clearShadowLayer();
        // centre cap
        c.drawCircle(0, 0, 4.5f, p);
        return out;
    }

    private static void hand(Canvas c, Paint p, float deg, float baseHalf, float tipHalf, float tail, float fwd) {
        Path path = new Path();
        path.moveTo(-baseHalf, tail);
        path.lineTo(baseHalf, tail);
        path.lineTo(tipHalf, -fwd);
        path.lineTo(-tipHalf, -fwd);
        path.close();
        c.save();
        c.rotate(deg);
        c.drawPath(path, p);
        c.restore();
    }
}
