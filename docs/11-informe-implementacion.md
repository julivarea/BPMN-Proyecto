# 11 · Informe breve de implementación

Explica las variables del proceso, los listeners y las decisiones de implementación (enunciado §5). Es un resumen: el
detalle de cada punto está en los documentos que se citan.

**Estado.** Está implementado y probado todo lo que pide el enunciado, salvo la **falta de aulas (escenario 7), que
está PENDIENTE** (§5). Se probó con los tests automáticos (`./mvnw verify`) y con un ensayo de los escenarios 1 a 6 y 8 a
11 sobre una instalación limpia.

## 1. Qué se hizo

| Pieza | Dónde | Resumen |
|-------|-------|---------|
| Proceso | `proceso/` | Modelo BPMN `organizarCurso` ejecutable en Flowable 6.8, regla DMN de viabilidad y las clases Java que corren dentro del motor |
| Sitio web | `web/` | Spring Boot + Thymeleaf; usa la API REST de Flowable. Solicitud pública, portal de empresas y tareas del personal |
| App de materiales | `app-materiales/` | Swing con motor Flowable embebido (API Java): tareas *Preparar materiales* y consultas del §4.3 |
| App de comunicaciones | `app-comunicaciones/` | Swing; simula a la empresa por la API REST |
| Base de la institución | `db/`, `web/.../db/migration` | PostgreSQL: empresas, cursos, versiones de la propuesta y cuentas de acceso |
| Minería | `mineria/` | Scripts de la cátedra adaptados (PM4Py) sobre la etapa de negociación |

Dónde está cada entregable del §5: README, "Dónde está cada entregable".

## 2. Variables del proceso

Son de tipo simple (texto, número, booleano, fecha ISO o JSON) para que la web y las apps puedan leerlas.

| Variable | Qué guarda | La escribe |
|----------|------------|------------|
| `resultado` | `EN_CURSO` y, al cerrar, uno de los cinco finales: `FINALIZADO`, `CANCELADO`, `RECHAZADO`, `NO_VIABLE`, `SIN_RESPUESTA` | El inicio y cada camino de cierre |
| `plazoRespuesta`, `nombreCurso` | Plazo de espera de la respuesta (ISO 8601, `P5D` por defecto) y nombre del curso | La web al iniciar la instancia |
| `cursosEnEjecucion`, `esViable` | Cursos en ejecución (contados por el motor) y salida de la regla | El listener de viabilidad y la regla DMN |
| `accesoHabilitado` | El curso ya pasó por *Habilitar acceso a la empresa* | Ese delegate |
| `programa`, `presupuesto`, `fechasPosibles` | Datos de la propuesta | *Registrar* y *Revisar propuesta* |
| `versionPropuesta`, `recordatoriosEnviados` | Versión enviada y recordatorios de esa versión (se reinicia con cada una) | *Enviar propuesta* y *Enviar recordatorio* |
| `observacionesEmpresa` | Lo que pidió la empresa al solicitar modificaciones | El mensaje de modificaciones |
| `fechaInicioCurso` | Fecha acordada, con desfase horario | El mensaje de aceptación; la usa el timer |
| `participantes`, `listaDefinitiva` | Nómina de la empresa (lista JSON) y lista definitiva (un participante por línea) | El mensaje de nómina y *Confeccionar lista definitiva* |
| `requiereAula`, `requiereAulaVirtual`, `requiereEquipamiento` | Recursos elegidos | *Evaluar recursos*; deciden qué ramas siguen |
| `aulaAsignada` | Aula física reservada | *Reservar aula*; la lee la compensación |
| `docenteAsignado` | Usuario elegido por el jefe docente | *Definir docente*; asigna *Desarrollar curso* |
| `informeResultados` | Texto del informe | *Elaborar informe de resultados* |

No son variables: las empresas, los cursos, las versiones de la propuesta y las cuentas de empresa, que viven en la base de
la institución. El estado "en curso" de un curso tampoco se guarda: se deriva de las actividades activas de la instancia.
Detalle: [06-guia-implementacion-modelo.md](06-guia-implementacion-modelo.md) §2.

## 3. Listeners, delegates y regla de negocio

| Componente | Dónde actúa | Qué hace |
|------------|-------------|----------|
| `ContarCursosEnEjecucionListener` (execution listener `start`) | *Evaluar viabilidad* | Pregunta al motor cuántos otros cursos están en ejecución y guarda `cursosEnEjecucion`; nadie lo ingresa a mano |
| `viabilidad.dmn` (*decision task*) | *Evaluar viabilidad* | Con menos de 5 cursos continúa; con 5 o más comunica la imposibilidad y termina |
| `AsignarAdministrativoConMenosTareasListener` (task listener `create`) | *Confeccionar lista definitiva* | Asigna la tarea al administrativo con menos tareas asignadas pendientes; ante un empate, por orden alfabético del ID |
| `RegistrarResultadoDelCursoListener` (execution listener `end`) | El proceso | Guarda en el curso el estado según `resultado` y, si los tiene, programa, presupuesto, fecha e informe |
| Expresiones de una línea en el XML | Inicio y caminos de cierre | Fijan `resultado`, versión, recordatorios y plazo por defecto |
| `NotificarEmpresaDelegate` | Cinco *service tasks* de envío | Simula la comunicación a la empresa (log); una sola clase, con el asunto como campo |
| `HabilitarAccesoEmpresaDelegate` | *Habilitar acceso a la empresa* | Crea la cuenta de la empresa o reutiliza la que tiene, y fija `accesoHabilitado` |
| `ReservarAulaDelegate`, `LiberarAulaDelegate`, `LiberarAulaVirtualDelegate` | *Reservar aula* y las compensaciones | Simulan el web service de aulas; el aula queda en `aulaAsignada` |

