# Verwende eine schlanke JDK 17 Runtime
FROM eclipse-temurin:17-jdk-alpine

# Setze Arbeitsverzeichnis im Container
WORKDIR /app

# Kopiere fertiges Spring Boot Jar ins Image
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# Expose Port (Railway mappt automatisch nach außen)
EXPOSE 8080

# Starte die App
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
