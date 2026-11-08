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
 * Draws the whole widget into one bitmap, in the SAB Monitor visual language (see {@link Theme}).
 * Layout units follow the original KWGT preset (750 units wide); the bitmap is rendered at S x that.
 */
final class Renderer {
    private Renderer() {}

    static final int W = 750, H = 730;
    static final float S = 1.4f;
    private static final float R = 16f; // card corner radius (SAB uses rounded-2xl)

    static Bitmap render(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(Math.round(W * S), Math.round(H * S), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(S, S);

        drawHeader(ctx, c);
        drawClocks(ctx, c);
        String to = Store.to(ctx).trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < 4; i++) drawRow(ctx, c, i, to);
        return bmp;
    }

    // ------------------------------------------------------------------ header (weather + sun)

    private static void drawHeader(Context ctx, Canvas c) {
        RectF r = new RectF(0, 0, W, 110);
        card(c, r, Theme.HEADER, Theme.DEEP, Theme.BORDER_SOFT);

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

        Typeface ui = Theme.uiMedium(ctx), mono = Theme.monoSemi(ctx), emoji = Typeface.DEFAULT;
        Seg sep = new Seg("  |  ", ui, Theme.DIM);
        // 💧 = droplet, 🌄 = sunrise, 🌇 = sunset
        segments(c, W / 2f, 32, 26,
                new Seg("Feels like ", ui, Theme.MUTED), new Seg(feels + "°C", mono, Theme.TEXT), sep,
                new Seg("💧 ", emoji, Theme.TEXT), new Seg(hum + "%", mono, Theme.TEXT), sep,
                new Seg("🌄 ", emoji, Theme.TEXT), new Seg(rise, mono, Theme.TEXT), sep,
                new Seg("🌇 ", emoji, Theme.TEXT), new Seg(set, mono, Theme.TEXT));
    }

    // ------------------------------------------------------------------ world clocks

    private static void drawClocks(Context ctx, Canvas c) {
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

            // day/night strip peeking out below the flag: amber = day, slate = night
            boolean day = SunCalc.isDay(lat, lon, z, ZonedDateTime.now(z));
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setShader(new LinearGradient(x, 0, x + colW, 0, day ? Theme.AMBER : Theme.NIGHT, Theme.HEADER,
                    Shader.TileMode.CLAMP));
            c.drawRect(x, y0 + 15, x + colW, y0 + 185, sp);

            RectF fr = new RectF(x, y0, x + colW, y0 + 169);
            Bitmap fb = Flags.flag(ctx, cc);
            if (fb != null) {
                drawCover(c, fb, fr, 0);
            } else {
                Paint p = new Paint();
                p.setColor(Theme.SURFACE_HI);
                c.drawRect(fr, p);
            }
        }

        // fade flags into graphite toward the bottom so the text stays readable
        Paint gp = new Paint();
        gp.setShader(new LinearGradient(0, y0, 0, y0 + 170, 0x66000000, 0xF20B0E15, Shader.TileMode.CLAMP));
        c.drawRect(0, y0, W, y0 + 170, gp);

        // clear everything OUTSIDE the rounded rectangle (inverse-filled path + CLEAR)
        Path outside = new Path();
        outside.addRoundRect(panel, R, R, Path.Direction.CW);
        outside.setFillType(Path.FillType.INVERSE_WINDING);
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        c.drawPath(outside, clear);
        c.restoreToCount(layer);

        // thin slate border, like SAB's cards
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setStyle(Paint.Style.STROKE);
        bp.setStrokeWidth(2);
        bp.setColor(Theme.BORDER);
        c.drawRoundRect(new RectF(panel.left + 1, panel.top + 1, panel.right - 1, panel.bottom - 1), R, R, bp);

