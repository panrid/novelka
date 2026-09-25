# The image only wraps the JAR that CI built and tested (the site is inside it).
FROM eclipse-temurin:25-jre

WORKDIR /app
COPY novelka.jar /app/novelka.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=70", "-jar", "/app/novelka.jar"]
