---
title: Configuration
description: Every Kafkador setting, as a property and an environment variable.
---

# Configuration

Kafkador is a Spring Boot application, so you can set any property below in three ways:

- as an environment variable, which is the usual choice in Docker
- as a command-line argument, such as `--server.port=9090`
- in an override file, with `./starter.sh --config /path/to/override.yml`

## First-run setup

| Environment variable | Default | Description |
|---|---|---|
| `KAFKADOR_SETUP_USERNAME` | | Database username. Setting this and the password skips the setup page |
| `KAFKADOR_SETUP_PASSWORD` | | Database password, at least 8 characters |
| `KAFKADOR_SETUP_TOKEN` | Random | The one-time setup token |
| `KAFKADOR_SETUP_AUTO` | `false` | [Skip setup for a single-user app](../getting-started/first-run-setup.md#single-user-apps) |

## Access

These apply at every start.

| Environment variable | Description |
|---|---|
| `KAFKADOR_ACCESS_MODE` | `none`, `database` or `ldap`. Leave it empty to manage access in Settings |
| `KAFKADOR_ACCESS_ADMINUSERNAME` | Admin username, for `database` |
| `KAFKADOR_ACCESS_ADMINPASSWORD` | Admin password, 8-128 characters, for `database` |
| `KAFKADOR_ACCESS_LDAP_URL` | `ldap://host:389` or `ldaps://host:636` |
| `KAFKADOR_ACCESS_LDAP_BASEDN` | Such as `dc=example,dc=com` |
| `KAFKADOR_ACCESS_LDAP_USERDNPATTERN` | Such as `uid={0},ou=people`, relative to the base DN |
| `KAFKADOR_ACCESS_LDAP_USERSEARCHBASE` | Such as `ou=people`, when searching |
| `KAFKADOR_ACCESS_LDAP_USERSEARCHFILTER` | Such as `(uid={0})`, when searching |
| `KAFKADOR_ACCESS_LDAP_MANAGERDN` | The account to search with, if searches can't be anonymous |
| `KAFKADOR_ACCESS_LDAP_MANAGERPASSWORD` | That account's password |

## Live record views

| Property | Default | Description |
|---|---|---|
| `kafkador.records.live.max-per-session` | `3` | Live views per session |
| `kafkador.records.live.max-total` | `20` | Live views across the server |
| `kafkador.records.live.max-duration` | `30m` | How long one live view can run |

## Other properties

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | Port to listen on |
| `server.address` | All addresses | Address to listen on, such as `127.0.0.1` |
| `kafkador.cors.allowed-origins` | None | Other origins allowed to call the API from a browser |
| `kafkador.db-credentials-file` | `./data/db-credentials.properties` | Where the database credentials are kept |

## Docker variables

These are read by `docker-compose.yml` from your `.env` file:

| Variable | Default | Description |
|---|---|---|
| `KAFKADOR_VERSION` | `latest` | Image tag |
| `KAFKADOR_PORT` | `8080` | Host port. `127.0.0.1:8080` publishes it on this machine only |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75.0` | JVM options |

## Settings kept in the database

You change these in the app rather than in configuration:

| Setting | Where |
|---|---|
| Session timeout | **Settings → Generic** |
| TLS, SASL and truststore | **Settings → Security** |
| Who may use Kafkador | **Settings → Access** |
| Schema Registry, ksqlDB and Connect | **Settings → Schema Registry**, **KsqlDB** and **Connectors** |
| AI assistant | **Settings → AI Assistant** |
