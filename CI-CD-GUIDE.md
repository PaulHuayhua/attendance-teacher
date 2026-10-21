# Pipeline CI/CD — vg-ms-attendanceTeacher

Guía completa de implementación del pipeline automatizado con Jenkins, SonarQube, Selenium, JMeter y Slack para el microservicio de asistencia de profesores de Via Academy.

---

## 1. Arquitectura del Pipeline

```
Git Push
   │
   ▼
┌──────────────────────────────────────────────────────────┐
│                      JENKINS PIPELINE                     │
│                                                           │
│  1. Checkout  →  2. Build & Test  →  3. SonarQube        │
│       │                │                    │             │
│       │            JUnit + JaCoCo      Quality Gate       │
│       ▼                                     │             │
│  4. Docker Build  →  5. Deploy Test  ←──────┘            │
│                           │                               │
│              ┌────────────┴────────────┐                 │
│              ▼                         ▼                  │
│      6. Selenium Tests        7. JMeter Load Tests        │
│         (PF-01, PF-02)      (Verde / Amarillo / Rojo)    │
│              │                         │                  │
│              └────────────┬────────────┘                 │
│                           ▼                               │
│                  8. Archive Artifacts                      │
│                           │                               │
│                           ▼                               │
│                  📢 Slack Notification                     │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Stack de Servicios Docker (docker-compose.ci.yml)

| Servicio              | Imagen                      | Puerto | Descripción                         |
|-----------------------|-----------------------------|--------|-------------------------------------|
| `jenkins`             | jenkins/jenkins:lts-jdk17   | 8080   | Servidor CI/CD                      |
| `sonarqube`           | sonarqube:10.4-community    | 9000   | Análisis de calidad de código       |
| `sonar-db`            | postgres:15-alpine          | —      | BD interna de SonarQube             |
| `selenium-hub`        | selenium/hub:4.18.1         | 4444   | Hub del Selenium Grid               |
| `selenium-chrome`     | selenium/node-chrome:4.18.1 | —      | Nodo Chrome para pruebas funcionales|
| `attendance-teacher-test` | (build local)           | 8089   | Microservicio en modo test          |

---

## 3. Levantamiento del Entorno

### 3.1 Requisitos previos

- Docker Desktop ≥ 25.0
- Docker Compose v2
- Al menos 6 GB de RAM disponibles

### 3.2 Iniciar el stack CI/CD

```bash
# Desde la raíz del proyecto
cd vg-ms-attendanceTeacher

# Levantar Jenkins + SonarQube + Selenium Grid
docker compose -f docker-compose.ci.yml up -d

# Verificar que todos los servicios están saludables
docker compose -f docker-compose.ci.yml ps
```

### 3.3 Accesos

| Servicio    | URL                              | Credenciales por defecto |
|-------------|----------------------------------|--------------------------|
| Jenkins     | http://localhost:8080            | admin / (ver primer arranque) |
| SonarQube   | http://localhost:9000            | admin / admin            |
| Selenium Grid | http://localhost:4444/ui       | —                        |

---

## 4. Configuración de Jenkins

### 4.1 Plugins requeridos

Instalar desde **Manage Jenkins → Plugins → Available**:

| Plugin                      | Propósito                          |
|-----------------------------|------------------------------------|
| Pipeline                    | Soporte Declarative Pipeline       |
| Git                         | Checkout de repositorio            |
| SonarQube Scanner           | Integración con SonarQube          |
| Slack Notification          | Notificaciones a Slack             |
| JaCoCo                      | Reporte de cobertura               |
| HTML Publisher              | Publicar reportes HTML de JMeter   |
| Performance Plugin          | Procesar archivos .jtl de JMeter   |
| Docker Pipeline             | Usar Docker en stages              |
| AnsiColor                   | Colores en consola                 |

### 4.2 Configurar SonarQube en Jenkins

1. Ir a **Manage Jenkins → Configure System**
2. Sección **SonarQube Servers** → Add SonarQube
3. Completar:
   - **Name**: `SonarQube` *(debe coincidir exactamente con el Jenkinsfile)*
   - **Server URL**: `http://sonarqube:9000`
   - **Server authentication token**: (generado en SonarQube)

#### Generar token en SonarQube:
1. Ir a `http://localhost:9000` → Login (admin/admin)
2. **My Account → Security → Generate Token**
3. Nombre: `jenkins-token` → copiar el token
4. Pegar en Jenkins como credencial tipo **Secret text**

### 4.3 Configurar Slack en Jenkins

1. **Manage Jenkins → Configure System → Slack**
2. Workspace: nombre de tu workspace Slack
3. Credential: token del bot de Slack
4. Default channel: `#prs-ci-cd`

#### Crear Bot de Slack:
1. Ir a https://api.slack.com/apps → Create New App
2. **OAuth & Permissions** → Bot Token Scopes: `chat:write`, `chat:write.public`
3. Instalar en workspace → copiar **Bot User OAuth Token**
4. Invitar el bot al canal: `/invite @jenkins-bot` en Slack

