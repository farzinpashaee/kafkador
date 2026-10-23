package com.csl.kafkador.config;

import org.springframework.core.io.ClassPathResource;

/**
 * Detects the Angular UI bundled into this jar (the Maven "with-frontend" profile copies its build to
 * classpath:/static/). When present, the jar serves the UI as well as the API; otherwise it serves only the API,
 * for running the UI separately (ng serve, or a web server that forwards /api to the backend).
 */
public final class BundledFrontend {

    private static final ClassPathResource INDEX = new ClassPathResource("static/index.html");

    private BundledFrontend() {
    }

    public static boolean isPresent() {
        return INDEX.exists();
    }

}
