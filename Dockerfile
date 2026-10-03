# syntax=docker/dockerfile:1

# Single image: the Angular UI is bundled into the Spring Boot jar (same as "mvnw package -P with-frontend"),
# so one container serves both the UI and the API on port 8080.
#
# The two build stages run on the builder's native platform ($BUILDPLATFORM): their output (static JS and a
# jar) is architecture independent, so only the small runtime stage is built per target platform.

# ---- 1. Frontend build -------------------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM node:22-alpine AS frontend
ENV NG_CLI_ANALYTICS=false
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --include=dev
COPY frontend/ ./
RUN npm run build

# ---- 2. Backend build --------------------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /src/backend
# Resolve dependencies in their own layer so source-only changes don't re-download them.
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
# Bundle the UI: BundledFrontend detects classpath:/static/index.html and serves the Angular app.
COPY --from=frontend /src/frontend/dist/kafkador/browser/ ./src/main/resources/static/
# Tests run in CI before the image is published.
RUN mvn -B -DskipTests package

# ---- 3. Runtime --------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

RUN groupadd --system --gid 10001 kafkador \
 && useradd --system --uid 10001 --gid kafkador --home-dir /app --no-create-home kafkador \
 && mkdir -p /app/data \
 && chown -R kafkador:kafkador /app

WORKDIR /app
COPY --from=backend --chown=kafkador:kafkador /src/backend/target/kafkador.jar /app/kafkador.jar

USER kafkador

# The H2 database is created at ./data/kafkadordb relative to the working directory.
VOLUME ["/app/data"]
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD bash -c '</dev/tcp/127.0.0.1/8080' || exit 1

# exec so java is PID 1 and receives SIGTERM; extra "docker run" arguments are passed on to Spring Boot.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/kafkador.jar \"$@\"", "--"]
