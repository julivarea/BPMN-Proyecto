# 11 · Informe técnico

Proyecto de **Gestión y Tecnologías de Procesos de Negocio**: workflow de cursos de
capacitación en Flowable.

Este informe reúne el análisis técnico del repositorio y complementa la documentación
específica de [requerimientos](01-requerimientos.md), [arquitectura](02-arquitectura.md),
[Flowable](03-guia-flowable.md), [base de datos](07-base-de-datos.md) y las guías de las
aplicaciones. La fuente de verdad del alcance sigue siendo el
[enunciado](enunciado/ProyectoCursos_V3.pdf).

**Actualizado:** 2026-11-20

## Alcance y criterio de lectura

Se revisaron el README, los POM, Docker Compose, CI, toda la documentación Markdown, el
enunciado PDF, el modelo BPMN, la tabla DMN, los scripts SQL/Bash, las clases Java y los
tests versionados. El repositorio contiene artefactos compilados bajo `target/`, pero este
informe se basa en las fuentes versionadas.

Todo lo que pide el enunciado está implementado, salvo el escenario 7 de la defensa (falta de
aulas, #36), que depende de lo que defina el profesor. La lista de la sección 5 resume el estado y
lo que queda.

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
| Spring Security / Thymeleaf extras | Login del personal y de las empresas, y autorización por rol | `web/pom.xml`, `web/.../seguridad/`, `templates/fragmentos.html` |
| Spring `RestClient` | Cliente REST de Flowable desde la web | `web/pom.xml`, `web/.../flowable/ClienteFlowable.java` |
| Spring JDBC `JdbcClient` | Acceso SQL a la BD `institucion` | `web/pom.xml`, arquitectura documentada en `docs/02-arquitectura.md` |
| Flyway | Migración del esquema propio de la institución | `web/pom.xml`, `web/src/main/resources/db/migration/V1__esquema_inicial.sql` |
| PostgreSQL 15 | BD `flowable` y BD `institucion` | `docker-compose.yml`, `db/init/01-crear-bd-institucion.sql` |
| Docker Compose | Orquestación de PostgreSQL, Flowable y pgAdmin opcional | `docker-compose.yml` |
| OpenPDF 1.3.39 | PDF de cada versión de la propuesta | `web/pom.xml`, `web/.../propuestas/GeneradorPdfPropuesta.java` |
| Swing | Interfaces de las apps auxiliares | `app-materiales/...`, `app-comunicaciones/...` |
| FlatLaf 3.5.4 | Apariencia moderna de Swing | `app-materiales/pom.xml`, `app-comunicaciones/pom.xml` |
| JDK `java.net.http.HttpClient` | Cliente HTTP de la app de comunicaciones | `app-comunicaciones/pom.xml`, `FlowableRest.java` |
| Jackson Databind 2.22.4 | JSON de la app de comunicaciones | `app-comunicaciones/pom.xml` |
| PostgreSQL JDBC 42.7.14 | Conexión del motor embebido de materiales | `app-materiales/pom.xml` |
| JUnit 5.14.4 | Tests unitarios/integración | `pom.xml` y los tres POM de módulos |
| H2 2.1.214 | Motor Flowable y bases en memoria para los tests de `proceso` y `app-materiales` | `proceso/pom.xml`, `app-materiales/pom.xml`, `proceso/src/test/resources/flowable.cfg.xml` |
| Python, PM4Py, pandas, requests | Exportación y minería de historial | Enunciado PDF y `docs/04-entorno-desarrollo.md`; scripts en `mineria/` |
| Graphviz | Render de gráficos de minería | Enunciado PDF y `docs/04-entorno-desarrollo.md` |
| GitHub Actions / Temurin | CI con Java 21 y `./mvnw verify` | `.github/workflows/ci.yml` |
| `curl`, `jar`, Bash | Healthcheck, configuración de identidades y despliegue `.bar` | `README.md`, `scripts/*.sh` |

No hay `requirements.txt`: las dependencias de Python se instalan con
`pip install pm4py pandas requests` (`docs/04-entorno-desarrollo.md`).

## 2. Arquitectura y estructura de directorios

### Componentes

- **`proceso/`**: fuente ejecutable del proceso: BPMN, DMN, listeners y delegates Java.
  `proceso/target/proceso.jar` se monta en `/app/WEB-INF/lib/proceso.jar` del contenedor
  Flowable.
- **`web/`**: aplicación Spring Boot en el puerto 8081. Incluye vistas Thymeleaf, seguridad,
  portal de empresas, bandeja y pantallas de tareas, PDF de propuestas, cliente REST y
  migración Flyway.
- **`app-materiales/`**: Swing local para preparación de materiales. Levanta un motor
  Flowable embebido conectado directamente a la BD `flowable`.
- **`app-comunicaciones/`**: Swing que simula a la empresa y usa HTTP REST contra Flowable.
- **`db/`**: inicialización de la base `institucion`; las tablas se crean realmente por Flyway.
- **`scripts/`**: crea usuarios/grupos/privilegios, empaqueta/despliega BPMN+DMN y deja listos
  los escenarios de la defensa (`scripts/escenarios/`).
- **`mineria/`**: exportación y análisis PM4Py de la etapa de negociación: scripts, CSV,
  gráficos y conclusión.
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
3. `app-comunicaciones/.../FlowableRest.java` consulta instancias en curso, detecta la
   ejecución que espera cada mensaje y lo entrega con `messageEventReceived`.
4. `scripts/setup-identidades.sh` usa REST de identidad; `scripts/desplegar-proceso.sh`
   usa el endpoint de deployments.
5. Los scripts de minería leen el historial mediante REST (`exportar_history.py`), y
   `generar_ejecuciones.py` recorre el proceso con la web y la API REST para generarlo.

**API Java nativa:**

`app-materiales` usa `ProcessEngine`, `IdentityService.checkPassword`,
`TaskService` para consultas/claim/complete y `RuntimeService` para recuperar el
`businessKey`. `ConsultasHistoricas` usa `HistoryService` para las consultas del §4.3.
El motor embebido desactiva actualización de esquema y async executor para no competir con
el servidor (`MotorFlowable.java`).

**Acceso directo a PostgreSQL:**

- Flowable Server escribe la BD `flowable`.
- El motor embebido de materiales comparte esa BD.
- La web escribe/lee `institucion` mediante JDBC/Flyway.
- Listeners/delegates del proceso escriben `institucion` usando `INSTITUCION_DB_*` (`ConexionInstitucion`).

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
5. el listener final actualiza el curso usando el `businessKey` (el `curso.id`).

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

`Liberar equipamiento` es una user task de compensación, no una service task. La cancelación termina con la user task
`Confirmar compensaciones`, que Flowable no deja completar mientras haya liberaciones pendientes, y recién después
ejecuta `Notificar cierre` (`docs/05-decisiones.md`, limitación 2).
*Habilitar acceso a la empresa* ya persiste la cuenta: `HabilitarAccesoEmpresaDelegate` consulta y crea
`cuenta_empresa` por JDBC con `INSTITUCION_DB_*` y guarda la clave con BCrypt (issue #17). La reserva y la
liberación de aulas ya pasan por `ServicioAulas` / `ServicioAulasVirtuales`, con implementación simulada
(issue #19). El registro del resultado final también persiste (issue #25). Queda simulado el envío de
notificaciones, que solo escribe en el log.

### Listeners y expresiones

- **Execution listener de fin de proceso**:
  `RegistrarResultadoDelCursoListener`; guarda en el curso el `resultado` como estado y, si los
  tiene, programa, presupuesto, fecha de inicio e informe.
- **Execution listener de inicio de viabilidad**:
  `ContarCursosEnEjecucionListener`; cuenta las instancias abiertas con `accesoHabilitado = true`
  y escribe `cursosEnEjecucion` para la DMN.
- **Task listener `create` en `Confeccionar lista definitiva`**:
  `AsignarAdministrativoConMenosTareasListener`; asigna la tarea al administrativo con menos
  tareas pendientes y desempata alfabéticamente por ID de usuario.
- Expresiones BPMN que fijan `resultado`, versión, recordatorios y cancelación.
- `resultado=RECHAZADO` al tomar el flujo de rechazo y `SIN_RESPUESTA` al superar dos
  recordatorios.

### Estructura BPMN

El modelo incluye negociación con eventos de mensaje y timer parametrizado, paralelo de
inscripciones/materiales/recursos, gateway inclusivo para aula física/aula virtual/
equipamiento, subproceso de cancelación, compensaciones y desarrollo docente. Las decisiones
de `docs/05-decisiones.md` explican los gateways de unión, la compensación intermedia, la
cancelación (compensaciones encadenadas con `activityRef` dentro del subproceso de evento y la tarea
`Confirmar compensaciones` que retiene el cierre hasta que se libere el equipamiento) y el paso asíncrono necesario porque la app de materiales no
contiene las clases del módulo `proceso`.

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

`mineria/` contiene los dos scripts adaptados al proceso, `generar_ejecuciones.py` (recorre el
proceso para tener historial), `eventos.csv`, los gráficos (`recorridos.png`,
`modelo_descubierto.png`), `resultados.txt` con los tres recorridos más frecuentes y
`conclusion.md`. La exportación toma las actividades de la etapa de negociación, desde
`solicitudRecibida` hasta *Habilitar acceso a la empresa* o un fin temprano. Cómo reproducirlo:
`mineria/README.md`.

## 5. Estado y tareas pendientes

### Lista de tareas y pendientes

> **Estado:** `[ ]` pendiente, `[-]` parcialmente implementado, `[x]` verificado.
> Esta lista es el checklist técnico del proyecto; al cerrar una tarea hay que actualizarla
> junto con su issue, código y tests.

- [x] **#11 · Motor de viabilidad:** consultar instancias abiertas de todas las versiones
  con `accesoHabilitado=true`, excluyendo la instancia actual; la prueba cubre el límite de
  cinco cursos.
- [x] **#18 · Asignación por carga:** el `TaskListener` de *Confeccionar lista definitiva*
  cuenta las tareas activas asignadas a cada miembro de `administracion` y asigna al de
  menor carga, con desempate alfabético por ID; lo cubre
  `AsignarAdministrativoConMenosTareasListenerTest`.
- [x] **#17 · Persistencia de acceso:** `HabilitarAccesoEmpresaDelegate` busca la empresa del
  curso (`businessKey`), crea la `cuenta_empresa` con usuario y clave BCrypt la primera vez y
  la reutiliza después; deja `accesoHabilitado = true`. Lo cubren
  `HabilitarAccesoEmpresaDelegateTest` y `CuentasDeEmpresaJdbcTest`.
- [x] **#25 · Persistencia final:** `RegistrarResultadoDelCursoListener` actualiza estado,
  programa, presupuesto, fecha e informe del curso por su `businessKey`, con `COALESCE` para no
  borrar lo ya registrado; lo cubren `RegistrarResultadoDelCursoListenerTest` y
  `CursoRepositoryJdbcTest`.
- [x] **#19 · Reserva/liberación:** `ReservarAulaDelegate` simula el web service de aulas
  (`ServicioAulasSimulado`), guarda `aulaAsignada` y `LiberarAulaDelegate` se la devuelve al
  liberar; la virtual usa `ServicioAulasVirtuales`. La excepción `AULA_NO_DISPONIBLE` queda
  para cuando la reserva deje de ser simulada (el servicio simulado nunca rechaza).
- [x] **#21 · Evaluar recursos:** `EvaluarRecursosController` muestra las tres casillas
  (`requiereAula`, `requiereAulaVirtual`, `requiereEquipamiento`) y exige al menos una antes
  de completar la tarea, porque sin recursos el modelo no sigue; lo cubre
  `EvaluarRecursosControllerTest`.
- [x] **#22 · Lista definitiva:** `ConfeccionarListaDefinitivaController` parte de la nómina
  recibida en `participantes`, la deja editar (un participante por línea) y guarda
  `listaDefinitiva`, rechazando una lista vacía; lo cubre
  `ConfeccionarListaDefinitivaControllerTest`.
- [x] **#26 · Tareas docentes:** `DefinirDocenteController` elige el docente del curso y
  completa `definirDocente` con `docenteAsignado`; lo cubre `DefinirDocenteControllerTest`.
- [x] **#27 · Informe y cierre:** `ElaborarInformeController` carga `informeResultados` y
  `EnviarInformeController` consulta, envía y archiva el expediente; el flujo libera los
  recursos y termina con `resultado = 'FINALIZADO'`.
- [x] **#28 · Login de empresas:** `AutenticacionEmpresa` valida contra `cuenta_empresa` con
  BCrypt y `SeguridadConfig` protege `/empresas/**` con el rol `EMPRESA`.
- [x] **#29 · Portal de empresas:** `PortalEmpresaController` lista solo los cursos de la
  empresa logueada, deriva el estado de las actividades en curso de Flowable y muestra las
  ramas paralelas durante la preparación; lo cubre `PortalEmpresaControllerTest`.
- [x] **#24 · App de materiales:** *Reclamar* y *Completar* con la validación de asignación
  (`ServicioDeTareas`); lo cubre `ServicioDeTareasTest`. Las reglas están en
  [09-app-materiales.md](09-app-materiales.md) §7.
- [x] **#30 · Resultados de las solicitudes:** `ConsultasHistoricas.resultadosDeLasSolicitudes()`
  agrupa las instancias terminadas por la variable `resultado` y calcula cantidad y
  porcentaje; la app de materiales lo muestra en la pestaña *Resultados de Solicitudes
  (§4.3.1)* y lo cubre `ConsultasHistoricasTest`.
- [x] **#31 · Duración promedio de las ramas:** `ConsultasHistoricas.duracionPromedioDeLasRamas()`
  mide inscripciones, materiales y recursos solo sobre las instancias que completaron
  `preparacionCompleta` (las canceladas quedan afuera) y devuelve
  `DuracionRamas(inscripciones, materiales, recursos, totalInstancias)`; la app de materiales
  lo muestra en la pestaña *Duración de Ramas (§4.3.2)* y lo cubre `ConsultasHistoricasTest`.
- [x] **#32 · Tareas completadas por persona:** `ConsultasHistoricas.tareasCompletadasPorPersona()`
  cuenta las tareas terminadas por `assignee`, descartando las que tienen `deleteReason` (canceladas
  o interrumpidas) y las que no tienen asignado; devuelve `TareaPorPersona(usuario, cantidad)`
  ordenado por cantidad y usuario. La app de materiales lo muestra en la pestaña
  *Tareas por Persona (§4.3.3)* y lo cubre `ConsultasHistoricasTest`.
- [x] **#16 · App de comunicaciones (negociación):** `Comunicacion` con los mensajes del
  modelo, detección de la ejecución que espera cada mensaje y envío con sus variables
  (fecha acordada en ISO, observaciones, rechazo sin datos). Los caminos de aceptación,
  rechazo y modificaciones los cubre `NegociacionTest`.
- [x] **#23 · App de comunicaciones (preparación):** nómina y cancelación, con el mismo
  mecanismo: una constante más en `Comunicacion`; lo cubre `ComunicacionesDeLaEmpresaTest`.
- [x] **M-01–M-03 · Minería de procesos:** `exportar_history.py`, `analizar.py`,
  `eventos.csv`, gráfico, tres recorridos más frecuentes con porcentajes y `conclusion.md`
  en `mineria/`.
- [x] **#44 · Cancelación con compensaciones:** compensaciones encadenadas con `activityRef`
  dentro del subproceso de evento y la tarea *Confirmar compensaciones*, que retiene el cierre
  hasta liberar el equipamiento (`docs/05-decisiones.md`, limitaciones 1 y 2); la web no ofrece
  completarla antes; lo cubre `CancelacionYCompensacionesTest`.
- [x] **#43 · Escenarios de la defensa:** `scripts/escenarios/` deja listos los escenarios 1, 4, 5
  y 6 (`docs/12-escenarios-defensa.md`).
- [x] **#46–#56 · Mejoras de calidad:** guardar la propuesta y completar la tarea en una
  transacción, lo común de las pantallas de tarea en `PantallasDeTareas`, el plazo de respuesta
  por defecto en el modelo, las cinco filas de la consulta §4.3.1, conexión compartida a la BD de
  la institución y limpieza de código muerto.
- [ ] **Escenario 7 de defensa (#36):** acordar con el profesor qué sucede cuando no hay aula;
  el modelo todavía no lanza `BpmnError`.
- [ ] **Entrega y defensa:** informe breve (#38), instrucciones finales (#37) y ensayo de los 11
  escenarios en una instalación limpia (#39).

### Puntos de prueba para la defensa

- Aceptación y cierre normal con compensación; modificaciones y nueva versión.
- Rechazo; dos recordatorios y cierre por falta de respuesta.
- Cinco cursos en ejecución y camino `NO_VIABLE`.
- Cancelación durante preparación con solo las compensaciones de recursos efectivamente
  gestionados; con equipamiento contratado, hay que completar *Liberar equipamiento* y después
  *Confirmar compensaciones*, y recién entonces se notifica el cierre.
- Asignación por carga, claim obligatorio para tareas de grupo y rechazo de completar tareas
  ajenas.
- Registro web, `businessKey`/`process_instance_id`, habilitación y aislamiento por empresa.
- Materiales por API Java y comunicaciones por API REST.
- Timer de respuesta reducido (`PLAZO_RESPUESTA=PT2M`) y fecha de curso cercana.
- Persistencia de cada resultado final y exactitud de las tres consultas históricas.

### Riesgos técnicos a controlar

- Dos motores comparten la BD `flowable`; solo el servidor debe ejecutar timers/jobs.
- El módulo `proceso` debe seguir compilando para Java 11.
- Flowable no impide completar una tarea ajena: la autorización debe permanecer en web/app.
- Cambiar IDs BPMN, mensajes o variables rompe clientes externos.
- El despliegue BPMN+DMN debe hacerse como `.bar` con `scripts/desplegar-proceso.sh`;
  desplegar solo BPMN por REST no garantiza la disponibilidad de la decisión DMN.
