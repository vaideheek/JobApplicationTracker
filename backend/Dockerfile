# Stage 1: Build stage using Maven Wrapper
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
# Resolve dependencies to cache them in the Docker layer
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as non-root user 'nobody' (UID/GID 65534)
USER 65534:65534

COPY --from=builder /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Limit JVM heap utilization in 512 MB production environment
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:MinRAMPercentage=50.0"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
