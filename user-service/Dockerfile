FROM eclipse-temurin:17-jdk-alpine

VOLUME /tmp

# Instalar curl para health checks
RUN apk add --no-cache curl

WORKDIR /app

# Copiar el JAR con nombre específico
COPY target/user-service-1.0.0.jar app.jar

# Verificar que el JAR existe
RUN ls -la /app/

# Exponer puerto
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]