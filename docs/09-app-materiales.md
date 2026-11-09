# 09 · App de materiales

Cómo está armada la aplicación de escritorio de los administrativos (§4.2.1) y qué cuidar al modificarla. La API Java
de Flowable está resumida en [03-guia-flowable.md](03-guia-flowable.md) §4.

## 1. Para qué sirve

Con esta app un administrativo hace su parte de la preparación del curso, la tarea **"Preparar materiales"**, sin
pasar por el sitio web:

1. se identifica con su usuario de Flowable;
2. ve sus tareas "Preparar materiales": las asignadas a él y las que puede reclamar por ser de su grupo;
3. reclama una disponible y la completa cuando los materiales están listos (#24).

También muestra las consultas sobre el historial de ejecuciones del §4.3 (#30–#32).

El enunciado pide que use la **API Java** de Flowable, y no la API REST como la web y la app de comunicaciones. Es
una herramienta interna: corre en la red de la institución y tiene acceso directo a la base del motor.

## 2. Cómo se conecta: motor embebido

Flowable es una librería Java. El servidor de Docker es esa librería dentro de una aplicación web que agrega la
interfaz y la API REST. Esta app incluye la misma librería (`flowable-engine` 6.8.0, la versión del servidor) y
levanta **su propio motor, conectado a la misma base `flowable`** que el servidor:

```
App de materiales ──(API Java)──▶ motor embebido ──┐
                                                    ├──▶ PostgreSQL: base "flowable"
Servidor Flowable ◀──(REST)── web, comunicaciones ──┘
```

No hay otro servidor: son dos motores que trabajan sobre las mismas tablas, así que lo que hace uno lo ve el otro al
instante. Desde la app se usan los servicios del motor: `IdentityService` (login), `TaskService` (tareas) y
`HistoryService` (consultas del §4.3).

## 3. Configuración del motor: lo delicado

Como la app escribe en la misma base que el servidor, su motor se configura para **no interferir** con él
(`MotorFlowable.configuracion()`):

| Configuración | Por qué |
|---------------|---------|
| `DB_SCHEMA_UPDATE_FALSE` | Las tablas son del servidor: la app no las crea ni las actualiza. Si la versión de la librería no coincide con la de la base, falla al conectar en lugar de modificar el esquema |
| `setAsyncExecutorActivate(false)` | Los timers y las tareas asíncronas los ejecuta solo el servidor. La app no tiene las clases Java del proceso: si ejecutara esos trabajos, fallarían y las instancias se trabarían |
| `StrongUuidGenerator` | Genera los IDs con el mismo formato (UUID) que el servidor |

`MotorFlowableTest` verifica estas tres cosas: si alguien las cambia, `./mvnw verify` falla. No se conecta a ninguna
base, así que corre también en CI.

**Lo que se ejecuta después de completar una tarea corre en la app.** Cuando el administrativo completa "Preparar
materiales" y era la última rama de la preparación, el proceso sigue avanzando dentro del motor de la app. Si llegara a
una clase Java del proceso, fallaría porque la app no la tiene. Por eso *Enviar confirmación* es asíncrona: la app deja
ese trabajo pendiente y lo ejecuta el servidor ([05-decisiones.md](05-decisiones.md), limitación 8). Si se cambia el
modelo después de la preparación, el primer paso con una clase Java tiene que seguir siendo asíncrono.

**Conexión.** Se toma de variables de entorno, con valores por defecto para el entorno Docker:

| Variable | Por defecto |
|----------|-------------|
| `FLOWABLE_DB_URL` | `jdbc:postgresql://localhost:5432/flowable` |
| `FLOWABLE_DB_USER` | `flowable` |
| `FLOWABLE_DB_PASSWORD` | `flowable` |

## 4. Levantarla

Con el entorno Docker levantado ([04-entorno-desarrollo.md](04-entorno-desarrollo.md)):

```bash
./mvnw -pl app-materiales compile exec:java
```

Tarda unos segundos en conectar el motor y después pide usuario y contraseña. Entran los usuarios de Flowable, por
ejemplo `administrativo` / `clave123`. Si los datos son incorrectos los vuelve a pedir; *Cancelar* cierra la app.

Al arrancar, la consola muestra avisos que son normales:
- `SLF4J: Failed to load class … Defaulting to no-operation (NOP) logger`: la app no incluye una librería de logs, así
  que los mensajes internos del motor se descartan.
- Líneas `INFO` de **liquibase** (`Cannot load service…`, `Reading from public.flw_ev_databasechangelog`): el motor
  comprueba que las tablas de la base correspondan a su versión. Solo las lee; no modifica nada (§3).

La ventana de login puede abrirse detrás de la terminal o del editor.

## 5. Organización

```
app-materiales/src/main/java/ar/edu/unrc/gtpn/materiales/
├── MaterialesApp.java       arranque: conecta el motor y pide el login
├── MotorFlowable.java       configuración del motor embebido (§3)
├── ServicioDeTareas.java    todo lo que la app le pide al motor: login y consultas de tareas
├── VentanaDeTareas.java     ventana con la lista de tareas del usuario
└── historial/
    └── ConsultasHistoricas.java   consultas del §4.3 (pendiente)
```

La interfaz (Swing) no habla con el motor directamente: usa `ServicioDeTareas`. Así las reglas quedan en un solo lugar
y se pueden testear sin ventanas.

El aspecto lo da **FlatLaf**, que se activa en `MaterialesApp.usarAspectoModerno()` junto con algunos ajustes
(bordes redondeados, filas más altas, botones de los diálogos en español). Las ventanas nuevas lo toman solas.
Todo se muestra un 25 % más grande. Para otro tamaño se agrega, por ejemplo, `-Dflatlaf.uiScale=1.5` al comando de
§4. Las medidas fijas en píxeles se pasan por `UIScale.scale(…)` para que acompañen ese tamaño.

## 6. Login y tareas

- **Login:** `identityService.checkPassword(usuario, clave)` contra los usuarios de Flowable, los mismos que crea
  `scripts/setup-identidades.sh`.
- **Tareas:** `taskDefinitionKey("prepararMateriales").taskCandidateOrAssigned(usuario)`, que devuelve las asignadas
  al usuario y las disponibles para sus grupos. La ventana muestra la solicitud (la `businessKey` de la instancia), la
  tarea, si está asignada al usuario o disponible para su grupo, y cuándo se creó. *Actualizar* vuelve a consultar.

## 7. Reclamar y completar (#24)

Flowable no controla quién completa una tarea ([05-decisiones.md](05-decisiones.md), limitación 7). La app aplica las
mismas reglas que la web ([08-web.md](08-web.md) §4), en `ServicioDeTareas`:

1. **Tarea disponible:** se reclama con `taskService.claim(tareaId, usuario)`. Si otro la reclamó antes, Flowable lanza
   `FlowableTaskAlreadyClaimedException` y la app lo informa.
2. **Tarea asignada:** solo la completa su asignado. Antes de `taskService.complete(tareaId)` se verifica que
   `tarea.getAssignee()` sea el usuario logueado.

Esta lógica se testea con un motor en memoria (`@FlowableTest` con H2), como los tests del módulo `proceso`.

## 8. Consultas históricas (#30–#32)

Van en `historial/ConsultasHistoricas`, con `HistoryService`. Cómo hacer cada consulta y qué cuidar (tareas canceladas,
IDs de las ramas): [03-guia-flowable.md](03-guia-flowable.md) §8 y
[06-guia-implementacion-modelo.md](06-guia-implementacion-modelo.md) §10.

## 9. Lo que falta

| Issue | Qué agrega |
|-------|------------|
| #24 | Botones *Reclamar* y *Completar*, con las reglas de §7 y sus tests |
| #30 | Resultados de las solicitudes (§4.3.1) |
| #31 | Duración promedio de las ramas de la preparación (§4.3.2) |
| #32 | Tareas completadas por persona (§4.3.3) |
