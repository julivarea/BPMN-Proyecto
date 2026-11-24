# 05 · Registro de decisiones

Interpretaciones de lo ambiguo del enunciado y cambios respecto del diagrama propuesto (`docs/referencia/diagrama-profesor.bpmn`).

## Cambios respecto del diagrama del enunciado

1. **Gateways XOR de unión explícitos.** Se agregaron cuatro XOR para juntar caminos antes de seguir: antes de
   *Enviar propuesta* (llegan *Registrar* y *Revisar propuesta*), antes del gateway de eventos (llegan *Enviar
   propuesta* y *Enviar recordatorio*), antes de *Archivar solicitud* (llegan el rechazo y la falta de respuesta) y
   antes de *Reservar aula* (llegan la rama de recursos y el reintento de *Confirmar aula disponible*).
   En el diagrama original esos caminos entraban directo a la tarea o al gateway (uniones implícitas). Así el modelo
   queda más estructurado y legible (7PMG, guía G4).

2. **"Recibir nómina" pasa de tarea de recepción a evento intermedio de mensaje.** Todas las demás comunicaciones
   de la empresa (solicitud, aceptación, rechazo, modificaciones, cancelación) son eventos de mensaje. Mezclar
   tareas y eventos de mensaje en un mismo modelo es una mala práctica vista en la materia. Además, la app de
   comunicaciones entrega todos los mensajes con la misma operación REST.

3. **Etiquetas en caminos y eventos de fin.**
   - Ramas del paralelo: *Inscripciones*, *Materiales*, *Recursos*.
   - Ramas del inclusivo: *Aula física*, *Aula virtual*, *Equipamiento*.
   - Fines con nombre según el resultado: no viable, solicitud archivada, cancelación, curso finalizado.

   Mejora la lectura del diagrama. Cada fin con nombre corresponde a un resultado distinto, que el Execution
   Listener de fin registra en la base de la institución.

