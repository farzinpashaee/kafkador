package com.csl.kafkador.controller;

import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.domain.dto.AccessSettingsDto;
import com.csl.kafkador.domain.dto.AccessStatusDto;
import com.csl.kafkador.domain.dto.LoginRequestDto;
import com.csl.kafkador.exception.InvalidAccessSettingsException;
import com.csl.kafkador.service.access.AccessService;
import com.csl.kafkador.service.access.LoginAttempts;
import com.csl.kafkador.service.access.SignInSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signing in to Kafkador, and Settings > Access. The /api/v1/auth endpoints are open to everyone (SecurityConfig);
 * /api/v1/settings/access needs a signed-in user whenever sign-in is on.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
public class AccessController {

    private final AccessService accessService;
    private final LoginAttempts loginAttempts;
    private final SignInSession signInSession;

    @GetMapping("/api/v1/auth/status")
    public ResponseEntity<GenericResponse<AccessStatusDto>> getStatus(Authentication authentication) {
        return new GenericResponse.Builder<AccessStatusDto>()
                .data(accessService.status(authentication))
                .success(HttpStatus.OK);
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<GenericResponse<AccessStatusDto>> login(@Valid @RequestBody LoginRequestDto login,
                                                                  HttpServletRequest request, HttpServletResponse response) {
        String address = request.getRemoteAddr();
        if (loginAttempts.isBlocked(address, login.getUsername())) {
            return failed(HttpStatus.TOO_MANY_REQUESTS, "Too many failed sign-ins. Wait 15 minutes and try again.");
        }
        Authentication authentication;
        try {
            authentication = accessService.authenticate(login.getUsername(), login.getPassword());
        } catch (BadCredentialsException e) {
            loginAttempts.failed(address, login.getUsername());
            return failed(HttpStatus.UNAUTHORIZED, "Wrong username or password.");
        } catch (AuthenticationException e) {
            log.warn("Signing in through LDAP failed", e);
            return failed(HttpStatus.SERVICE_UNAVAILABLE, "Couldn't check the password with the LDAP server. Try again, or see Kafkador's log.");
        }
        loginAttempts.succeeded(address, login.getUsername());
        // A new session id, so one planted in the browser beforehand doesn't get signed in too.
        if (request.getSession(false) != null) request.changeSessionId();
        signInSession.signIn(authentication, request, response);
        return new GenericResponse.Builder<AccessStatusDto>()
                .data(accessService.status(authentication))
                .success(HttpStatus.OK);
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<GenericResponse<Void>> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return new GenericResponse.Builder<Void>()
                .data(null)
                .success(HttpStatus.OK);
    }

    @GetMapping("/api/v1/settings/access")
    public ResponseEntity<GenericResponse<AccessSettingsDto>> getSettings() {
        return new GenericResponse.Builder<AccessSettingsDto>()
                .data(accessService.view())
                .success(HttpStatus.OK);
    }

    /** Saving signs everyone out; whoever saved stays signed in, as the account they just gave. */
    @PutMapping("/api/v1/settings/access")
    public ResponseEntity<GenericResponse<AccessStatusDto>> saveSettings(@Valid @RequestBody AccessSettingsDto settings,
                                                                         HttpServletRequest request, HttpServletResponse response)
            throws InvalidAccessSettingsException {
        accessService.save(settings);
        signInSession.signInAfterSaving(settings, request, response);
        return new GenericResponse.Builder<AccessStatusDto>()
                .data(accessService.status(SecurityContextHolder.getContext().getAuthentication()))
                .success(HttpStatus.OK);
    }

    private static <T> ResponseEntity<GenericResponse<T>> failed(HttpStatus status, String message) {
        return new GenericResponse.Builder<T>()
                .code(String.valueOf(status.value()))
                .message(message)
                .failed(status);
    }

}
