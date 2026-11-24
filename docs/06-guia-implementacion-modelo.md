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
| IDs de flechas | `flujo_<origen>_<destino>` con los IDs de los elementos (`flujo_evaluarViabilidad_gatewayEsViable`). Asociaciones de compensación: `asociacion_<evento>_<tarea>` |
| Nombres de mensajes | camelCase, declarados como `<message>` y referenciados con `messageRef` |
| Variables | camelCase y tipos simples: texto, número, booleano, fecha ISO-8601 o JSON. Nunca objetos Java: la web y las apps no podrían leerlos. |
| Tareas automáticas | Service tasks con clase Java (`flowable:class`). El modelo no tiene script tasks: todo el código queda en Java, testeable y versionado. |
| Clases Java | Paquete `ar.edu.unrc.gtpn.proceso.<funcionalidad>`, en el módulo `proceso` |
| Usuarios y grupos | `jefe_admin`, `administrativo` (grupo `administracion`); `jefe_docente`, `docente1`, `docente2` (grupo `docentes`) |

## 2. Variables del proceso

| Variable | Tipo | La escribe | La usa |
|----------|------|------------|--------|
| `resultado` | texto: `EN_CURSO`, `NO_VIABLE`, `RECHAZADO`, `SIN_RESPUESTA`, `CANCELADO`, `FINALIZADO` | Inicio (`EN_CURSO`) y cada camino de cierre | Listener de fin del proceso (§3.4); XOR de cancelación |
| `plazoRespuesta` | duración ISO-8601: `P5D` (demo: `PT2M`) | Web, al iniciar la instancia; si no viene, Inicio (`P5D`) | Timer "5 días" |
| `nombreCurso` | texto | Web, al iniciar la instancia | App de comunicaciones (lista de solicitudes) |
| `cursosEnEjecucion` | número | Listener de *Evaluar viabilidad* | Regla DMN |
| `esViable` | booleano | Regla DMN | XOR "¿es viable?" |
| `accesoHabilitado` | booleano | *Habilitar acceso a la empresa* | Conteo de cursos en ejecución |
| `programa`, `presupuesto`, `fechasPosibles` | texto, número, texto | *Registrar* y *Revisar propuesta* | PDF de la propuesta (web) |
| `versionPropuesta` | número | Inicio (0) y *Enviar propuesta* (+1) | PDF de cada versión (web) |
| `recordatoriosEnviados` | número | *Enviar propuesta* (0) y *Enviar recordatorio* (+1) | XOR "¿se enviaron 2 recordatorios?" |
| `observacionesEmpresa` | texto | Mensaje de modificaciones | *Revisar propuesta* |
| `fechaInicioCurso` | fecha ISO-8601 con desfase (`2026-12-01T09:00:00-03:00`) | Mensaje de aceptación | Timer "fecha programada" |
| `participantes` | JSON (lista) | Mensaje de nómina | *Confeccionar lista definitiva* |
| `listaDefinitiva` | texto (un participante por línea) | *Confeccionar lista definitiva* | *Desarrollar curso* |
| `requiereAula`, `requiereAulaVirtual`, `requiereEquipamiento` | booleano | *Evaluar recursos* | Ramas del gateway inclusivo |
| `aulaAsignada` | texto | *Reservar aula* | *Liberar aula* y el portal de la empresa (docs/08) |
| `docenteAsignado` | id de usuario | *Definir docente* | Asignación de *Desarrollar curso* |
| `informeResultados` | texto | *Elaborar informe de resultados* | *Enviar informe y archivar expediente*; BD |

## 3. Mensajes de la empresa

Los envía la app de comunicaciones por REST (`messageEventReceived`), excepto el de inicio, que lo envía la web.

