# Discovery Server

Servicio de descubrimiento (Eureka/Discovery) usado por los microservicios del ejemplo.

Requisitos

- Java 17+ (ver `pom.xml`).
- Maven (se incluye `mvnw` para mayor compatibilidad) o Docker.

Compilar y ejecutar localmente

```powershell
cd api-gateway
.\mvnw.cmd clean package
java -jar target\api-gateway-1.0.0.jar
```

Construir imagen Docker

```bash
docker build -t api-gateway:1.0.0 .
```