package com.csl.kafkador.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Detects a frontend bundled into this jar (Maven "with-frontend" profile copies the Angular build to
 * classpath:/static/). When present, the jar serves the web UI itself; otherwise the backend is API-only,
 * with the legacy Thymeleaf pages, exactly as before.
 */
public final class BundledFrontend {

    private static final ClassPathResource INDEX = new ClassPathResource("static/index.html");

    private BundledFrontend() {
    }

    public static boolean isPresent() {
        return INDEX.exists();
    }

    public static class Present implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isPresent();
        }
    }

    public static class Absent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !isPresent();
        }
    }

}
