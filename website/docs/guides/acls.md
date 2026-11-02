---
title: ACLs
description: Manage ACL bindings, and enable an authorizer on your brokers.
---

# ACLs

**Access Control** lists the ACL bindings on the cluster, and lets you create, edit and delete them.

These are Kafka's own permissions for clients. They're separate from [who may use Kafkador](../security/sign-in.md).

## Create a binding

| Field | Meaning |
|---|---|
| **Resource Type** | What the rule covers, such as a topic |
| **Resource Name** | Its name |
| Pattern | `LITERAL` matches the exact name, and `PREFIXED` matches names that start with it |
| Permission | `ALLOW` or `DENY` |
| **Principal** | Who it applies to, such as `User:orders-service` |
| **Host** | Where they connect from |
| **Operation** | The action, such as read or write |

## Enable an authorizer

If Kafka reports that no ACL authorizer is configured, brokers reject every ACL request, and Kafkador can't list, create
or delete bindings. Enabling one is a broker-side change.

1. Add an authorizer to each broker's `server.properties`.

   For KRaft mode (Kafka 3.3 or newer, no ZooKeeper):

   ```properties
   authorizer.class.name=org.apache.kafka.metadata.authorizer.StandardAuthorizer
   super.users=User:ANONYMOUS
   ```

   For ZooKeeper:

   ```properties
   authorizer.class.name=kafka.security.authorizer.AclAuthorizer
   super.users=User:ANONYMOUS
   ```

2. Restart the broker. The authorizer is only read at startup.

:::warning `User:ANONYMOUS` is only for local testing
Super users bypass every ACL, including DENY rules. Clients on a listener without authentication all connect as
`User:ANONYMOUS`, so listing it in `super.users` exempts all of them. Your ACLs will appear to have no effect.
:::

## ACLs that actually restrict clients

- Enable authentication, such as a `SASL_SSL` listener, so each client connects as its own principal.
- Put only an admin principal in `super.users`, such as `User:admin`, and connect Kafkador as that user.
- Keep the brokers' own principal in `super.users` too. If brokers talk to each other over a PLAINTEXT listener, they're
  `User:ANONYMOUS` and must stay super users, so keep that listener internal and point clients at the authenticated one.
- Leave `allow.everyone.if.no.acl.found` at its default of `false`, so resources without ACLs are denied.
