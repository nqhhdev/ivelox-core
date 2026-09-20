# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/ivelox-core-*.jar /app/app.jar
ENV PORT=8080
# Faster first requests on small Fly VMs; keep container RAM under control.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -XX:+UseG1GC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