| Evento del diagrama | ID | `messageName` | Variables que trae |
|---------------------|----|---------------|--------------------|
| recibir solicitud de curso | `solicitudRecibida` | `solicitudCapacitacion` | `plazoRespuesta`, `nombreCurso` (+ `businessKey`) |
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
| recibir solicitud de curso | `solicitudRecibida` | `messageRef` → `solicitudCapacitacion`. Execution listener `start` (expresión): `resultado = 'EN_CURSO'`, `versionPropuesta = 0`, y `plazoRespuesta = 'P5D'` si la instancia se inició sin esa variable |
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
| Preparar curso | `prepararCurso` | Subproceso embebido. Inicio `inicioPrepararCurso`, fin `finPrepararCurso` |
| Paralelo de inicio | `iniciarPreparacion` | — |
| recibir nómina de inscripciones | `nominaRecibida` | `messageRef` → `nominaParticipantes` |
| Confeccionar lista definitiva | `confeccionarListaDefinitiva` | `candidateGroups = administracion` + Task listener `create` → `AsignarAdministrativoConMenosTareasListener` (la deja asignada). Formulario: `listaDefinitiva` |
| Preparar materiales | `prepararMateriales` | `candidateGroups = administracion` (la completa la app de materiales) |
| Evaluar recursos | `evaluarRecursos` | `candidateGroups = administracion`. Formulario: `requiereAula`, `requiereAulaVirtual`, `requiereEquipamiento` |
| Inclusivo de recursos | `seleccionarRecursos` | **Aula física** → `${requiereAula}`, **Aula virtual** → `${requiereAulaVirtual}`, **Equipamiento** → `${requiereEquipamiento}`. Sin flujo por defecto: la web exige al menos una opción y, si no hubiera ninguna, Flowable corta con error |
| XOR de unión | `unirReservaAula` | Llegan *Aula física* y el reintento de *Confirmar aula disponible* |
| Reservar aula | `reservarAula` | Service task → `ReservarAulaDelegate` |
| Borde de error de Reservar aula | `aulaNoDisponible` | Evento de error de borde (interrumpe), `errorRef` → `errorAulaNoDisponible` (código `AULA_NO_DISPONIBLE`) |
| Confirmar aula disponible | `confirmarAulaDisponible` | User task, `candidateGroups = administracion`. Su flecha vuelve a `unirReservaAula` |
| Borde de compensación de Reservar aula | `compensarReservaAula` | — |
| Liberar aula (compensación) | `liberarAula` | Service task → `LiberarAulaDelegate` |
| Solicitar aula virtual | `solicitarAulaVirtual` | `candidateGroups = administracion` |
| Borde de compensación de Solicitar aula virtual | `compensarAulaVirtual` | — |
| Liberar aula virtual (compensación) | `liberarAulaVirtual` | Service task → `LiberarAulaVirtualDelegate` |
| Contratar equipamiento | `contratarEquipamiento` | `candidateGroups = administracion` |
| Borde de compensación de Contratar equipamiento | `compensarEquipamiento` | — |
| Liberar equipamiento (compensación) | `liberarEquipamiento` | User task, `candidateGroups = administracion`. Si se contrató, la cancelación y el cierre normal esperan a que se complete (ver 05-decisiones, limitación 2) |
| Inclusivo de cierre | `recursosListos` | — |
| Preparación de curso completa | `preparacionCompleta` | — |
| Cancelación (subproceso de evento) | `cancelacion` | Interruptor. Cadena: `cancelacionRecibida` → `lanzarCompensacionAula` → `lanzarCompensacionAulaVirtual` → `lanzarCompensacionEquipamiento` → `confirmarCompensaciones` → `preparacionCancelada` |
| recibir cancelación | `cancelacionRecibida` | `messageRef` → `cancelacionCurso` |
| compensar reserva de aula | `lanzarCompensacionAula` | Evento intermedio de compensación con `activityRef = reservarAula` |
| compensar aula virtual | `lanzarCompensacionAulaVirtual` | Evento intermedio de compensación con `activityRef = solicitarAulaVirtual` |
| compensar equipamiento | `lanzarCompensacionEquipamiento` | Evento intermedio de compensación con `activityRef = contratarEquipamiento` |
| Confirmar compensaciones | `confirmarCompensaciones` | User task, `candidateGroups = administracion`. Pausa: Flowable no deja completarla mientras haya liberaciones pendientes (05-decisiones, limitación 2). La web no ofrece *Completar* hasta entonces |
| Preparación cancelada (fin del subproceso de evento) | `preparacionCancelada` | Execution listener `start` (expresión): `resultado = 'CANCELADO'` |
| ¿Preparación cancelada? | `gatewayPreparacionCancelada` | **SI** → `${resultado == 'CANCELADO'}` → *Notificar cierre* → fin. **NO** → flujo por defecto (ya marcado en el modelo) → *Enviar confirmación* |
| Notificar cierre | `notificarCierre` | Service task → `NotificarEmpresaDelegate` |
| Fin por cancelación | `finCancelacion` | El resultado ya es `CANCELADO` |

