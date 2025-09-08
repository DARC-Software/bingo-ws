# --- Build Stage ---
FROM gradle:8.14.3-jdk21 AS build
COPY . /app
WORKDIR /app
RUN gradle clean build -x test --no-daemon

# --- Runtime Stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

# --- END ---