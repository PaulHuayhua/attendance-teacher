# vg-ms-attendance — Microservicio de Asistencia de Profesores

Microservicio reactivo que recibe marcaciones biométricas, calcula asistencia y permite justificaciones.  
**Stack:** Java 17, Spring Boot 3.5.14 (WebFlux), PostgreSQL (R2DBC).  
**Puerto:** `8089`.

---

## Endpoints (localhost:8089/api/v1/attendance)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/` | Todas las asistencias |
| `GET` | `/{id}` | Por ID |
| `GET` | `/date/{fecha}` | Por fecha (`2026-06-30`) |
| `GET` | `/range?from=&to=` | Por rango |
| `GET` | `/teacher/{personaId}` | Por profesor |
| `GET` | `/teacher/{personaId}/history?from=&to=` | Historial |
| `GET` | `/summary?date=` | Resumen diario |
| `GET` | `/teachers` | Lista de profesores |
| `GET` | `/teachers/{personaId}/schedule?date=` | Horario del profesor |
| `POST` | `/mark` | Recibir marcación |
| `POST` | `/manual` | Registro manual |
| `POST` | `/calculate/{personaId}?date=` | Calcular asistencia |
| `PUT` | `/{id}/justify` | Justificar |
| `GET` | `/{id}/justification-status` | Estado de justificación |
| `GET` | `/export?month=2026-06` | Reporte Excel |

### JSON de requests

**`POST /mark`**
```json
{"personaId":"550e8400-e29b-41d4-a716-446655440000","fechaHora":"2026-06-30T08:15:00"}
```

**`POST /manual`**
```json
{"personaId":"550e8400-e29b-41d4-a716-446655440000","fecha":"2026-06-30","turno":"MAÑANA","estado":"PRESENTE","horaEntradaReal":"07:50","horaEntradaEsperada":"08:00","diaSemana":"LUNES"}
```

**`PUT /{id}/justify`**
```json
{"motivo":"Certificado médico"}
```

### Respuesta genérica
```json
{"message":"Asistencias obtenidas correctamente","data":[...]}
```

---

## Lógica de negocio

1. Obtiene horario activo del profesor desde `vg-ms-schedules`
2. Busca marcaciones en ventana de 4h → `PRESENTE`, `TARDE` o `FALTA`
3. Justificación cambia estado a `JUSTIFICADO` con motivo/fecha/usuario
4. **Integración con microservicio de justificaciones:** Al justificar, envía la información al microservicio de justificaciones para flujo de aprobaciones
5. Job automático cada hora procesa marcaciones pendientes

## Integración con Microservicio de Justificaciones

El microservicio de asistencia se integra con el microservicio de justificaciones para:

- **Enviar justificaciones** cuando un registro de asistencia es justificado
- **Consultar estado** de aprobación de justificaciones
- **Mantener sincronización** entre ambos sistemas

Para más detalles, ver [INTEGRACION_JUSTIFICACIONES.md](./INTEGRACION_JUSTIFICACIONES.md)

## Configuración

| Variable | Default |
|----------|---------|
| `SERVER_PORT` | `8089` |
| `R2DBC_URL` | PostgreSQL Neon SSL |
| `SCHEDULES_SERVICE_URL` | `http://localhost:8083/api/v1` |
| `USERS_SERVICE_URL` | Gateway user API |
| `JUSTIFICATIONS_SERVICE_URL` | Gateway justifications API |

## Docker

```bash
./mvnw clean package -DskipTests
docker build -t vg-ms-attendance .
docker run -p 8089:8089 --env-file .env vg-ms-attendance
```
