# User Service

Microservicio de ejemplo que gestiona usuarios.

Requisitos

- Java 17+ (ver `pom.xml`).
- Maven (`mvnw`) o Docker.

Compilar y ejecutar

```powershell
cd user-service
.\mvnw.cmd clean package
java -jar target\user-service-1.0.0.jar
```

Construir imagen Docker

```bash
docker build -t user-service:1.0.0 .
```

Notas

- El `HELP.md` contiene la misma nota sobre el nombre de paquete y la herencia del POM padre.
