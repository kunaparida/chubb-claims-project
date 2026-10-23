# syntax=docker/dockerfile:1

# ---- build stage: compile and package the Spring Boot jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached until pom.xml changes.
# "|| true": this is only a cache warm-up; the real build below still fails loudly if something is missing.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
# Tests are skipped by default to keep the image build fast (run them with `mvn test`);
# build with  --build-arg SKIP_TESTS=false  to run them during the image build.
ARG SKIP_TESTS=true
RUN mvn -B -q clean package -DskipTests=${SKIP_TESTS}

# ---- runtime stage: JRE only, non-root ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/claims-management-*.jar /app/app.jar
USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
