package com.csl.kafkador.service.access;

import com.csl.kafkador.domain.dto.LdapSettingsDto;

import java.util.Locale;

/** The LDAP settings in effect. Every field is non-null; blank means not set. */
public record LdapSettings(String url, String baseDn, String userDnPattern, String userSearchBase, String userSearchFilter,
                           String managerDn, String managerPassword) {

    public LdapSettings {
        url = clean(url);
        baseDn = clean(baseDn);
        userDnPattern = clean(userDnPattern);
        userSearchBase = clean(userSearchBase);
        userSearchFilter = clean(userSearchFilter);
        managerDn = clean(managerDn);
        managerPassword = managerPassword == null ? "" : managerPassword;
    }

    public static LdapSettings of(LdapSettingsDto dto) {
        return new LdapSettings(dto.getUrl(), dto.getBaseDn(), dto.getUserDnPattern(), dto.getUserSearchBase(),
                dto.getUserSearchFilter(), dto.getManagerDn(), dto.getManagerPassword());
    }

    public LdapSettings withManagerPassword(String password) {
        return new LdapSettings(url, baseDn, userDnPattern, userSearchBase, userSearchFilter, managerDn, password);
    }

    /** @return what's wrong with these settings, or null when they can be tried */
    public String problem() {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("ldap://") && !lower.startsWith("ldaps://")) {
            return "The LDAP URL must start with ldap:// or ldaps://.";
        }
        if (userDnPattern.isBlank() && userSearchFilter.isBlank()) {
            return "Give a user DN pattern or a user search filter.";
        }
        if (!userDnPattern.isBlank() && !userDnPattern.contains("{0}")) {
            return "The user DN pattern must contain {0} where the username goes, e.g. uid={0},ou=people.";
        }
        if (userDnPattern.isBlank() && !userSearchFilter.contains("{0}")) {
            return "The user search filter must contain {0} where the username goes, e.g. (uid={0}).";
        }
        if (managerDn.isBlank() != managerPassword.isEmpty()) {
            return "Give both the manager DN and its password, or neither.";
        }
        return null;
    }

    /** Keeps the manager password out of logs. */
    @Override
    public String toString() {
        return "LdapSettings[url=" + url + ", baseDn=" + baseDn + ", userDnPattern=" + userDnPattern
                + ", userSearchBase=" + userSearchBase + ", userSearchFilter=" + userSearchFilter + ", managerDn=" + managerDn + "]";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

}
