# LIBERA — Backend

API REST (Spring Boot 4, Java 21+) del marketplace de reventa de reservas hoteleras.
El front es HTML/JS puro: consume esta API con `fetch()`, no necesita React.

## Cómo levantarlo

1. **Backend**: `mvnw spring-boot:run` (en Linux/Mac `./mvnw spring-boot:run`) → `http://localhost:8080`.
   El mismo servidor sirve el front (`../frontend/LIBERA WEB`) y la API en `/api/v1`.
   Por defecto usa una base **H2 en memoria**: las tablas se crean solas y se cargan los datos de [`db/seed.sql`](db/seed.sql)
   (se reinician en cada arranque). No hace falta instalar MySQL.
2. **Tests**: `mvnw test`. También usan H2 en memoria.

Usuarios de prueba de `seed.sql` (contraseña `Libera2026!`): `vendedor@libera.test`, `comprador@libera.test`,
`admin@libera.test` (rol ADMIN).

### Con MySQL (opcional)

Para que los datos persistan, con MySQL 8 corriendo en `localhost:3306` (usuario `root` / contraseña `root`):

1. Ejecutar [`db/schema.sql`](db/schema.sql) y después [`db/seed.sql`](db/seed.sql) en MySQL Workbench.
   `schema.sql` **borra y recrea** las tablas.
2. Arrancar con el perfil `mysql`: `mvnw spring-boot:run -Dspring-boot.run.profiles=mysql`.

### Configuración (`application.properties` / variables de entorno)

| Propiedad | Variable de entorno | Para qué |
|---|---|---|
| `spring.datasource.password` | `DB_PASSWORD` | contraseña de MySQL, solo con el perfil `mysql` (default `root`) |
| `libera.security.jwt.secret` | `LIBERA_JWT_SECRET` | clave para firmar los tokens (≥ 32 caracteres). **Cambiarla en producción** |
| `libera.webhook.api-key` | `LIBERA_WEBHOOK_API_KEY` | clave que mandan los PMS en `X-API-KEY` |
| `libera.cors.allowed-origins` | `LIBERA_CORS_ORIGINS` | orígenes del front permitidos (default `http://localhost:*,http://127.0.0.1:*`) |

## Autenticación

`POST /api/v1/auth/login` devuelve un `accessToken` (JWT, dura 24 h). Las rutas protegidas lo esperan en el header
`Authorization: Bearer <token>`. Sin token → `401`; sin permiso → `403`.

```js
const API = "http://localhost:8080/api/v1";

const { accessToken } = await fetch(`${API}/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email: "comprador@libera.test", password: "Libera2026!" })
}).then(r => r.json());

const compras = await fetch(`${API}/purchases/mine`, {
  headers: { Authorization: `Bearer ${accessToken}` }
}).then(r => r.json());
```

## Endpoints

Fechas en formato `YYYY-MM-DD`. Una estadía es `[checkIn, checkOut)`: la noche del check-out no se cuenta.

| Método | Ruta | Acceso | Qué hace |
|---|---|---|---|
| POST | `/auth/register` | público | crea la cuenta y devuelve token (`email, password, firstName, lastName, documentNumber`) |
| POST | `/auth/login` | público | `email, password` → token |
| GET | `/auth/me` | usuario | datos del usuario logueado |
| GET | `/hotels?q=&city=` | público | buscador de hoteles (wizard de venta) |
| GET | `/hotels/{id}` | público | detalle de hotel |
| POST | `/bookings` | usuario | el titular carga su reserva original (`hotelId, pmsConfirmationCode, checkIn, checkOut, roomType, totalAmountPaid`) |
| GET | `/bookings/mine` | usuario | reservas cargadas por el usuario |
| GET | `/listings?city=&hotelId=&checkIn=&checkOut=&maxPrice=` | público | catálogo (solo publicaciones con noches disponibles) |
| GET | `/listings/{id}` | público | detalle; `soldRanges` = noches ya vendidas |
| GET | `/listings/mine` | usuario | publicaciones del vendedor |
| POST | `/listings` | usuario | publicar una reserva propia (`originalBookingId, listedTotalPrice, allowsSplitBooking`) |
| POST | `/listings/{id}/cancel` | vendedor | cancelar (solo si no vendió noches) |
| POST | `/purchases` | usuario | comprar (`listingId, checkIn, checkOut`); con Split Booking se pueden comprar solo algunas noches |
| GET | `/purchases/mine` | usuario | compras del usuario |
| GET | `/purchases/sales` | usuario | ventas sobre mis publicaciones |
| GET | `/purchases/{id}` | comprador, vendedor o admin | detalle de una compra |
| POST | `/purchases/{id}/dispute` | comprador | reportar un problema; el pago queda retenido |
| POST | `/webhooks/pms/checkin` | PMS (`X-API-KEY`) | el hotel confirma el check-in → se libera el pago (idempotente) |
| GET | `/admin/purchases?status=` | admin | todas las compras |
| POST | `/admin/purchases/{id}/check-in` | admin | confirmación manual de check-in (hoteles sin PMS / disputas) |

Todas las rutas llevan el prefijo `/api/v1`.

### Errores

Todas las respuestas de error tienen la misma forma:

```json
{ "timestamp": "...", "status": 409, "error": "Conflict", "message": "Some of the requested nights have already been sold.", "path": "/api/v1/purchases" }
```

`400` datos inválidos · `401` sin token / login fallido · `403` sin permiso · `404` no existe · `409` choca con el estado actual (ya vendido, duplicado, etc.).

## Reglas de negocio

- Solo el titular de una reserva puede publicarla, a un precio que no supere lo que pagó; una reserva tiene una sola publicación vigente.
- Split Booking solo en hoteles del Modelo Integración. Sin Split, se compra la estadía completa.
- No se venden dos veces las mismas noches (la publicación se bloquea durante la compra para evitar ventas simultáneas).
  El estado pasa a `PARTIALLY_SOLD` / `SOLD_OUT` solo.
- El precio de una compra parcial es proporcional a las noches; la compra que completa la publicación se lleva el resto, así el total coincide con el precio publicado.
- El vendedor no puede comprar su propia publicación.
- Pago en custodia: la compra nace en `PAYMENT_HELD` y solo se liquida (`LIQUIDATED`, con la transacción y los fees) cuando el PMS o un admin confirman el check-in.
