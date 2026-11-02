---
title: LDAP and Active Directory
description: Sign in to Kafkador with accounts from your directory.
---

# LDAP and Active Directory

With LDAP, people sign in to Kafkador with their directory accounts. Set it up during
[first-run setup](../getting-started/first-run-setup.md), in **Settings → Access**, or with environment variables.

## Settings

| Field | Example |
|---|---|
| **LDAP URL** | `ldaps://ldap.example.com:636` |
| **Base DN** | `dc=example,dc=com` |

`ldaps://` needs a certificate that Java trusts.

Then choose how Kafkador finds a user.

### Search for the user

Use this for Active Directory, or to limit access to a group.

| Field | Example |
|---|---|
| **Search base** | `ou=people` |
| **Search filter** | `(uid={0})` |
| **Manager DN** (optional) | `cn=kafkador,ou=services,dc=example,dc=com` |
| **Manager password** | The password of that account |

The search base is relative to the base DN, and `{0}` is the username. You only need a manager account when the directory
doesn't allow anonymous searches.

| Goal | Search filter |
|---|---|
| Active Directory | `(sAMAccountName={0})` |
| Only one group | `(&(uid={0})(memberOf=cn=kafkador,ou=groups,dc=example,dc=com))` |

### User DN pattern

Use this when every user sits in one place, such as `uid={0},ou=people`. It's relative to the base DN, and `{0}` is the
username.

## Check before saving

Enter an **LDAP username** and **Password** to test with. Nothing is saved unless that account can sign in with these
settings, and you stay signed in as it.

## Set it with environment variables

```yaml
environment:
  KAFKADOR_ACCESS_MODE: ldap
  KAFKADOR_ACCESS_LDAP_URL: ldaps://ldap.example.com:636
  KAFKADOR_ACCESS_LDAP_BASEDN: dc=example,dc=com
  KAFKADOR_ACCESS_LDAP_USERSEARCHBASE: ou=people
  KAFKADOR_ACCESS_LDAP_USERSEARCHFILTER: (uid={0})
  KAFKADOR_ACCESS_LDAP_MANAGERDN: cn=kafkador,ou=services,dc=example,dc=com
  KAFKADOR_ACCESS_LDAP_MANAGERPASSWORD: ${KAFKADOR_LDAP_PASSWORD}
```

To use a pattern instead of a search, set `KAFKADOR_ACCESS_LDAP_USERDNPATTERN: uid={0},ou=people`.
