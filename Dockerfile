FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
ENV MAVEN_CONFIG=""

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp verify \
    && cp target/policies-service-0.1.0-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21.0.7_6-jre-alpine AS runtime

WORKDIR /app
RUN apk add --no-cache wget \
    && addgroup -S spring \
    && adduser -S spring -G spring

COPY --from=build --chown=spring:spring /workspace/app.jar app.jar

USER spring
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
