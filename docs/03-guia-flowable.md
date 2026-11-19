# 03 · Guía práctica de Flowable para el grupo

Lo marcado con ✔ se **probó** contra el entorno del
repo (Flowable 6.8.0 + PostgreSQL 15); el resto sale de la documentación oficial.

## 1. Conceptos en cinco minutos

| Concepto | Qué es |
|----------|--------|
| Definición de proceso | El BPMN desplegado. Cada despliegue crea una **versión** nueva con la misma clave. |
| Instancia | Una ejecución concreta del proceso: en este proyecto, una por solicitud de curso. |
| Ejecución (*execution*) | Un "token" dentro de la instancia. Con ramas paralelas hay varias a la vez. Los mensajes se entregan a una ejecución. |
| Tarea de usuario | Espera a que una persona la complete. `assignee` = dueño; `candidateGroups` = la puede **reclamar** (*claim*) cualquiera del grupo. |
| Variable | Dato de la instancia (texto, número, booleano, fecha, JSON). |
| Service task | Paso automático; con `flowable:class` ejecuta un `JavaDelegate` nuestro. |
| Listener | Código que reacciona a un evento: `ExecutionListener` (start/end/take) o `TaskListener` (create/assignment/complete). |
| Decision task | Service task `flowable:type="dmn"` que evalúa una tabla DMN. |
| Job | Trabajo diferido (timers, pasos `async`). Lo ejecuta el *async executor* **del servidor**. |

## 2. Cómo se trabaja el modelo

El archivo BPMN en git (en `proceso/src/main/resources/procesos/`) es la **única fuente de verdad** del modelo.
Lo que quede en el Modeler de Flowable de cada uno es local y se pierde.

1. **Avisar** en el issue del modelo que lo vas a editar. Una sola persona a la vez: los conflictos en XML de BPMN no
   se resuelven bien.
2. Editar con alguna de estas opciones:
   - **Flowable Modeler** (`http://localhost:8080/flowable-ui` → Modeler → *Import process* → editar → *Export*).
     Tiene el panel de propiedades de Flowable (assignee, listeners, async, form properties).
   - **Camunda Modeler** (el que se usó en las prácticas) para dibujar y acomodar, y los atributos `flowable:` a mano en
     el XML. En general conserva atributos que no conoce; confirmarlo siempre con `git diff`.
3. Mirar el `git diff`: ¿cambiaron IDs? ¿Se rompieron tildes? ¿Desapareció algún `flowable:`?
4. Correr los tests del proceso: `./mvnw -pl proceso verify`.
5. Probarlo en el servidor: `./mvnw -pl proceso package`, `docker compose restart flowable` (solo si cambió Java)
   y `bash scripts/desplegar-proceso.sh`.
6. Si cambiaste IDs de tareas, mensajes o variables, avisar al grupo: la web y las apps los usan.

**Versiones:** cada despliegue crea una versión nueva. Las instancias viejas siguen con la versión con la que
arrancaron, así que para probar un cambio hay que iniciar una instancia nueva. Si quedaron instancias viejas
inservibles, se borran desde la UI (Admin) o se reinicia todo (§7).

### Probar desde la UI de Flowable

Mientras no estén la web y las apps, el proceso se recorre desde <http://localhost:8080/flowable-ui> con `admin` /
`test`. Se usan dos apps: **Task** (iniciar y completar tareas) y **Admin** (ver y manejar las instancias).

1. **Iniciar una solicitud.** Task → *Processes* → *Start a process* → **Institución** → *Start*. Así la instancia
   arranca sin `plazoRespuesta` (en el uso real lo manda la web): agregarla en el paso 2 **antes** de completar la
   primera tarea, o falla al llegar al timer (§7).
2. **Process instances.** Admin → *Process Engine* → *Process instances* → elegir la instancia:
   - *Show process diagram*: el diagrama con las actividades en curso resaltadas;
   - *Variables*: las variables de la instancia. Con *Add variable* se agregan o se corrigen (por ejemplo,
     `plazoRespuesta`, tipo `string`, valor `P5D`);
   - *Tasks*: las tareas abiertas y a quién están asignadas;
   - también permite borrar la instancia.
3. **Completar tareas.** Task, entrando con el usuario asignado (por ejemplo `jefe_admin` / `clave123`) → la tarea →
   *Complete*. Los campos de formulario no se muestran (guía 06 §8): si la tarea carga datos, se agregan antes como
   variables de la instancia (paso 2).
