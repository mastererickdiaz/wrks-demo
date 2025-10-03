# wrks-demo

Proyecto demo que contiene tres microservicios basados en Spring Boot: `discovery-server`, `order-service` y `user-service`.

Resumen rápido

- Cada servicio incluye un wrapper de Maven (`mvnw` / `mvnw.cmd`) para compilar en entornos Windows/Linux.
- Hay scripts en la raíz para construir y ejecutar los servicios: `build-all.sh`, `start-services.sh`, `test-services.sh` y `docker-compose.yml` para orquestar localmente.

Cómo compilar todo (Linux/macOS)

1. Construir todos los JARs con el script incluido:

```bash
./build-all.sh
```

En Windows PowerShell (si prefieres usar Maven Wrapper por servicio):

```powershell
cd discovery-server; .\mvnw.cmd clean package; cd ..
cd order-service; .\mvnw.cmd clean package; cd ..
cd user-service; .\mvnw.cmd clean package; cd ..
```

Ejecutar los servicios con Docker Compose

```bash
docker-compose up --build
```

Archivos importantes

- `discovery-server/`, `order-service/`, `user-service/` — código de cada servicio.
- `docker-compose.yml` — orquesta los contenedores para desarrollo local.
- `HELP.md` en cada servicio — notas generadas durante la creación del proyecto. Los `README.md` de cada servicio mejoran y explican cómo usarlos.

Siguientes pasos sugeridos

- Inicializar un repositorio remoto (GitHub/GitLab): crear el repositorio remoto y ejecutar los comandos que encontrarás abajo en "Crear un repositorio remoto".
- Ajustar variables de configuración en `src/main/resources/application*.yml` antes de desplegar.
