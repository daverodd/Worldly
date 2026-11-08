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
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Currency widget as native views (res/layout/widget.xml). Rows, columns and text boxes are weighted, so the
 * widget follows any width and height the launcher gives it. Only the flags, the Visa logo and the clock
 * backgrounds are small bitmaps (rounded corners baked in).
 */
final class CurrencyView {
    private CurrencyView() {}

    private static int id(Context c, String name) {
        return c.getResources().getIdentifier(name, "id", c.getPackageName());
    }

    /** Text as a picture at a fixed size (sizeDp = font size); the ImageView height in the layout is sizeDp * 1.46. */
    private static void txt(Context ctx, RemoteViews rv, int viewId, String s, Typeface tf, int color, float sizeDp, boolean shadow) {
        rv.setImageViewBitmap(viewId, TextBmp.make(tf, sizeDp * 2.5f, 0.8f, false, shadow, s, color));
    }

    static RemoteViews build(Context ctx) {
        Typeface reg = Theme.uiMedium(ctx), bold = Theme.uiBold(ctx);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);

        // header: feels like, humidity, sunrise, sunset
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
        rv.setImageViewBitmap(R.id.hdr_feels, TextBmp.make(reg, 12 * 2.5f, 0.8f, false, false,
                new String[]{"Feels like ", feels + "\u00B0C"}, new int[]{Theme.MUTED, Theme.TEXT}));
        txt(ctx, rv, R.id.hdr_hum, hum + "%", reg, Theme.TEXT, 12, false);
        txt(ctx, rv, R.id.hdr_rise, rise, reg, Theme.TEXT, 12, false);
        txt(ctx, rv, R.id.hdr_set, set, reg, Theme.TEXT, 12, false);

        // world clocks
        DateTimeFormatter dayF = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault());
        DateTimeFormatter timeF = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
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
            ZonedDateTime now = ZonedDateTime.now(z);
            boolean day = SunCalc.isDay(lat, lon, z, now);
            rv.setImageViewBitmap(id(ctx, "clk_bg_" + i), clockBg(Flags.flag(ctx, cc), day, i == 0, i == 3));
            txt(ctx, rv, id(ctx, "clk_day_" + i), now.format(dayF), reg, Theme.TEXT_SOFT, 11, true);
            txt(ctx, rv, id(ctx, "clk_time_" + i), now.format(timeF), bold, Theme.TEXT, 22, true);
            txt(ctx, rv, id(ctx, "clk_city_" + i), Util.capWords(city), reg, Theme.TEXT, 12, true);
        }

        // currency rows
        String to = Store.to(ctx).trim().toUpperCase(Locale.ROOT);
        Bitmap visaLogo = visaLogo(ctx);
        Bitmap toFlag = flagOrLabel(ctx, Util.countryOfCurrency(to), to);
        for (int i = 0; i < Store.DEF_FROM.length; i++) {
            String from = Store.from(ctx, i).trim().toUpperCase(Locale.ROOT);
            rv.setImageViewBitmap(id(ctx, "fl_a_" + i), flagOrLabel(ctx, Util.countryOfCurrency(from), from));
            txt(ctx, rv, id(ctx, "code_a_" + i), from, reg, Theme.TEXT, 15, false);
            rv.setImageViewBitmap(id(ctx, "fl_b_" + i), toFlag);
            txt(ctx, rv, id(ctx, "code_b_" + i), to, reg, Theme.TEXT, 15, false);
            double market = Store.rate(ctx, from, to);
            txt(ctx, rv, id(ctx, "mkt_lbl_" + i), "Market", reg, Theme.MUTED, 10, false);
            txt(ctx, rv, id(ctx, "mkt_val_" + i), Util.fmt0(market), reg, Theme.TEXT, 14, false);
            rv.setImageViewBitmap(id(ctx, "visa_logo_" + i), visaLogo);
            double visa = Util.alignToMarket(Store.visa(ctx, from, to), market);
            int vv = id(ctx, "visa_val_" + i);
            if (!Double.isNaN(visa)) {
                txt(ctx, rv, vv, Util.fmt0(visa), reg, Theme.TEXT_SOFT, 12, false);
            } else if (!Double.isNaN(market)) {
                // Visa did not answer: estimate = market + fee, marked "~" and muted
                txt(ctx, rv, vv, "~" + Util.fmt0(market * (1.0 + Store.fee(ctx) / 100.0)), reg, Theme.MUTED, 12, false);
            } else {
                txt(ctx, rv, vv, "", reg, Theme.MUTED, 12, false);
            }
        }
        return rv;
    }

    // ------------------------------------------------------------------ bitmaps

    /** Flag with rounded corners, or a grey tile with the code when the flag isn't downloaded yet. */
    private static Bitmap flagOrLabel(Context ctx, String cc, String code) {
        Bitmap fl = Flags.flag(ctx, cc);
        Bitmap out = Bitmap.createBitmap(140, 120, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        RectF r = new RectF(0, 0, 140, 120);
        if (fl != null) {
            cover(c, fl, r, 16);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Theme.SURFACE_HI);
            c.drawRoundRect(r, 16, 16, p);
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(Theme.MUTED);
            t.setTextSize(40);
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText(cc == null ? "" : cc.toUpperCase(Locale.ROOT), 70, 60 - (t.ascent() + t.descent()) / 2f, t);
        }
        return out;
    }

    private static Bitmap visaLogo(Context ctx) {
        Bitmap vb = Flags.visa(ctx);
        Bitmap out = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        RectF r = new RectF(0, 0, 120, 80);
        if (vb != null) {
            cover(c, vb, r, 12);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF1A3C9C);
            c.drawRoundRect(r, 12, 12, p);
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(Theme.TEXT);
            t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC));
            t.setTextSize(30);
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText("VISA", 60, 40 - (t.ascent() + t.descent()) / 2f, t);
        }
        return out;
    }

    /** Clock cell background: flag faded into graphite, day (amber) / night (slate) strip below, outer corners rounded. */
    private static Bitmap clockBg(Bitmap flag, boolean day, boolean roundLeft, boolean roundRight) {
        final int w = 200, h = 240;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        int layer = c.saveLayer(0, 0, w, h, null);
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setShader(new LinearGradient(0, 0, w, 0, day ? Theme.AMBER : Theme.NIGHT, Theme.HEADER, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, sp);
        RectF fr = new RectF(0, 0, w, h * 0.92f);
        if (flag != null) {
            cover(c, flag, fr, 0);
        } else {
            Paint p = new Paint();
            p.setColor(Theme.SURFACE_HI);
            c.drawRect(fr, p);
        }
        Paint gp = new Paint();
        gp.setShader(new LinearGradient(0, 0, 0, fr.bottom, 0x66000000, 0xF20B0E15, Shader.TileMode.CLAMP));
        c.drawRect(fr, gp);

        float rx = 62f, ry = 58f;
        Path outside = new Path();
        outside.addRoundRect(new RectF(0, 0, w, h),
                new float[]{roundLeft ? rx : 0, roundLeft ? ry : 0, roundRight ? rx : 0, roundRight ? ry : 0,
                        roundRight ? rx : 0, roundRight ? ry : 0, roundLeft ? rx : 0, roundLeft ? ry : 0},
                Path.Direction.CW);
        outside.setFillType(Path.FillType.INVERSE_WINDING);
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        c.drawPath(outside, clear);
        c.restoreToCount(layer);
        return out;
    }

    /** Draws bmp scaled to cover dst (centre-crop) with anti-aliased rounded corners. */
    private static void cover(Canvas c, Bitmap bmp, RectF dst, float rad) {
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
}