        DateTimeFormatter dayF = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault());
        DateTimeFormatter timeF = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
        Typeface ui = Theme.uiMedium(ctx), uiB = Theme.uiSemi(ctx), mono = Theme.monoBold(ctx);
        for (int i = 0; i < 4; i++) {
            ZonedDateTime now = ZonedDateTime.now(zones[i]);
            float cx = i * colW + colW / 2f;
            text(c, now.format(dayF), cx, y0 + 45, 24, Paint.Align.CENTER, ui, Theme.TEXT_SOFT, true);
            text(c, now.format(timeF), cx, y0 + 83, 46, Paint.Align.CENTER, mono, Theme.TEXT, true);
            text(c, Util.capWords(Store.city(ctx, i)), cx, y0 + 121, 24, Paint.Align.CENTER, uiB, Theme.TEXT, true);
        }
    }

    // ------------------------------------------------------------------ currency rows

    private static void drawRow(Context ctx, Canvas c, int i, String to) {
        float y = 255 + i * 120f; // clock panel ends at y=245: same 10-unit gap as between the rows
        float cy = y + 55;
        boolean light = (i % 2 == 0);
        RectF r = new RectF(0, y, W, y + 110);
        card(c, r, light ? 0xFF181D28 : Theme.PANEL, light ? Theme.PANEL : Theme.DEEP, light ? Theme.BORDER : Theme.BORDER_SOFT);

        Typeface codeTf = Theme.uiMedium(ctx), labelTf = Theme.uiMedium(ctx), numTf = Theme.monoSemi(ctx); // regular weight, like the original
        String from = Store.from(ctx, i).trim().toUpperCase(Locale.ROOT);

        drawFlagOrLabel(ctx, c, Util.countryOfCurrency(from), new RectF(55, cy - 30, 125, cy + 30), codeTf);
        text(c, from, 140, cy, 34, Paint.Align.LEFT, codeTf, Theme.TEXT, false);

        // amber arrow (SAB's accent)
        Path arrow = new Path();
        arrow.moveTo(219, cy - 13);
        arrow.lineTo(219, cy + 13);
        arrow.lineTo(238, cy);
        arrow.close();
        Paint ap = new Paint(Paint.ANTI_ALIAS_FLAG);
        ap.setColor(Theme.AMBER);
        c.drawPath(arrow, ap);

        drawFlagOrLabel(ctx, c, Util.countryOfCurrency(to), new RectF(255, cy - 30, 325, cy + 30), codeTf);
        text(c, to, 340, cy, 34, Paint.Align.LEFT, codeTf, Theme.TEXT, false);

        // "Market" label (muted) + value (mono, white), right-aligned
        double market = Store.rate(ctx, from, to);
        String mv = Util.fmt0(market);
        Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
        mp.setTypeface(numTf);
        mp.setTextSize(32);
        float mw = mp.measureText(mv);
        text(c, mv, 730, y + 29, 32, Paint.Align.RIGHT, numTf, Theme.TEXT, false);
        text(c, "Market", 730 - mw - 12, y + 29, 26, Paint.Align.RIGHT, labelTf, Theme.MUTED, false);

        // Visa line: logo + rate (incl. configured fee)
        RectF logo = new RectF(560, y + 56, 620, y + 96);
        Bitmap vb = Flags.visa(ctx);
        if (vb != null) {
            drawCover(c, vb, logo, 6);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF1A3C9C);
            c.drawRoundRect(logo, 6, 6, p);
            text(c, "VISA", logo.centerX(), logo.centerY(), 18, Paint.Align.CENTER,
                    Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC), Theme.TEXT, false);
        }
        double visa = Util.alignToMarket(Store.visa(ctx, from, to), market);
        Typeface visaTf = Theme.monoMedium(ctx);
        if (!Double.isNaN(visa)) {
            text(c, Util.fmt0(visa), 730, y + 76, 28, Paint.Align.RIGHT, visaTf, Theme.TEXT_SOFT, false);
        } else if (!Double.isNaN(market)) {
            // Visa did not answer: estimate = market rate + the fee, marked with "~" and muted
            double est = market * (1.0 + Store.fee(ctx) / 100.0);
            text(c, "~" + Util.fmt0(est), 730, y + 76, 28, Paint.Align.RIGHT, visaTf, Theme.MUTED, false);
        }
    }

    private static void drawFlagOrLabel(Context ctx, Canvas c, String cc, RectF r, Typeface tf) {
        Bitmap b = Flags.flag(ctx, cc);
        if (b != null) {
            drawCover(c, b, r, 8);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Theme.SURFACE_HI);
            c.drawRoundRect(r, 8, 8, p);
            text(c, cc.toUpperCase(Locale.ROOT), r.centerX(), r.centerY(), 22, Paint.Align.CENTER, tf, Theme.MUTED, false);
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    /** Rounded graphite card: horizontal gradient + 1-unit slate border. */
    private static void card(Canvas c, RectF r, int left, int right, int border) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(r.left, 0, r.right, 0, left, right, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, R, R, p);
        Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
        b.setStyle(Paint.Style.STROKE);
        b.setStrokeWidth(2);
        b.setColor(border);
        c.drawRoundRect(new RectF(r.left + 1, r.top + 1, r.right - 1, r.bottom - 1), R, R, b);
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

    /** Draws text vertically centered on cy. Shadow only where text sits on top of photos (flags). */
    private static void text(Canvas c, String s, float x, float cy, float size, Paint.Align a, Typeface tf,
                             int color, boolean shadow) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setTextAlign(a);
        p.setColor(color);
        if (shadow) p.setShadowLayer(6, 0, 2, 0x99000000);
        float y = cy - (p.ascent() + p.descent()) / 2f;
        c.drawText(s, x, y, p);
    }

    private static final class Seg {
        final String s;
        final Typeface tf;
        final int color;

        Seg(String s, Typeface tf, int color) {
            this.s = s;
            this.tf = tf;
            this.color = color;
        }
    }

    /** One centered line made of differently styled pieces (muted label + white number, etc.). */
    private static void segments(Canvas c, float cx, float cy, float size, Seg... segs) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.LEFT);
        float[] w = new float[segs.length];
        float total = 0;
        for (int i = 0; i < segs.length; i++) {
            p.setTypeface(segs[i].tf);
            w[i] = p.measureText(segs[i].s);
            total += w[i];
        }
        p.setTypeface(segs[0].tf);
        float y = cy - (p.ascent() + p.descent()) / 2f;
        float x = cx - total / 2f;
        for (int i = 0; i < segs.length; i++) {
            p.setTypeface(segs[i].tf);
            p.setColor(segs[i].color);
            c.drawText(segs[i].s, x, y, p);
            x += w[i];
        }
    }
}