4. **Event subscriptions** (mensajes de la empresa). Admin → *Process Engine* → *Event subscriptions*: hay una
   suscripción por cada mensaje que una instancia está esperando (`aceptacionPropuesta`, `rechazoPropuesta`,
   `modificacionesPropuesta`, `nominaParticipantes`, `cancelacionCurso`). Elegir la de la instancia → *Trigger event*.
   El mensaje llega sin datos: las variables que trae (`fechaInicioCurso`, `observacionesEmpresa`, `participantes`) se
   agregan antes en la instancia. No disparar `solicitudCapacitacion`, que es la suscripción de inicio del proceso.
5. **Jobs** (timers). Admin → *Process Engine* → *Jobs*, con el filtro *Job type*:
   - *Timer job*: los timers pendientes y cuándo vencen. Abrir uno → *Move job* lo hace vencer en el momento (así se
     prueba el plazo de 5 días sin esperar);
   - *Deadletter job*: los jobs que fallaron, con el error.

Las mismas acciones por REST están en §3 (las usan la web y la app de comunicaciones).

## 3. API REST (sitio web y app de comunicaciones)

Base: `http://localhost:8080/flowable-ui/process-api`. Autenticación HTTP Basic con un usuario de Flowable que tenga
el privilegio `access-rest-api` (lo da `setup-identidades.sh` a cada miembro de los grupos). Credenciales incorrectas → 401; sin
privilegio → 403. ✔

| Para qué | Llamada | |
|----------|---------|:-:|
| Iniciar una instancia por mensaje (§4.1.1) | `POST /runtime/process-instances` `{"message":"<mensajeDeInicio>","businessKey":"42","variables":[{"name":"<variable>","value":"<valor>"}]}` | ✔ |
| Tareas asignadas a un usuario | `GET /runtime/tasks?assignee=jefe_admin` | ✔ |
| Tareas que puede reclamar (por sus grupos) | `GET /runtime/tasks?candidateUser=administrativo` | ✔ |
| Reclamar | `POST /runtime/tasks/{id}` `{"action":"claim","assignee":"administrativo"}` | |
| Completar con datos | `POST /runtime/tasks/{id}` `{"action":"complete","variables":[{"name":"<variable>","value":"<valor>"}]}` | ✔ |
| ¿La instancia espera tal mensaje? (comunicaciones habilitadas, X-04) | `GET /runtime/executions?processInstanceId={pi}&messageEventSubscriptionName=<mensaje>` (lista vacía = no habilitada) | ✔ |
| Entregar un mensaje | `PUT /runtime/executions/{executionId}` `{"action":"messageEventReceived","messageName":"<mensaje>","variables":[…]}` | ✔ |
| Actividades en curso, incluidas las paralelas (W-05) | `GET /history/historic-activity-instances?processInstanceId={pi}&finished=false` | ✔ |
| Variables de una instancia | `GET /runtime/process-instances/{pi}/variables` | ✔ |
| Instancias en curso | `GET /runtime/process-instances?processDefinitionKey=<claveDelProceso>` | ✔ |
| Miembros de un grupo | `GET /identity/users?memberOfGroup=docentes` (ojo: `GET /identity/groups/{id}/members` da 500) | ✔ |
| Desplegar BPMN + DMN | `POST /flowable-ui/app-api/app-repository/deployments` con un `.bar` (lo hace el script) | ✔ |

Tipos de variable: `"type":"string" | "integer" | "long" | "boolean" | "date" | "json"`. Si no se indica, Flowable
lo infiere del JSON.

> ⚠ **Flowable no valida quién completa una tarea.** Se probó que `docente1` puede completar una tarea asignada a
> `jefe_admin`. La web y la app de materiales tienen que comprobar, antes de llamar al motor, que la tarea es del
> usuario (o que la reclamó) y que le corresponde según el §2.

## 4. API Java (app de materiales)

