package com.csl.kafkador.service.access;

import com.csl.kafkador.config.ApplicationConfig;
import com.csl.kafkador.domain.dto.AccessSettingsDto;
import com.csl.kafkador.domain.dto.AccessStatusDto;
import com.csl.kafkador.domain.dto.LdapSettingsDto;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.domain.model.KafkadorUser;
import com.csl.kafkador.domain.options.AccessMode;
import com.csl.kafkador.exception.InvalidAccessSettingsException;
import com.csl.kafkador.repository.KafkadorUserRepository;
import com.csl.kafkador.service.config.KafkadorConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.AbstractMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Who may use Kafkador: nobody needs to sign in (NONE), the admin account kept in the database signs in (DATABASE),
 * or LDAP accounts do (LDAP). First-run setup chooses, and Settings can change it, unless kafkador.access.mode
 * (KAFKADOR_ACCESS_MODE) sets it at every startup.
 *
 * <p>Every change bumps a generation number, and a sign-in only counts for the generation it was made in, so changing
 * who may use Kafkador signs everyone out.</p>
 */
@Slf4j
@Service
public class AccessService {

    static final String SCOPE = "global";
    static final String MODE = "kafkador.access.mode";
    static final String GENERATION = "kafkador.access.generation";
    static final String LDAP = "kafkador.access.ldap.";
    public static final String AUTHORITY_PREFIX = "KAFKADOR_ACCESS_";

    static final Pattern USERNAME = Pattern.compile("[A-Za-z][A-Za-z0-9._@-]{2,63}");
    static final int MIN_PASSWORD = 8;
    static final int MAX_PASSWORD = 128;

    private final KafkadorConfigService<String, Map.Entry<String, String>> config;
    private final KafkadorUserRepository users;
    private final LdapAuthenticator ldapAuthenticator;
    private final PasswordEncoder encoder = passwordEncoder();
    /** Checked against when the username doesn't exist, so a wrong username takes as long as a wrong password. */
    private final String unknownUserHash = encoder.encode("no such user");

    private volatile State state;
    private volatile boolean managedByEnvironment;

    /** What's saved; mode is null until setup chooses. */
    record State(AccessMode mode, long generation, LdapSettings ldap) {}

    public AccessService(KafkadorConfigService<String, Map.Entry<String, String>> config, KafkadorUserRepository users,
                         LdapAuthenticator ldapAuthenticator) {
        this.config = config;
        this.users = users;
        this.ldapAuthenticator = ldapAuthenticator;
    }

    /** PBKDF2 rather than the usual BCrypt, which ignores everything past a password's 72nd byte. */
    static PasswordEncoder passwordEncoder() {
        String id = "pbkdf2@SpringSecurity_v5_8";
        return new DelegatingPasswordEncoder(id, Map.of(
                id, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                "bcrypt", new BCryptPasswordEncoder()));
    }

    public boolean isSetupRequired() {
        return state().mode() == null;
    }

    public Optional<AccessMode> mode() {
        return Optional.ofNullable(state().mode());
    }

    public boolean isLoginRequired() {
        AccessMode mode = state().mode();
        return mode != null && mode.requiresLogin();
    }

    public boolean isManagedByEnvironment() {
        return managedByEnvironment;
    }

    /** Whether this request may use the API: always when no sign-in is needed, else when signed in since the last change. */
    public boolean isAllowed(Authentication authentication) {
        if (!isLoginRequired()) return true;
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        String current = authority(state().generation());
        return authentication.getAuthorities().stream().anyMatch(granted -> current.equals(granted.getAuthority()));
    }

    public AccessStatusDto status(Authentication authentication) {
        boolean signedIn = isLoginRequired() && isAllowed(authentication);
        return new AccessStatusDto()
                .setMode(state().mode())
                .setLoginRequired(isLoginRequired())
                .setAuthenticated(signedIn)
                .setUsername(signedIn ? authentication.getName() : null);
    }

    /**
     * @throws BadCredentialsException when the username or password is wrong
     * @throws AuthenticationException when LDAP can't be asked
     */
    public Authentication authenticate(String username, String password) throws AuthenticationException {
        State current = state();
        if (current.mode() == null || !current.mode().requiresLogin()) {
            throw new BadCredentialsException("Kafkador doesn't ask anyone to sign in.");
        }
        String name = username.trim();
        if (current.mode() == AccessMode.DATABASE) {
            Optional<KafkadorUser> user = users.findByUsernameIgnoreCase(name);
            boolean matches = encoder.matches(password, user.map(KafkadorUser::getPasswordHash).orElse(unknownUserHash));
            if (user.isEmpty() || !matches) throw new BadCredentialsException("Wrong username or password.");
            return signedIn(user.get().getUsername(), current);
        }
        return signedIn(ldapAuthenticator.authenticate(current.ldap(), name, password).getName(), current);
    }

