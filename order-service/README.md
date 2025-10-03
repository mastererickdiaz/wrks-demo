# Order Service

Microservicio de ejemplo que gestiona pedidos.

Requisitos

- Java 17+ (ver `pom.xml`).
- Maven (se incluye `mvnw`) o Docker.

Compilar y ejecutar

```powershell
cd order-service
.\mvnw.cmd clean package
java -jar target\order-service-1.0.0.jar
```

Construir imagen Docker

```bash
docker build -t order-service:1.0.0 .
```

Notas

- El `HELP.md` incluye información del POM padre y overrides añadidos para evitar heredar `<license>` y `<developers>`.
