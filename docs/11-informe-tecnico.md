# 11 · Informe técnico

Proyecto de **Gestión y Tecnologías de Procesos de Negocio**: workflow de cursos de
capacitación en Flowable.

Este informe reúne el análisis técnico del repositorio y complementa la documentación
específica de [requerimientos](01-requerimientos.md), [arquitectura](02-arquitectura.md),
[Flowable](03-guia-flowable.md), [base de datos](07-base-de-datos.md) y las guías de las
aplicaciones. La fuente de verdad del alcance sigue siendo el
[enunciado](enunciado/ProyectoCursos_V3.pdf).

**Fecha del análisis:** 2026-11-09

## Alcance y criterio de lectura

Se revisaron el README, los POM, Docker Compose, CI, toda la documentación Markdown, el
enunciado PDF, el modelo BPMN, la tabla DMN, los scripts SQL/Bash, las clases Java y los
tests versionados. El repositorio contiene artefactos compilados bajo `target/`, pero este
informe se basa en las fuentes versionadas.

Hay una diferencia relevante entre la documentación de diseño y la implementación actual:
la documentación describe el objetivo completo, mientras que varias clases y aplicaciones
todavía son esqueletos. Las secciones de pendientes lo indican expresamente.

## 1. Stack tecnológico y herramientas utilizadas

| Tecnología/herramienta | Uso | Declaración o configuración |
|---|---|---|
| Java 21 | Desarrollo de web y aplicaciones de escritorio | `pom.xml` (`maven.compiler.release=21`), `web/pom.xml`, CI |
| Java 11 bytecode | Listeners/delegates cargados dentro de la imagen Flowable | `proceso/pom.xml` (`maven.compiler.release=11`) |
| Maven multi-módulo | Build de `proceso`, `web`, `app-materiales` y `app-comunicaciones` | `pom.xml` |
| Maven Wrapper 3.9.16 | Build reproducible sin Maven instalado | `.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd` |
| Flowable Engine 6.8.0 | Motor BPMN/DMN, jobs, tareas, identidad, historial y REST | `pom.xml`, `proceso/pom.xml`, `app-materiales/pom.xml`, `docker-compose.yml` |
| Flowable UI 6.8.0 | Servidor, UI Task/Modeler/Admin e API REST | `docker-compose.yml` |
| BPMN 2.0 | Modelo ejecutable `organizarCurso` | `proceso/src/main/resources/procesos/organizarCurso.bpmn20.xml` |
| DMN 2019 | Regla de viabilidad | `proceso/src/main/resources/procesos/viabilidad.dmn` |
| Spring Boot 4.1.2 | Sitio web | `web/pom.xml` |
| Spring MVC/Web, Thymeleaf | HTTP y vistas HTML renderizadas en servidor | `web/pom.xml`, `web/src/main/resources/templates/` |
| Spring Security 6 / Thymeleaf extras | Login del personal y autorización por grupos | `web/pom.xml`, `web/.../seguridad/`, `templates/fragmentos.html` |
| Spring `RestClient` | Cliente REST de Flowable desde la web | `web/pom.xml`, `web/.../flowable/ClienteFlowable.java` |
| Spring JDBC `JdbcClient` | Acceso SQL a la BD `institucion` | `web/pom.xml`, arquitectura documentada en `docs/02-arquitectura.md` |
| Flyway | Migración del esquema propio de la institución | `web/pom.xml`, `web/src/main/resources/db/migration/V1__esquema_inicial.sql` |
| PostgreSQL 15 | BD `flowable` y BD `institucion` | `docker-compose.yml`, `db/init/01-crear-bd-institucion.sql` |
| Docker Compose | Orquestación de PostgreSQL, Flowable y pgAdmin opcional | `docker-compose.yml` |
| Swing | Interfaces de las apps auxiliares | `app-materiales/...`, `app-comunicaciones/...` |
| FlatLaf 3.5.4 | Apariencia moderna de Swing | `app-materiales/pom.xml`, `app-comunicaciones/pom.xml` |
| JDK `java.net.http.HttpClient` | Cliente HTTP de la app de comunicaciones | `app-comunicaciones/pom.xml`, `FlowableRest.java` |
| Jackson Databind 2.22.4 | JSON de la app de comunicaciones | `app-comunicaciones/pom.xml` |
| PostgreSQL JDBC 42.7.14 | Conexión del motor embebido de materiales | `app-materiales/pom.xml` |
| JUnit 5.14.4 | Tests unitarios/integración | `pom.xml` y los tres POM de módulos |
| H2 2.1.214 | Motor Flowable en memoria para tests del módulo proceso | `proceso/pom.xml`, `proceso/src/test/resources/flowable.cfg.xml` |
| Python, PM4Py, pandas, requests | Exportación y minería de historial | Enunciado PDF y `docs/04-entorno-desarrollo.md`; actualmente solo existe `mineria/README.md` |
| Graphviz | Render de gráficos de minería | Enunciado PDF y `docs/04-entorno-desarrollo.md` |
| GitHub Actions / Temurin | CI con Java 21 y `./mvnw verify` | `.github/workflows/ci.yml` |
| `curl`, `jar`, Bash | Healthcheck, configuración de identidades y despliegue `.bar` | `README.md`, `scripts/*.sh` |

