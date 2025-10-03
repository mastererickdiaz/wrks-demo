# Discovery Server

Servicio de descubrimiento (Eureka/Discovery) usado por los microservicios del ejemplo.

Requisitos

- Java 17+ (ver `pom.xml`).
- Maven (se incluye `mvnw` para mayor compatibilidad) o Docker.

Compilar y ejecutar localmente

```powershell
cd discovery-server
.\mvnw.cmd clean package
java -jar target\discovery-server-1.0.0.jar
```

Construir imagen Docker

```bash
docker build -t discovery-server:1.0.0 .
```