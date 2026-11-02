---
title: Desktop app
description: Run Kafkador as a Windows or Mac app with its own Java runtime.
---

# Desktop app

The desktop app runs Kafkador in its own window for one person on their own computer. It bundles the same server as the
Docker image and its own Java runtime, so there's nothing else to install.

:::note Not released yet
The desktop app isn't part of a published release. Until it is, [build the installer yourself](#build-the-installer).
:::

## How it differs from Docker

- It runs on this machine only (`127.0.0.1`), so nobody else can reach it.
- There's no setup page and no sign-in. The database gets generated credentials.
- Only one copy runs at a time, and closing the window stops the server.

## Install

| Platform | Installer |
|---|---|
| Windows (x64) | `Kafkador-<version>-win-x64.exe` |
| Mac with Apple Silicon | `Kafkador-<version>-mac-arm64.dmg` |
| Mac with Intel | `Kafkador-<version>-mac-x64.dmg` |

The installers are **unsigned** for now, so your system warns you before opening them.

- **Windows:** when SmartScreen appears, select **More info → Run anyway**.
- **Mac:** drag the app to Applications, then run this command before opening it:

```bash
xattr -cr /Applications/Kafkador.app
```

## Where your data is

Connections, settings and the log are kept in your user app-data folder. The log is at `logs/kafkador.log` in that
folder. If the app fails to start, look there first.

## Build the installer

You need a JDK 21 that includes `jmods`. The build makes an installer for the machine it runs on, and a Mac installer
can only be built on a Mac.

```bash
cd backend
./mvnw clean package -P with-frontend,desktop -DskipTests
```

The installer is written to `desktop/dist/`.
