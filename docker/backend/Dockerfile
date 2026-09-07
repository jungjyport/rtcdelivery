# Spring Boot 서비스 공통 이미지 (Java 21)
# compose에서 SERVICE_PATH 로 대상 모듈을 지정한다.
FROM gradle:8.12-jdk21 AS builder
WORKDIR /app
ARG SERVICE_PATH
COPY ${SERVICE_PATH}/settings.gradle ${SERVICE_PATH}/build.gradle ./
COPY ${SERVICE_PATH}/src ./src
RUN gradle bootJar --no-daemon -x test \
    && rm -f build/libs/*-plain.jar \
    && mv build/libs/*.jar /tmp/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /tmp/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
