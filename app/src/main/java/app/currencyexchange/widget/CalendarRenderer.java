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
 * transparent tap zones of widget_calendar.xml are (card = bottom 40%, prev 72-85%, next 85-98% of the width).
 */
final class CalendarRenderer {
    private CalendarRenderer() {}

    static Bitmap render(Context ctx, int w, int h, List<CalendarData.Event> events, int idx, boolean allowed) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Typeface font = Theme.futura(ctx);
        float u = Math.min(w / 500f, h / 310f);
        float r = 14f * u;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(font);

        LocalDate today = LocalDate.now();
        Locale loc = Locale.getDefault();

        // ---------------- top block: header + week strip
        RectF top = new RectF(0, 0, w, h * 0.55f);
        p.setShader(new LinearGradient(0, 0, w, 0, Theme.HEADER, Theme.PANEL, Shader.TileMode.CLAMP));
        c.drawRoundRect(top, r, r, p);
        p.setShader(null);
        border(c, top, r, u);

        p.setColor(Theme.TEXT);
        p.setTextSize(21f * u);
        String head = today.format(DateTimeFormatter.ofPattern("MMM yyyy", loc)) + " | Week "
                + today.get(WeekFields.of(DayOfWeek.SUNDAY, 1).weekOfYear());
        c.drawText(head, w * 0.05f, h * 0.115f + 7f * u, p);

        float pad = w * 0.012f;
        RectF strip = new RectF(pad, h * 0.17f, w - pad, h * 0.55f - pad);
        Path clip = new Path();
        clip.addRoundRect(strip, 10f * u, 10f * u, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        LocalDate sunday = today.minusDays(today.getDayOfWeek().getValue() % 7); // ISO: Sunday = 7 -> 0
        float cw = strip.width() / 7f;
        DateTimeFormatter dayName = DateTimeFormatter.ofPattern("EEE", loc);
        for (int i = 0; i < 7; i++) {
            LocalDate d = sunday.plusDays(i);
            boolean isToday = d.equals(today);
            RectF cell = new RectF(strip.left + i * cw, strip.top, strip.left + (i + 1) * cw + 1, strip.bottom);
            int base = isToday ? Theme.AMBER : (i % 2 == 0 ? Theme.SURFACE : Theme.SURFACE_HI);
            int end = isToday ? Theme.AMBER_DEEP : (i % 2 == 0 ? Theme.PANEL : Theme.SURFACE);
            p.setShader(new LinearGradient(0, cell.top, 0, cell.bottom, base, end, Shader.TileMode.CLAMP));
            c.drawRect(cell, p);
            p.setShader(null);
            float cx = cell.centerX();
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(isToday ? 0xFFFFFFFF : Theme.TEXT_SOFT);
            p.setTextSize(18f * u);
            c.drawText(d.format(dayName), cx, cell.centerY() - 5f * u, p);
            p.setColor(isToday ? 0xFFFFFFFF : Theme.TEXT);
            p.setTextSize(27f * u);
            c.drawText(String.format(loc, "%02d", d.getDayOfMonth()), cx, cell.centerY() + 26f * u, p);
        }
        p.setTextAlign(Paint.Align.LEFT);
        c.restore();

        // ---------------- event card
        RectF card = new RectF(0, h * 0.60f, w, h);
        p.setShader(new LinearGradient(0, 0, w, 0, Theme.SURFACE_HI, Theme.SURFACE, Shader.TileMode.CLAMP));
        c.drawRoundRect(card, r, r, p);
        p.setShader(null);
        border(c, card, r, u);

        float ch = card.height();
        float tile = Math.min(ch * 0.74f, w * 0.19f);
        RectF tileR = new RectF(w * 0.033f, card.centerY() - tile / 2f, w * 0.033f + tile, card.centerY() + tile / 2f);
        p.setShader(new LinearGradient(0, tileR.top, 0, tileR.bottom, Theme.PANEL, Theme.DEEP, Shader.TileMode.CLAMP));
        c.drawRoundRect(tileR, 8f * u, 8f * u, p);
        p.setShader(null);
        border(c, tileR, 8f * u, u);

        boolean has = allowed && !events.isEmpty();
        CalendarData.Event ev = has ? events.get(Math.max(0, Math.min(idx, events.size() - 1))) : null;
        float textX = tileR.right + w * 0.065f;

        if (has) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Theme.TEXT);
            p.setTextSize(45f * u);
            c.drawText(String.format(loc, "%02d", ev.date.getDayOfMonth()), tileR.centerX(), tileR.centerY() + 6f * u, p);
            p.setColor(Theme.AMBER_LIGHT);
            p.setTextSize(21f * u);
            c.drawText(ev.date.format(DateTimeFormatter.ofPattern("MMM", loc)), tileR.centerX(), tileR.centerY() + 34f * u, p);
            p.setTextAlign(Paint.Align.LEFT);

            p.setColor(Theme.TEXT);
            p.setTextSize(21f * u);
            c.drawText(Util.ellipsize(Util.capWords(ev.title), 16), textX, card.centerY() - 16f * u, p);
            p.setColor(Theme.TEXT_SOFT);
            p.setTextSize(17f * u);
            c.drawText(ev.date.format(DateTimeFormatter.ofPattern("EEEE", loc)), textX, card.centerY() + 9f * u, p);

            // paging dots
            float dr = 3.2f * u, gap = 22f * u;
            for (int i = 0; i < events.size(); i++) {
                float dx = textX + dr + i * gap, dy = card.centerY() + 38f * u;
                boolean on = i == Math.max(0, Math.min(idx, events.size() - 1));
                p.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
                p.setStrokeWidth(1.6f * u);
                p.setColor(on ? Theme.AMBER_LIGHT : Theme.MUTED);
                c.drawCircle(dx, dy, dr, p);
            }
            p.setStyle(Paint.Style.FILL);

            // arrows
            float d = Math.min(w * 0.08f, ch * 0.40f);
            arrow(c, p, w * 0.785f, card.centerY(), d, true, u);
            arrow(c, p, w * 0.915f, card.centerY(), d, false, u);
        } else {
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Theme.AMBER_LIGHT);
            p.setTextSize(30f * u);
            c.drawText(String.format(loc, "%02d", today.getDayOfMonth()), tileR.centerX(), tileR.centerY() + 2f * u, p);
            p.setColor(Theme.MUTED);
            p.setTextSize(19f * u);
            c.drawText(today.format(DateTimeFormatter.ofPattern("MMM", loc)), tileR.centerX(), tileR.centerY() + 28f * u, p);
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(Theme.TEXT);
            p.setTextSize(21f * u);
            c.drawText(allowed ? "No upcoming events" : "Calendar access needed", textX, card.centerY() - 8f * u, p);
            p.setColor(Theme.MUTED);
            p.setTextSize(16f * u);
            c.drawText(allowed ? "Nothing in the next 120 days" : "Tap to open settings", textX, card.centerY() + 18f * u, p);
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
