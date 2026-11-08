package app.currencyexchange.widget;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Offline sunrise/sunset (NOAA / "sunrise equation"). Replaces the Sun Surveyor app and the
 * sunrisesunset.io calls the original KWGT preset used. Accuracy is about +-2 minutes.
 */
final class SunCalc {
    private SunCalc() {}

    /**
     * @return {sunriseMinute, sunsetMinute} as local minutes-of-day in {@code zone}.
     * Polar day returns {0, 1440}; polar night returns {0, 0}.
     */
    static int[] times(double lat, double lon, LocalDate date, ZoneId zone) {
        double jd = date.toEpochDay() + 2440587.5 + 0.5; // noon UTC of that date
        double n = Math.ceil(jd - 2451545.0 + 0.0008);
        double jStar = n - lon / 360.0; // east-positive longitude: eastern places transit earlier (UTC)
        double m = norm360(357.5291 + 0.98560028 * jStar);
        double mr = Math.toRadians(m);
        double c = 1.9148 * Math.sin(mr) + 0.0200 * Math.sin(2 * mr) + 0.0003 * Math.sin(3 * mr);
        double lam = norm360(m + c + 180.0 + 102.9372);
        double lr = Math.toRadians(lam);
        double jTransit = 2451545.0 + jStar + 0.0053 * Math.sin(mr) - 0.0069 * Math.sin(2 * lr);
        double sinD = Math.sin(lr) * Math.sin(Math.toRadians(23.4397));
        double cosD = Math.sqrt(1.0 - sinD * sinD);
        double latR = Math.toRadians(lat);
        double cosW = (Math.sin(Math.toRadians(-0.833)) - Math.sin(latR) * sinD) / (Math.cos(latR) * cosD);
        if (cosW < -1) return new int[]{0, 1440};
        if (cosW > 1) return new int[]{0, 0};
        double w0 = Math.toDegrees(Math.acos(cosW));
        double jRise = jTransit - w0 / 360.0;
        double jSet = jTransit + w0 / 360.0;
        return new int[]{minuteOfDay(jRise, zone), minuteOfDay(jSet, zone)};
    }

    /** True if it is currently daytime at the given place. */
    static boolean isDay(double lat, double lon, ZoneId zone, ZonedDateTime now) {
        int m = now.getHour() * 60 + now.getMinute();
        if (Double.isNaN(lat) || Double.isNaN(lon)) return m >= 6 * 60 && m < 18 * 60;
        int[] t = times(lat, lon, now.toLocalDate(), zone);
        if (t[0] <= t[1]) return m >= t[0] && m < t[1];
        return m >= t[0] || m < t[1]; // wraps past midnight (extreme longitudes)
    }

    private static int minuteOfDay(double julian, ZoneId zone) {
        long ms = Math.round((julian - 2440587.5) * 86400000.0);
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(zone);
        return z.getHour() * 60 + z.getMinute();
    }

    private static double norm360(double d) {
        double r = d % 360.0;
        return r < 0 ? r + 360.0 : r;
    }
}
