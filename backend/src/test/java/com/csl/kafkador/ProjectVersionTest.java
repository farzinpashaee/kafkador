package com.csl.kafkador;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The pom sets the version; the frontend's and desktop app's package.json have to carry the same one. */
class ProjectVersionTest {

    @Test
    void theFrontendHasTheSameVersionAsThePom() throws Exception {
        assertSameVersionAsThePom("frontend");
    }

    @Test
    void theDesktopAppHasTheSameVersionAsThePom() throws Exception {
        assertSameVersionAsThePom("desktop");
    }

    private static void assertSameVersionAsThePom(String module) throws Exception {
        // Tests run from backend/; the project's own <version> is the first one after the parent block.
        String pom = Files.readString(Path.of("pom.xml"));
        String afterParent = pom.substring(pom.indexOf("</parent>"));
        String pomVersion = first(Pattern.compile("<version>([^<]+)</version>"), afterParent);
        String packageVersion = first(Pattern.compile("\"version\"\s*:\s*\"([^\"]+)\""),
                Files.readString(Path.of("..", module, "package.json")));

        assertThat(packageVersion)
                .as("%s/package.json version (set it to the pom's: cd %s && npm version %s --no-git-tag-version)", module, module, pomVersion)
                .isEqualTo(pomVersion);
    }

    private static String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        assertThat(matcher.find()).as("no %s", pattern).isTrue();
        return matcher.group(1);
    }

}
