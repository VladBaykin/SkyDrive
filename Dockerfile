FROM gradle:8.8-jdk17-alpine AS build
WORKDIR /workspace
COPY . .
RUN gradle clean bootJar -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
