FROM eclipse-temurin:21-jre-alpine

VOLUME /tmp

# Instalar curl para health checks
RUN apk add --no-cache curl

WORKDIR /app

# Copiar el JAR con nombre específico
COPY target/product-service-1.0.0.jar app.jar

# Verificar que el JAR existe
RUN ls -la /app/

# Exponer puerto
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
