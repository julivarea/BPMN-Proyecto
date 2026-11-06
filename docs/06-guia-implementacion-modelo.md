# 06 · Guía de implementación del modelo

Qué hay que configurar en `proceso/src/main/resources/procesos/organizarCurso.bpmn20.xml` para que el proceso cumpla
el enunciado y se ejecute en Flowable 6.8. Las limitaciones del motor que condicionan algunas decisiones están en
[05-decisiones.md](05-decisiones.md).

## 1. Convenciones

| Qué | Convención |
|-----|------------|
| Clave del proceso | `organizarCurso` |
| `businessKey` de cada instancia | id del curso en la BD de la institución (lo envía la web al iniciar) |
| IDs de elementos | camelCase con significado (`registrarYElaborarPropuesta`, no `Activity_15h2gcg`). **Obligatorio** en tareas de usuario, eventos de mensaje y timers: la web y las apps los usan. Recomendado en el resto. |
| Nombres de mensajes | camelCase, declarados como `<message>` y referenciados con `messageRef` |
| Variables | camelCase y tipos simples: texto, número, booleano, fecha ISO-8601 o JSON. Nunca objetos Java: la web y las apps no podrían leerlos. |
| Tareas automáticas | Service tasks con clase Java (`flowable:class`). Las script tasks del diagrama se convierten en service tasks: el código queda en Java, testeable y versionado. |
| Clases Java | Paquete `ar.edu.unrc.gtpn.proceso.<funcionalidad>`, en el módulo `proceso` |
| Usuarios y grupos | `jefe_admin`, `administrativo` (grupo `administracion`); `jefe_docente`, `docente1`, `docente2` (grupo `docentes`) |

## 2. Variables del proceso

| Variable | Tipo | La escribe | La usa |
|----------|------|------------|--------|
| `resultado` | texto: `EN_CURSO`, `NO_VIABLE`, `RECHAZADO`, `SIN_RESPUESTA`, `CANCELADO`, `FINALIZADO` | Inicio (`EN_CURSO`) y cada camino de cierre | Listener de fin del proceso (§3.4); XOR de cancelación |
| `plazoRespuesta` | duración ISO-8601: `P5D` (demo: `PT2M`) | Web, al iniciar la instancia | Timer "5 días" |
| `cursosEnEjecucion` | número | Listener de *Evaluar viabilidad* | Regla DMN |
| `esViable` | booleano | Regla DMN | XOR "¿es viable?" |
| `accesoHabilitado` | booleano | *Habilitar acceso a la empresa* | Conteo de cursos en ejecución |
| `programa`, `presupuesto`, `fechasPosibles` | texto, número, texto | *Registrar* y *Revisar propuesta* | PDF de la propuesta (web) |
| `versionPropuesta` | número | Inicio (0) y *Enviar propuesta* (+1) | PDF de cada versión (web) |
| `recordatoriosEnviados` | número | *Enviar propuesta* (0) y *Enviar recordatorio* (+1) | XOR "¿se enviaron 2 recordatorios?" |
| `observacionesEmpresa` | texto | Mensaje de modificaciones | *Revisar propuesta* |
| `fechaInicioCurso` | fecha ISO-8601 (`2026-12-01T09:00:00`) | Mensaje de aceptación | Timer "fecha programada" |
| `participantes` | JSON (lista) | Mensaje de nómina | *Confeccionar lista definitiva* |
| `listaDefinitiva` | texto (un participante por línea) | *Confeccionar lista definitiva* | *Desarrollar curso* |
| `requiereAula`, `requiereAulaVirtual`, `requiereEquipamiento` | booleano | *Evaluar recursos* | Ramas del gateway inclusivo |
| `aulaAsignada` | texto | *Reservar aula* | *Liberar aula* |
| `docenteAsignado` | id de usuario | *Definir docente* | Asignación de *Desarrollar curso* |
| `informeResultados` | texto | *Elaborar informe de resultados* | *Enviar informe y archivar expediente*; BD |

## 3. Mensajes de la empresa

Los envía la app de comunicaciones por REST (`messageEventReceived`), excepto el de inicio, que lo envía la web.

| Evento del diagrama | ID | `messageName` | Variables que trae |
|---------------------|----|---------------|--------------------|
| recibir solicitud de curso | `solicitudRecibida` | `solicitudCapacitacion` | `plazoRespuesta` (+ `businessKey`) |
| recibir aceptación de propuesta | `aceptacionRecibida` | `aceptacionPropuesta` | `fechaInicioCurso` |
| recibir rechazo de propuesta | `rechazoRecibido` | `rechazoPropuesta` | — |
| recibir solicitud de modificaciones a la propuesta | `modificacionesRecibidas` | `modificacionesPropuesta` | `observacionesEmpresa` |
| recibir nómina de inscripciones | `nominaRecibida` | `nominaParticipantes` | `participantes` |
| recibir cancelación | `cancelacionRecibida` | `cancelacionCurso` | — |

