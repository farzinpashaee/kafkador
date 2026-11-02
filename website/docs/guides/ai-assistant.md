---
title: AI assistant
description: Ask read-only questions about the connected cluster.
---

# AI assistant

The optional AI assistant answers questions about your cluster, brokers and listeners, topics, consumer groups, schemas,
ksqlDB, connectors and Kafkador setup. It has read-only access, so it can explain and troubleshoot but never change
anything.

:::caution What leaves your network
To answer each question, Kafkador sends cluster metadata (names, counts and non-secret configuration) to the provider
you choose. Check that this is allowed for your clusters before you turn it on.
:::

## Turn it on

1. Go to **Settings → AI Assistant**.
2. Turn on **Enable AI assistant**.
3. Choose a **Provider**:

   | Provider | Default model |
   |---|---|
   | OpenAI (ChatGPT) | `gpt-4o-mini` |
   | Anthropic (Claude) | `claude-sonnet-5` |
   | Google (Gemini) | `gemini-2.0-flash` |

4. Paste your **API Key**. It's stored on the Kafkador server and never sent back to the browser.
5. Optionally, set a **Model**, or a **Base URL** for a proxy or an OpenAI-compatible server.
6. Select **Save**.

## Ask a question

Open the assistant from the button in the bottom-right corner. Chats are saved, and you can reopen them from **Chat
history**.