```java
ProcessEngine motor = ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration()
        .setJdbcDriver("org.postgresql.Driver")
        .setJdbcUrl("jdbc:postgresql://localhost:5432/flowable")
        .setJdbcUsername("flowable").setJdbcPassword("flowable")
        .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE) // no tocar el esquema del servidor
        .setAsyncExecutorActivate(false)                                            // los jobs los ejecuta el servidor
        .buildProcessEngine();                                                       // ✔ con Java 21
boolean ok = motor.getIdentityService().checkPassword(usuario, clave);                       // ✔
List<Task> tareas = motor.getTaskService().createTaskQuery()
        .taskDefinitionKey(idDeLaTareaPrepararMateriales)
        .taskCandidateOrAssigned(usuario)                 // asignadas a él o reclamables por sus grupos ✔
        .list();
motor.getTaskService().claim(tarea.getId(), usuario);
motor.getTaskService().complete(tarea.getId());
```

- El motor embebido **no ejecuta jobs** (`setAsyncExecutorActivate(false)`): los timers son del servidor.
- Lo que el proceso ejecute después de completar una tarea corre **en la app**. Si llega a una clase nuestra que la
  app no tiene, falla: conviene marcar como `async` el paso siguiente para que lo ejecute el servidor.
- `DB_SCHEMA_UPDATE_FALSE`: si la versión del motor no coincide con la de la BD, falla al conectar (mejor eso que
  romper el esquema).

## 5. Escribir listeners y delegates

Reglas:

1. **Una clase, una responsabilidad.** `NotificarEmpresaDelegate` notifica; no también calcula ni guarda.
2. **Sin estado mutable en campos.** Flowable crea **una sola instancia por elemento del modelo** y la reutiliza entre
   instancias de proceso.
3. **Configuración por campos (`flowable:field`)** cuando la misma clase sirve a varias tareas (Open/Closed).
4. **Dependencias por constructor.** Flowable usa el constructor sin argumentos; los tests usan el otro:

   ```java
   public class RegistrarResultadoDelCursoListener implements ExecutionListener {

       private final CursoRepository cursos;

       public RegistrarResultadoDelCursoListener() {                 // el que usa Flowable
           this(CursoRepositoryJdbc.desdeEntorno());
       }

       RegistrarResultadoDelCursoListener(CursoRepository cursos) {  // el que usan los tests
           this.cursos = cursos;
       }

       @Override
       public void notify(DelegateExecution execution) {
           cursos.actualizarEstado(execution.getProcessInstanceId(), (String) execution.getVariable("resultado"));
       }
   }
   ```

5. **Errores de negocio** → `throw new BpmnError("AULA_NO_DISPONIBLE")`, que lo atrapa un evento de error del modelo.
   Cualquier otra excepción **revierte toda la transacción**: la llamada REST devuelve 500 y la instancia queda como
   estaba.
6. **Servicios del motor** desde un listener o delegate: `CommandContextUtil.getProcessEngineConfiguration()`
   (por ejemplo, `.getRuntimeService()` para consultar instancias).
7. **Java 11** en el módulo `proceso`: nada de records, `Stream.toList()` ni otras API de Java 16+. El compilador lo
   avisa.
8. **Variables simples o JSON**, nunca objetos Java serializados: la web y la app de comunicaciones no podrían leerlas.

## 6. Timers y fechas

- Duración: ISO-8601 (`P5D`, `PT1M`), tomada de una variable del proceso: `<timeDuration>${plazo}</timeDuration>`.
- Fecha: `<timeDate>${fechaInicio}</timeDate>`, con un `java.util.Date` o un texto ISO-8601
  (`2026-12-01T09:00:00-03:00`). Conviene incluir el desfase: sin él Flowable lee la hora en la zona de su servidor (UTC),
  y el timer vence 3 horas antes que la hora local argentina. Si la fecha ya pasó, el timer se dispara enseguida (sirve
  para la demo).
- El servidor revisa los timers cada pocos segundos: un `PT1M` tarda aproximadamente un minuto en dispararse.

## 7. Errores comunes (y cómo se ven)