    /** The settings as Settings shows them, without passwords. */
    public AccessSettingsDto view() {
        State current = state();
        AccessSettingsDto view = new AccessSettingsDto()
                .setMode(current.mode())
                .setManagedByEnvironment(managedByEnvironment);
        if (current.mode() == AccessMode.DATABASE) {
            users.findAll().stream().findFirst().ifPresent(admin -> view.setUsername(admin.getUsername()));
        }
        if (current.mode() == AccessMode.LDAP) {
            LdapSettings ldap = current.ldap();
            view.setLdap(new LdapSettingsDto()
                    .setUrl(ldap.url())
                    .setBaseDn(ldap.baseDn())
                    .setUserDnPattern(ldap.userDnPattern())
                    .setUserSearchBase(ldap.userSearchBase())
                    .setUserSearchFilter(ldap.userSearchFilter())
                    .setManagerDn(ldap.managerDn())
                    .setManagerPasswordSaved(!ldap.managerPassword().isEmpty()));
        }
        return view;
    }

    /**
     * Saves who may use Kafkador, from the setup page or Settings. For LDAP, the test account has to sign in with the
     * new settings first. Saving anything that changes signs everyone out.
     */
    @Transactional
    public synchronized void save(AccessSettingsDto settings) throws InvalidAccessSettingsException {
        if (managedByEnvironment) {
            throw new InvalidAccessSettingsException("KAFKADOR_ACCESS_MODE decides who may use Kafkador, so it can't be changed here.");
        }
        if (settings.getMode() == null) throw new InvalidAccessSettingsException("Choose who may use Kafkador.");
        State current = state();
        switch (settings.getMode()) {
            case NONE -> {
                if (current.mode() != AccessMode.NONE) store(AccessMode.NONE, null);
            }
            case DATABASE -> saveDatabase(current, trim(settings.getUsername()), settings.getPassword());
            case LDAP -> saveLdap(current, settings);
        }
    }

    /**
     * Applies kafkador.access.* when kafkador.access.mode is set, at every startup. Unlike the setup page it doesn't
     * try the LDAP settings, as the directory may not be up yet.
     *
     * @throws IllegalStateException when the settings are incomplete, so Kafkador doesn't start open by mistake
     */
    @Transactional
    public synchronized void applyPreset(ApplicationConfig.Access preset) {
        if (preset.getMode() == null || preset.getMode().isBlank()) {
            managedByEnvironment = false;
            return;
        }
        AccessMode mode = AccessMode.parse(preset.getMode()).orElseThrow(() ->
                new IllegalStateException("kafkador.access.mode must be none, database or ldap, not '" + preset.getMode() + "'"));
        State current = state();
        try {
            switch (mode) {
                case NONE -> {
                    if (current.mode() != AccessMode.NONE) store(AccessMode.NONE, null);
                }
                case DATABASE -> {
                    String password = preset.getAdminPassword() == null ? "" : preset.getAdminPassword();
                    if (password.isEmpty()) throw new InvalidAccessSettingsException("Set the admin password too.");
                    saveDatabase(current, trim(preset.getAdminUsername()), password);
                }
                case LDAP -> {
                    ApplicationConfig.Ldap ldap = preset.getLdap();
                    LdapSettings settings = new LdapSettings(ldap.getUrl(), ldap.getBaseDn(), ldap.getUserDnPattern(),
                            ldap.getUserSearchBase(), ldap.getUserSearchFilter(), ldap.getManagerDn(), ldap.getManagerPassword());
                    String problem = settings.problem();
                    if (problem != null) throw new InvalidAccessSettingsException(problem);
                    if (current.mode() != AccessMode.LDAP || !settings.equals(current.ldap())) store(AccessMode.LDAP, settings);
                }
            }
        } catch (InvalidAccessSettingsException e) {
            throw new IllegalStateException("kafkador.access (KAFKADOR_ACCESS_*): " + e.getMessage(), e);
        }
        managedByEnvironment = true;
        log.info("Who may use Kafkador is set by kafkador.access.mode: {}", mode);
    }

    private void saveDatabase(State current, String username, String password) throws InvalidAccessSettingsException {
        if (!USERNAME.matcher(username).matches()) {
            throw new InvalidAccessSettingsException("The admin username must be 3-64 characters, start with a letter, "
                    + "and contain only letters, digits and . _ @ -");
        }
        Optional<KafkadorUser> existing = users.findByUsernameIgnoreCase(username);
        boolean keepPassword = password == null || password.isEmpty();
        if (keepPassword && (existing.isEmpty() || current.mode() != AccessMode.DATABASE)) {
            throw new InvalidAccessSettingsException("Choose a password for the admin account.");
        }
        if (!keepPassword && (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD)) {
            throw new InvalidAccessSettingsException("The admin password must be " + MIN_PASSWORD + "-" + MAX_PASSWORD + " characters.");
        }
        boolean unchanged = current.mode() == AccessMode.DATABASE && existing.isPresent() && users.count() == 1
                && existing.get().getUsername().equals(username)
                && (keepPassword || encoder.matches(password, existing.get().getPasswordHash()));
        if (unchanged) return;

        Date now = new Date();
        KafkadorUser admin = existing.orElseGet(() -> {
            KafkadorUser created = new KafkadorUser();
            created.setCreateDateTime(now);
            return created;
        });
        admin.setUsername(username);
        if (!keepPassword) admin.setPasswordHash(encoder.encode(password));
        admin.setUpdateDateTime(now);
        // One admin account: the one chosen replaces any other.
        users.findAll().stream().filter(user -> !user.getId().equals(admin.getId())).forEach(users::delete);
        users.save(admin);
        store(AccessMode.DATABASE, null);
    }

