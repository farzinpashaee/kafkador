---
title: Running in production
description: Sign-in, HTTPS and network advice for shared Kafkador instances.
---

# Running in production

Whoever uses Kafkador can browse your clusters, read and produce records, change topics, ACLs and consumer offsets, and
use any credentials saved in it. Treat access to Kafkador like access to Kafka itself.

## Checklist

- [ ] [Turn sign-in on](./sign-in.md).
- [ ] Serve Kafkador over HTTPS.
- [ ] Keep it on a private network or behind a VPN, rather than on the internet.
- [ ] Pin the image to a version.
- [ ] [Back up its data](../reference/data-and-backup.md).
- [ ] Don't run the `dev` profile.

## Use HTTPS

Kafkador speaks plain HTTP, so passwords cross the network in the clear unless a reverse proxy adds HTTPS. Here's an
Nginx example. Turning off buffering keeps [live record views](../guides/records.md#follow-new-records-live) working:

```nginx
server {
    listen 443 ssl;
    server_name kafkador.example.com;

    ssl_certificate     /etc/ssl/kafkador/fullchain.pem;
    ssl_certificate_key /etc/ssl/kafkador/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_buffering off;
        proxy_read_timeout 1h;
    }
}
```

Publish the container on this machine only, so that traffic has to go through the proxy. In `.env`:

```bash
KAFKADOR_PORT=127.0.0.1:8080
```

## What's protected by default

- Requests that change something need a CSRF token.
- The H2 console and cross-origin API calls are off outside the `dev` profile.
- `/actuator/health` answers only UP or DOWN, with no details.
- A password you type for a SASL connection stays in memory unless you
  [save it in Settings](../connect/tls-and-sasl.md#save-the-settings-for-a-cluster).
- The first-run setup page needs a one-time token.
