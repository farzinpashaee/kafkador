---
title: First-run setup
description: The two setup steps, and how to skip them with environment variables.
---

# First-run setup

The first time Kafkador starts, it asks you to set up its database and choose who may use it. You only do this once,
because the answers are saved with Kafkador's data.

## The setup token

So that whoever reaches a new instance first can't claim it, the setup page asks for a one-time token. Kafkador prints
it in the log at startup:

```text
============================================================
Kafkador needs first-run setup. The setup page asks for this
one-time setup token:  3f9c1e...
============================================================
```

To choose the token yourself, set `KAFKADOR_SETUP_TOKEN`. The `dev` profile doesn't ask for one.

## Step 1: Database credentials

Choose a username and password for the embedded database. Kafkador stores these in `db-credentials.properties` beside
the database, and [they must be backed up together](../reference/data-and-backup.md).

## Step 2: Who may use Kafkador

| Option | Who gets in | Use it when |
|---|---|---|
| **No sign-in** | Anyone who can reach Kafkador | Nobody else can reach it, for example when it listens on this machine only |
| **Kafkador account** | One admin account, kept in the database with its password hashed | You want sign-in without a directory |
| **LDAP** | Accounts from your directory or Active Directory | Your organisation already has one |

With LDAP, setup signs in with an account you provide before it saves anything, so a typo can't lock you out. See
[LDAP and Active Directory](../security/ldap.md).

You can change this later in **Settings → Access**, which signs everyone else out.

## Skip the setup page

For Docker, Kubernetes or scripted installs, provide the answers up front.

**Database credentials** are only read on the first start:

```bash
KAFKADOR_SETUP_USERNAME=kafkador_admin KAFKADOR_SETUP_PASSWORD='a-long-password' java -jar kafkador.jar
```

**Access** is applied at every start, with `KAFKADOR_ACCESS_MODE` set to `none`, `database` or `ldap`:

```yaml
environment:
  KAFKADOR_SETUP_USERNAME: kafkador_admin
  KAFKADOR_SETUP_PASSWORD: ${KAFKADOR_SETUP_PASSWORD}     # at least 8 characters
  KAFKADOR_ACCESS_MODE: database
  KAFKADOR_ACCESS_ADMINUSERNAME: admin
  KAFKADOR_ACCESS_ADMINPASSWORD: ${KAFKADOR_ADMIN_PASSWORD}   # 8-128 characters
```

While `KAFKADOR_ACCESS_MODE` is set, **Settings → Access** can't change who may use Kafkador.

## Single-user apps

For an app one person runs on their own machine, `KAFKADOR_SETUP_AUTO=true` skips setup entirely. The database gets
generated credentials and nobody signs in.

Kafkador then has to listen only on this machine (`server.address=127.0.0.1`), or it won't start. Set
`KAFKADOR_ACCESS_MODE` as well to keep sign-in on. The [desktop app](../install/desktop.md) uses this mode.

## Upgrading from an older instance

An instance set up before sign-in existed asks the second step on its next start.