**No se encontró** `requirements.txt`, código Python, Postman collection ni dependencia
OpenPDF. OpenPDF aparece como elección futura en `docs/02-arquitectura.md`, pero no está
declarado en `web/pom.xml`.

## 2. Arquitectura y estructura de directorios

### Componentes

- **`proceso/`**: fuente ejecutable del proceso: BPMN, DMN, listeners y delegates Java.
  `proceso/target/proceso.jar` se monta en `/app/WEB-INF/lib/proceso.jar` del contenedor
  Flowable.
- **`web/`**: aplicación Spring Boot en el puerto 8081. Incluye vistas Thymeleaf, seguridad,
  bandeja de tareas, cliente REST y migración Flyway.
- **`app-materiales/`**: Swing local para preparación de materiales. Levanta un motor
  Flowable embebido conectado directamente a la BD `flowable`.
- **`app-comunicaciones/`**: Swing que simula a la empresa y usa HTTP REST contra Flowable.
- **`db/`**: inicialización de la base `institucion`; las tablas se crean realmente por Flyway.
- **`scripts/`**: crea usuarios/grupos/privilegios y empaqueta/despliega BPMN+DMN.
- **`mineria/`**: reservado a exportación/análisis PM4Py; actualmente solo contiene README.
- **`docs/`**: requisitos, arquitectura, guía Flowable, entorno, decisiones, modelo, BD y
  guías de las aplicaciones.
- **`docs/referencia/`** y **`docs/diagramas/`**: modelo de referencia de la cátedra y
  diagrama PNG.
- **`.github/workflows/`**: verificación automática.

### Comunicación con Flowable

**API REST:**

1. `web/.../flowable/ClienteFlowable.java` concentra login del personal, inicio de
   instancias, consultas de tareas/instancias/variables, claim y complete.
2. `web/.../seguridad/AutenticacionContraFlowable.java` valida las credenciales del usuario
   mediante `GET /identity/groups`.
3. `app-comunicaciones/.../FlowableRest.java` consulta instancias en curso; la interfaz
   prevista debe enviar mensajes mediante `/runtime/executions`.
4. `scripts/setup-identidades.sh` usa REST de identidad; `scripts/desplegar-proceso.sh`
   usa el endpoint de deployments.
5. Los scripts de minería previstos leen historial mediante REST.

**API Java nativa:**

`app-materiales` usa `ProcessEngine`, `IdentityService.checkPassword`,
`TaskService` para consultas/claim/complete y `RuntimeService` para recuperar el
`businessKey`. `ConsultasHistoricas` está diseñada para usar `HistoryService`.
El motor embebido desactiva actualización de esquema y async executor para no competir con
el servidor (`MotorFlowable.java`).

**Acceso directo a PostgreSQL:**

