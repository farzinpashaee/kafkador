package com.csl.kafkador.controller;

import com.csl.kafkador.component.SetupGuard;
import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.domain.dto.AccessSettingsDto;
import com.csl.kafkador.domain.dto.DatabaseCredentialsDto;
import com.csl.kafkador.domain.dto.SetupStatusDto;
import com.csl.kafkador.exception.DatabaseAlreadyConfiguredException;
import com.csl.kafkador.exception.DatabaseSetupException;
import com.csl.kafkador.exception.InvalidAccessSettingsException;
import com.csl.kafkador.exception.SetupTokenException;
import com.csl.kafkador.service.DatabaseCredentialsService;
import com.csl.kafkador.service.access.AccessService;
import com.csl.kafkador.service.access.SignInSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * Reachable regardless of setup, sign-in and session state (see DatabaseSetupInterceptor's and SessionInterceptor's
 * exclusions in WebConfig) — this is how the frontend checks whether first-run setup is needed, and how
 * it's completed.
 */
@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
@Validated
public class SetupController {

    private final DatabaseCredentialsService databaseCredentialsService;
    private final SetupGuard setupGuard;
    private final AccessService accessService;
    private final SignInSession signInSession;

    @GetMapping("/status")
    public ResponseEntity<GenericResponse<SetupStatusDto>> getStatus() {
        return new GenericResponse.Builder<SetupStatusDto>()
                .data(new SetupStatusDto()
                        .setDatabaseSetupRequired(databaseCredentialsService.isSetupRequired())
                        .setAccessSetupRequired(accessService.isSetupRequired())
                        .setSetupTokenRequired(setupGuard.isTokenRequired()))
                .success(HttpStatus.OK);
    }

    /** Needs the one-time setup token from the log, unless setup was configured not to (see SetupGuard). */
    @PostMapping("/database")
    public ResponseEntity<GenericResponse<Void>> configureDatabase(@Valid @RequestBody DatabaseCredentialsDto request)
            throws DatabaseAlreadyConfiguredException, DatabaseSetupException, SetupTokenException {
        setupGuard.verify(request.getSetupToken());
        databaseCredentialsService.configure(request.getUsername(), request.getPassword());
        setupGuard.stepDone();
        return new GenericResponse.Builder<Void>()
                .data(null)
                .success(HttpStatus.OK);
    }

    /**
     * The second step: who may use Kafkador. Needs the same token as the first. With sign-in on, this browser is signed
     * in with the account it just gave.
     */
    @PostMapping("/access")
    public ResponseEntity<GenericResponse<Void>> configureAccess(@Valid @RequestBody AccessSettingsDto settings,
                                                                 HttpServletRequest request, HttpServletResponse response)
            throws InvalidAccessSettingsException, SetupTokenException {
        setupGuard.verify(settings.getSetupToken());
        if (databaseCredentialsService.isSetupRequired()) {
            throw new InvalidAccessSettingsException("Set the database username and password first.");
        }
        if (!accessService.isSetupRequired()) {
            throw new InvalidAccessSettingsException("Who may use Kafkador is already chosen. Change it in Settings > Access.");
        }
        accessService.save(settings);
        setupGuard.stepDone();
        if (request.getSession(false) != null) request.changeSessionId();
        signInSession.signInAfterSaving(settings, request, response);
        return new GenericResponse.Builder<Void>()
                .data(null)
                .success(HttpStatus.OK);
    }

}
