package com.csl.kafkador.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** How Kafkador finds and checks an LDAP account. The manager password is never sent back; see managerPasswordSaved. */
@Data
@Accessors(chain = true)
public class LdapSettingsDto {

    /** ldap://host:389 or ldaps://host:636 */
    private String url;
    /** e.g. dc=example,dc=com; the patterns and search base below are relative to it. */
    private String baseDn;
    /** e.g. uid={0},ou=people: the user's DN, {0} being the username. Or leave it empty and search instead. */
    private String userDnPattern;
    /** e.g. ou=people; where to search for the user. */
    private String userSearchBase;
    /** e.g. (uid={0}) or (sAMAccountName={0}); add (memberOf=...) to let in only one group. */
    private String userSearchFilter;
    /** The account that searches, when the directory doesn't allow anonymous searches. */
    private String managerDn;
    /** Blank keeps the saved one. */
    private String managerPassword;
    private Boolean managerPasswordSaved;

}
