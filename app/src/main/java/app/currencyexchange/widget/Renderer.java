package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.json.JSONObject;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Draws the whole widget into one bitmap. Layout units follow the original KWGT preset
 * (750 units wide); the bitmap is rendered at S x that for sharpness.
 */
final class Renderer {
    private Renderer() {}

    static final int W = 750, H = 730;
    static final float S = 1.4f;

    private static final int WHITE = 0xFFFFFFFF;

    static Bitmap render(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(Math.round(W * S), Math.round(H * S), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(S, S);
        Typeface reg = font(ctx, "fonts/carbonplus.otf", Typeface.NORMAL);
        Typeface bold = font(ctx, "fonts/carbonplus-Bold.otf", Typeface.BOLD);

        drawHeader(ctx, c, reg);
        drawClocks(ctx, c, reg, bold);
        String to = Store.to(ctx).trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < 4; i++) drawRow(ctx, c, reg, i, to);
        return bmp;
    }

    // ------------------------------------------------------------------ header (weather + sun)

    private static void drawHeader(Context ctx, Canvas c, Typeface reg) {
        fillRound(c, new RectF(0, 0, W, 110), 15, 0xFF0F1B2B, 0xFF03070D);

        double[] wx = Store.weather(ctx);
        String feels = wx == null ? "--" : String.valueOf(Math.round(wx[0]));
        String hum = wx == null ? "--" : String.valueOf(Math.round(wx[1]));

        double[] ll = Store.weatherLatLon(ctx);
        String rise = "--:--", set = "--:--";
        if (!Double.isNaN(ll[0]) && !Double.isNaN(ll[1])) {
            int[] t = SunCalc.times(ll[0], ll[1], ZonedDateTime.now().toLocalDate(), ZoneId.systemDefault());
            rise = Util.hm(t[0]);
            set = Util.hm(t[1]);
        }
        // 💧 = droplet, 🌄 = sunrise, 🌇 = sunset
        String s = "Feels like " + feels + "°C | 💧" + hum + "% | 🌄 " + rise
                + " | 🌇 " + set;
        text(c, s, W / 2f, 29, 30, Paint.Align.CENTER, reg, WHITE);
    }

    // ------------------------------------------------------------------ world clocks

