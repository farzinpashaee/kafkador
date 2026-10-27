package com.csl.kafkador.domain.dto;

import com.csl.kafkador.domain.options.AccessMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Who may use Kafkador: chosen on the setup page and in Settings. Passwords are only ever sent in, never back.
 */
@Data
@Accessors(chain = true)
public class AccessSettingsDto {

    @NotNull(message = "Choose who may use Kafkador")
    private AccessMode mode;

    /** DATABASE: the admin account. */
    private String username;
    /** DATABASE: blank keeps the current password when the username stays the same. */
    private String password;

    private LdapSettingsDto ldap;
    /** LDAP: an account to sign in with before saving, so a mistake can't lock everyone out. */
    private String testUsername;
    private String testPassword;

    /** Set by the server: KAFKADOR_ACCESS_MODE decides, so Settings can't change it. */
    private Boolean managedByEnvironment;

    /** The one-time token from the log, when setup asks for one (see SetupGuard). */
    private String setupToken;

}
