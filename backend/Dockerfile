# Stage 1: Build selected module
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
ARG SERVICE=game-service
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY game-service/pom.xml game-service/pom.xml
COPY social-service/pom.xml social-service/pom.xml
COPY common ./common
COPY auth-service ./auth-service
COPY game-service ./game-service
COPY social-service ./social-service
RUN mvn -pl ${SERVICE} -am clean package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
ARG SERVICE=game-service
COPY --from=build /app/${SERVICE}/target/${SERVICE}.jar app.jar
EXPOSE 8081 8082 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
