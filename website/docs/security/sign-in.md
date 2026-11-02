---
title: Sign-in and access
description: Choose who may use Kafkador, change it later, and recover a lost password.
---

# Sign-in and access

Kafkador has three access modes. You choose one during [first-run setup](../getting-started/first-run-setup.md) and can
change it later.

| Mode | `KAFKADOR_ACCESS_MODE` | Who gets in |
|---|---|---|
| No sign-in | `none` | Anyone who can reach Kafkador |
| Kafkador account | `database` | One admin account |
| LDAP | `ldap` | [Accounts from your directory](./ldap.md) |

:::warning Everyone has full access
There are no read-only users yet. Whoever signs in can use every feature.
:::

## Change the access mode

Go to **Settings → Access**. Saving signs everyone else out.

While `KAFKADOR_ACCESS_MODE` is set, this page can't change who may use Kafkador, because that variable applies at every
start.

## The admin account

- The username is 3-64 characters and starts with a letter. It can contain letters, digits and `.`, `_`, `@` and `-`.
- The password is 8-128 characters, and is stored hashed with PBKDF2.

## Password guessing

Five wrong passwords for a username from one address block it for 15 minutes.

## Sessions

Sessions start with a 30-minute idle timeout. To raise it, up to 24 hours, go to **Settings → Generic**.

## Recover a lost admin password

Set the mode and a new password, then restart Kafkador:

```yaml
environment:
  KAFKADOR_ACCESS_MODE: database
  KAFKADOR_ACCESS_ADMINUSERNAME: admin
  KAFKADOR_ACCESS_ADMINPASSWORD: ${KAFKADOR_ADMIN_PASSWORD}
```

These variables apply at every start. To manage access from **Settings → Access** again, remove them and restart.