### 4.4 Crear el Pipeline en Jenkins

1. **New Item** → Nombre: `vg-ms-attendanceTeacher` → Pipeline
2. En **Pipeline** seleccionar: **Pipeline script from SCM**
3. SCM: Git → URL del repositorio
4. Branch: `*/main` (o la rama de trabajo)
5. Script Path: `vg-ms-attendanceTeacher/Jenkinsfile`
6. **Save** y hacer clic en **Build Now**

---

## 5. Integración con Slack

### 5.1 Qué notifica el pipeline

| Evento        | Color   | Información incluida                                    |
|---------------|---------|---------------------------------------------------------|
| ✅ Éxito      | Verde   | Nombre, build #, rama, commit, duración, links directos |
| ❌ Falla      | Rojo    | Nombre, build #, stage fallida, link a consola          |
| ⚠️ Inestable  | Amarillo| Advertencia de Quality Gate o pruebas con errores       |

### 5.2 Ejemplo de notificación de éxito

```
✅ BUILD EXITOSO — vg-ms-attendanceTeacher #15
• Rama: `main`
• Commit: `a1b2c3d4`
• Duración: 8 min 23 sec
• SonarQube: Ver análisis
• JMeter: Green | Yellow | Red
• Pipeline: Ver en Jenkins
```

---

## 6. Análisis de Calidad con SonarQube

### 6.1 Métricas analizadas

| Métrica                  | Umbral configurado | Descripción                            |
|--------------------------|--------------------|----------------------------------------|
| Cobertura de código      | ≥ 40%              | % de líneas cubiertas por pruebas      |
| Bugs                     | 0 bloqueantes      | Defectos críticos de lógica            |
| Vulnerabilidades         | 0                  | Problemas de seguridad                 |
| Code Smells              | < 20               | Código con mal olor / deuda técnica    |
| Duplicación              | < 10%              | Código duplicado                       |
| Security Hotspots        | Revisados          | Puntos de potencial vulnerabilidad     |

### 6.2 Qué se analiza en este microservicio

- **Domain Layer**: reglas de negocio de `AttendanceDomainService`
- **Application Layer**: casos de uso (UseCase implementations)
- **Infrastructure Layer**: controladores REST, clientes externos
- **Configuraciones excluidas**: `*Config.java`, DTOs, mappers (falsos positivos)

---

## 7. Pruebas Funcionales con Selenium

### ¿Qué son las pruebas funcionales?

Las pruebas funcionales verifican que el sistema se comporta correctamente desde la perspectiva del usuario final, validando los requisitos funcionales sin importar la implementación interna. Se ejecutan sobre el sistema completo o sus interfaces externas.

**Selenium** es un framework de automatización de navegadores web que permite:
- Controlar navegadores (Chrome, Firefox, Edge) programáticamente
- Simular interacciones de usuarios reales
- Ejecutar pruebas en modo headless (sin interfaz gráfica) para CI/CD
- Integrar con Selenium Grid para ejecución paralela en múltiples navegadores

### Prueba PF-01 — Health Check del Microservicio

| Campo                 | Detalle                                              |
|-----------------------|------------------------------------------------------|
| **Funcionalidad**     | Endpoint `GET /actuator/health`                      |
| **Pasos**             | 1. GET http://attendance-teacher-test:8089/actuator/health via REST Assured · 2. Abrir URL en Chrome via Selenium Grid · 3. Verificar contenido de la página |
| **Resultado esperado** | HTTP 200, body `{"status":"UP"}`, texto "UP" visible |
| **Resultado obtenido** | ✅ Validado en pipeline (ver reporte Jenkins)        |
| **Clase de prueba**   | `ActuatorHealthTest.java`                            |

### Prueba PF-02 — API de Resumen Diario de Asistencia

| Campo                 | Detalle                                              |
|-----------------------|------------------------------------------------------|
| **Funcionalidad**     | Endpoint `GET /api/v1/attendance/summary`            |
| **Pasos**             | 1. GET con parámetro `?date=YYYY-MM-DD` · 2. Verificar HTTP 200 y estructura JSON · 3. Abrir en Chrome y validar que no hay errores 500 · 4. Probar sin parámetro para verificar default |
| **Resultado esperado** | HTTP 200, campo `message` presente, sin errores 500 |
| **Resultado obtenido** | ✅ Validado en pipeline (ver reporte Jenkins)        |
| **Clase de prueba**   | `AttendanceApiTest.java`                             |

---

## 8. Pruebas de Carga con JMeter

### 8.1 Descripción de los 3 escenarios

#### 🟢 Escenario VERDE — Carga Normal

> Simula el uso habitual del sistema durante operación normal.

| Parámetro         | Valor        |
|-------------------|--------------|
| Usuarios concurrentes | 10       |
| Rampa de subida   | 10 segundos  |
| Duración total    | 30 segundos  |

**Comportamiento esperado**: El sistema responde con tiempos < 800ms (p95), tasa de error < 1%. Todos los endpoints responden con HTTP 200. El servicio opera sin degradación. Este es el estado óptimo y la base de referencia.

