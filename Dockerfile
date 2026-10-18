FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /opt/quay
COPY --from=build /src/api/target/quay-api.jar /opt/quay/quay-api.jar
EXPOSE 8091
ENV PORT=8091
ENTRYPOINT ["java", "-jar", "/opt/quay/quay-api.jar"]
