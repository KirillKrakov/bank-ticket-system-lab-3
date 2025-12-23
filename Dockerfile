FROM maven:3.8-openjdk-17 AS builder
ARG MODULE_PATH
ARG JAR_NAME
WORKDIR /workspace

COPY . .

RUN mvn -B -DskipTests clean package -pl ${MODULE_PATH} -am

FROM eclipse-temurin:17-jre
ARG MODULE_PATH
ARG JAR_NAME
WORKDIR /app
COPY --from=builder /workspace/$MODULE_PATH/target/$JAR_NAME app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]