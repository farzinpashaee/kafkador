package com.csl.kafkador.service.access;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Checks a username and password against an LDAP directory by binding as the user, found from a DN pattern or by a
 * search. The provider for the current settings is kept, so each sign-in doesn't set one up again.
 */
@Component
public class LdapAuthenticator {

    /** How long a sign-in waits for the directory, in milliseconds. */
    static final String CONNECT_TIMEOUT = "5000";
    static final String READ_TIMEOUT = "10000";

    private volatile Cached cached;

    private record Cached(LdapSettings settings, LdapAuthenticationProvider provider) {}

    /**
     * @return the user's authentication, without authorities
     * @throws org.springframework.security.authentication.BadCredentialsException when the directory says no
     * @throws org.springframework.security.authentication.InternalAuthenticationServiceException when the
     *         directory can't be reached or the settings are wrong
     */
    public Authentication authenticate(LdapSettings settings, String username, String password) throws AuthenticationException {
        return provider(settings).authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
    }

    private LdapAuthenticationProvider provider(LdapSettings settings) {
        Cached current = cached;
        if (current != null && current.settings().equals(settings)) return current.provider();
        LdapAuthenticationProvider provider = build(settings);
        cached = new Cached(settings, provider);
        return provider;
    }

    static LdapAuthenticationProvider build(LdapSettings settings) {
        DefaultSpringSecurityContextSource source = new DefaultSpringSecurityContextSource(List.of(settings.url()), settings.baseDn());
        if (!settings.managerDn().isBlank()) {
            source.setUserDn(settings.managerDn());
            source.setPassword(settings.managerPassword());
        }
        source.setBaseEnvironmentProperties(Map.of(
                "com.sun.jndi.ldap.connect.timeout", CONNECT_TIMEOUT,
                "com.sun.jndi.ldap.read.timeout", READ_TIMEOUT));
        source.afterPropertiesSet();

        BindAuthenticator authenticator = new BindAuthenticator(source);
        if (!settings.userDnPattern().isBlank()) {
            authenticator.setUserDnPatterns(new String[]{settings.userDnPattern()});
        } else {
            FilterBasedLdapUserSearch search = new FilterBasedLdapUserSearch(settings.userSearchBase(), settings.userSearchFilter(), source);
            search.setSearchSubtree(true);
            authenticator.setUserSearch(search);
        }
        authenticator.afterPropertiesSet();
        return new LdapAuthenticationProvider(authenticator);
    }

}