- Flowable Server escribe la BD `flowable`.
- El motor embebido de materiales comparte esa BD.
- La web escribe/lee `institucion` mediante JDBC/Flyway.
- Listeners/delegates del proceso deben escribir `institucion` usando `INSTITUCION_DB_*`.

## 3. Base de datos y modelo BPMN

### Esquema de la institución

`db/init/01-crear-bd-institucion.sql` crea la base vacía `institucion`. La migración
`web/src/main/resources/db/migration/V1__esquema_inicial.sql` crea:

- `empresa(id, cuit, razon_social)`;
- `cuenta_empresa(empresa_id, usuario, password_hash, creada_en)`;
- `curso(id, empresa_id, process_instance_id, nombre, descripcion,
  participantes_estimados, fechas_preferidas, contacto_*, programa, presupuesto,
  fecha_inicio, informe_resultados, estado, creado_en)`;
- `propuesta(id, curso_id, version, programa, presupuesto, fechas_posibles,
  observaciones_empresa, creada_en)`, con `UNIQUE(curso_id, version)`.

El vínculo es doble:

1. la web crea `curso`;
2. inicia Flowable con `businessKey = curso.id`;
3. guarda el ID retornado en `curso.process_instance_id`;
4. las apps muestran la solicitud mediante ese `businessKey`;
5. el listener final actualiza el curso usando el `processInstanceId`.

El estado `EN_CURSO` se deriva de la instancia mientras está activa; al cerrar se espera uno
de `FINALIZADO`, `CANCELADO`, `RECHAZADO`, `SIN_RESPUESTA` o `NO_VIABLE`. Personal,
participantes y recursos no tienen tablas propias: personal vive en IDM de Flowable y los
otros datos son variables del proceso.

### Variables y regla DMN

La clave BPMN es `organizarCurso`; los mensajes son
`solicitudCapacitacion`, `aceptacionPropuesta`, `rechazoPropuesta`,
`modificacionesPropuesta`, `nominaParticipantes` y `cancelacionCurso`.
Las variables principales están documentadas en `docs/06-guia-implementacion-modelo.md`:
`resultado`, `plazoRespuesta`, `cursosEnEjecucion`, `esViable`, `accesoHabilitado`,
datos de propuesta/versiones/recordatorios, `fechaInicioCurso`, `participantes`,
`listaDefinitiva`, flags de recursos, `aulaAsignada`, `docenteAsignado` e
`informeResultados`.

`viabilidad.dmn` usa hit policy `UNIQUE`:

- `cursosEnEjecucion < 5` → `esViable=true`;
- `cursosEnEjecucion >= 5` → `esViable=false`.

La entrada debe proceder del motor, no de un formulario. **Estado actual:** el execution listener
`ContarCursosEnEjecucionListener` corre al inicio de la tarea DMN, cuenta las instancias de
`organizarCurso` en curso con `accesoHabilitado = true` (sin contar la instancia actual) y escribe
ese total en `cursosEnEjecucion`, que la tabla consume en la misma decisión. Lo cubre
`ContarCursosEnEjecucionListenerTest`.

### Service Tasks y clases

En `organizarCurso.bpmn20.xml`:

| Service task / función | Implementación |
|---|---|
| `Evaluar viabilidad` (`flowable:type="dmn"`) | Tabla DMN `viabilidad`; no es un delegate Java |
| `Enviar recordatorio` | `NotificarEmpresaDelegate` |
| `Comunicar imposibilidad` | `NotificarEmpresaDelegate` |
| `Enviar propuesta` | `NotificarEmpresaDelegate` |
| `Enviar confirmación` | `NotificarEmpresaDelegate`, `async=true` |
| `Notificar cierre` | `NotificarEmpresaDelegate` |
| `Habilitar acceso a la empresa` | `HabilitarAccesoEmpresaDelegate` |
| `Reservar aula` | `ReservarAulaDelegate` |
| `Liberar aula` | `LiberarAulaDelegate`, compensación |
| `Liberar aula virtual` | `LiberarAulaVirtualDelegate`, compensación |

