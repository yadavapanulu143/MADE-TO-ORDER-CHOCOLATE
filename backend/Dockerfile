# syntax=docker/dockerfile:1
#
# One parameterised image recipe for the gateway and all seven services. The service is chosen at
# build time with `--build-arg SERVICE_MODULE=<maven module path>`, so a new service needs a compose
# entry rather than a new Dockerfile that can drift from its siblings.
#
#   docker build -f backend/Dockerfile \
#     --build-arg SERVICE_MODULE=services/identity-service/identity-app \
#     --build-arg SERVICE_JAR=identity-app-*.jar \
#     --build-arg SERVICE_PORT=8081 -t choco/identity-service backend
#
# The build is staged so a source change re-uses the Maven dependency layer: the POMs are copied and
# resolved first, then the sources.

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre

# ---- build stage -------------------------------------------------------------------------------
FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace

# Parent POM and every module POM, so dependency resolution is cached independently of the sources.
COPY pom.xml ./
COPY shared/common-core/pom.xml shared/common-core/
COPY shared/common-web/pom.xml shared/common-web/
COPY shared/common-security/pom.xml shared/common-security/
COPY shared/common-events/pom.xml shared/common-events/
COPY shared/common-redis/pom.xml shared/common-redis/
COPY shared/common-test/pom.xml shared/common-test/
COPY gateway/pom.xml gateway/
COPY services/identity-service/pom.xml services/identity-service/
COPY services/catalog-service/pom.xml services/catalog-service/
COPY services/order-service/pom.xml services/order-service/
COPY services/payment-service/pom.xml services/payment-service/
COPY services/fulfilment-service/pom.xml services/fulfilment-service/
COPY services/notification-service/pom.xml services/notification-service/
COPY services/reporting-service/pom.xml services/reporting-service/

# Warm the local repository. `|| true` because a module POM that resolves fine as part of the reactor
# can still fail go-offline in isolation; the real build below is what must succeed.
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY shared ./shared
COPY gateway ./gateway
COPY services ./services

ARG SERVICE_MODULE
# Build the target module and everything it depends on. Tests are skipped here because the CI
# pipeline runs `mvn verify` before this image is ever built — an image build is not a test run.
RUN mvn -B -DskipTests -pl ${SERVICE_MODULE} -am package

ARG SERVICE_JAR
# Normalise the fat jar to a fixed name so the runtime stage needs no per-service knowledge.
RUN cp $(find ${SERVICE_MODULE}/target -maxdepth 1 -name "${SERVICE_JAR}" ! -name "*.original" | head -1) /workspace/app.jar

# ---- runtime stage -----------------------------------------------------------------------------
#
# A glibc base image, deliberately not alpine. The identity service hashes passwords with Argon2id
# through JNA, which loads a native library; on musl that library is built and loaded in a way that
# segfaults the JVM under concurrency (a real crash observed as SIGSEGV inside jna*.tmp while hashing
# during registration). A glibc image runs the same native code without the crash. The cost is a
# larger image; the alternative is a JVM that dies under load.
FROM ${JRE_IMAGE}

# tini reaps the JVM and forwards signals, so `docker stop` reaches the app's graceful shutdown.
# curl is used by the health check; the JRE base image has curl but not tini.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tini \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1001 choco \
    && useradd --system --uid 1001 --gid choco --home-dir /app --shell /usr/sbin/nologin choco

WORKDIR /app
COPY --from=build --chown=choco:choco /workspace/app.jar /app/app.jar
COPY --chmod=0755 docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080

# Container-aware JVM flags: respect the cgroup limit, exit on OOM rather than thrash, and use the
# container's available processors.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport"

# Starts as root so the entrypoint can stage a mounted signing key for the unprivileged user, then
# drops to `choco` before the JVM starts. The application itself never runs as root.
ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/docker-entrypoint.sh", "java", "-jar", "/app/app.jar"]