    private static void drawClocks(Context ctx, Canvas c, Typeface reg, Typeface bold) {
        final float y0 = 60, colW = W / 4f;
        RectF panel = new RectF(0, y0, W, y0 + 185);
        int layer = c.saveLayer(panel, null);

        ZoneId[] zones = new ZoneId[4];
        for (int i = 0; i < 4; i++) {
            String city = Store.city(ctx, i);
            JSONObject g = Store.geo(ctx, city);
            ZoneId z = ZoneId.systemDefault();
            double lat = Double.NaN, lon = Double.NaN;
            String cc = "";
            if (g != null) {
                try { z = ZoneId.of(g.optString("tz", z.getId())); } catch (Exception ignored) { }
                lat = g.optDouble("lat", Double.NaN);
                lon = g.optDouble("lon", Double.NaN);
                cc = g.optString("cc", "");
            }
            zones[i] = z;
            float x = i * colW;

            // day/night strip (yellow = day, navy = night), peeking out below the flag
            boolean day = SunCalc.isDay(lat, lon, z, ZonedDateTime.now(z));
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setShader(new LinearGradient(x, 0, x + colW, 0, day ? 0xFFFFCD00 : 0xFF002260, 0xFF2E2E2E,
                    Shader.TileMode.CLAMP));
            c.drawRect(x, y0 + 15, x + colW, y0 + 185, sp);

            RectF fr = new RectF(x, y0, x + colW, y0 + 169);
            Bitmap fb = Flags.flag(ctx, cc);
            if (fb != null) {
                drawCover(c, fb, fr, 0);
            } else {
                Paint p = new Paint();
                p.setColor(0xFF3A3F45);
                c.drawRect(fr, p);
            }
        }

        // fade flags to dark toward the bottom so the white text stays readable
        Paint gp = new Paint();
        gp.setShader(new LinearGradient(0, y0 + 170 * 0.24f, 0, y0 + 170, 0x002E2E2E, 0xE62E2E2E,
                Shader.TileMode.CLAMP));
        c.drawRect(0, y0, W, y0 + 170, gp);

        // round the outer corners of the whole panel
        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawRoundRect(panel, 15, 15, mask);
        c.restoreToCount(layer);

        DateTimeFormatter dayF = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault());
        DateTimeFormatter timeF = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
        for (int i = 0; i < 4; i++) {
            ZonedDateTime now = ZonedDateTime.now(zones[i]);
            float cx = i * colW + colW / 2f;
            text(c, now.format(dayF), cx, y0 + 45, 25, Paint.Align.CENTER, reg, WHITE);
            text(c, now.format(timeF), cx, y0 + 83, 50, Paint.Align.CENTER, bold, WHITE);
            text(c, Util.capWords(Store.city(ctx, i)), cx, y0 + 121, 25, Paint.Align.CENTER, reg, WHITE);
        }
    }

    // ------------------------------------------------------------------ currency rows

    private static void drawRow(Context ctx, Canvas c, Typeface reg, int i, String to) {
        float y = 250 + i * 120f;
        float cy = y + 55;
        boolean light = (i % 2 == 0);
        fillRound(c, new RectF(0, y, W, y + 110), 15,
                light ? 0xFF404444 : 0xFF0F1B2B, light ? 0xFF2E2E2E : 0xFF03070D);

        String from = Store.from(ctx, i).trim().toUpperCase(Locale.ROOT);

        drawFlagOrLabel(ctx, c, reg, Util.countryOfCurrency(from), new RectF(55, cy - 30, 125, cy + 30));
        text(c, from, 140, cy, 35, Paint.Align.LEFT, reg, WHITE);

        Path arrow = new Path();
        arrow.moveTo(219, cy - 13);
        arrow.lineTo(219, cy + 13);
        arrow.lineTo(238, cy);
        arrow.close();
        Paint ap = new Paint(Paint.ANTI_ALIAS_FLAG);
        ap.setColor(WHITE);
        c.drawPath(arrow, ap);

        drawFlagOrLabel(ctx, c, reg, Util.countryOfCurrency(to), new RectF(255, cy - 30, 325, cy + 30));
        text(c, to, 340, cy, 35, Paint.Align.LEFT, reg, WHITE);

        double market = Store.rate(ctx, from, to);
        text(c, "Market: " + Util.fmt0(market), 730, y + 29, 32, Paint.Align.RIGHT, reg, WHITE);

        // Visa line: logo + rate (incl. configured fee)
        RectF logo = new RectF(560, y + 56, 620, y + 96);
        Bitmap vb = Flags.visa(ctx);
        if (vb != null) {
            drawCover(c, vb, logo, 5);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF1A3C9C);
            c.drawRoundRect(logo, 5, 5, p);
            text(c, "VISA", logo.centerX(), logo.centerY(), 18, Paint.Align.CENTER,
                    Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC), WHITE);
        }
        double visa = Util.alignToMarket(Store.visa(ctx, from, to), market);
        if (!Double.isNaN(visa)) {
            text(c, Util.fmt0(visa), 730, y + 76, 28, Paint.Align.RIGHT, reg, 0xFFE6E6E6);
        }
    }

    private static void drawFlagOrLabel(Context ctx, Canvas c, Typeface reg, String cc, RectF r) {
        Bitmap b = Flags.flag(ctx, cc);
        if (b != null) {
            drawCover(c, b, r, 5);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF555B63);
            c.drawRoundRect(r, 5, 5, p);
            text(c, cc.toUpperCase(Locale.ROOT), r.centerX(), r.centerY(), 22, Paint.Align.CENTER, reg, WHITE);
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    private static void fillRound(Canvas c, RectF r, float rad, int left, int right) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(r.left, 0, r.right, 0, left, right, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
    }

    /** Draws bmp scaled to cover dst (center-crop) with anti-aliased rounded corners. */
    private static void drawCover(Canvas c, Bitmap bmp, RectF dst, float rad) {
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        float sc = Math.max(dst.width() / bw, dst.height() / bh);
        Matrix m = new Matrix();
        m.setScale(sc, sc);
        m.postTranslate(dst.left + (dst.width() - bw * sc) / 2f, dst.top + (dst.height() - bh * sc) / 2f);
        BitmapShader sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        sh.setLocalMatrix(m);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(sh);
        if (rad > 0) c.drawRoundRect(dst, rad, rad, p);
        else c.drawRect(dst, p);
    }

    /** Draws text vertically centered on cy. */
    private static void text(Canvas c, String s, float x, float cy, float size, Paint.Align a, Typeface tf, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setTextAlign(a);
        p.setColor(color);
        p.setShadowLayer(6, 0, 2, 0x99000000);
        float y = cy - (p.ascent() + p.descent()) / 2f;
        c.drawText(s, x, y, p);
    }

    private static Typeface font(Context ctx, String asset, int fallbackStyle) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), asset);
        } catch (Exception e) {
            return Typeface.create(Typeface.SANS_SERIF, fallbackStyle);
        }
    }
}
