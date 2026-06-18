# Data Model: Plataforma de Gestión de Usuarios y Pedidos

## User (gestionado por `user-service`)

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | Long | Generado automáticamente (IDENTITY) |
| `name` | String | `@NotBlank` |
| `email` | String | `@NotBlank`, `@Email` |
| `phone` | String | Opcional |
| `active` | Boolean | Por defecto `true` |

Sin cambios para esta feature.

## Order (gestionado por `order-service`)

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | Long | Generado en memoria (`AtomicLong`) |
| `userId` | Long | `@NotNull`. **Nuevo (FR-011)**: debe corresponder a un `User` existente con `active=true` en `user-service` antes de crear el pedido. |
| `productName` | String | `@NotBlank` |
| `quantity` | Integer | `@Min(1)` |
| `price` | BigDecimal | `@NotNull`, `@Min(0)` |
| `status` | OrderStatus | `PENDING \| CONFIRMED \| SHIPPED \| DELIVERED \| CANCELLED`. Se crea siempre en `PENDING`. |
| `createdAt` | LocalDateTime | Asignado al crear |

### Relación Order → User

- Relación lógica (no FK de base de datos, ya que `order-service` no persiste en BD):
  un `Order.userId` MUST referenciar un `User.id` existente y activo en el momento de
  la creación (FR-011). No se re-valida después de creado el pedido (p. ej. si el
  usuario se desactiva luego, los pedidos ya creados no se ven afectados).

### Nuevos tipos de error (no son entidades, pero forman parte del modelo de dominio)

- **UserNotFoundException**: `userId` no existe en `user-service` → `404 Not Found`.
- **UserInactiveException**: `userId` existe pero `active=false` → `400 Bad Request`
  (o `409 Conflict`, a definir en `contracts/orders-api.md`).
- Distinto de una falla de infraestructura (circuito abierto / timeout hacia
  `user-service`), que sigue resolviéndose con el fallback de Resilience4j ya
  existente (FR-008) y no debe traducirse a ninguna de las dos excepciones anteriores.