---

#### 🟡 Escenario AMARILLO — Carga Media

> Simula un pico de carga moderado, como el inicio de jornada escolar cuando todos los docentes ingresan simultáneamente.

| Parámetro         | Valor        |
|-------------------|--------------|
| Usuarios concurrentes | 50       |
| Rampa de subida   | 20 segundos  |
| Duración total    | 60 segundos  |

**Comportamiento esperado**: Tiempos de respuesta hasta 2000ms (p95), tasa de error < 5%. Puede haber lentitud notable pero el sistema se mantiene funcional. Se espera que el pool de conexiones R2DBC gestione la carga sin saturarse.

---

#### 🔴 Escenario ROJO — Carga Alta / Estrés

> Simula condición de estrés extrema para encontrar el punto de quiebre del sistema y evaluar su comportamiento bajo presión máxima.

| Parámetro         | Valor        |
|-------------------|--------------|
| Usuarios concurrentes | 150      |
| Rampa de subida   | 30 segundos  |
| Duración total    | 90 segundos  |

**Comportamiento esperado**: Degradación evidente del rendimiento. Se acepta hasta 10% de errores. Se observa cómo el sistema gestiona la saturación: timeouts de conexión, circuit breaking o respuestas 503. Permite identificar cuellos de botella en el pool R2DBC (max-size: 10 conexiones).

---

### 8.2 Endpoints probados en cada escenario

1. `GET /actuator/health` — baseline de disponibilidad
2. `GET /api/v1/attendance/summary?date={hoy}` — resumen diario
3. `GET /api/v1/attendance` — listado completo
4. `GET /api/v1/attendance/range?from={inicio-mes}&to={hoy}` — rango mensual

### 8.3 Interpretación de resultados

| Métrica JMeter        | VERDE (ok)   | AMARILLO (advertencia) | ROJO (crítico)  |
|-----------------------|--------------|------------------------|-----------------|
| Throughput (req/s)    | > 50         | 20 - 50                | < 20            |
| Response Time p95     | < 800ms      | 800ms - 2000ms         | > 2000ms        |
| Error Rate            | < 1%         | 1% - 5%                | > 5%            |
| Active Threads        | Estable      | Picos ocasionales      | Saturación      |

---

## 9. Pruebas Unitarias

Las pruebas unitarias cubren la capa de dominio (reglas de negocio puras, sin Spring):

| Clase de prueba                  | Casos de prueba | Cobertura objetivo |
|----------------------------------|-----------------|-------------------|
| `AttendanceDomainServiceTest`    | 16 tests        | > 90% en dominio  |
| `RegistroAsistenciaTest`         | 4 tests         | > 80% en modelos  |

**Casos clave cubiertos**:
- Validación de marcaciones biométricas (nulos, fechas futuras)
- Cálculo de estado PRESENTE / TARDE / FALTA según ventana horaria
- Justificación de asistencias (validaciones y transformación de estado)
- Mapeo de días de la semana al español
- Verificación de ventana temporal de 4 horas

---

## 10. Estructura de archivos generados

```
vg-ms-attendanceTeacher/
├── Jenkinsfile                          ← Pipeline declarativo completo
├── docker-compose.ci.yml                ← Stack CI/CD (Jenkins+Sonar+Selenium)
├── sonar-project.properties             ← Configuración SonarQube
├── pom.xml                              ← Actualizado con JaCoCo + Sonar + Surefire
├── CI-CD-GUIDE.md                       ← Esta guía
│
├── src/test/java/prs02/via/academy/
│   ├── domain/service/
│   │   └── AttendanceDomainServiceTest.java  ← 16 pruebas unitarias dominio
│   └── domain/model/
│       └── RegistroAsistenciaTest.java       ← 4 pruebas del modelo
│
├── selenium-tests/                      ← Módulo Maven independiente
│   ├── pom.xml
│   └── src/test/java/prs02/via/academy/selenium/
│       ├── BaseSeleniumTest.java             ← Configuración RemoteWebDriver
│       ├── ActuatorHealthTest.java           ← PF-01: Health Check
│       └── AttendanceApiTest.java            ← PF-02: Summary API
│
└── jmeter-tests/
    └── attendance_load_test.jmx         ← Plan JMeter con 3 escenarios
```

---

## 11. Troubleshooting frecuente

### Jenkins no puede conectar a Docker
```bash
# Dar permisos al socket Docker
sudo chmod 666 /var/run/docker.sock
# O agregar jenkins al grupo docker
sudo usermod -aG docker jenkins
```

### SonarQube no inicia (vm.max_map_count)
```bash
# En el host, no dentro del contenedor
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
```

### Quality Gate timeout
- Verificar que el webhook de SonarQube apunta a Jenkins:
  `http://jenkins:8080/sonarqube-webhook/`

### Selenium tests fallan por timeout
- Verificar que el contenedor `attendance-teacher-test` tiene el health check en UP:
  ```bash
  docker ps --filter name=attendance-teacher-test
  ```
