package com.csl.kafkador.controller;

import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.domain.dto.AppInfoDto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The version of the running Kafkador, read from the build info Maven writes into the jar, so the pom is its one
 * source. Open before setup and without a connection (see WebConfig): the setup and Connect pages show it.
 */
@RestController
@RequestMapping("/api/v1/app-info")
public class AppInfoController {

    static final String DEVELOPMENT = "development";

    private final String version;

    public AppInfoController(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build == null || build.getVersion() == null ? DEVELOPMENT : build.getVersion();
    }

    @GetMapping
    public ResponseEntity<GenericResponse<AppInfoDto>> getAppInfo() {
        return new GenericResponse.Builder<AppInfoDto>()
                .data(new AppInfoDto().setVersion(version))
                .success(HttpStatus.OK);
    }

}