`Liberar equipamiento` es una user task de compensación, no una service task.
Las implementaciones actuales de acceso, reserva/liberación y notificación registran logs o
variables mínimas; la persistencia real y la simulación del web service están marcadas como
pendientes en los Javadocs.

### Listeners y expresiones

- **Execution listener de fin de proceso**:
  `RegistrarResultadoDelCursoListener`; actualmente solo registra un log.
- **Execution listener de inicio de viabilidad**:
  `ContarCursosEnEjecucionListener`; cuenta las instancias abiertas con `accesoHabilitado = true`
  y escribe `cursosEnEjecucion` para la DMN.
- **Task listener `create` en `Confeccionar lista definitiva`**:
  `AsignarAdministrativoConMenosTareasListener`; actualmente deja la tarea disponible
  para el grupo y no calcula cargas.
- Expresiones BPMN que fijan `resultado`, versión, recordatorios y cancelación.
- `resultado=RECHAZADO` al tomar el flujo de rechazo y `SIN_RESPUESTA` al superar dos
  recordatorios.

### Estructura BPMN

El modelo incluye negociación con eventos de mensaje y timer parametrizado, paralelo de
inscripciones/materiales/recursos, gateway inclusivo para aula física/aula virtual/
equipamiento, subproceso de cancelación, compensaciones y desarrollo docente. Las decisiones
de `docs/05-decisiones.md` explican los gateways de unión, la compensación intermedia y el
paso asíncrono necesario porque la app de materiales no contiene las clases del módulo
`proceso`.

## 4. Despliegue y ejecución

### Primera ejecución

Requisitos: Git, Docker Desktop, JDK 21, `curl`; Maven no es necesario.

```bash
./mvnw -pl proceso clean package
docker compose up -d
until curl -sf -u admin:test \
  http://localhost:8080/flowable-ui/process-api/repository/deployments >/dev/null
do sleep 5
done
bash scripts/setup-identidades.sh
bash scripts/desplegar-proceso.sh
```

URLs:

- Flowable UI: `http://localhost:8080/flowable-ui` (`admin`/`test`);
- REST: `http://localhost:8080/flowable-ui/process-api`;
- web: `http://localhost:8081`;
- pgAdmin opcional: `docker compose --profile herramientas up -d`, en `http://localhost:5050`.

`docker-compose.yml` levanta PostgreSQL 15 (`flowable` y `institucion`) y Flowable UI.
El volumen `postgres_data` conserva datos. Para empezar de cero:

```bash
docker compose down -v
# repetir la secuencia de primera ejecución
```

### Aplicaciones

```bash
./mvnw -pl web spring-boot:run
./mvnw -pl app-materiales compile exec:java
./mvnw -pl app-comunicaciones compile exec:java
```

La app de materiales usa `FLOWABLE_DB_URL`, `FLOWABLE_DB_USER` y
`FLOWABLE_DB_PASSWORD`. La web usa `INSTITUCION_DB_*` y `FLOWABLE_*`; comunicaciones usa
`FLOWABLE_URL`, `FLOWABLE_USUARIO` y `FLOWABLE_CLAVE`. Los defaults están en
`web/src/main/resources/application.yml`, `MotorFlowable.java` y
`FlowableRest.java`.

### Tests y operación diaria

```bash
./mvnw verify
./mvnw -pl proceso verify
FLOWABLE_DOCKER=si ./mvnw -pl web,app-comunicaciones verify
./mvnw -pl proceso clean package && docker compose restart flowable
bash scripts/desplegar-proceso.sh
docker compose logs -f flowable
```

### Minería PM4Py

El procedimiento previsto por el enunciado y `docs/04-entorno-desarrollo.md` es:

```bash
sudo apt install python3-venv graphviz
cd mineria
python3 -m venv .venv
source .venv/bin/activate
pip install pm4py pandas requests
python exportar_history.py
python analizar.py
```

Sin embargo, `mineria/` actualmente **no contiene** `exportar_history.py`, `analizar.py`,
CSV, capturas ni conclusión. Por tanto los dos últimos comandos no son ejecutables hasta
incorporar esos entregables. La exportación debe tomar actividades históricas de la etapa
desde `solicitudRecibida` hasta la preparación o un fin temprano; el análisis debe producir
gráfico y los tres recorridos más frecuentes con cantidad y porcentaje.

