package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

/**
 * Draws the week-strip + next-event card of the KWGT "Calendar" preset. Same structure as the preset:
 * "MMM yyyy | Week n" header, seven day cells (Sunday to Saturday, today highlighted), a date tile, the event
 * title, its weekday, five paging dots and previous/next arrows.
 * Surfaces, borders and the amber highlight come from the shared SAB palette; the font stays Futura Medium BT.
 *
 * Sizing: text and tiles keep a FIXED size (in dp), so making the widget wider only gives the cells and the
 * title more room, and making it taller adds event rows. Wide-and-short widgets show one event with arrows,
 * taller ones show a list. The arrow tap zones are fixed-size at the right edge (see widget_calendar.xml).
 * The outermost corners use the launcher's own widget corner radius so they are never cut off.
 */
final class CalendarRenderer {
    private CalendarRenderer() {}

    static final float DESIGN_H = 238f;
    /** dp of each arrow tap zone and of the margin to the right edge; must match widget_calendar.xml. */
    static final float ZONE_DP = 42f, MARGIN_DP = 7f;

    /** One layout unit in pixels (about 0.66 dp). */
    static float unit(Context ctx) { return ctx.getResources().getDisplayMetrics().density * 0.66f; }

    /** Corner radius the launcher clips widgets with (system value on Android 12+). */
    static float outerRadius(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                return ctx.getResources().getDimension(android.R.dimen.system_app_widget_background_radius);
            } catch (Exception ignored) { }
        }
        return 16f * d;
    }

    /** True when the widget is short enough for the one-event card; taller widgets get an event list. */
    static boolean compact(Context ctx, int w, int h) {
        return h <= DESIGN_H * unit(ctx) * 1.3f;
    }

    static Bitmap render(Context ctx, int w, int h, List<CalendarData.Event> events, int idx, boolean allowed) {
        return compact(ctx, w, h) ? renderCard(ctx, w, h, events, idx, allowed) : renderList(ctx, w, h, events, allowed);
    }

    // ------------------------------------------------------------------ drawing helpers

    /** Filled rounded panel with per-corner radii and a thin slate border. */
    private static void panel(Canvas c, Paint p, RectF r, float top, float bottom, Shader fill, float u) {
        Path path = new Path();
        path.addRoundRect(r, new float[]{top, top, top, top, bottom, bottom, bottom, bottom}, Path.Direction.CW);
        p.setStyle(Paint.Style.FILL);
        p.setShader(fill);
        c.drawPath(path, p);
        p.setShader(null);
        float sw = Math.max(1f, 1.2f * u);
        RectF in = new RectF(r.left + sw / 2, r.top + sw / 2, r.right - sw / 2, r.bottom - sw / 2);
        Path bp = new Path();
        float t = Math.max(0, top - sw / 2), b = Math.max(0, bottom - sw / 2);
        bp.addRoundRect(in, new float[]{t, t, t, t, b, b, b, b}, Path.Direction.CW);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(sw);
        line.setColor(Theme.BORDER);
        c.drawPath(bp, line);
    }

    private static LinearGradient h(RectF r, int a, int b) {
        return new LinearGradient(r.left, 0, r.right, 0, a, b, Shader.TileMode.CLAMP);
    }

    private static LinearGradient v(RectF r, int a, int b) {
        return new LinearGradient(0, r.top, 0, r.bottom, a, b, Shader.TileMode.CLAMP);
    }

    // ------------------------------------------------------------------ shared: header + week strip

    private static void drawTop(Canvas c, Paint p, RectF top, float u, float rOut, Locale loc, LocalDate today) {
        float r = 14f * u;
        panel(c, p, top, Math.max(r, rOut), r, h(top, Theme.HEADER, Theme.PANEL), u);

        float th = top.height();
        p.setColor(Theme.TEXT);
        p.setTextSize(20f * u);
        p.setTextAlign(Paint.Align.LEFT);
        String head = today.format(DateTimeFormatter.ofPattern("MMM yyyy", loc)) + " | Week "
                + today.get(WeekFields.of(DayOfWeek.SUNDAY, 1).weekOfYear());
        c.drawText(head, top.left + Math.max(16f * u, rOut * 0.8f), top.top + th * 0.21f + 7f * u, p);

        float pad = 7f * u, gap = 4f * u;
        RectF strip = new RectF(top.left + pad, top.top + th * 0.40f, top.right - pad, top.bottom - pad);
        LocalDate sunday = today.minusDays(today.getDayOfWeek().getValue() % 7); // ISO: Sunday = 7 -> 0
        float cw = (strip.width() - 6 * gap) / 7f;
        DateTimeFormatter dayName = DateTimeFormatter.ofPattern("EEE", loc);
        for (int i = 0; i < 7; i++) {
            LocalDate d = sunday.plusDays(i);
            boolean isToday = d.equals(today);
            float x0 = strip.left + i * (cw + gap);
            RectF cell = new RectF(x0, strip.top, x0 + cw, strip.bottom);
            p.setShader(v(cell, isToday ? Theme.AMBER : Theme.SURFACE_HI, isToday ? Theme.AMBER_DEEP : Theme.SURFACE));
            c.drawRoundRect(cell, 9f * u, 9f * u, p);
            p.setShader(null);
            float cx = cell.centerX(), cy = cell.centerY();
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(isToday ? 0xFFFFFFFF : Theme.MUTED);
            p.setTextSize(15f * u);
            c.drawText(d.format(dayName), cx, cy - 5f * u, p);
            p.setColor(isToday ? 0xFFFFFFFF : Theme.TEXT);
            p.setTextSize(24f * u);
            c.drawText(String.format(loc, "%02d", d.getDayOfMonth()), cx, cy + 21f * u, p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private static void tile(Canvas c, Paint p, RectF t, float u) {
        panel(c, p, t, 9f * u, 9f * u, v(t, Theme.PANEL, Theme.DEEP), u);
    }

    // ------------------------------------------------------------------ card mode (short widgets)

    private static Bitmap renderCard(Context ctx, int w, int h, List<CalendarData.Event> events, int idx, boolean allowed) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float density = ctx.getResources().getDisplayMetrics().density;
        float u = Math.min(unit(ctx), h / DESIGN_H);
        float rOut = outerRadius(ctx);
        float r = 14f * u;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Theme.futura(ctx));
        LocalDate today = LocalDate.now();
        Locale loc = Locale.getDefault();

        drawTop(c, p, new RectF(0, 0, w, h * 0.50f), u, rOut, loc, today);

        RectF card = new RectF(0, h * 0.54f, w, h);
        panel(c, p, card, r, Math.max(r, rOut), h(card, Theme.SURFACE_HI, Theme.SURFACE), u);

        float ch = card.height();
        float tileSz = Math.min(ch * 0.76f, 90f * u);
        float tl = Math.max(14f * u, rOut * 0.6f);
        RectF tileR = new RectF(tl, card.centerY() - tileSz / 2f, tl + tileSz, card.centerY() + tileSz / 2f);
        tile(c, p, tileR, u);

        boolean has = allowed && !events.isEmpty();
        CalendarData.Event ev = has ? events.get(Math.max(0, Math.min(idx, events.size() - 1))) : null;
        float textX = tileR.right + 16f * u;
        float mid = card.centerY();

        // arrows: fixed-size zones at the right edge
        float zone = ZONE_DP * density, margin = MARGIN_DP * density;
        float prevC = w - margin - zone * 1.5f, nextC = w - margin - zone * 0.5f;
        float textMax = prevC - zone * 0.5f - 8f * u - textX;

        if (has) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Theme.TEXT);
            p.setTextSize(38f * u);
            c.drawText(String.format(loc, "%02d", ev.date.getDayOfMonth()), tileR.centerX(), tileR.centerY() + 3f * u, p);
            p.setColor(Theme.AMBER_LIGHT);
            p.setTextSize(18f * u);
            c.drawText(ev.date.format(DateTimeFormatter.ofPattern("MMM", loc)), tileR.centerX(), tileR.centerY() + 27f * u, p);
            p.setTextAlign(Paint.Align.LEFT);

            p.setColor(Theme.TEXT);
            p.setTextSize(20f * u);
            c.drawText(android.text.TextUtils.ellipsize(Util.capWords(ev.title), new android.text.TextPaint(p), textMax,
                    android.text.TextUtils.TruncateAt.END).toString(), textX, mid - 12f * u, p);
            p.setColor(Theme.TEXT_SOFT);
            p.setTextSize(15f * u);
            c.drawText(ev.date.format(DateTimeFormatter.ofPattern("EEEE", loc)), textX, mid + 9f * u, p);

            float dr = 3f * u, step = 19f * u;
            int cur = Math.max(0, Math.min(idx, events.size() - 1));
            for (int i = 0; i < events.size(); i++) {
                float dx = textX + dr + i * step, dy = mid + 31f * u;
                boolean on = i == cur;
                p.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
                p.setStrokeWidth(1.5f * u);
                p.setColor(on ? Theme.AMBER_LIGHT : Theme.MUTED);
                c.drawCircle(dx, dy, dr, p);
            }
            p.setStyle(Paint.Style.FILL);

            float d = Math.min(zone * 0.86f, ch * 0.42f);
            arrow(c, p, prevC, mid, d, true, u);
            arrow(c, p, nextC, mid, d, false, u);
        } else {
            emptyCard(c, p, tileR, textX, mid, u, today, loc, allowed);
        }
        return bmp;
    }

    private static void emptyCard(Canvas c, Paint p, RectF tileR, float textX, float mid, float u, LocalDate today,
                                  Locale loc, boolean allowed) {
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Theme.AMBER_LIGHT);
        p.setTextSize(32f * u);
        c.drawText(String.format(loc, "%02d", today.getDayOfMonth()), tileR.centerX(), tileR.centerY() + 2f * u, p);
        p.setColor(Theme.MUTED);
        p.setTextSize(17f * u);
        c.drawText(today.format(DateTimeFormatter.ofPattern("MMM", loc)), tileR.centerX(), tileR.centerY() + 25f * u, p);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(Theme.TEXT);
        p.setTextSize(20f * u);
        c.drawText(allowed ? "No upcoming events" : "Calendar access needed", textX, mid - 4f * u, p);
        p.setColor(Theme.MUTED);
        p.setTextSize(15f * u);
        c.drawText(allowed ? "Nothing in the next 120 days" : "Tap to open settings", textX, mid + 20f * u, p);
    }

    // ------------------------------------------------------------------ list mode (tall widgets)

    private static Bitmap renderList(Context ctx, int w, int h, List<CalendarData.Event> events, boolean allowed) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float u = unit(ctx);
        float rOut = outerRadius(ctx);
        float r = 14f * u;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Theme.futura(ctx));
        LocalDate today = LocalDate.now();
        Locale loc = Locale.getDefault();

        float topH = 118f * u;
        drawTop(c, p, new RectF(0, 0, w, topH), u, rOut, loc, today);

        float y = topH + 8f * u;
        float avail = h - y;
        boolean has = allowed && !events.isEmpty();
        if (!has) {
            float hh = Math.min(avail, 110f * u);
            RectF card = new RectF(0, y, w, y + hh);
            panel(c, p, card, r, card.bottom >= h - 1 ? Math.max(r, rOut) : r, h(card, Theme.SURFACE_HI, Theme.SURFACE), u);
            float tileSz = Math.min(hh * 0.76f, 90f * u);
            float tl = Math.max(14f * u, rOut * 0.6f);
            RectF tileR = new RectF(tl, card.centerY() - tileSz / 2f, tl + tileSz, card.centerY() + tileSz / 2f);
            tile(c, p, tileR, u);
            emptyCard(c, p, tileR, tileR.right + 16f * u, card.centerY(), u, today, loc, allowed);
            return bmp;
        }

        float gap = 7f * u;
        float rowH = 58f * u;
        int rows = (int) Math.max(1, Math.min(events.size(), Math.floor((avail + gap) / (rowH + gap))));
        float spare = (avail + gap) / rows - gap; // spread leftover height over the rows (up to 1.5x)
        rowH = Math.min(spare, rowH * 1.5f);
        for (int i = 0; i < rows; i++) {
            CalendarData.Event ev = events.get(i);
            RectF row = new RectF(0, y, w, y + rowH);
            boolean atBottom = row.bottom >= h - 1;
            panel(c, p, row, r, atBottom ? Math.max(r, rOut) : r, h(row, Theme.SURFACE_HI, Theme.SURFACE), u);

            float tileSz = rowH - 14f * u;
            float tl = Math.max(8f * u, atBottom ? rOut * 0.5f : 8f * u);
            RectF t = new RectF(tl, row.centerY() - tileSz / 2f, tl + tileSz, row.centerY() + tileSz / 2f);
            panel(c, p, t, 8f * u, 8f * u, v(t, Theme.PANEL, Theme.DEEP), u);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Theme.TEXT);
            p.setTextSize(Math.min(tileSz * 0.46f, 26f * u));
            c.drawText(String.format(loc, "%02d", ev.date.getDayOfMonth()), t.centerX(), t.centerY() + 1f * u, p);
            p.setColor(Theme.AMBER_LIGHT);
            p.setTextSize(Math.min(tileSz * 0.26f, 14f * u));
            c.drawText(ev.date.format(DateTimeFormatter.ofPattern("MMM", loc)), t.centerX(), t.bottom - 7f * u, p);
            p.setTextAlign(Paint.Align.LEFT);

            float tx = t.right + 14f * u;
            float maxW = w - tx - 14f * u;
            p.setColor(Theme.TEXT);
            p.setTextSize(19f * u);
            c.drawText(android.text.TextUtils.ellipsize(Util.capWords(ev.title), new android.text.TextPaint(p),
                    maxW, android.text.TextUtils.TruncateAt.END).toString(), tx, row.centerY() - 3f * u, p);
            p.setColor(Theme.TEXT_SOFT);
            p.setTextSize(14f * u);
            c.drawText(ev.date.format(DateTimeFormatter.ofPattern("EEEE", loc)), tx, row.centerY() + 16f * u, p);
            y += rowH + gap;
        }
        return bmp;
    }

    /** Circle with a chevron, like the preset's circle-chevron icons. */
    private static void arrow(Canvas c, Paint p, float cx, float cy, float d, boolean left, float u) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.AMBER);
        c.drawCircle(cx, cy, d / 2f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(d * 0.14f);
        p.setColor(Theme.DEEP);
        float k = d * 0.15f, dir = left ? -1f : 1f;
        Path chev = new Path();
        chev.moveTo(cx - dir * k * 0.6f, cy - k * 1.3f);
        chev.lineTo(cx + dir * k * 0.8f, cy);
        chev.lineTo(cx - dir * k * 0.6f, cy + k * 1.3f);
        c.drawPath(chev, p);
        p.setStyle(Paint.Style.FILL);
    }
}
