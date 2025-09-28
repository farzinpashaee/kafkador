package com.csl.kafkador.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.spel.spi.Function;

import java.util.function.Supplier;

@Slf4j
public class ValidationHelper {

    public static <T> T safeCall(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (NullPointerException e) {
            log.error(e.toString());
            return null;
        }
    }

}
