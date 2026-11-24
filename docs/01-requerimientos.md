# 01 · Requerimientos

> **Fuente de verdad:** [`enunciado/ProyectoCursos_V3.pdf`](enunciado/ProyectoCursos_V3.pdf). Este documento lo
> resume y lo numera para poder referenciarlo; si algo de acá contradice al PDF, manda el PDF, salvo las aclaraciones
> posteriores del profesor, que se anotan en §4 y en [05-decisiones.md](05-decisiones.md). Todo desvío del enunciado se
> documenta y se justifica.

## 1. Qué hay que construir

Una institución recibe pedidos de cursos de capacitación de empresas, evalúa si puede tomarlos, negocia una
propuesta, prepara el curso (inscripciones, materiales y recursos en paralelo), lo dicta y lo cierra. Hay que:

1. **Implementar ese proceso en Flowable** (modelo BPMN ejecutable, regla de negocio, listeners y componentes Java).
2. **Crear la BD propia de la institución** (empresas y cursos), vinculada a las instancias del proceso.
3. **Construir tres aplicaciones externas** que operan sobre las mismas instancias y tareas:
   - un **sitio web** (sección pública, empresas, administración y docentes) que usa la **API REST** de Flowable;
   - una **app de escritorio de materiales** que usa la **API Java** de Flowable;
   - una **app de escritorio de comunicaciones**, con interfaz gráfica, que simula a la empresa usando la **API REST**.
4. **Analizar las ejecuciones del proceso** con el historial de Flowable: tres consultas (resultados, duración de las
   ramas de la preparación, tareas por persona) y un análisis breve de minería de procesos de la etapa de negociación.
5. **Entregar** el código, las instrucciones y un informe breve, y **defender** 11 escenarios de demostración.

El enunciado no fija lenguajes ni frameworks para el sitio y las apps. Exige Flowable, la API REST para el sitio y la
app de comunicaciones, la API Java (con un componente Java) para la app de materiales, y que estas dos últimas sean
de escritorio. Las tecnologías elegidas están en [`02-arquitectura.md`](02-arquitectura.md).

## 2. Catálogo de requerimientos

Cada requisito tiene un ID para referenciarlo desde issues, commits y tests.

### Proceso (§1)

| ID | Requisito | § |
|----|-----------|---|
| P-01 | Al recibir una solicitud se evalúa la viabilidad; con 5 o más cursos en ejecución se comunica la imposibilidad y finaliza. | 1.1 |
| P-02 | El jefe administrativo registra la solicitud y elabora la propuesta (programa, presupuesto, fechas posibles) y la envía. | 1.1 |
| P-03 | La empresa puede aceptar, rechazar o pedir modificaciones; ante modificaciones el jefe revisa y envía una nueva versión; puede repetirse. | 1.1 |
| P-04 | A los 5 días sin respuesta se envía recordatorio; máximo 2; 5 días después del segundo sin respuesta se archiva y finaliza. | 1.1 |
| P-05 | El rechazo lleva al archivo y finalización. | 1.1 |
| P-06 | El contador de recordatorios se reinicia con cada nueva versión enviada. | 1.1 |
| P-07 | Aceptada la propuesta, en paralelo y sin dependencias: Inscripciones (recibir nómina → lista definitiva, sin verificar cada participante), Materiales, Recursos. | 1.2 |
| P-08 | Recursos: evaluar y realizar una o varias de: reservar aula física, solicitar aula virtual, contratar equipamiento; al menos una; deben completarse todas las elegidas. | 1.2 |
| P-09 | La preparación concluye cuando terminan las tres ramas. | 1.2 |
| P-10 | Durante *Preparar curso* la empresa puede cancelar: se interrumpe la preparación y se compensan **solo** los recursos efectivamente gestionados (liberar aula física, aula virtual, equipamiento); luego, concluidas las compensaciones (incluida *Liberar equipamiento*, tarea de usuario, y la tarea de confirmación que agrega el modelo), se notifica el cierre y finaliza. | 1.3 |
| P-11 | Concluida la preparación se envía la confirmación; el jefe docente define el docente (uno de los tres, puede ser él). | 1.4 |
| P-12 | El docente elegido se guarda en una variable y queda asignado a *Desarrollar curso*; alcanzada la fecha programada registra la finalización del dictado. | 1.4 |
| P-13 | El jefe docente elabora el informe de resultados; el jefe administrativo lo envía a la empresa y archiva el expediente. | 1.4 |
| P-14 | Al finalizar normalmente se lanza la compensación para liberar los recursos; el proceso concluye una vez completada la liberación. | 1.4 |
| P-15 | El diagrama de la Figura 1 es una guía: se puede extender o modificar en forma menor, respetando los requisitos y justificando los cambios. | 1.5 |

