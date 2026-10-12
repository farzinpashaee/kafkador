package com.csl.kafkador.util;

import com.csl.kafkador.domain.dto.RecordFilterDto;
import com.csl.kafkador.domain.dto.TopicRecordDto;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Checks a decoded record against the Records tab filters; a record must meet all of them. */
public final class RecordFilterMatcher {

    private RecordFilterMatcher() {
    }

    public static boolean matches(TopicRecordDto record, List<RecordFilterDto> filters) {
        return filters == null || filters.stream().allMatch(filter -> matches(record, filter));
    }

    private static boolean matches(TopicRecordDto record, RecordFilterDto filter) {
        List<String> candidates = candidates(record, filter.getField());
        return switch (filter.getOperator()) {
            case CONTAINS -> candidates.stream().anyMatch(text -> containsIgnoreCase(text, filter.getValue()));
            case NOT_CONTAINS -> candidates.stream().noneMatch(text -> containsIgnoreCase(text, filter.getValue()));
            case EQUALS -> candidates.stream().anyMatch(filter.getValue()::equals);
        };
    }

    /** The texts a filter on {@code field} looks at; null keys/values are left out. */
    private static List<String> candidates(TopicRecordDto record, RecordFilterDto.Field field) {
        Stream<String> headers = record.getHeaders() == null ? Stream.empty()
                : record.getHeaders().stream().flatMap(h -> Stream.of(h.key(), h.value()));
        Stream<String> texts = switch (field) {
            case KEY -> Stream.of(record.getKey());
            case VALUE -> Stream.of(record.getValue());
            case HEADERS -> headers;
            case ANY -> Stream.concat(Stream.of(record.getKey(), record.getValue()), headers);
        };
        return texts.filter(java.util.Objects::nonNull).toList();
    }

    private static boolean containsIgnoreCase(String text, String part) {
        return text.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
    }

}
