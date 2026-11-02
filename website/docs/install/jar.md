---
title: Jar and source builds
description: Build Kafkador from source and run the jar.
---

# Jar and source builds

Use this option on machines with Java and no Docker, or to work on Kafkador itself.

You need **JDK 21**. Maven comes with the project (`mvnw`). The build below downloads its own Node, so you don't need to
install it.

## Build and run

```bash
git clone https://github.com/farzinpashaee/kafkador.git
cd kafkador/backend
./mvnw clean package -P with-frontend
java -jar target/kafkador.jar
```

The UI and API are now on **http://localhost:8080**. Complete
[first-run setup](../getting-started/first-run-setup.md). Add `-DskipTests` to the build to skip the backend tests.

:::tip Keep your data
The database is created at `./data/kafkadordb`, relative to where you start the jar. Run it from a stable folder rather
than `target/`, which `mvnw clean` deletes.
:::

## Run it in the background

The build also generates `target/starter.sh`. It stops any running Kafkador, then starts the jar in the background and
writes `starter.pid` and `starter.log` next to it:

```bash
./starter.sh                                  # start or restart
./starter.sh --config /path/to/override.yml   # layer overrides on the packaged application.yml
./starter.sh --server.port=9090               # any other argument goes straight to java
```

## Build the backend and frontend separately

Without the `with-frontend` profile, the jar serves only the API:

```bash
cd backend
./mvnw clean package
```

The frontend needs Node 22.12 or newer:

```bash
cd frontend
npm ci
npm run build
```

This writes static files to `dist/kafkador/browser`. They call the API on the same origin (`/api/v1`), so serve them
from a web server that forwards `/api` to the backend.

## Develop Kafkador

Run the backend with the `dev` profile, then the Angular dev server on port 4200:

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
cd frontend && npm start
```

The `dev` profile lets the dev server call the API from its own origin, and turns on the H2 console at `/kafkador-h2`.
Don't use it in production, because the console gives full access to Kafkador's database.

Run the tests with `./mvnw test` in `backend/` and `npm test` in `frontend/`.
