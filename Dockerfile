# Multi-stage: build with the JDK, ship only the JRE.
#
# Railway detects this file automatically and ignores Nixpacks, which is the
# point -- Nixpacks would guess a JDK version, and this project needs 17.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Dependencies resolve in their own layer, so a code-only change does not
# re-download the world on every deploy.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Tests are skipped here deliberately: OrderingIntegrationTest writes a SQLite
# file, and volumes are not mounted during the build step. Run them in CI or
# locally, not in the deploy path.
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

# Wildcard because the artifact name comes from your pom, not from this file.
COPY --from=build /app/target/*.jar app.jar

# DB_PATH is deliberately NOT set here. Unset, the app writes to the working
# directory, which on a container platform is ephemeral -- the board resets to
# the fixture on every restart, which is a reasonable demo default.
#
# For persistence, mount a volume and set DB_PATH as a service variable to a
# path inside it. Baking a default in here would force every deployment to
# provide that mount, whether it wanted persistence or not.

# No USER directive on purpose: Railway mounts volumes as root, and a non-root
# container would need RAILWAY_RUN_UID=0 set on the service to write to one.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
