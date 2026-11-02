---
title: Troubleshooting
description: Fixes for common connection, setup and sign-in problems.
---

# Troubleshooting

## Connecting to Kafka

### The connection fails from Docker

`localhost` inside the container is the container itself. Use `host.docker.internal` or the server's real address. See
[Connecting from Docker](./connect/from-docker.md).

### It connects, then can't reach the brokers

The broker is probably advertising an address Kafkador can't reach, such as `localhost:9092`. Set
[`advertised.listeners`](./connect/clusters.md#reach-the-brokers-it-advertises) to a reachable address.

### The log says `DNS resolution failed`

The container doesn't add your network's domain to short hostnames. Use `dns_search` or `extra_hosts`. See
[Short hostnames](./connect/from-docker.md#short-hostnames).

### TLS connections fail with a certificate error

If your brokers use a private or self-signed CA, [upload a truststore](./connect/tls-and-sasl.md#trust-a-private-ca).

### I'm asked for a password every time

A password typed when connecting isn't saved. To save one,
[use Settings → Security](./connect/tls-and-sasl.md#save-the-settings-for-a-cluster).

## Setup and sign-in

### Where's the setup token?

It's printed in the log at startup:

```bash
docker compose logs kafkador | grep -A1 "setup token"
```

### I lost the admin password

[Set a new one with environment variables](./security/sign-in.md#recover-a-lost-admin-password) and restart.

### Settings → Access can't be changed

`KAFKADOR_ACCESS_MODE` is set. Remove it and restart, or change the variables instead.

### A username is blocked

Five wrong passwords for a username from one address block it for 15 minutes. Wait for the block to end.

### Kafkador won't start with `KAFKADOR_SETUP_AUTO`

With no sign-in, Kafkador has to listen only on this machine. Set `server.address=127.0.0.1`, or also set
`KAFKADOR_ACCESS_MODE`.

## Using Kafkador

### ACLs are unavailable

Your brokers have no authorizer. [Enable one](./guides/acls.md#enable-an-authorizer).

### My ACLs have no effect

Clients on an unauthenticated listener are `User:ANONYMOUS`. If that's in `super.users`, they
[bypass every ACL](./guides/acls.md#acls-that-actually-restrict-clients).

### Offset reset is blocked

Kafka only allows a reset while the group has no active members. Stop its consumers first.

### A record search stops early

A filtered read stops at its scan limit. Choose fewer partitions or a later start.

### Broker metrics are missing

Kafkador reads metrics from the Confluent metrics topics. On a cluster that doesn't publish them, the
[metrics pages](./guides/overview.md#metrics) have no data.

### Live view stops working behind a proxy

Turn off response buffering for Kafkador. See the [Nginx example](./security/production.md#use-https).

## Getting help

Check the container's log first:

```bash
docker compose logs -f kafkador
```

If that doesn't explain it, [open an issue](https://github.com/farzinpashaee/kafkador/issues) with your Kafkador version
and the relevant part of the log. Remove any hostnames or credentials you don't want to share.
