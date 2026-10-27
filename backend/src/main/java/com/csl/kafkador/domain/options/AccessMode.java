package com.csl.kafkador.domain.options;

import java.util.Locale;
import java.util.Optional;

/** Who may use Kafkador, chosen during first-run setup and changeable in Settings. */
public enum AccessMode {

    /** No sign-in: anyone who can reach Kafkador can use it. */
    NONE,
    /** Sign in with the admin account kept in Kafkador's database. */
    DATABASE,
    /** Sign in with an LDAP (or Active Directory) account. */
    LDAP;

    public boolean requiresLogin() {
        return this != NONE;
    }

    public static Optional<AccessMode> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

}