Los listeners no guardan estado y reciben sus dependencias por constructor, para probarlos con dobles. Detalle:
[06-guia-implementacion-modelo.md](06-guia-implementacion-modelo.md) §7.

## 4. Decisiones de implementación

Todas están justificadas en [05-decisiones.md](05-decisiones.md); estas son las principales.

1. **Cambios menores al diagrama.** Gateways de unión explícitos, la nómina como evento de mensaje (como las demás
   comunicaciones), todas las tareas automáticas como *service tasks* Java, sin *data objects* y con nombres normalizados.
2. **Cancelación con compensaciones.** Dentro del subproceso de evento hay una compensación por recurso (solo se
   compensa lo ya gestionado) y una tarea final, *Confirmar compensaciones*. Flowable 6.8 no espera a las tareas de usuario
   de una compensación; esa tarea retiene el cierre hasta que se libere el equipamiento, como recomienda la cátedra.
   Aparece en toda cancelación y suma una tarea más a la consulta de tareas por persona.
3. **Cierre normal.** *Liberar recursos* es un evento intermedio de compensación seguido del fin, porque en Flowable el
   evento de *fin* de compensación no compensa nada. La instancia termina al completarse *Liberar equipamiento*.
4. **Viabilidad.** Por aclaración del profesor, cuentan los cursos que ya pasaron por *Habilitar acceso a la empresa* y
   siguen abiertos, de todas las versiones del proceso y sin contar la propia instancia.
5. **Plazos y fechas.** El plazo de respuesta se configura sin tocar el modelo (`PLAZO_RESPUESTA`). La fecha de inicio
   viaja con desfase horario, porque el servidor corre en UTC; sin hora, un día futuro empieza a las 09:00 y hoy, dentro de
   1 minuto (para pruebas y demos).
6. **Resultado en cada cierre.** Cada camino fija `resultado` y el listener de fin lo guarda en la base de la institución.
7. **Base de la institución.** Cada curso se vincula con su instancia (`process_instance_id`, y `businessKey` = id del
   curso). Las cuentas de empresa se guardan con BCrypt; la clave es `clave123` para todas y el envío de credenciales se
   simula en el log (el enunciado lo permite).
8. **Autorización.** Flowable no impide completar la tarea de otro, así que la web y la app de materiales verifican, antes
   de llamar al motor, que la tarea sea del usuario o la haya reclamado (A-06).
9. **Tareas también en la web.** Las que el enunciado deja para la UI de Flowable (*Solicitar aula virtual*, *Contratar
   equipamiento*, *Liberar equipamiento*, *Elaborar informe*) también se pueden completar en la web, para reutilizar la
   bandeja y las pantallas de tarea; siguen disponibles en Flowable.
10. **Propuesta y tarea en una transacción.** Se guarda la versión y se completa la tarea juntas: si Flowable no la
    completa, la versión se descarta.
11. **App de materiales con motor embebido.** Trabaja sobre la base del servidor sin ejecutar timers ni tareas
    asíncronas, en UTC, y *Enviar confirmación* es asíncrona para que la ejecute el servidor (la app no tiene nuestras
    clases).
12. **Interfaz de la app de comunicaciones.** Tiene ventana: la línea de comando es solo para grupos de hasta 3 personas y
    este grupo tiene 5.
13. **Errores completos en las pantallas de tarea.** Se muestra el mensaje de la excepción para facilitar las pruebas y
    la demo; en producción correspondería un mensaje genérico y el detalle en el log.

## 5. Pendiente: falta de aulas (escenario 7)

**PENDIENTE.** El enunciado pide mostrar la falta de aulas y generar la excepción correspondiente, pero no dice qué hace
el proceso después. Hoy el web service de aulas simulado siempre reserva y numera las aulas sin límite, así que el
escenario no se puede mostrar. Está en definición con el profesor (issue #36). Cuando se resuelva, se completa acá: cómo
se determina que no hay aulas, el error de negocio que se lanza, cómo lo maneja el modelo y cómo se recorre el escenario
en la defensa.

## 6. Dónde ampliar

| Tema | Documento |
|------|-----------|
| Qué pide el enunciado, numerado | [01-requerimientos.md](01-requerimientos.md) |
| Tecnologías y arquitectura | [02-arquitectura.md](02-arquitectura.md) |
| Todas las decisiones y las limitaciones de Flowable | [05-decisiones.md](05-decisiones.md) |
| Modelo elemento por elemento, variables, clases | [06-guia-implementacion-modelo.md](06-guia-implementacion-modelo.md) |
| Base de datos | [07-base-de-datos.md](07-base-de-datos.md) |
| Web y aplicaciones | [08](08-web.md), [09](09-app-materiales.md) y [10](10-app-comunicaciones.md) |
| Escenarios de la defensa | [12-escenarios-defensa.md](12-escenarios-defensa.md) |
| Minería de procesos y su conclusión | [mineria/README.md](../mineria/README.md) y [mineria/conclusion.md](../mineria/conclusion.md) |
