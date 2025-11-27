# wrks-demo

Proyecto de demostración de una arquitectura de microservicios basada en Spring Boot y Spring Cloud.

## Resumen de la Arquitectura

Este proyecto implementa varios patrones comunes en arquitecturas de microservicios:

- **API Gateway (`api-gateway`):** Punto de entrada único para todas las peticiones externas. Enruta el tráfico, aplica filtros de seguridad como Rate Limiting y maneja la terminación SSL.
- **Service Discovery (`discovery-server`):** Un servidor Eureka que permite a los servicios registrarse y descubrirse dinámicamente.
- **Configuration Server (`config-server`):** Proporciona configuración centralizada para todos los microservicios.
- **Servicios de Negocio (`order-service`, `user-service`, `product-service`):** Microservicios que implementan la lógica de negocio principal.
- **Seguridad Interna:** La comunicación entre servicios está protegida mediante **Firmas de Mensajes HTTP** para garantizar la autenticidad e integridad de las peticiones.

## Flujo de Seguridad con Firmas HTTP

La comunicación entre microservicios (por ejemplo, de `order-service` a `user-service`) está protegida para asegurar que solo servicios autorizados puedan acceder a los endpoints internos. Esto se logra mediante un esquema de firmas HTTP, que funciona de la siguiente manera:

1.  **Firma de la Petición (Lado del Cliente):**
    *   Cuando un servicio como `order-service` necesita llamar a otro servicio (por ejemplo, a `user-service`), utiliza un `RequestInterceptor` de Feign para interceptar la petición saliente.
    *   Antes de enviar la petición, el interceptor construye un "string base" que contiene los componentes más importantes de la petición, como el método HTTP, el path, y la fecha (`@method`, `@path`, `date`).
    *   Este string base es firmado digitalmente usando la **clave privada** del servicio que origina la llamada (`order-service` en este caso). La clave privada se carga desde un archivo configurado en las variables de entorno.
    *   La firma resultante, junto con la lista de los componentes que se firmaron, se añade a las cabeceras de la petición en los headers `Signature` y `Signature-Input`.

2.  **Verificación de la Firma (Lado del Servidor):**
    *   Cuando el `user-service` recibe una petición en un endpoint protegido (marcado con `@PreAuthorize("hasAuthority('SERVICE')")`), un filtro de seguridad (`HttpSignatureAuthenticationFilter`) la intercepta.
    *   El filtro extrae la firma y los metadatos de las cabeceras `Signature` y `Signature-Input`.
    *   Reconstruye el "string base" usando los mismos componentes de la petición que el cliente usó para firmar.
    *   Utiliza el `keyId` de la cabecera `Signature-Input` para buscar la **clave pública** correspondiente del servicio que hizo la llamada (`order-service`). Las claves públicas de los servicios de confianza se configuran en el `user-service` a través de variables de entorno.
    *   Con la clave pública, verifica que la firma sea válida para el string base reconstruido.

3.  **Autorización:**
    *   Si la firma es válida, el filtro de seguridad considera la petición como auténtica y le otorga la autoridad `SERVICE`.
    *   El control de acceso de Spring Security (`@PreAuthorize`) ve que la petición tiene la autoridad `SERVICE` y permite el acceso al endpoint.
    *   Si la firma es inválida o no está presente, se deniega el acceso con un error `401 Unauthorized`.

**Nota sobre el componente `@authority`:**
Originalmente, la firma también incluía el componente `@authority` (el host del servicio). Sin embargo, este componente fue eliminado del proceso de firma y verificación para hacer el mecanismo más robusto en entornos de contenedores, donde la resolución de nombres de host puede ser inconsistente entre el momento de la firma y el de la verificación.

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
