package com.csl.kafkador.util;

import java.time.Duration;

public enum TimeUnitEnum {
    SECOND(Duration.ofSeconds(1)),
    MINUTE(Duration.ofMinutes(1)),
    HOUR(Duration.ofHours(1)),
    DAY(Duration.ofDays(1));

    private final Duration duration;

    TimeUnitEnum(Duration duration) {
        this.duration = duration;
    }

    public Duration getDuration() {
        return duration;
    }

    public long toMillis(long amount) {
        return duration.multipliedBy(amount).toMillis();
    }

    public long toSeconds(long amount) {
        return duration.multipliedBy(amount).getSeconds();
    }

}
