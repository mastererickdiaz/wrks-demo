# Order Service

El `order-service` es un microservicio responsable de crear y gestionar las órdenes de los clientes.

## Seguridad: Firmas de Mensajes HTTP (HTTP Message Signatures)

Para garantizar una comunicación segura y autenticada entre microservicios, `order-service` utiliza el estándar de **Firmas de Mensajes HTTP**. Este mecanismo criptográfico asegura la integridad y autenticidad de las solicitudes.

Este servicio tiene un doble rol:

1.  **Firmante de Solicitudes**: Cuando `order-service` necesita comunicarse con otro servicio (ej. `user-service` o `product-service`), firma sus solicitudes HTTP salientes.
2.  **Verificador de Firmas**: También verifica las firmas de las solicitudes entrantes en sus endpoints internos protegidos (ej. `/api/internal/**`), asegurando que solo servicios autorizados puedan acceder.

### Flujo de Firma (Solicitudes Salientes)

1.  **Creación de Solicitud**: Cuando se usa un cliente HTTP (como `WebClient` o `RestTemplate`) para llamar a otro servicio, un interceptor se activa.
2.  **Generación de Digest**: El interceptor calcula un `digest` (un hash SHA-256) del cuerpo de la solicitud (si lo hay).
3.  **Construcción del Mensaje a Firmar**: Se construye una cadena de texto que incluye las cabeceras HTTP seleccionadas (como `(request-target)`, `host`, `date`, `digest`).
4.  **Firma Criptográfica**: Usando su **clave privada**, el servicio firma esta cadena de texto.
5.  **Inyección de Cabeceras**: El interceptor añade las cabeceras `Signature` y `Signature-Input` a la solicitud HTTP antes de enviarla.

### Flujo de Verificación (Solicitudes Entrantes)

1.  **Recepción de Solicitud**: El servicio recibe una solicitud en un endpoint interno (ej. `/api/internal/orders/**`).
2.  **Extracción de Cabeceras**: Un filtro de seguridad (`HttpSignatureAuthenticationFilter`) intercepta la solicitud y extrae las cabeceras `Signature` y `Signature-Input`.
3.  **Búsqueda de Clave Pública**: Usando el `keyId` de la firma, busca la clave pública del servicio emisor.
4.  **Verificación**: Reconstruye el mensaje base y utiliza la clave pública para verificar la firma.
5.  **Autenticación**: Si la firma es válida, se autentica la solicitud en el contexto de seguridad de Spring con la autoridad `SERVICE`.

### Configuración

Para que la firma y verificación funcionen, se deben configurar las siguientes variables de entorno:

```bash
# Identificador único para el par de claves de este servicio.
HTTP_SIGNATURE_KEY_ID="order-service-key"

# Ruta a la clave privada del servicio, usada para firmar solicitudes salientes.
HTTP_SIGNATURE_PRIVATE-KEY-FILE="file:/app/security-keys/private_key.pem"

# Mapa con las claves públicas de los servicios en los que este servicio confía.
# Se usa para verificar las firmas de las solicitudes entrantes.
HTTP_SIGNATURE_PUBLIC-KEY-FILES={'order-service-key':'file:/app/security-keys/public_key.pem'}
```

-   `HTTP_SIGNATURE_KEY_ID`: Un identificador único para el par de claves de `order-service`. Se incluye en las firmas para que los verificadores sepan qué clave pública usar.
-   `HTTP_SIGNATURE_PRIVATE-KEY-FILE`: La ruta a la **clave privada** del servicio. **Este es un secreto muy sensible** y se usa para firmar todas las solicitudes salientes.
-   `HTTP_SIGNATURE_PUBLIC-KEY-FILES`: Un mapa que asocia el `keyId` de otros servicios con la ubicación de sus **claves públicas**. Se usa para verificar las firmas de las solicitudes que llegan a este servicio.

### Despliegue en Producción

En un entorno de producción, la gestión de claves debe ser robusta y segura.

1.  **Gestión Centralizada de Claves**: Tanto la clave privada de `order-service` como las claves públicas de otros servicios deben almacenarse en un sistema de gestión de secretos como **HashiCorp Vault**. Nunca deben estar versionadas en el código fuente.
2.  **Inyección Segura de Claves**:
    -   La **clave privada** debe ser obtenida por `order-service` desde Vault durante el arranque. Debe ser inyectada de forma segura en la aplicación sin ser expuesta en logs o variables de entorno visibles.
    -   Las **claves públicas** de otros servicios también deben ser cargadas desde Vault.
3.  **Rotación de Claves**: Es una práctica de seguridad fundamental.
    -   El sistema de gestión de secretos debe permitir la rotación periódica de los pares de claves (privada/pública).
    -   Cuando se rota el par de claves de `order-service`, la nueva clave pública debe distribuirse a todos los servicios que la necesiten (como `user-service`).
    -   La aplicación debe ser capaz de manejar múltiples claves públicas (la antigua y la nueva) durante un período de transición para evitar fallos de comunicación mientras los servicios se actualizan.

---
