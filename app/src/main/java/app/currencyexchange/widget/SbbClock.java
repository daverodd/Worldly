package app.currencyexchange.widget;

/**
 * Motion model of the Swiss railway (SBB) station clock. Pure maths, no Android classes.
 *
 * How the real clock behaves (Hilfiker design, master-clock system):
 *  - the red second hand is driven by its own motor and runs a little FAST: one lap in about 58.5 s;
 *  - it then stops at 12 and waits for the next minute impulse from the master clock (about 1.5 s);
 *  - on that impulse the minute hand advances one whole minute (a step, not a glide) and the second
 *    hand starts its next lap, so both happen on the same instant, at :00;
 *  - the hour hand is geared to the minute hand, so it advances by the same one-minute steps.
 */
final class SbbClock {
    private SbbClock() {}

    /** Seconds the second hand needs for one full lap. */
    static final double LAP_SECONDS = 58.5;

    /** Second hand angle in degrees clockwise from 12, for a time {@code sec} (0 <= sec < 60) into the minute. */
    static double secondAngle(double sec) {
        if (sec <= 0) return 0;
        if (sec >= LAP_SECONDS) return 0; // parked at 12, waiting for the minute impulse
        return 360.0 * sec / LAP_SECONDS;
    }

    /** Minute hand: jumps to the new minute at the impulse, never in between. */
    static double minuteAngle(int minute) {
        return (minute % 60) * 6.0;
    }

    /** Hour hand: moves with the minute steps, 0.5 degrees per minute. */
    static double hourAngle(int hour, int minute) {
        return (hour % 12) * 30.0 + (minute % 60) * 0.5;
    }

    /** True while the second hand is parked at the top. */
    static boolean parked(double sec) {
        return sec >= LAP_SECONDS;
    }
}
