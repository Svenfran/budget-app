# ---- Build Stage ----
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app

# nur pom.xml und src kopieren, damit Docker Caching optimal genutzt wird
COPY pom.xml .
COPY src ./src

# Maven build -> erstellt das JAR im target/ Verzeichnis
RUN mvn clean package -DskipTests

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jdk
WORKDIR /app

# JAR aus dem Build Stage übernehmen
COPY --from=build /app/target/*.jar app.jar

# Port öffnen (Railway mapped automatisch auf extern)
EXPOSE 8080

# Spring Boot mit Prod-Profile starten
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