## 4. Elemento por elemento

### Proceso y negociación de curso (§1.1)

| Elemento | ID | Configuración |
|----------|----|---------------|
| Proceso | `organizarCurso` | Execution listener `end` → `RegistrarResultadoDelCursoListener` |
| recibir solicitud de curso | `solicitudRecibida` | `messageRef` → `solicitudCapacitacion`. Execution listener `start` (expresión): `resultado = 'EN_CURSO'`, `versionPropuesta = 0` |
| Evaluar viabilidad | `evaluarViabilidad` | **Decision task**: service task `flowable:type="dmn"`, campo `decisionTableReferenceKey = viabilidad`. Execution listener `start` → `ContarCursosEnEjecucionListener` |
| ¿es viable? | `gatewayEsViable` | **SI** → `${esViable}`. **NO** → flujo por defecto |
| Comunicar imposibilidad de realizar el curso | `comunicarImposibilidad` | Service task → `NotificarEmpresaDelegate` (campo `asunto`) |
| fin por no ser viable | `finNoViable` | Execution listener `start`: `resultado = 'NO_VIABLE'` |
| Registrar y elaborar propuesta | `registrarYElaborarPropuesta` | `assignee = jefe_admin`. Formulario: `programa`, `presupuesto`, `fechasPosibles` |
| XOR de unión | `unirPropuestas` | — |
| Enviar propuesta | `enviarPropuesta` | Service task → `NotificarEmpresaDelegate`. Execution listener `start` (expresión): `versionPropuesta + 1`, `recordatoriosEnviados = 0` |
| XOR de unión | `unirEspera` | — |
| Gateway de eventos | `esperarRespuesta` | Nombre "Esperar respuesta" |
| 5 días | `plazoRespuestaVencido` | `timeDuration = ${plazoRespuesta}` |
| ¿se enviaron 2 recordatorios? | `gatewayRecordatorios` | **SI** → `${recordatoriosEnviados >= 2}`, con listener `take`: `resultado = 'SIN_RESPUESTA'`. **NO** → flujo por defecto |
| Enviar recordatorio | `enviarRecordatorio` | Service task → `NotificarEmpresaDelegate`. Execution listener `end` (expresión): `recordatoriosEnviados + 1` |
| recibir rechazo de propuesta | `rechazoRecibido` | `messageRef` → `rechazoPropuesta`. Su flecha al XOR, listener `take`: `resultado = 'RECHAZADO'` |
| XOR de unión | `unirArchivo` | — |
| Archivar solicitud | `archivarSolicitud` | `assignee = jefe_admin` |
| fin por solicitud archivada | `finSolicitudArchivada` | El resultado ya viene fijado por el camino |
| recibir solicitud de modificaciones | `modificacionesRecibidas` | `messageRef` → `modificacionesPropuesta` |
| Revisar propuesta | `revisarPropuesta` | `assignee = jefe_admin`. Formulario: `programa`, `presupuesto`, `fechasPosibles` |
| recibir aceptación de propuesta | `aceptacionRecibida` | `messageRef` → `aceptacionPropuesta` |
| Habilitar acceso a la empresa | `habilitarAccesoEmpresa` | Service task → `HabilitarAccesoEmpresaDelegate` |

### Preparación de curso (§1.2, §1.3)

