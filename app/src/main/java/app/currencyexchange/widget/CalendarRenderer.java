package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

/**
 * Calendar widget layout (modelled on a modern agenda widget): one rounded panel with the big day number,
 * month + "Today, weekday" and a "+" button; the week row with today in an amber circle; a divider; then your
 * upcoming events, each with a colour bar, time range, an "in N minutes" hint and the title.
 * Colours, borders and the amber accent come from the shared SAB palette and the font stays Futura Medium BT.
 *
 * Sizing: text and rows keep a FIXED size, so a wider widget gives titles more room and a taller widget lists
 * more events. Every shape uses the same corner radius, and the whole picture is inset from the widget edge by
 * just enough that the launcher's own rounded clip can never cut a corner.
 */
final class CalendarRenderer {
    private CalendarRenderer() {}

    /** Height of the content in layout units when it shows header + week row + one event row. */
    private static final float NEED_UNITS = 177f;
    /** dp of the "+" tap zone; must match widget_calendar.xml. */
    static final float ADD_ZONE_DP = 56f;

    /** One layout unit in pixels (about 0.66 dp). */
    static float unit(Context ctx) { return ctx.getResources().getDisplayMetrics().density * 0.66f; }

    /** Radius the launcher clips widgets with (system value on Android 12+). */
    private static float clipRadius(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                return ctx.getResources().getDimension(android.R.dimen.system_app_widget_background_radius);
            } catch (Exception ignored) { }
        }
        return 16f * d;
    }

    static Bitmap render(Context ctx, int w, int h, List<CalendarData.Event> events, boolean allowed) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float u = Math.min(unit(ctx), h / NEED_UNITS);
        float r = 14f * u;
        // inset so a corner of radius r sits fully inside the launcher's clip circle of radius R
        float inset = Math.max(3f * ctx.getResources().getDisplayMetrics().density, 0.293f * Math.max(0, clipRadius(ctx) - r));
        RectF box = new RectF(inset, inset, w - inset, h - inset);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Theme.futura(ctx));
        Locale loc = Locale.getDefault();
        LocalDate today = LocalDate.now();

        // ---- panel
        p.setShader(new LinearGradient(0, box.top, 0, box.bottom, Theme.SURFACE, Theme.PANEL, Shader.TileMode.CLAMP));
        c.drawRoundRect(box, r, r, p);
        p.setShader(null);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, 1.2f * u));
        line.setColor(Theme.BORDER);
        float lw = line.getStrokeWidth() / 2f;
        c.drawRoundRect(new RectF(box.left + lw, box.top + lw, box.right - lw, box.bottom - lw), r, r, line);

        float px = 14f * u;                       // inner side padding
        float left = box.left + px, right = box.right - px;
        float y = box.top + 10f * u;

        // ---- header: big day number, month + "Today, weekday", plus button
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(Theme.TEXT);
        p.setTextSize(42f * u);
        String day = String.valueOf(today.getDayOfMonth());
        c.drawText(day, left, y + 36f * u, p);
        float dayW = p.measureText(day);
        float tx = left + dayW + 12f * u;
        p.setTextSize(17f * u);
        c.drawText(today.format(DateTimeFormatter.ofPattern("MMMM", loc)), tx, y + 18f * u, p);
        p.setColor(Theme.MUTED);
        p.setTextSize(13f * u);
        int week = today.get(WeekFields.of(DayOfWeek.SUNDAY, 1).weekOfYear());
        c.drawText("Today, " + today.format(DateTimeFormatter.ofPattern("EEEE", loc)) + "  \u00B7  Week " + week, tx, y + 36f * u, p);

        float bd = 28f * u; // plus button diameter
        float bcx = right - bd / 2f, bcy = y + 20f * u;
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setStyle(Paint.Style.STROKE);
        bp.setStrokeWidth(Math.max(1f, 1.2f * u));
        bp.setColor(Theme.BORDER);
        c.drawCircle(bcx, bcy, bd / 2f, bp);
        bp.setColor(Theme.TEXT_SOFT);
        bp.setStrokeWidth(1.8f * u);
        bp.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(bcx - 6f * u, bcy, bcx + 6f * u, bcy, bp);
        c.drawLine(bcx, bcy - 6f * u, bcx, bcy + 6f * u, bp);
        y = box.top + 10f * u + 46f * u + 8f * u;

        // ---- week row (Sunday first, as in the preset)
        LocalDate sunday = today.minusDays(today.getDayOfWeek().getValue() % 7);
        float colW = (right - left) / 7f;
        DateTimeFormatter dn = DateTimeFormatter.ofPattern("EEE", loc);
        for (int i = 0; i < 7; i++) {
            LocalDate d = sunday.plusDays(i);
            boolean isToday = d.equals(today);
            float cx = left + colW * (i + 0.5f);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(isToday ? Theme.AMBER_LIGHT : Theme.MUTED);
            p.setTextSize(11f * u);
            c.drawText(d.format(dn).toUpperCase(loc), cx, y + 11f * u, p);
            float cy = y + 12f * u + 6f * u + 13f * u;
            if (isToday) {
                RectF circle = new RectF(cx - 13f * u, cy - 13f * u, cx + 13f * u, cy + 13f * u);
                p.setShader(new LinearGradient(0, circle.top, 0, circle.bottom, Theme.AMBER, Theme.AMBER_DEEP, Shader.TileMode.CLAMP));
                c.drawOval(circle, p);
                p.setShader(null);
            }
            p.setColor(isToday ? 0xFFFFFFFF : Theme.TEXT);
            p.setTextSize(15f * u);
            c.drawText(String.valueOf(d.getDayOfMonth()), cx, cy + 5.5f * u, p);
        }
        y += 46f * u + 8f * u;

        // ---- divider
        Paint dv = new Paint();
        dv.setColor(Theme.BORDER);
        c.drawRect(left, y, right, y + Math.max(1f, u), dv);
        y += 8f * u;

        // ---- events
        p.setTextAlign(Paint.Align.LEFT);
        float rowH = 40f * u, gap = 6f * u;
        float bottom = box.bottom - 8f * u;
        if (!allowed || events.isEmpty()) {
            p.setColor(Theme.TEXT);
            p.setTextSize(16f * u);
            c.drawText(allowed ? "No upcoming events" : "Calendar access needed", left, y + 17f * u, p);
            p.setColor(Theme.MUTED);
            p.setTextSize(13f * u);
            c.drawText(allowed ? "Nothing in the next 120 days" : "Tap to open settings", left, y + 36f * u, p);
            return bmp;
        }
        int rows = (int) Math.max(0, Math.min(events.size(), Math.floor((bottom - y + gap) / (rowH + gap))));
        long now = System.currentTimeMillis();
        DateTimeFormatter tf = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(loc);
        DateTimeFormatter df = DateTimeFormatter.ofPattern("EEE d MMM", loc);
        ZoneId zone = ZoneId.systemDefault();
        for (int i = 0; i < rows; i++) {
            CalendarData.Event ev = events.get(i);
            int col = ev.color != 0 ? (0xFF000000 | ev.color) : Theme.AMBER;
            Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
            bar.setColor(col);
            c.drawRoundRect(new RectF(left, y + 3f * u, left + 3.5f * u, y + rowH - 3f * u), 2f * u, 2f * u, bar);
            float x = left + 12f * u;

            // time line
            String when;
            if (ev.allDay) when = "All day";
            else {
                ZonedDateTime b = Instant.ofEpochMilli(ev.begin).atZone(zone), e = Instant.ofEpochMilli(ev.end).atZone(zone);
                when = b.format(tf) + " \u2013 " + e.format(tf);
            }
            if (!ev.date.equals(today)) when = ev.date.format(df) + "  \u00B7  " + when;
            p.setColor(Theme.MUTED);
            p.setTextSize(12f * u);
            c.drawText(when, x, y + 14f * u, p);
            if (!ev.allDay) {
                long mins = (ev.begin - now + 59_999L) / 60_000L;
                String hint = null;
                if (ev.begin <= now) hint = "now";
                else if (mins <= 90) hint = mins <= 1 ? "in 1 minute" : "in " + mins + " minutes";
                if (hint != null) {
                    p.setColor(Theme.AMBER_LIGHT);
                    p.setTextSkewX(-0.2f);
                    c.drawText(hint, x + p.measureText(when) + 10f * u, y + 14f * u, p);
                    p.setTextSkewX(0f);
                }
            }
            // title
            p.setColor(Theme.TEXT);
            p.setTextSize(17f * u);
            String t = android.text.TextUtils.ellipsize(ev.title, new android.text.TextPaint(p), right - x,
                    android.text.TextUtils.TruncateAt.END).toString();
            c.drawText(t, x, y + 34f * u, p);
            y += rowH + gap;
        }
        if (events.size() > rows && rows > 0) {
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(Theme.DIM);
            p.setTextSize(11f * u);
            c.drawText("+" + (events.size() - rows) + " more", right, box.bottom - 4f * u, p);
        }
        return bmp;
    }
}