    private void saveLdap(State current, AccessSettingsDto settings) throws InvalidAccessSettingsException {
        if (settings.getLdap() == null) throw new InvalidAccessSettingsException("Fill in the LDAP settings.");
        LdapSettings ldap = LdapSettings.of(settings.getLdap());
        // A blank manager password keeps the saved one, as long as it's for the same manager account.
        if (ldap.managerPassword().isEmpty() && current.mode() == AccessMode.LDAP && !ldap.managerDn().isBlank()
                && ldap.managerDn().equals(current.ldap().managerDn())) {
            ldap = ldap.withManagerPassword(current.ldap().managerPassword());
        }
        String problem = ldap.problem();
        if (problem != null) throw new InvalidAccessSettingsException(problem);
        if (current.mode() == AccessMode.LDAP && ldap.equals(current.ldap())) return;

        String testUsername = trim(settings.getTestUsername());
        String testPassword = settings.getTestPassword() == null ? "" : settings.getTestPassword();
        if (testUsername.isEmpty() || testPassword.isEmpty()) {
            throw new InvalidAccessSettingsException("Enter an LDAP username and password to sign in with, to check the settings work.");
        }
        try {
            ldapAuthenticator.authenticate(ldap, testUsername, testPassword);
        } catch (BadCredentialsException e) {
            throw new InvalidAccessSettingsException("LDAP didn't accept that username and password with these settings.");
        } catch (AuthenticationException e) {
            log.warn("Trying the LDAP settings {} failed", ldap, e);
            throw new InvalidAccessSettingsException("Couldn't sign in through LDAP. Check the URL, base DN and manager account; "
                    + "Kafkador's log has the details.");
        }
        store(AccessMode.LDAP, ldap);
    }

    /** Saves the mode and, for LDAP, its settings; forgets the database accounts otherwise; signs everyone out. */
    private void store(AccessMode mode, LdapSettings ldap) {
        if (mode != AccessMode.DATABASE) users.deleteAll();
        LdapSettings saved = ldap == null ? new LdapSettings(null, null, null, null, null, null, null) : ldap;
        put(LDAP + "url", saved.url());
        put(LDAP + "base-dn", saved.baseDn());
        put(LDAP + "user-dn-pattern", saved.userDnPattern());
        put(LDAP + "user-search-base", saved.userSearchBase());
        put(LDAP + "user-search-filter", saved.userSearchFilter());
        put(LDAP + "manager-dn", saved.managerDn());
        put(LDAP + "manager-password", saved.managerPassword());
        long generation = state().generation() + 1;
        put(GENERATION, String.valueOf(generation));
        put(MODE, mode.name());
        state = new State(mode, generation, saved);
        log.info("Who may use Kafkador changed to {}; everyone signed in before has to sign in again", mode);
    }

    private State state() {
        State current = state;
        if (current == null) {
            current = load();
            state = current;
        }
        return current;
    }

    private State load() {
        Map<String, KafkadorConfig> saved = config.get(SCOPE);
        long generation;
        try {
            generation = Long.parseLong(value(saved, GENERATION));
        } catch (NumberFormatException e) {
            generation = 0;
        }
        LdapSettings ldap = new LdapSettings(value(saved, LDAP + "url"), value(saved, LDAP + "base-dn"),
                value(saved, LDAP + "user-dn-pattern"), value(saved, LDAP + "user-search-base"),
                value(saved, LDAP + "user-search-filter"), value(saved, LDAP + "manager-dn"), value(saved, LDAP + "manager-password"));
        return new State(AccessMode.parse(value(saved, MODE)).orElse(null), generation, ldap);
    }

    private void put(String key, String value) {
        config.save(new AbstractMap.SimpleEntry<>(key, value), SCOPE);
    }

    private static String value(Map<String, KafkadorConfig> saved, String key) {
        KafkadorConfig entry = saved.get(key);
        return entry == null || entry.getConfigValue() == null ? "" : entry.getConfigValue();
    }

    private static Authentication signedIn(String username, State state) {
        return UsernamePasswordAuthenticationToken.authenticated(username, null,
                List.of(new SimpleGrantedAuthority(authority(state.generation()))));
    }

    private static String authority(long generation) {
        return AUTHORITY_PREFIX + generation;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
