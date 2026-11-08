package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.LruCache;

/**
 * Text drawn as a small transparent picture. Launchers draw RemoteViews text with their own fonts, so this is the
 * only way to guarantee the original fonts (Carbon+). Every picture has the same line height for a given size, so
 * text from different strings scales consistently when an ImageView shrinks it (scaleType fit*).
 * `fill` = text line height / picture height; the rest is empty margin that keeps text from touching the box.
 */
final class TextBmp {
    private TextBmp() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(300);

    static Bitmap make(Typeface tf, float px, float fill, boolean italic, boolean shadow, String[] parts, int[] colors) {
        StringBuilder k = new StringBuilder();
        k.append(System.identityHashCode(tf)).append('|').append(px).append('|').append(fill).append('|')
                .append(italic).append(shadow);
        for (int i = 0; i < parts.length; i++) k.append('|').append(colors[i]).append(':').append(parts[i]);
        String key = k.toString();
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(px);
        if (italic) p.setTextSkewX(-0.2f);
        Paint.FontMetrics fm = p.getFontMetrics();
        float line = fm.descent - fm.ascent;
        int h = Math.max(2, Math.round(line / fill));
        float total = 0;
        float[] w = new float[parts.length];
        for (int i = 0; i < parts.length; i++) { w[i] = p.measureText(parts[i]); total += w[i]; }
        float pad = shadow ? px * 0.15f : px * 0.08f;
        int bw = Math.max(2, (int) Math.ceil(total + 2 * pad));
        Bitmap b = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float base = (h - line) / 2f - fm.ascent;
        float x = pad;
        for (int i = 0; i < parts.length; i++) {
            p.setColor(colors[i]);
            if (shadow) p.setShadowLayer(px * 0.08f, 0, px * 0.04f, 0x99000000);
            c.drawText(parts[i], x, base, p);
            x += w[i];
        }
        CACHE.put(key, b);
        return b;
    }

    static Bitmap make(Typeface tf, float px, float fill, boolean italic, boolean shadow, String s, int color) {
        return make(tf, px, fill, italic, shadow, new String[]{s == null ? "" : s}, new int[]{color});
    }
}
