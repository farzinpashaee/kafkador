---
title: TLS and SASL
description: Connect to clusters secured with TLS, SASL/PLAIN or SCRAM.
---

# TLS and SASL

Kafkador connects to clusters secured with TLS and with SASL using PLAIN, SCRAM-SHA-256 or SCRAM-SHA-512.

## Sign in with SASL

When you add a connection, turn on **This cluster requires authentication (SASL)**, then match the broker listener you
connect to:

| Field | Options |
|---|---|
| **Security protocol** | `SASL_PLAINTEXT` or `SASL_SSL` |
| **SASL mechanism** | `PLAIN`, `SCRAM-SHA-256` or `SCRAM-SHA-512` |

A password you enter here is kept in server memory for this session only and is never saved. You're asked again after
disconnecting.

## Save the settings for a cluster

Once connected, go to **Settings → Security**. These settings apply to every session using this cluster.

1. Turn on **Connect over TLS (SSL)** if the listener uses TLS.
2. Choose an **Authentication method**: None, SASL/PLAIN, SASL/SCRAM-SHA-256 or SASL/SCRAM-SHA-512.
3. Enter the **Username**. The **Password** is optional here.
4. Select **Save**. Kafkador first checks that the cluster accepts a connection with these settings.

If you save a password, it's stored in Kafkador's database and used automatically when connecting. Without one, you're
asked each time. To remove a saved password, select **Forget the saved password**.

## Trust a private CA

You only need a truststore when the brokers' certificates are signed by a private or self-signed CA. Without one, Kafkador
uses Java's default trusted CAs.

1. Go to **Settings → Security → Truststore**.
2. Choose the file (`.jks`, `.p12`, `.pfx` or `.pkcs12`).
3. Choose the **Type** (`PKCS12` or `JKS`) and enter the **Truststore password**.
4. Select **Upload truststore**.
5. Turn on **Connect over TLS (SSL)** if it isn't already on.

To go back to the default CAs, select **Remove from config**.
