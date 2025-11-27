# Order Service

El `order-service` es un microservicio responsable de crear y gestionar las órdenes de los clientes. Para funcionar, necesita obtener información de otros servicios como `user-service` y `product-service`.

## Seguridad: Rol como Firmante de Peticiones (HTTP Message Signatures)

Para garantizar que la comunicación interna sea segura, `order-service` actúa como un **firmante** de peticiones. Cada vez que llama a un endpoint interno de otro microservicio, firma digitalmente la petición para probar su identidad y asegurar que el mensaje no ha sido alterado.

### Flujo de Firma de Peticiones Salientes

Cuando `order-service` necesita consultar, por ejemplo, los datos de un usuario en `user-service`, sigue este proceso:

1.  **Construir la Petición**: Se prepara la petición HTTP normal (ej. `GET /api/users/{id}`).

2.  **Crear la "Cadena Base"**: Se construye una cadena de texto normalizada que incluye componentes clave de la petición, como el método HTTP, el path, y ciertas cabeceras (`x-request-id`, `x-request-date`, etc.).

3.  **Generar la Firma Digital**: Utilizando su **clave privada** (`private_key.pem`), el servicio firma la "cadena base" con el algoritmo `SHA256`. El resultado se codifica en Base64.

4.  **Adjuntar Cabeceras de Firma**: Se añaden dos cabeceras a la petición original:
    -   `Signature-Input`: Describe qué componentes se incluyeron en la firma y especifica el `keyId` (ej. `"order-service"`).
    -   `Signature`: Contiene la firma en Base64 generada en el paso anterior.

5.  **Enviar la Petición**: La petición, ahora con las cabeceras de firma, se envía al servicio de destino (ej. `user-service`). El servicio de destino usará estas cabeceras y la clave pública de `order-service` para verificar la autenticidad de la petición.

### Configuración en `docker-compose.yml`

La configuración para este servicio se define a través de variables de entorno, las cuales le indican dónde encontrar su clave privada para firmar.

```yaml
services:
  order-service:
    # ...
    environment:
      # ...
      # Configuración para HTTP Signatures
      # Clave privada que usa para firmar las peticiones que envía a otros servicios.
      - HTTP_SIGNATURE_PRIVATE-KEY-FILE=file:/app/security-keys/private_key.pem
      # Claves públicas en las que confía (en este caso, la suya propia para pruebas).
      - HTTP_SIGNATURE_PUBLIC-KEY-FILES={'order-service':'file:/app/security-keys/public_key.pem'}
    volumes:
      - ./security-keys:/app/security-keys:ro
```

-   `HTTP_SIGNATURE_PRIVATE-KEY-FILE`: Especifica la ruta a la **clave privada** que `order-service` utiliza para firmar todas sus peticiones salientes.
-   `HTTP_SIGNATURE_PUBLIC-KEY-FILES`: Define un mapa de `keyId` a **claves públicas**. Este servicio lo usaría para verificar peticiones entrantes si tuviera endpoints internos protegidos.
