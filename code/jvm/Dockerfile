FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY gradlew gradlew.bat* ./
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts* ./
RUN ./gradlew dependencies --no-daemon --quiet || true

COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
