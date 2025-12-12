# 🚀 Kafkador - Kafka UI Dashboard

**Kafkador** is a modern, open-source **Kafka UI Dashboard** built with **Java + Spring Boot**, designed to provide developers and operators with an intuitive interface for managing and monitoring Apache Kafka clusters.

> 🔧 Compatible with **JDK 17+**  
> ☕️ Built with **Spring Boot**
---

## 📸 Features

- 🔍 **Cluster Overview**
  - View cluster metadata (Cluster ID, Controller, Brokers)
  - Broker health and configuration

- 👥 **Consumer Group Insights**
  - Active consumer groups and their states
  - Partition assignments and consumer lag

- 📦 **Topic Explorer**
  - List and inspect all topics
  - View partition count, replication factor, and leader status
  - Topic configuration inspection (e.g., `retention.ms`, `cleanup.policy`)

- 📊 **Partition Details**
  - Leader and replica assignments
  - In-sync replica status
  - Under-replicated partitions

- 🛡️ **Security Ready**
  - TLS & SASL support (if Kafka is secured)
  - No data is stored or leaked — real-time view only

- 📁 **Easy Integration**
  - Plug into any Apache Kafka cluster (v2.8+ recommended)

---

## 🏗️ Architecture

- **Backend**: Java 17+, Spring Boot 3.x, Kafka AdminClient
- **Frontend**: (Optional) Can be integrated with React/Semantic UI for visualization *(not included here)*

---

## 🚀 Getting Started

### 🔧 Prerequisites

- Java 17+
- Apache Kafka cluster (local or remote)
- Maven 3.6+ or Gradle

### 📥 Clone & Run

```bash
git clone https://github.com/your-org/kafkador.git
cd kafkador
./mvnw spring-boot:run
```
---
## Versions
### v1
- Dashboard
- Cluster
- Brokers
- Topics
- Consumers