| Síntoma | Causa | Solución |
|---------|-------|----------|
| `docker compose up` falla con "bind source path does not exist" | Falta `proceso/target/proceso.jar` | `./mvnw -pl proceso package` |
| `ClassNotFoundException` en los logs del servidor | Jar viejo o no reiniciado | `./mvnw -pl proceso package && docker compose restart flowable` |
| `UnsupportedClassVersionError` | Clase compilada para Java > 11 | No tocar `maven.compiler.release=11` en `proceso/pom.xml` |
| "No decision found for key…" | DMN desplegado por la REST de procesos | Usar `scripts/desplegar-proceso.sh` |
| `cvc-complex-type…` al desplegar | Orden inválido en el XML: las `association` van al final del subproceso | Reordenar (o guardar desde el Modeler) |
| `Cannot find the declaration of element 'definitions'` (DMN) | Comentario antes de `<definitions>` | Mover el comentario adentro |
| 403 en la REST con un usuario del personal | Falta el privilegio `access-rest-api` | `bash scripts/setup-identidades.sh` |
| `Unknown property used in expression: ${plazoRespuesta}` al completar una tarea | La instancia se inició sin `plazoRespuesta` (por ejemplo, desde la Task app) | Agregarla en Admin → la instancia → *Add variable* (§2, "Probar desde la UI de Flowable") |
| La consulta del listener no ve la instancia actual | Se insertó en la misma transacción y todavía no se escribió | Filtrar por ID igual (ya se hace) |

Logs del servidor: `docker compose logs -f flowable`. Reiniciar todo desde cero (**borra instancias, usuarios y la
BD de la institución**): `docker compose down -v`, después `docker compose up -d`, el setup y el despliegue.

## 8. Historial: consultas (§4.3) y minería de procesos (§4.4)

Flowable guarda en su BD el historial de cada instancia, también de las terminadas: actividades (con inicio, fin y
duración), tareas (con quién las completó) y variables. Con el nivel de historial por defecto del servidor ya
quedan registrados. ✔

| Consulta | API Java (`HistoryService`) | API REST |
|----------|-----------------------------|----------|
| Instancias terminadas y su resultado (§4.3.1) | `createHistoricProcessInstanceQuery().processDefinitionKey("organizarCurso").finished().includeProcessVariables()` → variable `resultado` | `GET /history/historic-process-instances?processDefinitionKey=organizarCurso&finished=true&includeProcessVariables=true` |
| Actividades de una instancia, con inicio y fin (§4.3.2, §4.4) | `createHistoricActivityInstanceQuery().processInstanceId(id).orderByHistoricActivityInstanceStartTime().asc()` | `GET /history/historic-activity-instances?processInstanceId={id}` |
| Tareas completadas por persona (§4.3.3) | `createHistoricTaskInstanceQuery().processDefinitionKey("organizarCurso").finished()`, agrupando por `getAssignee()` | `GET /history/historic-task-instances?processDefinitionKey=organizarCurso&finished=true` |

Cuidados:
- **Tareas completadas vs. canceladas:** una tarea interrumpida (por ejemplo, por la cancelación del curso) también
  figura como terminada, pero con `deleteReason` distinto de `null`. Para §4.3.3 se cuentan solo las que tienen
  `deleteReason == null`.
- **Duración de las ramas (§4.3.2):** se calcula con el inicio de la primera actividad de la rama y el fin de la
  última, buscándolas por ID. Los IDs de cada rama están en [06-guia-implementacion-modelo.md](06-guia-implementacion-modelo.md) §10.
- **Minería (§4.4):** el CSV usa una fila por actividad con el caso (la instancia), la actividad y el momento. Por
  eso los nombres de las actividades del modelo tienen que ser claros: son los que aparecen en el gráfico.

## 9. Referencias

- Flowable 6.8 – BPMN: <https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs> (eventos, compensación,
  subprocesos de evento) y <https://www.flowable.com/open-source/docs/bpmn/ch07a-BPMN-Introduction>.
- Flowable 6.8 – REST: <https://www.flowable.com/open-source/docs/bpmn/ch14-REST>.
- Flowable 6.8 – DMN: <https://www.flowable.com/open-source/docs/dmn/ch06-DMN-Introduction>.
- Javadoc 6.8: <https://javadoc.io/doc/org.flowable/flowable-engine/6.8.0/index.html>.
- Camunda – semántica BPMN (sirve como referencia conceptual; la ejecución puede diferir en Flowable):
  <https://docs.camunda.io/docs/components/modeler/bpmn/compensation-handler/>,
  <https://docs.camunda.io/docs/components/modeler/bpmn/event-subprocesses/>,
  <https://docs.camunda.io/docs/components/modeler/bpmn/error-events/>.
- Materia: `Teorias/` (7PMG, patrones, Clase 4 Flowable) y prácticas 4 y 5.
