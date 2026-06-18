FROM eclipse-temurin:25-jre-alpine

VOLUME /tmp

# Instalar curl para health checks
RUN apk add --no-cache curl

# Crear directorio de trabajo
WORKDIR /app

COPY target/config-server-1.0.0.jar app.jar

# Verificar que el JAR existe
RUN ls -la /app/

# Exponer puerto
EXPOSE 8888

# Comando de ejecución
ENTRYPOINT ["java", "-jar", "app.jar"]
