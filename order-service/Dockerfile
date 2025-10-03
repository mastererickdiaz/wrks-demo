FROM eclipse-temurin:17-jdk-alpine

VOLUME /tmp

# Instalar curl para health checks
RUN apk add --no-cache curl

# Crear directorio de trabajo
WORKDIR /app

COPY target/order-service-1.0.0.jar app.jar

# Verificar que el JAR existe
RUN ls -la /app/

# Exponer puerto
EXPOSE 8082

# Comando de ejecución
ENTRYPOINT ["java", "-jar", "app.jar"]