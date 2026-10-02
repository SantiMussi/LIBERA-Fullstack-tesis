# LIBERA

Marketplace de reventa de reservas hoteleras: el viajero que no puede usar su reserva la publica y otro viajero la compra.

- `backend/` — API REST en Spring Boot (Java 21+). Detalle de endpoints y reglas de negocio en [backend/README.md](backend/README.md).
- `frontend/LIBERA WEB/` — sitio en HTML/CSS/JS puro que consume la API.

## Cómo levantarlo

Lo único que hace falta instalado es **Java 21 o superior** (Maven se descarga solo con el wrapper).

```
cd backend
mvnw spring-boot:run
```

En Linux/Mac: `./mvnw spring-boot:run`.

Después abrir **http://localhost:8080**. El backend sirve el sitio y la API juntos, con una base de datos en memoria
que se carga sola con datos de prueba (se reinicia cada vez que se levanta el backend). No hay que instalar MySQL ni configurar nada.

Usuarios de prueba (contraseña `Libera2026!`):

| Usuario | Para qué |
|---|---|
| `comprador@libera.test` | comprar reservas |
| `vendedor@libera.test` | titular de las reservas publicadas |
| `admin@libera.test` | rol ADMIN: panel `admin.html` (revisión de publicaciones, check-ins, solicitudes de demo) |

## Tests

```
cd backend
mvnw test
```