## 5. Próximos milestones y tareas pendientes

### Lista de tareas y pendientes

> **Estado:** `[ ]` pendiente, `[-]` parcialmente implementado, `[x]` verificado.
> Esta lista es el checklist técnico del proyecto; al cerrar una tarea hay que actualizarla
> junto con su issue, código y tests.

- [x] **#11 · Motor de viabilidad:** consultar instancias abiertas de todas las versiones
  con `accesoHabilitado=true`, excluyendo la instancia actual; la prueba cubre el límite de
  cinco cursos.
- [ ] **#18 · Asignación por carga:** implementar el `TaskListener` que cuente tareas
  asignadas pendientes y asigne al administrativo con menor carga.
- [ ] **#17 · Persistencia de acceso:** hacer que `HabilitarAccesoEmpresaDelegate`
  cree/reutilice `cuenta_empresa`, vincule el curso y gestione la conexión JDBC.
- [ ] **#25 · Persistencia final:** hacer que `RegistrarResultadoDelCursoListener`
  actualice estado, programa, presupuesto, fecha e informe.
- [ ] **#19 · Reserva/liberación:** persistir o simular `aulaAsignada` y definir la
  excepción `AULA_NO_DISPONIBLE`.
- [-] **#12–#15, #21–#22, #26–#29 · Sitio web funcional:** completar formulario público,
  propuestas y PDF, archivo, recursos, lista definitiva, tareas docentes, informe, cierre,
  login y portal de empresas. Ya existe la base de bandeja y seguridad, pero las pantallas
  de negocio son incompletas.
- [-] **#24 · App de materiales:** agregar botones y lógica de reclamar/completar, junto
  con sus tests. Actualmente la ventana solo lista y actualiza tareas.
- [ ] **#30–#32 · Consultas históricas:** implementar resultados, duración de las ramas y
  tareas por persona, filtrando tareas canceladas.
- [-] **#16 y #23 · App de comunicaciones:** implementar enum de comunicaciones, detección
  de suscripciones y envío de mensajes/variables. Actualmente solo lista instancias.
- [ ] **M-01–M-03 · Minería de procesos:** agregar `exportar_history.py`, `analizar.py`,
  CSV, gráfico, recorridos, porcentajes y conclusión.
- [ ] **Escenario 7 de defensa:** acordar con el profesor qué sucede cuando no hay aula;
  el modelo todavía no lanza `BpmnError`.
- [ ] **Entrega y defensa:** completar informe, capturas de minería, datos de prueba y
  validar los 11 escenarios en una instalación limpia.

### Puntos de prueba para la defensa

- Aceptación y cierre normal con compensación; modificaciones y nueva versión.
- Rechazo; dos recordatorios y cierre por falta de respuesta.
- Cinco cursos en ejecución y camino `NO_VIABLE`.
- Cancelación durante preparación con solo las compensaciones de recursos efectivamente
  gestionados.
- Asignación por carga, claim obligatorio para tareas de grupo y rechazo de completar tareas
  ajenas.
- Registro web, `businessKey`/`process_instance_id`, habilitación y aislamiento por empresa.
- Materiales por API Java y comunicaciones por API REST.
- Timer de respuesta reducido (`PROCESO_PLAZO_RESPUESTA=PT2M`) y fecha de curso cercana.
- Persistencia de cada resultado final y exactitud de las tres consultas históricas.

### Riesgos técnicos a controlar

- Dos motores comparten la BD `flowable`; solo el servidor debe ejecutar timers/jobs.
- El módulo `proceso` debe seguir compilando para Java 11.
- Flowable no impide completar una tarea ajena: la autorización debe permanecer en web/app.
- Cambiar IDs BPMN, mensajes o variables rompe clientes externos.
- El despliegue BPMN+DMN debe hacerse como `.bar` con `scripts/desplegar-proceso.sh`;
  desplegar solo BPMN por REST no garantiza la disponibilidad de la decisión DMN.
