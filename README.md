# wrks-demo

Proyecto de demostración de una arquitectura de microservicios basada en Spring Boot y Spring Cloud.

## Resumen de la Arquitectura

Este proyecto implementa varios patrones comunes en arquitecturas de microservicios:

- **API Gateway (`api-gateway`):** Punto de entrada único para todas las peticiones externas. Enruta el tráfico, aplica filtros de seguridad como Rate Limiting y maneja la terminación SSL.
- **Service Discovery (`discovery-server`):** Un servidor Eureka que permite a los servicios registrarse y descubrirse dinámicamente.
- **Configuration Server (`config-server`):** Proporciona configuración centralizada para todos los microservicios.
- **Servicios de Negocio (`order-service`, `user-service`, `product-service`):** Microservicios que implementan la lógica de negocio principal.
- **Seguridad Interna:** La comunicación entre servicios está protegida mediante **Firmas de Mensajes HTTP** para garantizar la autenticidad e integridad de las peticiones.

## Cómo Empezar

### Prerrequisitos
- Java 21+
- Docker y Docker Compose

### 1. Construir los Proyectos (Linux/macOS)

El siguiente script compilará todos los módulos de Maven:
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
