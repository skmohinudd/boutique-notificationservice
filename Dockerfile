# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode --no-transfer-progress -Dmaven.wagon.http.retryHandler.count=5 clean package -DskipTests \
 && JAR_FILE="$(find target -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -1)" \
 && test -n "$JAR_FILE" \
 && cp "$JAR_FILE" /app/app.jar
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 --create-home appuser
COPY --from=build --chown=appuser:appuser /app/app.jar /app/app.jar
USER appuser
EXPOSE 8088
ENTRYPOINT ["java","-Duser.timezone=UTC","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