### Personal y asignación (§2, §3.3)

| ID | Requisito | § |
|----|-----------|---|
| A-01 | Administración: 2 personas (una es el jefe administrativo). Docentes: 3 personas (una es el jefe). | 2 |
| A-02 | Jefe administrativo: registrar y elaborar la propuesta, enviarla, revisarla, archivar una solicitud rechazada o sin respuesta; enviar el informe final y archivar el expediente. | 2 |
| A-03 | Cualquiera de los dos administrativos: lista definitiva, preparar materiales, evaluar recursos, solicitar aula virtual, contratar equipamiento, liberar equipamiento (compensación). | 2 |
| A-04 | Docente seleccionado por el jefe del equipo: desarrollar el curso y registrar la finalización del dictado. | 2 |
| A-05 | Jefe docente: definir el docente y elaborar el informe de resultados. | 2 |
| A-06 | Tareas abiertas a varios: grupo candidato; un miembro la reclama antes de completarla. Tareas asignadas a un usuario (por rol, listener o selección del jefe docente): sin reclamo y solo ese usuario las completa. Las automáticas no requieren intervención humana. | 2 |
| A-07 | Crear en Flowable los 5 usuarios y los grupos Administración y Docentes; identificar a los jefes; configurar asignaciones y grupos candidatos. | 3.3 |
| A-08 | El personal ingresa a las apps externas con su usuario y contraseña de Flowable; no se mantienen cuentas del personal en la BD de la institución. | 3.3 |

### Implementación en el motor (§3)

| ID | Requisito | § |
|----|-----------|---|
| I-01 | La evaluación de viabilidad se implementa con una **decision task** de Flowable. | 3.1 |
| I-02 | Entrada: cantidad de **otras** instancias del proceso que no finalizaron **y ya pasaron por *Habilitar acceso a la empresa*** (aclaración del profesor: el §3.1 habla de todas las que no finalizaron), considerando **todas las versiones** desplegadas, obtenida **del motor** y nunca ingresada a mano. | 3.1 |
| I-03 | Resultado de la regla: continuar si la cantidad es menor que 5; comunicar la imposibilidad y finalizar si es 5 o más. | 3.1 |
| I-04 | Task Listener en la creación de *Confeccionar lista definitiva*: asigna al integrante de Administración con menos tareas de usuario pendientes **asignadas** en el motor (no cuentan las que solo tienen al grupo como candidato); empate con criterio a elección. | 3.4 |
| I-05 | Execution Listener al finalizar la instancia: actualiza el estado del curso en la BD según el resultado: finalizado, cancelado, rechazado, cerrado por falta de respuesta o no viable. | 3.4 |
| I-06 | El resultado del proceso se establece en **cada camino de cierre**. | 3.4 |
| I-07 | Variables para propuestas, respuestas de la empresa, recordatorios, fecha del curso, recursos requeridos, aula asignada, participantes e informe. | 3.5 |
| I-08 | La fecha de inicio se fija al acordar la propuesta y se guarda en una variable; el timer de espera la usa (sin fecha fija en el modelo) y debe estar definida antes de alcanzar el evento. | 3.5 |
| I-09 | Para las pruebas se pueden usar fechas cercanas y reducir los plazos de los recordatorios. | 3.5 |
| I-10 | La reserva y la liberación del aula física son automáticas y simulan una conexión con un web service. Si no hay aulas disponibles se genera la excepción correspondiente (escenario 7). | 4.1.3 |

### Base de datos de la institución (§3.2)

| ID | Requisito | § |
|----|-----------|---|
| BD-01 | BD propia con las empresas solicitantes y la información de los cursos: nombre, descripción, programa, presupuesto, fechas e informe de resultados. | 3.2 |
| BD-02 | Cada curso vinculado con su instancia de Flowable (por ejemplo, guardando el ID de la instancia). La lista de participantes y los recursos asignados pueden quedar como variables del proceso. | 3.2, 4.1.1 |
| BD-03 | Cuentas de acceso de las empresas, creadas o reutilizadas por *Habilitar acceso de la empresa*. | 4.1.2 |

### Sitio web (§4, §4.1)

