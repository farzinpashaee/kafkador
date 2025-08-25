package com.csl.kafkador.record;

public record ConfigEntry(
        String name,
        String value,
        String source,
        Boolean sensitive,
        Boolean readOnly,
        String type,
        String documentation) {
}