| Elemento | ID | Configuración |
|----------|----|---------------|
| Preparar curso | `prepararCurso` | Subproceso embebido |
| Paralelo de inicio | `iniciarPreparacion` | — |
| recibir nómina de inscripciones | `nominaRecibida` | `messageRef` → `nominaParticipantes` |
| Confeccionar lista definitiva | `confeccionarListaDefinitiva` | `candidateGroups = administracion` + Task listener `create` → `AsignarAdministrativoConMenosTareasListener` (la deja asignada). Formulario: `listaDefinitiva` |
| Preparar materiales | `prepararMateriales` | `candidateGroups = administracion` (la completa la app de materiales) |
| Evaluar recursos | `evaluarRecursos` | `candidateGroups = administracion`. Formulario: `requiereAula`, `requiereAulaVirtual`, `requiereEquipamiento` |
| Inclusivo de recursos | `seleccionarRecursos` | **Aula física** → `${requiereAula}`, **Aula virtual** → `${requiereAulaVirtual}`, **Equipamiento** → `${requiereEquipamiento}`. Sin flujo por defecto: la web exige al menos una opción y, si no hubiera ninguna, Flowable corta con error |
| Reservar aula | `reservarAula` | Service task → `ReservarAulaDelegate` |
| Liberar aula (compensación) | `liberarAula` | Service task → `LiberarAulaDelegate` |
| Solicitar aula virtual | `solicitarAulaVirtual` | `candidateGroups = administracion` |
| Liberar aula virtual (compensación) | `liberarAulaVirtual` | Service task → `LiberarAulaVirtualDelegate` |
| Contratar equipamiento | `contratarEquipamiento` | `candidateGroups = administracion` |
| Liberar equipamiento (compensación) | `liberarEquipamiento` | User task, `candidateGroups = administracion` |
| Inclusivo de cierre | `recursosListos` | — |
| Preparación de curso completa | `preparacionCompleta` | — |
| Cancelación (subproceso de evento) | `cancelacion` | Inicio `cancelacionRecibida`, `messageRef` → `cancelacionCurso`. Reemplazar compensación + fin de error por un fin con listener `start`: `resultado = 'CANCELADO'` (ver 05-decisiones) |
| Borde de error de Preparar curso | — | Se elimina. Su función pasa al XOR siguiente |
| XOR nuevo después de Preparar curso | `gatewayPreparacionCancelada` | **Sí** → `${resultado == 'CANCELADO'}` → compensación "Liberar recursos" → *Notificar cierre* → fin. **No** → flujo por defecto → *Enviar confirmación* |
| Notificar cierre | `notificarCierre` | Service task → `NotificarEmpresaDelegate` |
| Fin por cancelación | `finCancelacion` | El resultado ya es `CANCELADO` |

Las tres asociaciones de compensación no se tocan en el editor de Flowable (05-decisiones, limitación 5).

### Desarrollo de curso (§1.4)

| Elemento | ID | Configuración |
|----------|----|---------------|
| Enviar confirmación | `enviarConfirmacion` | Service task → `NotificarEmpresaDelegate`, **asíncrona** (`flowable:async="true"`) |
| Definir docente que realizará el curso | `definirDocente` | `assignee = jefe_docente`. Formulario: `docenteAsignado` (uno de `jefe_docente`, `docente1`, `docente2`) |
| fecha programada | `fechaProgramada` | `timeDate = ${fechaInicioCurso}` |
| Desarrollar curso | `desarrollarCurso` | `assignee = ${docenteAsignado}` |
| Elaborar informe de resultados | `elaborarInformeResultados` | `assignee = jefe_docente`. Formulario: `informeResultados` |
| Enviar informe y archivar expediente | `enviarInformeYArchivarExpediente` | `assignee = jefe_admin` |
| Liberar recursos (compensación) | `liberarRecursos` | Evento intermedio de compensación, sin configuración extra |
| Curso finalizado | `finCursoFinalizado` | Execution listener `start`: `resultado = 'FINALIZADO'` |

## 5. Timers

| Timer | Configuración | Nota |
|-------|---------------|------|
| 5 días | `timeDuration = ${plazoRespuesta}` | `P5D` en serio; `PT2M` en la demo (§3.5 permite reducir plazos) |
| fecha programada | `timeDate = ${fechaInicioCurso}` | Fecha ISO-8601 fijada al aceptar. Si ya pasó, se dispara enseguida (útil para la demo) |

## 6. Regla de negocio

`viabilidad.dmn`, junto al BPMN. Tabla con política `UNIQUE`:

| `cursosEnEjecucion` | `esViable` |
|---------------------|------------|
| `< 5` | `true` |
| `>= 5` | `false` |

El `id` de la decisión (`viabilidad`) es el `decisionTableReferenceKey` de *Evaluar viabilidad*. No poner comentarios XML
antes de `<definitions>`: Flowable 6.8 deja de reconocer la versión de DMN.

## 7. Clases Java del módulo `proceso`

