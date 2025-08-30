# ---- Build Stage ----
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app

# zuerst nur pom.xml kopieren für Dependency-Caching
COPY pom.xml .

# Dependencies auflösen (schnellerer Build, falls src sich ändert)
RUN mvn dependency:go-offline

# jetzt Source-Code kopieren
COPY src ./src

# Baue Projekt
RUN mvn clean package -DskipTests

# DEBUG: prüfe, was im target-Verzeichnis liegt
RUN echo "===== Inhalt von /app nach Maven Build =====" && ls -l /app \
 && echo "===== Inhalt von /app/target =====" && ls -l /app/target || echo "Kein target gefunden"

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# JAR übernehmen
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Spring Boot starten mit Prod-Profil
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
