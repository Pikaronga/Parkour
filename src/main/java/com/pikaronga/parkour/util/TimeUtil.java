package com.pikaronga.parkour.util;

import java.time.Duration;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).toSeconds();
        long milli = duration.minusMinutes(minutes).minusSeconds(seconds).toMillis();
        return String.format("%02d:%02d.%03d", minutes, seconds, milli);
    }
}