| Clase | Tipo | Dónde | Qué hace |
|-------|------|-------|----------|
| `viabilidad.ContarCursosEnEjecucionListener` | Execution listener `start` | *Evaluar viabilidad* | Cuenta las instancias de `organizarCurso` (todas las versiones) que siguen abiertas y tienen `accesoHabilitado = true`, y guarda `cursosEnEjecucion` (§3.1 y aclaración del profesor) |
| `notificacion.NotificarEmpresaDelegate` | Delegate | *Comunicar imposibilidad*, *Enviar propuesta*, *Enviar recordatorio*, *Enviar confirmación*, *Notificar cierre* | Simula el envío a la empresa. Una sola clase; cada tarea la configura con el campo `asunto` |
| `acceso.HabilitarAccesoEmpresaDelegate` | Delegate | *Habilitar acceso a la empresa* | Crea la cuenta de la empresa o reutiliza la existente, vincula el curso (§4.1.2) y fija `accesoHabilitado = true` |
| `recursos.ReservarAulaDelegate` | Delegate | *Reservar aula* | Simula el web service de aulas (§4.1.3) y guarda `aulaAsignada` |
| `recursos.LiberarAulaDelegate` | Delegate | *Liberar aula* | Simula la liberación del aula física |
| `recursos.LiberarAulaVirtualDelegate` | Delegate | *Liberar aula virtual* | Simula la liberación del aula virtual |
| `asignacion.AsignarAdministrativoConMenosTareasListener` | Task listener `create` | *Confeccionar lista definitiva* | Asigna la tarea al integrante de `administracion` con menos tareas **asignadas** pendientes (§3.4) |
| `estado.RegistrarResultadoDelCursoListener` | Execution listener `end` | Proceso | Guarda en el curso de la BD de la institución el `resultado` como estado (§3.4), y lo acordado: programa, presupuesto, fecha de inicio e informe (ver [07-base-de-datos.md](07-base-de-datos.md)) |

Reglas para estas clases:
- Flowable crea **una instancia por elemento** y la reutiliza: sin estado mutable en campos.
- Las dependencias (por ejemplo, el acceso a la BD) entran por constructor, con un constructor sin argumentos que arma
  las reales. Así se pueden testear con dobles.
- El módulo compila para Java 11, que es lo que corre la imagen del servidor.

Las asignaciones de resultado, contadores y versiones son expresiones de una línea en el propio XML
(`${execution.setVariable('resultado', 'RECHAZADO')}`): no necesitan clase.

## 8. Formularios para la UI de Flowable

Mientras la web no esté hecha, las tareas que cargan datos se completan desde la Task app de Flowable. Para eso
necesitan campos de formulario (*form properties*) con el mismo nombre que la variable:

| Tarea | Campos |
|-------|--------|
| Registrar y elaborar propuesta / Revisar propuesta | `programa` (texto), `presupuesto` (número), `fechasPosibles` (texto) |
| Confeccionar lista definitiva | `listaDefinitiva` (texto) |
| Evaluar recursos | `requiereAula`, `requiereAulaVirtual`, `requiereEquipamiento` (booleanos) |
| Definir docente | `docenteAsignado` (lista: `jefe_docente`, `docente1`, `docente2`) |
| Elaborar informe de resultados | `informeResultados` (texto) |

Los mensajes de la empresa no se pueden enviar desde la Task app: hasta tener la app de comunicaciones, se envían
por REST con `curl` (ver [03-guia-flowable.md](03-guia-flowable.md), §3).

## 9. Pendiente

- **Falta de aulas (escenario 7):** cuando el profesor defina qué pasa, *Reservar aula* lanzará un error de negocio
  (`BpmnError`) y se agregará su manejo en el modelo.

## 10. Lo que necesita el análisis del historial (§4.3, §4.4)

Las consultas del §4.3 y la minería del §4.4 leen el historial de Flowable, así que dependen de cómo se implementa el
modelo:

| Requisito | Qué tiene que garantizar el modelo |
|-----------|------------------------------------|
| Resultados de las solicitudes (§4.3.1) | Toda instancia terminada tiene `resultado` con uno de los cinco valores finales. `FINALIZADO`, `CANCELADO`, `RECHAZADO`, `NO_VIABLE` y `SIN_RESPUESTA` se corresponden uno a uno con las categorías del enunciado |
| Duración de las ramas (§4.3.2) | Cada rama se identifica por los IDs de su primera y su última actividad: **inscripciones** = inicio de `nominaRecibida` → fin de `confeccionarListaDefinitiva`; **materiales** = inicio → fin de `prepararMateriales`; **recursos** = inicio de `evaluarRecursos` → fin de `recursosListos` (el inclusivo de cierre, que se completa cuando terminaron todas las acciones elegidas). Solo cuentan las instancias que pasaron por `preparacionCompleta`, lo que excluye las canceladas |
| Tareas por persona (§4.3.3) | Las tareas quedan asignadas a un usuario al completarse: las de grupo se reclaman antes, como pide el §2 |
| Minería de la negociación (§4.4) | La etapa de negociación va desde `solicitudRecibida` hasta *Habilitar acceso a la empresa* o hasta un fin anterior (no viable o solicitud archivada). Los nombres de esas actividades tienen que ser claros y únicos, porque son los que aparecen en el gráfico y en los recorridos |
