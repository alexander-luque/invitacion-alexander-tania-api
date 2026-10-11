# Invitación Alexander & Tania — Backend v1.0.0

API REST en Java 21, Spring Boot 3.5.6 y PostgreSQL. Proyecto independiente del frontend Next.js v0.9.7.7.

## Datos importados

- **52 invitaciones** con los códigos originales del archivo `data_invitados.xlsx`.
- **95 cupos** asignados en total.
- **3 registros con 0 cupos**: pendientes de asignación; pueden ver el saludo pero no confirmar ni obtener un pase.
- Migraciones Flyway automáticas: `V1__schema.sql` y `V2__invitados_iniciales.sql`.
- La migración inicial **no sobrescribe** cambios de invitados existentes.

## Publicación en GitHub + Supabase + Railway

1. Crea un repositorio **privado** en GitHub, por ejemplo `invitacion-alexander-tania-api`. Sube **el contenido** de este ZIP a la raíz del repositorio (no el ZIP comprimido).
2. En Supabase crea un proyecto PostgreSQL. En `Project Settings > Database`, copia los datos de conexión. Utiliza una conexión accesible desde Railway (pooler si corresponde); en la URL JDBC, usa `jdbc:postgresql://HOST:PUERTO/BASE?sslmode=require`. No publiques credenciales.
3. En Railway, crea un nuevo proyecto y selecciona `Deploy from GitHub repo`. Conecta el repositorio privado. Railway detectará el `Dockerfile`.
4. En Railway, sección `Variables`, define `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ADMIN_API_KEY`, `FRONTEND_ORIGIN`, `PUBLIC_BASE_URL` según `.env.example`. `ADMIN_API_KEY` debe ser aleatoria y larga (mínimo 32 bytes de entropía). `PUBLIC_BASE_URL` debe ser la URL HTTPS pública de Railway **sin barra final**. `FRONTEND_ORIGIN` debe ser el origen exacto de Vercel (sin ruta ni barra final).
5. En Railway, configura `Networking > Generate Domain`. Si cambias de dominio, actualiza `PUBLIC_BASE_URL` y redepliega.
6. Consulta `https://TU-API.up.railway.app/actuator/health`. Debe devolver `{"status":"UP"}`. Las migraciones se ejecutan al arrancar la API.
7. Para integrar la web existente, añade `NEXT_PUBLIC_API_URL=https://TU-API.up.railway.app` en Vercel **y adapta el código frontend** para consumir los endpoints. La variable por sí sola **no conecta** automáticamente la web.

**Nota:** No he desplegado ni probado la compilación en Railway desde este entorno. Antes de uso real, prueba todo el flujo con datos de ensayo.

## Desarrollo local

Requiere Java 21, Maven 3.9+ y PostgreSQL.

```bash
export DB_URL='jdbc:postgresql://localhost:5432/invitacion'
export DB_USER='postgres'
export DB_PASSWORD='postgres'
export ADMIN_API_KEY='un-secreto-aleatorio-largo'
export FRONTEND_ORIGIN='http://localhost:3000'
export PUBLIC_BASE_URL='http://localhost:8080'
mvn spring-boot:run
```

También puedes construir con `docker build -t invitacion-api .`.

## Endpoints

| Método | Ruta | Acceso | Uso |
|---|---|---|---|
| GET | `/actuator/health` | Público | Estado de la API |
| GET | `/api/invitaciones/{codigo}` | Público | Nombre, estado, habilitación de cupos |
| POST | `/api/invitaciones/{codigo}/rsvp` | Público | `{ "asistira": true, "mensaje": "..." }` |
| GET | `/api/invitaciones/{codigo}/pase` | Público | Nombre, cupos y URL del QR |
| GET | `/api/pases/{uuid}/qr` | Público | PNG del QR |
| POST | `/api/invitaciones/{codigo}/aportes` | Público | `{ "monto": 100, "fechaDeposito": "2026-10-11" }` |
| GET | `/api/admin/invitados` | `X-Admin-Key` | Listado completo |
| PATCH | `/api/admin/invitados/{codigo}/cupos` | `X-Admin-Key` | `{ "cupos": 4 }` |
| GET | `/api/pases/{uuid}/verificar` | `X-Admin-Key` | Validar pase al escanear |
| POST | `/api/admin/pases/{uuid}/ingresos` | `X-Admin-Key` | `{ "cantidad": 1 }` (registra cupos utilizados) |
| GET | `/api/admin/aportes` | `X-Admin-Key` | Ver declaraciones |
| PATCH | `/api/admin/aportes/{id}` | `X-Admin-Key` | `{ "estado": "VERIFICADO" }` |

### Ejemplo de integración Next.js

```ts
const api = process.env.NEXT_PUBLIC_API_URL;
const codigo = new URLSearchParams(window.location.search).get('id');
if (codigo) {
  const r = await fetch(`${api}/api/invitaciones/${encodeURIComponent(codigo)}`);
  if (r.ok) {
    const invitado = await r.json();
    // Mostrar `Querida ${invitado.nombre}` en portada.
    // `invitado.puedeConfirmar` indica si se habilita RSVP/pase.
  }
}
```

### Cómo escanear un pase

El QR codifica la URL `/api/pases/{uuid}/verificar`, pero **esa URL exige la clave administrativa**. No sirve abrirla en un navegador anónimo. Una aplicación de control de acceso debe leer el token UUID del QR y consultar el endpoint con `X-Admin-Key` desde un servidor seguro, nunca desde JavaScript público. Tras validar, envía `POST /api/admin/pases/{uuid}/ingresos` con la cantidad a registrar. El bloqueo transaccional evita exceder los cupos ante escaneos simultáneos.

## Seguridad y límites de esta primera versión

- La clave administrativa **nunca debe** ir en Vercel como `NEXT_PUBLIC_*`, ni en el navegador del invitado. Para administración real se necesita una interfaz autenticada y un proxy de servidor.
- Los códigos del Excel son numéricos de seis dígitos y por tanto **adivinables**. Esta versión permite consulta, RSVP y declaración de aportes con solo conocer el código. **Antes de distribuir públicamente invitaciones**, sustituye ese mecanismo por enlaces con tokens aleatorios de alta entropía, añade límites de peticiones y protege la modificación de RSVP frente a terceros. El token UUID del QR es independiente del código de invitación.
- Los aportes son **declaraciones**, no verificación bancaria ni procesamiento de pagos.
- Los registros con cero cupos no pueden confirmar ni obtener pase; al asignarles cupos quedan habilitados.
- Para ingresar, además de tener cupos, el invitado debe haber confirmado `SI`. El QR puede visualizarse antes de confirmar, pero no autoriza ingreso hasta la confirmación.
- La base contiene nombres de personas: conserva el repositorio privado, controla accesos y no distribuyas la carga CSV públicamente.
- Este proyecto no incluye todavía una pantalla de administración ni un lector de QR; entrega los endpoints necesarios para desarrollarlos.
