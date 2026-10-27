package com.csl.kafkador.service.access;

import com.csl.kafkador.domain.dto.AccessSettingsDto;
import com.csl.kafkador.domain.options.AccessMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Keeps a sign-in in the browser's session, where SecurityConfig's filters find it on the next request. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignInSession {

    private final AccessService accessService;
    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    public void signIn(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
    }

    /**
     * After the setup page or Settings saved who may use Kafkador, signs this browser in with the account it gave (the
     * admin, or the LDAP test account), so whoever set it up isn't asked for the same password straight away.
     */
    public void signInAfterSaving(AccessSettingsDto settings, HttpServletRequest request, HttpServletResponse response) {
        if (!accessService.isLoginRequired()) return;
        boolean ldap = settings.getMode() == AccessMode.LDAP;
        String username = ldap ? settings.getTestUsername() : settings.getUsername();
        String password = ldap ? settings.getTestPassword() : settings.getPassword();
        if (username == null || password == null || password.isEmpty()) return;
        try {
            signIn(accessService.authenticate(username, password), request, response);
        } catch (AuthenticationException e) {
            // Saved all the same; this browser signs in on the sign-in page.
            log.debug("Couldn't sign in after saving who may use Kafkador: {}", e.getMessage());
        }
    }

}
