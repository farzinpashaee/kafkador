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

## 📦 Building the final package

The repository has two modules: `backend/` (Spring Boot) and `frontend/` (Angular). Both build modes need JDK 21.

### Default: build the backend and frontend separately

```bash
# backend — API-only jar plus a start/stop script
cd backend
./mvnw clean package
#   -> target/kafkador.jar, target/starter.sh

# frontend — static files (Node 22.12+ / npm)
cd ../frontend
npm ci
npm run build
#   -> dist/kafkador/browser
```

`npm run build` produces a production build that calls the API on the **same origin** (`/api/v1`), so serve
`dist/kafkador/browser` from a web server that forwards `/api` to the backend.

### Option: one jar that serves both the API and the UI

```bash
cd backend
./mvnw clean package -P with-frontend
java -jar target/kafkador.jar        # UI and API on http://localhost:8080
```

The `with-frontend` profile downloads a local Node/npm (nothing to install), runs `npm ci` and `npm run build`
in `frontend/`, and copies the result into the jar's `static/` folder. Add `-DskipTests` to skip backend tests.
When the UI is bundled the jar serves the Angular app for every non-API URL (deep links and refreshes work) and the
old server-rendered pages are switched off; without the profile nothing changes.

### Starting the built jar

`target/starter.sh` (generated during `package`) stops any running Kafkador instance, then starts the jar in the
background, writing `starter.pid` and `starter.log` next to it:

```bash
./starter.sh                                  # start / restart
./starter.sh --config /path/to/override.yml   # layer overrides on top of the packaged application.yml
./starter.sh --server.port=9090               # any other argument is passed straight to java
```

The H2 database is created at `./data/kafkadordb` relative to where the jar is started, so run it from a stable
directory (not `target/`, which `mvn clean` deletes) if you want to keep your data.

---
## Versions
### v1
- Dashboard
- Cluster
- Brokers
- Topics
- Consumers