| ID | Requisito | § |
|----|-----------|---|
| W-01 | Sección pública para solicitar capacitaciones y sección de acceso con credenciales. Interacción con Flowable por **API REST**. | 4.1 |
| W-02 | Formulario de solicitud: datos de la empresa y del contacto, temática y descripción, cantidad estimada de participantes, descripción de fechas preferidas. El envío registra la información e inicia una instancia, vinculada a la BD. | 4.1.1 |
| W-03 | *Habilitar acceso de la empresa* (automática, después de aceptar y antes de preparar): crea la cuenta si no existe; si existe por una contratación anterior, la reutiliza y le vincula el curso. La comunicación de credenciales puede simularse. | 4.1.2 |
| W-04 | La empresa identificada consulta sus cursos y el estado de cada uno: en preparación, confirmado y pendiente de inicio, en desarrollo, en elaboración del informe, finalizado o cancelado. | 4.1.2 |
| W-05 | Durante la preparación, la empresa consulta las actividades pendientes o en ejecución, incluidas las que corren en simultáneo en las ramas paralelas. | 4.1.2 |
| W-06 | Administrativos: consultar las tareas asignadas y las disponibles para su grupo; reclamar y completar las que correspondan (las asignadas no requieren reclamo). | 4.1.3 |
| W-07 | Completar *Registrar y elaborar propuesta* mediante un formulario y generar el PDF; completar *Revisar propuesta* generando una nueva versión del PDF. | 4.1.3 |
| W-08 | Completar *Archivar solicitud* (rechazo o plazos vencidos) y *Confeccionar lista definitiva*. | 4.1.3 |
| W-09 | Completar *Evaluar recursos* en una página para elegir aula física, aula virtual y/o equipamiento especial (al menos una); al completarla, el motor activa las ramas correspondientes. | 4.1.3 |
| W-10 | El jefe administrativo consulta el informe de resultados y completa *Enviar informe a empresa y archivar expediente*. | 4.1.3 |
| W-11 | *Solicitar aula virtual*, *Contratar equipamiento* y *Liberar equipamiento* pueden hacerse desde la UI de Flowable. *Elaborar informe de resultados* la hace el jefe docente desde la UI de Flowable (opcionalmente, desde el sitio). | 4.1.3 |
| W-12 | Jefe docente: consultar sus tareas *Definir docente*, elegir uno de los tres integrantes (incluido él), asignarlo a *Desarrollar curso* y completar la tarea. | 4.1.4 |
| W-13 | Docente: consultar las tareas *Desarrollar curso* que tenga asignadas y completarlas. | 4.1.4 |
| W-14 | Todas las interfaces operan sobre las mismas instancias y tareas; las operaciones dependen del usuario, sus responsabilidades y el estado del proceso. | 4 |

### Aplicaciones auxiliares de escritorio (§4.2)

| ID | Requisito | § |
|----|-----------|---|
| X-01 | App de materiales con interfaz gráfica, local a la intranet y con acceso a la BD del motor, usando la **API Java**: identificarse, consultar las tareas *Preparar materiales* asignadas o reclamables, reclamar una disponible para su grupo y completarla. | 4.2.1 |
| X-02 | La app puede ser completamente Java; si otras funciones usan otro lenguaje, esta interacción con el motor se hace con un componente Java. | 4.2.1 |
| X-03 | App de comunicaciones (con interfaz gráfica: ver §4, punto 9): elegir una solicitud y simular, por **API REST** y sobre la instancia correspondiente, la aceptación (con fecha acordada), el rechazo, la solicitud de modificaciones (con observaciones), el envío de la nómina y la cancelación durante la preparación. | 4.2.2 |
| X-04 | Mostrar solo las comunicaciones habilitadas en el estado actual. La negociación y el envío de la nómina se simulan solo desde esta app (no en el sitio). | 4.2.2 |

### Análisis de las ejecuciones del proceso (§4.3)

Consultas sobre el historial de ejecuciones de Flowable. Pueden incorporarse a la app de materiales o mostrarse por
consola, sin interfaz gráfica.

| ID | Requisito | § |
|----|-----------|---|
| H-01 | Tabla con la cantidad y el porcentaje de solicitudes terminadas con cada resultado: curso finalizado, cancelación, rechazo de la propuesta, solicitud no viable, cierre por falta de respuesta. Porcentajes sobre el total de instancias terminadas. | 4.3.1 |
| H-02 | Para los cursos que completaron normalmente la preparación, duración promedio de cada rama: inscripciones, materiales y recursos. Cada rama se mide desde su activación hasta su finalización; la de recursos termina cuando se completaron todas las acciones seleccionadas. Se excluyen las preparaciones interrumpidas por cancelación. | 4.3.2 |
| H-03 | Tabla con la cantidad de tareas de usuario completadas por cada administrativo y docente. Se excluyen las tareas pendientes y las interrumpidas o canceladas. | 4.3.3 |

### Minería de procesos (§4.4)

Análisis breve, con PM4Py u otra herramienta, de la **etapa de negociación** (la previa a la preparación del curso).