Las tres asociaciones de compensación no se tocan en el editor de Flowable (05-decisiones, limitación 5). Conviene
editar este modelo directamente en el XML y no guardarlo desde el editor (limitaciones 5 y 6).

Si se agrega un recurso con compensación, además de su borde y su tarea *Liberar…* se agrega un evento de compensación
con `activityRef` a la cadena de la cancelación.

### Desarrollo de curso (§1.4)

| Elemento | ID | Configuración |
|----------|----|---------------|
| Enviar confirmación | `enviarConfirmacion` | Service task → `NotificarEmpresaDelegate`, **asíncrona** (`flowable:async="true"`) |
| Definir docente que realizará el curso | `definirDocente` | `assignee = jefe_docente`. Formulario: `docenteAsignado` (uno de `jefe_docente`, `docente1`, `docente2`) |
| fecha programada | `fechaProgramada` | `timeDate = ${fechaInicioCurso}` |
| Desarrollar curso | `desarrollarCurso` | `assignee = ${docenteAsignado}` |
| Elaborar informe de resultados | `elaborarInformeResultados` | `assignee = jefe_docente`. Formulario: `informeResultados` |
| Enviar informe y archivar expediente | `enviarInformeYArchivarExpediente` | `assignee = jefe_admin` |
| Liberar recursos (compensación) | `liberarRecursos` | Evento intermedio de compensación, sin configuración extra. Después va directo el fin: la instancia concluye al completarse *Liberar equipamiento*, si se contrató (no hace falta una espera explícita; ver 05-decisiones, limitación 2) |
| Curso finalizado | `finCursoFinalizado` | Execution listener `start`: `resultado = 'FINALIZADO'` |

## 5. Timers

| Timer | Configuración | Nota |
|-------|---------------|------|
| 5 días | `timeDuration = ${plazoRespuesta}` | Lo envía la web al iniciar la instancia, tomado de su propiedad `solicitudes.plazo-respuesta`: `P5D` en serio; en la demo, `PLAZO_RESPUESTA=PT2M` (§3.5 permite reducir plazos). Si la instancia se inicia sin esa variable (por ejemplo, desde la Task app), el evento de inicio le pone `P5D`; si viene, se respeta |
| fecha programada | `timeDate = ${fechaInicioCurso}` | Fecha ISO-8601 fijada al aceptar. Con desfase (`-03:00`) el timer vence en el instante correcto; sin desfase, Flowable la lee en la zona de su servidor (UTC) y vencería horas antes. Si ya pasó, se dispara enseguida (útil para la demo) |

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
| `acceso.HabilitarAccesoEmpresaDelegate` | Delegate | *Habilitar acceso a la empresa* | Crea la cuenta de la empresa (usuario = razón social, clave `clave123`) o reutiliza la existente, vincula el curso (§4.1.2) y fija `accesoHabilitado = true` |
| `recursos.ReservarAulaDelegate` | Delegate | *Reservar aula* | Simula el web service de aulas (§4.1.3) y guarda `aulaAsignada`; sin aulas libres lanza `BpmnError("AULA_NO_DISPONIBLE")` |
| `recursos.LiberarAulaDelegate` | Delegate | *Liberar aula* | Simula la liberación del aula física |
| `recursos.LiberarAulaVirtualDelegate` | Delegate | *Liberar aula virtual* | Simula la liberación del aula virtual |
| `asignacion.AsignarAdministrativoConMenosTareasListener` | Task listener `create` | *Confeccionar lista definitiva* | Asigna la tarea al integrante de `administracion` con menos tareas **asignadas** pendientes (§3.4); cuentan las de todos los cursos, y las que solo tienen al grupo como candidato, no. Ante un empate, el criterio es el orden alfabético del ID (con `administrativo` y `jefe_admin` sin tareas, gana `administrativo`). Asigna por el motor (`TaskHelper.changeTaskAssignee`) y no con `tarea.setAssignee`, para que la persona también quede en el historial y la consulta de tareas por persona (§4.3.3) la cuente |
| `estado.RegistrarResultadoDelCursoListener` | Execution listener `end` | Proceso | Guarda en el curso de la BD de la institución el `resultado` como estado (§3.4), y lo acordado: programa, presupuesto, fecha de inicio e informe (ver [07-base-de-datos.md](07-base-de-datos.md)) |

Reglas para estas clases:
- Flowable crea **una instancia por elemento** y la reutiliza: sin estado mutable en campos.
- Las dependencias (por ejemplo, el acceso a la BD) entran por constructor, con un constructor sin argumentos que arma
  las reales. Así se pueden testear con dobles.
