---
title: Connecting from Docker
description: Reach Kafka from the Kafkador container, including short hostnames and Compose networks.
---

# Connecting from Docker

Kafkador runs inside a container, so **`localhost` means the container itself, not your machine.** Use one of these as
the connection host:

| Where Kafka runs | Host to enter in Kafkador |
|---|---|
| On the same machine, installed or in a container with a published port | `host.docker.internal` |
| On another server | Its IP address or DNS name, such as `10.0.0.12` |
| In another Docker Compose project | The Kafka service name, after [joining its network](#join-a-kafka-compose-network) |

## Check advertised listeners

Brokers must advertise an address that's reachable **from the container**. For example, on the same machine:

```properties
advertised.listeners=PLAINTEXT://host.docker.internal:9092
```

Otherwise, use the server's real IP address or hostname.

## Short hostnames

Your computer may turn a short name such as `kafka-1` into `kafka-1.home` or `kafka-1.corp.example.com`. The container
doesn't. If Kafkador logs `DNS resolution failed for kafka-1`, give the container your network's domain, or pin the name
to an address, in `docker-compose.yml`:

```yaml
services:
  kafkador:
    # ...existing settings...
    dns_search: home                      # your network's domain
    extra_hosts:
      - "host.docker.internal:host-gateway"
      - "kafka-1:192.168.1.30"            # or pin the name to the broker's address
```

Brokers usually advertise their short name, so typing the full name in Kafkador isn't enough. Recreate the container
afterwards:

```bash
docker compose up -d
```

## Join a Kafka Compose network

If Kafka runs in another Compose project, attach Kafkador to that project's network. Find its name with
`docker network ls`, then add this to `docker-compose.yml`:

```yaml
services:
  kafkador:
    # ...existing settings...
    networks: [ default, kafka-net ]

networks:
  kafka-net:
    external: true
    name: my-kafka_default   # the name from "docker network ls"
```

Then use the Kafka service name, such as `kafka`, and its internal port as the connection host and port.
