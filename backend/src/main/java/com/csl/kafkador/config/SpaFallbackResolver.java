package com.csl.kafkador.config;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Serves the Angular app for client-side routes: a request that matches no file is answered with
 * index.html so deep links and page refreshes (e.g. /topic/orders) work. Missing API paths and missing
 * files that look like assets stay 404 instead of returning HTML.
 */
class SpaFallbackResolver extends PathResourceResolver {

    private static final Set<String> ASSET_EXTENSIONS = Set.of(
            "js", "mjs", "css", "map", "ico", "png", "jpg", "jpeg", "gif", "svg", "webp", "avif",
            "woff", "woff2", "ttf", "otf", "eot", "json", "txt", "xml", "webmanifest");

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        Resource requested = location.createRelative(resourcePath);
        if (requested.exists() && requested.isReadable()) {
            return requested;
        }
        if (resourcePath.startsWith("api/") || looksLikeAsset(resourcePath)) {
            return null;
        }
        Resource index = location.createRelative("index.html");
        return index.exists() && index.isReadable() ? index : null;
    }

    // A topic can legitimately contain dots (/topic/my.topic), so only well-known asset extensions count.
    static boolean looksLikeAsset(String resourcePath) {
        String lastSegment = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        int dot = lastSegment.lastIndexOf('.');
        return dot >= 0 && ASSET_EXTENSIONS.contains(lastSegment.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

}