4. **Nombres de tareas y eventos normalizados.** Los eventos se nombran con verbo + objeto ("recibir aceptación de
   propuesta"), y se corrigieron la ortografía ("Notificar Desición" → "Comunicar imposibilidad de realizar el
   curso") y las mayúsculas. Es la guía G6 de etiquetas verbo-objeto.

5. **Todas las tareas automáticas son service tasks con una clase Java.** Las script tasks del diagrama
   (*Habilitar acceso de la empresa*, *Comunicar imposibilidad de realizar el curso*, *Enviar propuesta*, *Enviar
   recordatorio*, *Enviar confirmación*, *Notificar cierre* y *Liberar aula virtual*) pasan a service tasks, igual que
   *Reservar aula* y *Liberar aula*. Así todo el código del proceso queda en un solo lugar y en un solo lenguaje
   (el módulo `proceso`), con tests, y el modelo no depende de scripts escritos dentro del XML. Las notificaciones a
   la empresa comparten una misma clase, configurada en cada tarea con su asunto.

6. **La compensación final pasa de evento de fin a evento intermedio seguido de un fin.** "Liberar recursos" es
   ahora un evento intermedio de compensación, seguido del fin "Curso finalizado". En Flowable 6.8 el evento de
   *fin* de compensación no compensa nada (verificado); el intermedio sí, y el proceso concluye recién después de
   la liberación, como pide el §1.4.

7. **Sin data objects.** *Propuesta y presupuesto*, *Lista definitiva* e *Informe de resultados* eran solo
   documentales. Los datos reales viven en variables del proceso y en la base de la institución. El editor de
   Flowable, además, no los conserva.

8. **La cancelación compensa dentro del subproceso de evento, con una compensación por recurso y una tarea final.**
   El diagrama lanza una compensación general dentro del subproceso de evento *Cancelación* y lo termina con un fin de
   error. En el modelo, el subproceso de evento tiene esta cadena:

   1. *recibir cancelación* (mensaje `cancelacionCurso`, interruptor), que corta *Preparar curso*;
   2. tres eventos intermedios de compensación en cadena, uno por recurso, cada uno con su `activityRef`: *compensar
      reserva de aula*, *compensar aula virtual* y *compensar equipamiento*. Cada uno compensa solo lo que ya se
      completó: un recurso no gestionado no genera nada;
   3. la tarea de usuario *Confirmar compensaciones* (grupo `administracion`);
   4. el fin *Preparación cancelada*, que fija `resultado = CANCELADO`.

   Al terminar el subproceso, el proceso principal sigue por el XOR "¿Preparación cancelada?": **sí** va a *Notificar
   cierre* y al fin "cancelación"; **no**, a *Enviar confirmación* (camino normal).

   *Confirmar compensaciones* no es una actividad del negocio: es un punto de pausa. Flowable no deja completarla
   mientras haya compensaciones sin terminar, así que retiene el subproceso interruptor hasta que se libere el
   equipamiento y evita que *Notificar cierre* salga antes (§1.3). Es el patrón que recomienda la cátedra
   (`docs/referencia/Seg_ejemplo_Con_Compensacion.bpmn20.xml`). Se mantiene lo que pide el diagrama: se interrumpe la
   preparación, se compensan solo los recursos efectivamente gestionados y el cierre se notifica una vez concluidas
   las compensaciones. Motivos en las limitaciones 1 y 2.

## Aclaraciones e interpretaciones

- **Cursos en ejecución (§1.1, §3.1).** Por aclaración del profesor, para evaluar la viabilidad cuentan los cursos
  que ya pasaron por *Habilitar acceso de la empresa*. No cuentan todas las instancias sin finalizar, como sugiere
  el texto del §3.1.
- **Alcance de la cancelación (§1.3).** Vale solo durante *Preparar curso*, que es donde el enunciado la ubica: el
  subproceso de evento *Cancelación* pertenece a ese subproceso. Cuando la preparación termina, la cancelación deja de
  estar disponible, y tampoco se puede cancelar dos veces (el evento es interruptor).
- **"Aplicación móvil" (§1.4).** *Definir docente* es una tarea de usuario como cualquier otra. El §4 solo pide un
  sitio web y dos apps de escritorio, y el §4.1.4 ubica esta función en el sitio. No cambia el modelo.
- **CUIT registrado con otra razón social (§4.1.1).** Si una solicitud trae un CUIT que ya existe con otra razón
  social, se rechaza y se muestra el error junto a ese campo: puede ser un CUIT mal tipeado que la vincularía a otra
  empresa. La comparación ignora mayúsculas y espacios en los extremos, y el CUIT se guarda solo con dígitos
  (`30-12345678-9` y `30123456789` son la misma empresa).
- **Plazo de respuesta configurable (§3.5).** La web inicia cada instancia con la variable `plazoRespuesta`, tomada
  de `solicitudes.plazo-respuesta` (por defecto `P5D`). Para la demo se reduce sin tocar código:
  `PLAZO_RESPUESTA=PT1M ./mvnw -pl web spring-boot:run`. Si una instancia se inicia sin la variable (por ejemplo, desde
  la Task app de Flowable), el evento de inicio del modelo la completa con `P5D`, así no falla al llegar al timer. El
  valor por defecto queda en dos lugares (la web y el modelo) con el mismo `P5D` del enunciado.
- **Registro de la solicitud y su instancia (§4.1.1).** Guardar el curso, iniciar la instancia y guardar su id en el
  curso van en una sola transacción: si Flowable falla, no queda el curso sin instancia. La transacción no puede
  deshacer lo hecho en Flowable, así que si fallara el último `UPDATE` quedaría una instancia sin curso. Se acepta
  porque es una actualización simple sobre una fila recién insertada.  

- **Propuesta y tarea de Flowable en una sola operación (§4.1.3).** Al completar *Registrar y elaborar propuesta* o
  *Revisar propuesta*, la web inserta la versión en `propuesta` y recién después completa la tarea, dentro de una
  transacción de la base de la institución (`RegistroDePropuestas`): si Flowable no puede completarla, la versión se
  descarta, y si el guardado falla, la tarea no se completa. Flowable no puede participar de esa transacción, así que
  solo queda sin cubrir que falle el commit después de completar la tarea, un caso muy poco probable (el mismo criterio
  que en el registro de la solicitud).

- **Solicitud con sesión de empresa (§4.1, §4.1.2).** Si una empresa inició sesión, `/solicitar` precarga su CUIT y
  razón social y los muestra bloqueados, para que solicite a su nombre. El bloqueo no es solo visual: el servidor
  toma la empresa de la sesión y no valida ni usa el CUIT y la razón social del envío, así que armarlo a mano con los
  de otra empresa registra igual la solicitud a nombre de la empresa con sesión. Sin sesión, el formulario sigue siendo
  público y un CUIT ya registrado con otra razón social se rechaza con el error junto a ese campo.

- **Fecha de inicio: no anterior a hoy, con zona horaria y hora por defecto (§3.5, §4.2.2).** La app de comunicaciones
  valida y completa la fecha acordada al aceptar la propuesta:
  - No admite una fecha anterior al día actual. La de hoy sí, aunque su hora ya haya pasado: el timer "fecha
    programada" dispara enseguida, que es lo que usan las pruebas y la demo ("fechas cercanas", §3.5).
  - Sin hora, un día futuro empieza a las 09:00 y **hoy empieza dentro de 1 minuto**: el curso queda habilitado apenas
    termina la preparación, sin esperar a una hora del día. Es una comodidad para probar y para las demos.
  - La fecha viaja con el desfase de la zona de la app (`2026-12-01T09:00:00-03:00`). El servidor de Flowable corre en
    UTC: sin el desfase leería la hora local como UTC y el timer vencería 3 horas antes. El listener de fin del proceso
    y el portal aceptan la fecha con o sin desfase y guardan o muestran la hora local que escribió la empresa.
  - *Desarrollar curso* queda habilitado cuando se cumplen las tres condiciones: terminó *Preparar curso*, se definió el
    docente y llegó la fecha. No alcanza con que llegue la fecha.

- **Tareas que el enunciado deja para la UI de Flowable, también disponibles en la web (§4.1.3).** El enunciado
  permite hacer *Solicitar aula virtual*, *Contratar equipamiento* y *Liberar equipamiento* desde la UI de Flowable, sin
  exigir pantallas adicionales en el sitio, y *Elaborar informe de resultados* desde Flowable o, opcionalmente, desde el
  sitio. En la web se pueden completar igual: *Elaborar informe de resultados* tiene su pantalla
  (`ElaborarInformeController`) y las otras tres se completan con el *Completar* de la bandeja, que alcanza porque no
  cargan datos. Se hizo así para reutilizar lo que ya estaba construido (la bandeja, con sus reglas de reclamo y
  asignación, y las pantallas de tarea) en lugar de dejar esas tareas fuera del sitio. No reemplaza a la UI de
  Flowable: las mismas tareas se siguen pudiendo completar desde ahí, como permite el enunciado.

- **Falta de aulas: reintento tras una confirmación manual (escenario 7, I-10).** El enunciado pide generar la
  excepción, pero no dice qué hace el proceso después. Se decidió:
  - El web service simulado (`ServicioAulasSimulado`) maneja un conjunto fijo de aulas, "Aula 101" en adelante: 10 por
    defecto (no se agotan en el uso normal; el escenario 7 usa 3), o las de la variable `AULAS_DISPONIBLES` del servidor Flowable. *Reservar aula* toma una libre y *Liberar
    aula* la devuelve, así que un aula liberada puede asignarse a otro curso. Si no queda ninguna, `ReservarAulaDelegate`
    lanza `BpmnError("AULA_NO_DISPONIBLE")`. El estado de las aulas es del servicio y no del proceso, y vive en memoria: reiniciar
    Flowable lo deja todo libre aunque haya cursos con aula asignada (dos cursos podrían compartirla, y el aula que uno
    libere después se suma al cupo aunque ya estuviera libre), y el aula de una instancia borrada sin compensar (por
    ejemplo, con `limpiar.py`) queda ocupada hasta reiniciar. Se aceptó así para no acoplar el servicio simulado al motor;
    por eso el escenario 7 arranca recreando Flowable (docs/12, escenario 7).
  - Un evento de error de borde en *Reservar aula* lleva a la tarea de usuario ***Confirmar aula disponible***
    (grupo `administracion`). Una persona la completa cuando hay un aula (porque terminó o se canceló otro curso) y
    el proceso vuelve a *Reservar aula* por un XOR de unión. Si sigue sin haber, el error se repite y la tarea
    vuelve a quedar pendiente.
  - Mientras tanto las demás ramas de la preparación siguen, y *Preparar curso* no termina hasta que se reserve el
    aula. Si la empresa cancela durante la espera, se interrumpe la tarea y no hay aula que liberar.
  - No se agregó un resultado nuevo: los de §4.3.1 son cinco y ninguno corresponde a "sin aulas", porque el curso
    no termina por esto. Si se prefiriera otro destino (por ejemplo, terminar la solicitud), habría que acordar con
    qué resultado se cuenta.

- **Detalle de errores en las pantallas de tarea (§4.1.3, §4.1.4).** En los controladores de tareas de la web, los
  errores inesperados incluyen `e.getMessage()` en el aviso que ve el usuario (por ejemplo, `"Curso no encontrado: 99"`
  si se abre una instancia iniciada desde la UI de Flowable sin fila en la base de la institución). Es una decisión
  deliberada por tratarse de un sistema didáctico y de demostración: permite diagnosticar fallos durante las pruebas y
  la defensa sin tener que revisar los logs de la consola. En una implementación de producción correspondería registrar
  la excepción únicamente en el log del servidor y mostrar al usuario un mensaje genérico sin detalles técnicos.

## Limitaciones de Flowable 6.8 y soluciones

Comportamientos del motor (y de su editor) que se probaron en el entorno del proyecto y afectan al modelo.

1. **Cancelación durante la preparación (§1.3).** El patrón del diagrama no funciona tal cual en Flowable 6.8. Ese
   patrón es: subproceso de evento que compensa y termina con un fin de error, atrapado por un borde de error en
   *Preparar curso*. Pasan dos cosas:
   - la compensación lanzada desde adentro del subproceso de evento **sin `activityRef`** (como en el diagrama) no
     compensa nada; con `activityRef` hacia cada actividad sí compensa, solo lo ya completado;
   - el fin de error no llega al borde de error y la instancia queda trabada.

   **Solución (aplicada en el modelo):** una compensación con `activityRef` por cada recurso, en cadena, dentro del
   subproceso de evento; después la tarea *Confirmar compensaciones* (limitación 2) y el fin. Fuera del subproceso, el
   XOR "¿Preparación cancelada?" separa la cancelación del camino normal. El modelo no tiene fin de error ni borde de
   error.

   Si se agrega un recurso con compensación, hay que agregar su evento de compensación a la cadena.

2. **El flujo no espera a las tareas de usuario de una compensación.** *Liberar equipamiento* es una tarea de usuario.
   Al lanzar la compensación Flowable la crea, pero el flujo que la lanzó sigue de largo: lo que viene después se
   ejecuta enseguida. Esto afecta distinto a los dos cierres:
   - **Cierre normal.** Después de *Liberar recursos* solo hay un evento de fin. Alcanza con eso: la instancia no
     termina mientras la tarea siga activa, así que concluye recién al completarla, como pide el §1.4.
   - **Cancelación.** Si el subproceso de evento terminara apenas lanzadas las compensaciones, *Notificar cierre*
     saldría con la liberación del equipamiento todavía pendiente (§1.3).

   **Solución (aplicada en el modelo):** la tarea *Confirmar compensaciones* al final del subproceso de evento.
   Flowable no deja completarla mientras haya compensaciones sin terminar: falla con una restricción de integridad de
   su propia base (`ACT_FK_EXE_PARENT`). Por eso:
   - la web no ofrece *Completar* en esa tarea mientras *Liberar equipamiento* siga pendiente, y avisa qué falta (igual
     que con *Preparar materiales*, que se completa desde su app). Si alguien arma la URL a mano, también lo rechaza
     con un mensaje (`BandejaDeTareas`);
   - se completa en este orden: *Liberar equipamiento* (si se contrató) y después *Confirmar compensaciones*. Recién
     entonces se notifica el cierre y la instancia termina.

   Mientras tanto la instancia sigue abierta, y el portal de la empresa muestra el curso como "Cancelándose:
   liberando recursos" (o "Finalizando: liberando recursos" en el cierre normal). `curso.estado` pasa a su valor final
   cuando la instancia termina.

   **Limitaciones de esta solución:**
   - *Confirmar compensaciones* aparece en **toda** cancelación, aunque no haya nada que liberar a mano: alguien del
     grupo `administracion` tiene que completarla.
   - Cuenta como una tarea completada más en *Tareas completadas por persona* (§4.3.3): quien la completa suma una
     tarea que no es de negocio. No afecta a *Resultados de las solicitudes* (§4.3.1), a la *Duración de las ramas*
     (§4.3.2, las canceladas se excluyen) ni a la minería de la negociación (§4.4).
   - Si se agrega otra liberación que sea tarea de usuario, hay que sumarla a `BandejaDeTareas.LIBERACIONES_DE_USUARIO`
     para que la web la tenga en cuenta (Flowable la bloquea igual).

3. **No se pueden usar eventos intermedios de envío de mensaje.** Flowable 6.8 rechaza el despliegue si el modelo
   tiene un evento intermedio que *lanza* un mensaje. **Solución:** los envíos a la empresa (*Enviar propuesta*,
   *Enviar recordatorio*, *Enviar confirmación*, *Notificar cierre*, *Comunicar imposibilidad*) son tareas que
   simulan el envío. Es lo mismo que hacía el diagrama original.

4. **Business rule task.** En Flowable es para reglas Drools. **Solución:** *Evaluar viabilidad* pasa a ser una
   *decision task* (tarea de servicio de tipo DMN), que es lo que pide el §3.1.

5. **El editor de Flowable rompe las compensaciones al guardar.** Cada vez que se guarda el modelo en el editor,
   las tres asociaciones entre los eventos de compensación y las tareas *Liberar…* se exportan como flechas de
   secuencia (conservan su id `asociacion_…`). El modelo se despliega igual, pero **falla al ejecutar la
   compensación** ("Parent activity for boundary compensation event could not be found"). **Solución:** después de
   cada export, en el XML convertir esas tres `<sequenceFlow id="asociacion_…">` en
   `<association … associationDirection="None">` y moverlas al final del subproceso *Preparar curso*, después de
   todas sus flechas (si quedan antes, Flowable rechaza el despliegue). Revisar el `git diff` antes de commitear.
   La cátedra aclaró que, con pools, el Modeler puede *dibujar* mal las asociaciones sin que el proceso falle. En
   nuestras pruebas, en cambio, el XML exportado traía flechas de secuencia y la compensación fallaba al ejecutar,
   por eso se mantiene esta verificación (y los tests de cancelación y compensación la cubren).

6. **La importación al editor de Flowable puede romper conexiones.** Al importar un XML pasa lo siguiente:
   - puede dejar flechas sin destino, eventos de borde sin enganchar a su tarea o data objects sin nombre (Flowable
     rechaza el despliegue);
   - engancha todas las flechas al centro de sus figuras, así que aparecen codos o diagonales donde el centro no
     está alineado con la flecha;
   - pierde la referencia de *Evaluar viabilidad* a la tabla `viabilidad`, porque el editor solo la conserva si
     apunta a una tabla de decisión creada en el propio editor. Al guardar se borra y Flowable rechaza el despliegue.

   **Solución:**
   - importar solo cuando el archivo del repo cambió, y seguir trabajando sobre ese modelo en el editor;
   - después de importar, enderezar las flechas en el editor;
   - crear en el editor una tabla de decisión vacía con key `viabilidad` y elegirla en *Decision table reference*.
     Las reglas reales están en `viabilidad.dmn` del repo: esa tabla del editor no se exporta;
   - revisar el XML exportado y que los tests del proceso lo cubran.

7. **Flowable no controla quién completa una tarea.** Cualquier usuario con acceso a la API REST puede completar
   una tarea asignada a otro. **Solución:** la web y la app de materiales verifican, antes de llamar al motor, que
   la tarea sea del usuario o que la haya reclamado (§2).

8. **El motor embebido de la app de materiales no tiene nuestras clases.** Al completar *Preparar materiales*
   desde esa app, lo que sigue en el proceso se ejecuta dentro de la app. Si llega a una clase Java nuestra (por
   ejemplo, *Enviar confirmación* cuando es la última rama en terminar), falla. **Solución:** marcar *Enviar
   confirmación* como asíncrona, para que la ejecute el servidor.
9. **El orden de los bordes de *Reservar aula* importa.** Si en el XML el borde de compensación `compensarReservaAula` se declara
   antes que el de error `aulaNoDisponible`, el proceso falla al ejecutarse (comprobado con los tests de cancelación y de
   falta de aulas). **Solución:** el de error va primero en el archivo; al editar el modelo hay que conservar ese orden.