- El módulo compila para Java 11, que es lo que corre la imagen del servidor.

Las asignaciones de resultado, contadores y versiones son expresiones de una línea en el propio XML
(`${execution.setVariable('resultado', 'RECHAZADO')}`): no necesitan clase.

## 8. Campos de formulario

Las tareas que cargan datos declaran campos de formulario (*form properties*) con el mismo nombre que la variable.
Documentan qué datos completa cada tarea, pero la Task app de Flowable 6.8 **no los muestra**: solo muestra los
formularios hechos con el editor de formularios de Flowable. Por eso la web tiene una pantalla propia para cada una
(docs/08 §3). Desde la Task app se pueden completar con *Complete*, agregando antes los datos como variables de la
instancia desde la app Admin (ver [03-guia-flowable.md](03-guia-flowable.md), §2, "Probar desde la UI de Flowable").

| Tarea | Campos |
|-------|--------|
| Registrar y elaborar propuesta / Revisar propuesta | `programa` (texto), `presupuesto` (número), `fechasPosibles` (texto) |
| Confeccionar lista definitiva | `listaDefinitiva` (texto) |
| Evaluar recursos | `requiereAula`, `requiereAulaVirtual`, `requiereEquipamiento` (booleanos) |
| Definir docente | `docenteAsignado` (lista: `jefe_docente`, `docente1`, `docente2`) |
| Elaborar informe de resultados | `informeResultados` (texto) |

Los mensajes de la empresa no se pueden enviar desde la Task app: los envía la app de comunicaciones y también se
pueden disparar desde la app Admin (ver [03-guia-flowable.md](03-guia-flowable.md), §2, "Probar desde la UI de
Flowable").

## 9. Falta de aulas (escenario 7)

`ServicioAulasSimulado` maneja un conjunto fijo de aulas (10 por defecto; `AULAS_DISPONIBLES` en el servidor). Si
*Reservar aula* no encuentra ninguna libre, `ReservarAulaDelegate` lanza `BpmnError("AULA_NO_DISPONIBLE")` y el evento
de error de borde `aulaNoDisponible` pasa el token a la tarea de usuario *Confirmar aula disponible*, del grupo
`administracion`. Al completarla, el XOR `unirReservaAula` devuelve el token a *Reservar aula*, que reintenta. Las demás
ramas siguen mientras tanto y la cancelación interrumpe la espera sin nada que liberar. La decisión y sus razones están
en [05-decisiones.md](05-decisiones.md), "Falta de aulas".

## 10. Lo que necesita el análisis del historial (§4.3, §4.4)

Las consultas del §4.3 y la minería del §4.4 leen el historial de Flowable, así que dependen de cómo se implementa el
modelo:

| Requisito | Qué tiene que garantizar el modelo |
|-----------|------------------------------------|
| Resultados de las solicitudes (§4.3.1) | Toda instancia terminada tiene `resultado` con uno de los cinco valores finales. `FINALIZADO`, `CANCELADO`, `RECHAZADO`, `NO_VIABLE` y `SIN_RESPUESTA` se corresponden uno a uno con las categorías del enunciado |
| Duración de las ramas (§4.3.2) | Cada rama se identifica por los IDs de su primera y su última actividad: **inscripciones** = inicio de `nominaRecibida` → fin de `confeccionarListaDefinitiva`; **materiales** = inicio → fin de `prepararMateriales`; **recursos** = inicio de `evaluarRecursos` → fin de `recursosListos` (el inclusivo de cierre, que se completa cuando terminaron todas las acciones elegidas). Solo cuentan las instancias que pasaron por `preparacionCompleta`, lo que excluye las canceladas |
| Tareas por persona (§4.3.3) | Las tareas quedan asignadas a un usuario al completarse: las de grupo se reclaman antes, como pide el §2. **Limitación:** *Confirmar compensaciones* (la pausa de la cancelación, 05-decisiones limitación 2) es una tarea más: la persona que la completa suma una tarea que no es de negocio. La consulta no la distingue |
| Minería de la negociación (§4.4) | La etapa de negociación va desde `solicitudRecibida` hasta *Habilitar acceso a la empresa* o hasta un fin anterior (no viable o solicitud archivada). Los nombres de esas actividades tienen que ser claros y únicos, porque son los que aparecen en el gráfico y en los recorridos |
