# User Service

Microservicio de ejemplo que gestiona usuarios. Expone endpoints para operaciones CRUD sobre usuarios.

## Seguridad: Rol como Verificador de Firmas (HTTP Message Signatures)

Para proteger la comunicación interna, `user-service` actúa como un **verificador** de firmas. Ciertos endpoints internos (ej. los que son llamados por `order-service`) requieren que las peticiones entrantes estén firmadas digitalmente. Esto asegura que solo servicios autorizados y de confianza puedan acceder a sus datos.

### Flujo de Verificación de Peticiones Entrantes

Cuando `user-service` recibe una petición en un endpoint protegido:

1.  **Interceptar la Petición**: Un filtro de seguridad intercepta la petición antes de que llegue al controlador.

2.  **Extraer Cabeceras de Firma**: El filtro lee las cabeceras `Signature` y `Signature-Input` de la petición entrante.

3.  **Identificar la Clave Pública**: A partir del `keyId` encontrado en la cabecera `Signature-Input` (ej. `"order-service"`), el servicio busca la **clave pública** correspondiente en su configuración.

4.  **Reconstruir la "Cadena Base"**: El servicio reconstruye la "cadena base" de la misma manera que lo hizo el firmante, utilizando los componentes de la petición recibida (método, path, cabeceras, etc.).

5.  **Verificar la Firma**: Usando la **clave pública** del servicio firmante, verifica que la firma recibida en la cabecera `Signature` coincide con la "cadena base" que acaba de reconstruir.
    -   **Éxito**: Si la firma es válida, la petición se considera auténtica y se permite que continúe hacia el controlador para ser procesada.
    -   **Fallo**: Si la firma no es válida (porque los datos fueron alterados, la firma es incorrecta o la clave no se encuentra), la petición se rechaza inmediatamente con un error `401 Unauthorized`.

### Configuración en `docker-compose.yml`

La configuración para este servicio se define a través de variables de entorno, las cuales le indican qué claves públicas debe usar para verificar las firmas de los clientes en los que confía.

```yaml
services:
  user-service:
    # ...
    environment:
      # ...
      # Configuración para HTTP Signatures
      # Mapa de 'keyId' a claves públicas. user-service confía en las peticiones firmadas por 'order-service'.
      - HTTP_SIGNATURE_PUBLIC-KEY-FILES={'order-service':'file:/app/security-keys/public_key.pem'}
    volumes:
      - ./security-keys:/app/security-keys:ro
```

Construir imagen Docker

```bash
docker build -t user-service:1.0.0 .
```

Notas

- El `HELP.md` contiene la misma nota sobre el nombre de paquete y la herencia del POM padre.
