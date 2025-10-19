package com.pikaronga.parkour.util;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static String formatDuration(long nanos) {
        if (nanos < 0) {
            nanos = 0;
        }

        long minutes = nanos / 60_000_000_000L;
        long remainingAfterMinutes = nanos - minutes * 60_000_000_000L;
        long seconds = remainingAfterMinutes / 1_000_000_000L;
        long remainingAfterSeconds = remainingAfterMinutes - seconds * 1_000_000_000L;

        long millis = Math.round(remainingAfterSeconds / 1_000_000.0);
        if (millis == 1000) {
            millis = 0;
            seconds += 1;
            if (seconds == 60) {
                seconds = 0;
                minutes += 1;
            }
        }

        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }
}
