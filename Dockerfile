FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/jobhunter-ai-0.1.0.jar app.jar
USER 1000
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
