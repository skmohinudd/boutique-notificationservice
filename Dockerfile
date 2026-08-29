# Build the Notification Service JAR with Java 21
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

# Copy Maven files first for better dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Download dependencies before copying source code
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/

# Build JAR; tests are executed by CI
RUN ./mvnw --batch-mode --no-transfer-progress clean package -DskipTests \
    && JAR_FILE="$(find target -maxdepth 1 -type f -name '*.jar' \
       ! -name 'original-*.jar' \
       ! -name '*-sources.jar' \
       ! -name '*-javadoc.jar' | head -1)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" /app/app.jar


# Use a smaller Java runtime image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Run the worker as a non-root user
RUN useradd --system --uid 10001 --no-create-home appuser

COPY --from=build --chown=10001:10001 /app/app.jar /app/app.jar

USER 10001

# Notification is a background worker, so no HTTP port is exposed
ENTRYPOINT ["java","-Duser.timezone=UTC","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]