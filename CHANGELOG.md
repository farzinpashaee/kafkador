# Changelog

All notable changes to Kafkador are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0-beta.1] - 2026-10-25

The first public release.

### Added

- **Clusters and brokers:** cluster overview with partition health; broker details and editable configuration;
  broker and topic metrics from the Confluent metrics topics, with headline numbers, trends and searchable groups.
- **Topics and records:** create, configure and delete topics; browse records from the newest, the oldest, an offset
  or a time, with filters, live tailing and export; produce records with headers.
- **Schema Registry decoding** of keys and values written with Avro, Protobuf and JSON Schema, picked automatically
  when the topic has a subject.
- **Consumer groups:** members, assignments and lag, and **offset reset** (earliest, latest, a time or an offset),
  previewed before it is applied.
- **ACLs:** list, create, edit and delete bindings.
- **Schema Registry:** subjects, versions, side-by-side diffs and compatibility settings.
- **Kafka Connect** connectors and plugins, and **ksqlDB** streams, tables, queries and statements.
- Connections with TLS and SASL (PLAIN, SCRAM-SHA-256/512); Basic or bearer authentication and a custom CA for
  Schema Registry, Connect and ksqlDB.
- An optional AI assistant for questions about the connected cluster.
- About page; collapsible side menu; clickable table rows.
- One jar (`-P with-frontend`) or one Docker image serving the API and UI, with a healthcheck on `/actuator/health`.
- Optional Java agent (`agent/`) that reports broker metrics to Kafkador.

### Security

- **Sign-in.** First-run setup asks who may use Kafkador: an admin account kept in its database (password hashed
  with PBKDF2), LDAP / Active Directory accounts, or nobody needs to sign in. Settings → Access changes it later and
  signs everyone else out; `KAFKADOR_ACCESS_*` presets it. Five wrong passwords for a username from one address
  block it for 15 minutes.
- Requests that change something need a CSRF token (the `XSRF-TOKEN` cookie, sent back in `X-XSRF-TOKEN`).
- First-run setup asks for a one-time token printed in the log, or takes credentials from
  `KAFKADOR_SETUP_USERNAME` / `KAFKADOR_SETUP_PASSWORD`.
- The H2 console and cross-origin API calls are on only in the `dev` profile.
- Sessions start with a 30-minute idle timeout, configurable up to 24 hours.
- Live record views are capped at 3 per session, 20 in total, and 30 minutes each.

### Changed

- The version is set once, in `backend/pom.xml`, and the UI shows what the backend reports (`/api/v1/app-info`).
- Licensed under the [PolyForm Shield License 1.0.0](LICENSE) (source-available).

### Removed

- The legacy server-rendered (Thymeleaf) UI; the Angular app replaces it.

[Unreleased]: https://github.com/farzinpashaee/kafkador/compare/v1.0.0-beta.1...HEAD
[1.0.0-beta.1]: https://github.com/farzinpashaee/kafkador/releases/tag/v1.0.0-beta.1
