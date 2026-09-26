package com.csl.kafkador.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpaFallbackResolverTest {

    private final SpaFallbackResolver resolver = new SpaFallbackResolver();
    private Resource location;

    @BeforeEach
    void setUp(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("index.html"), "<app-root></app-root>");
        Files.writeString(dir.resolve("main-ABC123.js"), "console.log(1)");
        location = new FileSystemResource(dir.toString() + "/");
    }

    @Test
    void existingFile_isServedAsIs() throws IOException {
        assertThat(resolver.getResource("main-ABC123.js", location).getFilename()).isEqualTo("main-ABC123.js");
    }

    @Test
    void clientSideRoute_fallsBackToIndexHtml() throws IOException {
        assertThat(resolver.getResource("topic/orders", location).getFilename()).isEqualTo("index.html");
        assertThat(resolver.getResource("settings", location).getFilename()).isEqualTo("index.html");
    }

    @Test
    void topicNameWithDots_isStillARoute() throws IOException {
        assertThat(resolver.getResource("topic/my.company.orders", location).getFilename()).isEqualTo("index.html");
    }

    @Test
    void missingAssets_stayNotFoundInsteadOfReturningHtml() throws IOException {
        assertThat(resolver.getResource("main-OLD999.js", location)).isNull();
        assertThat(resolver.getResource("images/missing.svg", location)).isNull();
        assertThat(resolver.getResource("styles.CSS", location)).isNull();
    }

    @Test
    void unknownApiPaths_stayNotFound() throws IOException {
        assertThat(resolver.getResource("api/v1/nothing", location)).isNull();
    }

    @Test
    void looksLikeAsset_onlyMatchesKnownExtensionsOnTheLastSegment() {
        assertThat(SpaFallbackResolver.looksLikeAsset("a/b/app.js")).isTrue();
        assertThat(SpaFallbackResolver.looksLikeAsset("fonts/x.woff2")).isTrue();
        assertThat(SpaFallbackResolver.looksLikeAsset("topic/my.topic")).isFalse();
        assertThat(SpaFallbackResolver.looksLikeAsset("v1.2/orders")).isFalse();
        assertThat(SpaFallbackResolver.looksLikeAsset("settings")).isFalse();
    }

}
