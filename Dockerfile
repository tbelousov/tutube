# ---------- Сборка ----------
FROM gradle:8.10-jdk17-alpine AS build
WORKDIR /app

# Копируем всё (включая gradlew и папку gradle, если они есть)
# Если у вас есть gradle wrapper, он будет использован автоматически.
COPY --chown=gradle:gradle . .

# Сборка fat-jar (Spring Boot)
# -x test (пропускаем тесты) и --no-daemon для контейнеров
RUN gradle clean bootJar -x test --no-daemon

# ---------- Финальный образ ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Устанавливаем curl для healthcheck (в alpine wget может отсутствовать)
RUN apk add --no-cache curl

# Создаём непривилегированного пользователя
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем собранный JAR
# В build/libs обычно лежит один bootJar — заберём его как app.jar
COPY --from=build /app/build/libs/*.jar /app/app.jar

# Expose порт
EXPOSE 8080

# Health check (Spring Boot actuator)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD curl -fsS http://localhost:8080/actuator/health >/dev/null || exit 1

# Запуск
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"]