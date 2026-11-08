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
 * title (16 characters, capitalised), its weekday, five paging dots and previous/next arrows.
 * Surfaces, borders and the amber highlight come from the shared SAB palette; the font stays Futura Medium BT.
 * Everything is laid out as fractions of the widget so it fills any size; the arrows sit exactly where the
 * transparent tap zones of widget_calendar.xml are (card = bottom 46%, prev 72-85%, next 85-98% of the width).
 */
final class CalendarRenderer {
    private CalendarRenderer() {}

    /** Design width in units; heights adapt to the widget. */
    static final float DESIGN_W = 500f, DESIGN_H = 238f;

    /** True when the widget is wide enough for the one-event card; taller widgets get an event list instead. */
    static boolean compact(int w, int h) {
        return h <= DESIGN_H * (w / DESIGN_W) * 1.3f;
    }

    static Bitmap render(Context ctx, int w, int h, List<CalendarData.Event> events, int idx, boolean allowed) {
        return compact(w, h) ? renderCard(ctx, w, h, events, idx, allowed) : renderList(ctx, w, h, events, allowed);
    }

    // ------------------------------------------------------------------ shared: header + week strip

    private static void drawTop(Canvas c, Paint p, RectF top, float u, Locale loc, LocalDate today) {
        float r = 14f * u;
        p.setShader(new LinearGradient(0, 0, top.right, 0, Theme.HEADER, Theme.PANEL, Shader.TileMode.CLAMP));
        c.drawRoundRect(top, r, r, p);
        p.setShader(null);
        border(c, top, r, u);

        float th = top.height();
        p.setColor(Theme.TEXT);
        p.setTextSize(20f * u);
        p.setTextAlign(Paint.Align.LEFT);
        String head = today.format(DateTimeFormatter.ofPattern("MMM yyyy", loc)) + " | Week "
                + today.get(WeekFields.of(DayOfWeek.SUNDAY, 1).weekOfYear());
        c.drawText(head, top.left + 16f * u, top.top + th * 0.21f + 7f * u, p);

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
            int a = isToday ? Theme.AMBER : Theme.SURFACE_HI;
            int b = isToday ? Theme.AMBER_DEEP : Theme.SURFACE;
            p.setShader(new LinearGradient(0, cell.top, 0, cell.bottom, a, b, Shader.TileMode.CLAMP));
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

    // ------------------------------------------------------------------ card mode (wide widgets)

    private static Bitmap renderCard(Context ctx, int w, int h, List<CalendarData.Event> events, int idx, boolean allowed) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float u = Math.min(w / DESIGN_W, h / DESIGN_H);
        float r = 14f * u;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Theme.futura(ctx));
        LocalDate today = LocalDate.now();
        Locale loc = Locale.getDefault();

        drawTop(c, p, new RectF(0, 0, w, h * 0.50f), u, loc, today);

        RectF card = new RectF(0, h * 0.54f, w, h);
        p.setShader(new LinearGradient(0, 0, w, 0, Theme.SURFACE_HI, Theme.SURFACE, Shader.TileMode.CLAMP));
        c.drawRoundRect(card, r, r, p);
        p.setShader(null);
        border(c, card, r, u);

        float ch = card.height();
        float tile = Math.min(ch * 0.76f, 90f * u);
        float tl = 14f * u;
        RectF tileR = new RectF(tl, card.centerY() - tile / 2f, tl + tile, card.centerY() + tile / 2f);
        p.setShader(new LinearGradient(0, tileR.top, 0, tileR.bottom, Theme.PANEL, Theme.DEEP, Shader.TileMode.CLAMP));
        c.drawRoundRect(tileR, 9f * u, 9f * u, p);
        p.setShader(null);
        border(c, tileR, 9f * u, u);

        boolean has = allowed && !events.isEmpty();
        CalendarData.Event ev = has ? events.get(Math.max(0, Math.min(idx, events.size() - 1))) : null;
        float textX = tileR.right + 18f * u;
        float mid = card.centerY();

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
            c.drawText(Util.ellipsize(Util.capWords(ev.title), 16), textX, mid - 12f * u, p);
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