| ID | Requisito | § |
|----|-----------|---|
| M-01 | Generar un CSV a partir de las actividades históricas de Flowable, con el script provisto por la cátedra (`exportar_history.py`) adaptado al proceso. | 4.4 |
| M-02 | Obtener una representación gráfica del comportamiento observado e identificar los tres recorridos más frecuentes (o todos, si hay menos de tres), con la cantidad y el porcentaje de instancias de cada uno. Puede usarse el script provisto (`analizar.py`). | 4.4 |
| M-03 | Comparar los resultados con la etapa de negociación del modelo BPMN y redactar una conclusión breve sobre coincidencias, diferencias y limitaciones del análisis. | 4.4 |

### Entrega y defensa (§5)

| ID | Entregable |
|----|------------|
| E-01 | Modelo BPMN ejecutable. |
| E-02 | Código del servicio REST y de los componentes incorporados al motor. |
| E-03 | Reglas de negocio. |
| E-04 | Instrucciones para configurar usuarios y grupos y ejecutar la solución. |
| E-05 | Informe breve: variables, listeners y decisiones de implementación. |
| E-06 | Código de las tres aplicaciones externas e instrucciones para configurarlas y ejecutarlas. |
| E-07 | Minería de procesos: el CSV utilizado, el código o las instrucciones para reproducir el análisis y una captura de los resultados (§4.4). |

## 3. Escenarios de la defensa

| # | Escenario | Requisitos |
|---|-----------|------------|
| 1 | Aceptación de la propuesta y finalización normal, con liberación de recursos | P-02…P-14 |
| 2 | Solicitud de modificaciones y envío de una nueva propuesta | P-03, P-06, W-07 |
| 3 | Rechazo de la propuesta | P-05, I-05 |
| 4 | Cierre por falta de respuesta, luego de los recordatorios | P-04, I-09 |
| 5 | Imposibilidad de continuar por el límite de cursos en ejecución | P-01, I-01…I-03 |
| 6 | Cancelación durante la preparación, con recursos ya gestionados y sus compensaciones | P-10 |
| 7 | Falta de aulas disponibles y generación de la excepción correspondiente | I-10 |
| 8 | Asignación, reclamo y finalización de tareas según los responsables | A-01…A-06, I-04 |
| 9 | Registro de una solicitud desde el sitio e inicio de su instancia | W-02 |
| 10 | Habilitación del acceso de la empresa y consulta exclusiva de sus cursos | W-03…W-05 |
| 11 | Materiales por API Java y comunicaciones por API REST | X-01…X-04 |

## 4. Puntos del enunciado a definir

1. **"Aplicación móvil" (§1.4)** para *Definir docente*: el §4 enumera un sitio web y dos apps de escritorio, y el
   §4.1.4 pone esa función en el sitio.
2. **Escenario 7 (falta de aulas):** no aparece en el §1 y no se especifica qué ocurre después de la excepción. El modelo la atrapa y espera a que un administrativo confirme que hay un aula (05-decisiones, "Falta de aulas").
3. **"Cinco o más cursos en ejecución" (§1.1, §3.1):** el §3.1 dice que cuentan todas las instancias sin finalizar,
   incluidas las que están en acuerdo, excluyendo la propia. Por aclaración del profesor, cuentan los cursos que ya
   pasaron por *Habilitar acceso a la empresa* y siguen sin finalizar (05-decisiones, "Cursos en ejecución").
4. **Informe de resultados:** el jefe administrativo debe poder consultarlo (§4.1.3), aunque *Elaborar informe* se haga
   desde la UI de Flowable.
5. **Comunicaciones a la empresa** (notificar la decisión, enviar propuesta, recordatorio, confirmación, cierre,
   informe): no se especifica el medio.
6. **Tarea *Confirmar compensaciones* (§1.3):** no está en el diagrama. La agrega el modelo al final de la cancelación,
   como recomienda la cátedra, para que el cierre no se notifique antes de terminar las compensaciones
   (05-decisiones, limitación 2).
7. **Estados visibles para la empresa (§4.1.2):** no incluyen rechazado, no viable ni sin respuesta. Es coherente,
   porque la cuenta se crea recién después de aceptar la propuesta.
8. **Lo tachado en el enunciado V3 no se exige:** la entrega de los scripts para crear la base de datos y cargar datos
   de prueba (§3.2) y que el informe de resultados se genere o adjunte en PDF (§4.1.3). El repo igual trae la creación
   de la base (`db/init` y la migración de Flyway) y los scripts de los escenarios; el informe de resultados es un texto
   (`informeResultados`). Los PDF que sí se piden son los de la propuesta y sus revisiones (W-07).
9. **Interfaz de la app de comunicaciones (§4.2.2):** los grupos de hasta 3 personas pueden resolverla por línea de
   comando, sin interfaz gráfica. Este grupo tiene 5 integrantes, así que la app tiene interfaz gráfica.
