---
title: Data and backups
description: Where Kafkador keeps its data, and how to back it up and restore it.
---

# Data and backups

Kafkador keeps connections, settings, saved credentials and sessions in an embedded H2 database:

| How you run it | Where the data is |
|---|---|
| Docker | The `kafkador-data` volume, mounted at `/app/data` |
| Jar | `./data`, relative to where you start it |
| Desktop app | Your user app-data folder |

The folder also holds `db-credentials.properties`, the database credentials chosen during setup. **Back up and restore
them together**, because the database can't be opened without the matching credentials.

The Docker volume survives restarts, upgrades and `docker compose down`. Only `docker compose down -v` deletes it.

## Back up

```bash
docker compose stop
docker run --rm -v kafkador_kafkador-data:/data -v "$PWD":/backup alpine tar czf /backup/kafkador-data.tgz -C /data .
docker compose start
```

## Restore

:::danger
Restoring replaces everything in the volume.
:::

```bash
docker compose stop
docker run --rm -v kafkador_kafkador-data:/data -v "$PWD":/backup alpine sh -c "rm -rf /data/* && tar xzf /backup/kafkador-data.tgz -C /data"
docker compose start
```

The volume name starts with the Compose project name, which is the folder name by default. Check it with
`docker volume ls`.

## Use a host folder

To keep the data in a folder rather than a named volume, replace `kafkador-data:/app/data` with `./data:/app/data` in
`docker-compose.yml`. Then make the folder writable by the container user, whose UID is `10001`:

```bash
sudo chown -R 10001:10001 ./data
```
