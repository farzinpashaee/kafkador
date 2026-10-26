package com.csl.kafkador.controller;

import com.csl.kafkador.domain.dto.AppInfoDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppInfoControllerTest {

    @Test
    void reportsTheVersionTheJarWasBuiltAs() {
        Properties info = new Properties();
        info.setProperty("version", "1.0.0-beta.1");

        assertThat(version(new BuildProperties(info))).isEqualTo("1.0.0-beta.1");
    }

    @Test
    void saysDevelopmentWhenRunWithoutAMavenBuild() {
        assertThat(version(null)).isEqualTo("development");
    }

    @SuppressWarnings("unchecked")
    private static String version(BuildProperties build) {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(build);
        AppInfoDto info = new AppInfoController(provider).getAppInfo().getBody().getData();
        return info.getVersion();
    }

}