            float d = Math.min(w * 0.08f, ch * 0.40f);
            arrow(c, p, w * 0.785f, mid, d, true, u);
            arrow(c, p, w * 0.915f, mid, d, false, u);
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
        float u = w / DESIGN_W;
        float r = 14f * u;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Theme.futura(ctx));
        LocalDate today = LocalDate.now();
        Locale loc = Locale.getDefault();

        float topH = 118f * u;
        drawTop(c, p, new RectF(0, 0, w, topH), u, loc, today);

        float y = topH + 8f * u;
        float avail = h - y;
        boolean has = allowed && !events.isEmpty();
        if (!has) {
            RectF card = new RectF(0, y, w, y + Math.min(avail, 110f * u));
            p.setShader(new LinearGradient(0, 0, w, 0, Theme.SURFACE_HI, Theme.SURFACE, Shader.TileMode.CLAMP));
            c.drawRoundRect(card, r, r, p);
            p.setShader(null);
            border(c, card, r, u);
            float tile = Math.min(card.height() * 0.76f, 90f * u);
            RectF tileR = new RectF(14f * u, card.centerY() - tile / 2f, 14f * u + tile, card.centerY() + tile / 2f);
            p.setShader(new LinearGradient(0, tileR.top, 0, tileR.bottom, Theme.PANEL, Theme.DEEP, Shader.TileMode.CLAMP));
            c.drawRoundRect(tileR, 9f * u, 9f * u, p);
            p.setShader(null);
            border(c, tileR, 9f * u, u);
            emptyCard(c, p, tileR, tileR.right + 18f * u, card.centerY(), u, today, loc, allowed);
            return bmp;
        }

        float gap = 7f * u;
        float rowH = 58f * u;
        int rows = (int) Math.max(1, Math.min(events.size(), Math.floor((avail + gap) / (rowH + gap))));
        // spread leftover height over the rows (up to 1.5x) so the widget never looks half empty
        float spare = (avail + gap) / rows - gap;
        rowH = Math.min(spare, rowH * 1.5f);
        for (int i = 0; i < rows; i++) {
            CalendarData.Event ev = events.get(i);
            RectF row = new RectF(0, y, w, y + rowH);
            p.setShader(new LinearGradient(0, 0, w, 0, Theme.SURFACE_HI, Theme.SURFACE, Shader.TileMode.CLAMP));
            c.drawRoundRect(row, r, r, p);
            p.setShader(null);
            border(c, row, r, u);

            float tile = rowH - 14f * u;
            RectF t = new RectF(8f * u, row.centerY() - tile / 2f, 8f * u + tile, row.centerY() + tile / 2f);
            p.setShader(new LinearGradient(0, t.top, 0, t.bottom, Theme.PANEL, Theme.DEEP, Shader.TileMode.CLAMP));
            c.drawRoundRect(t, 8f * u, 8f * u, p);
            p.setShader(null);
            border(c, t, 8f * u, u);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Theme.TEXT);
            p.setTextSize(Math.min(tile * 0.46f, 26f * u));
            c.drawText(String.format(loc, "%02d", ev.date.getDayOfMonth()), t.centerX(), t.centerY() + 1f * u, p);
            p.setColor(Theme.AMBER_LIGHT);
            p.setTextSize(Math.min(tile * 0.26f, 14f * u));
            c.drawText(ev.date.format(DateTimeFormatter.ofPattern("MMM", loc)), t.centerX(), t.bottom - 7f * u, p);
            p.setTextAlign(Paint.Align.LEFT);

            float tx = t.right + 14f * u;
            float maxW = w - tx - 14f * u;
            p.setColor(Theme.TEXT);
            p.setTextSize(19f * u);
            CharSequence title = android.text.TextUtils.ellipsize(Util.capWords(ev.title), new android.text.TextPaint(p),
                    maxW, android.text.TextUtils.TruncateAt.END);
            c.drawText(title.toString(), tx, row.centerY() - 3f * u, p);
            p.setColor(Theme.TEXT_SOFT);
            p.setTextSize(14f * u);
            c.drawText(ev.date.format(DateTimeFormatter.ofPattern("EEEE", loc)), tx, row.centerY() + 16f * u, p);
            y += rowH + gap;
        }
        return bmp;
    }

    private static void border(Canvas c, RectF rect, float radius, float u) {
        Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
        b.setStyle(Paint.Style.STROKE);
        b.setStrokeWidth(Math.max(1f, 1.2f * u));
        b.setColor(Theme.BORDER);
        RectF in = new RectF(rect.left + b.getStrokeWidth() / 2, rect.top + b.getStrokeWidth() / 2,
                rect.right - b.getStrokeWidth() / 2, rect.bottom - b.getStrokeWidth() / 2);
        c.drawRoundRect(in, radius, radius, b);
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
