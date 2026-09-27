package com.csl.kafkador.controller;

import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.domain.dto.DatabaseCredentialsDto;
import com.csl.kafkador.domain.dto.SetupStatusDto;
import com.csl.kafkador.exception.DatabaseAlreadyConfiguredException;
import com.csl.kafkador.exception.DatabaseSetupException;
import com.csl.kafkador.service.DatabaseCredentialsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reachable regardless of setup/session state (see DatabaseSetupInterceptor's and SessionInterceptor's
 * exclusions in WebConfig) — this is how the frontend checks whether first-run setup is needed, and how
 * it's completed.
 */
@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
@Validated
public class SetupController {

    private final DatabaseCredentialsService databaseCredentialsService;

    @GetMapping("/status")
    public ResponseEntity<GenericResponse<SetupStatusDto>> getStatus() {
        return new GenericResponse.Builder<SetupStatusDto>()
                .data(new SetupStatusDto().setDatabaseSetupRequired(databaseCredentialsService.isSetupRequired()))
                .success(HttpStatus.OK);
    }

    @PostMapping("/database")
    public ResponseEntity<GenericResponse<Void>> configureDatabase(@Valid @RequestBody DatabaseCredentialsDto request)
            throws DatabaseAlreadyConfiguredException, DatabaseSetupException {
        databaseCredentialsService.configure(request.getUsername(), request.getPassword());
        return new GenericResponse.Builder<Void>()
                .data(null)
                .success(HttpStatus.OK);
    }

}
