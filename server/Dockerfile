FROM eclipse-temurin:25-jre

WORKDIR /app
COPY server/novelka-server.jar /app/novelka-server.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/novelka-server.jar"]
