# 05 · Registro de decisiones

Interpretaciones de lo ambiguo del enunciado y cambios respecto del diagrama propuesto (`docs/referencia/diagrama-profesor.bpmn`).

## Cambios respecto del diagrama del enunciado

1. **Gateways XOR de unión explícitos.** Se agregaron tres XOR para juntar caminos antes de seguir: antes de
   *Enviar propuesta* (llegan *Registrar* y *Revisar propuesta*), antes del gateway de eventos (llegan *Enviar
   propuesta* y *Enviar recordatorio*) y antes de *Archivar solicitud* (llegan el rechazo y la falta de respuesta).
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

8. **La cancelación compensa y cierra afuera del subproceso de evento.** El diagrama compensa dentro del subproceso
   de evento *Cancelación*, lo termina con un fin de error que atrapa un borde de error en *Preparar curso* y recién
   ahí notifica el cierre. En el modelo, el subproceso de evento solo marca `resultado = CANCELADO` y termina, y lo que
   sigue lo decide el proceso principal:

   1. XOR "¿Preparación cancelada?".
   2. **Sí:** evento intermedio de compensación "Liberar recursos", que libera solo lo ya gestionado.
   3. XOR "¿Se contrató equipamiento?": con equipamiento contratado, el flujo espera el evento de señal "recibir
      liberación del equipamiento"; sin equipamiento, sigue directo.
   4. XOR de unión, *Notificar cierre* y el fin "cancelación".

   Se mantiene lo que pide el diagrama y el §1.3: se interrumpe la preparación, se compensan solo los recursos
   efectivamente gestionados y el cierre se notifica una vez concluidas las compensaciones, incluida *Liberar
   equipamiento*, que es una tarea de usuario. Lo que cambia es dónde se lanza la compensación y cómo se espera.
   Motivos en las limitaciones 1 y 2.

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
  `PLAZO_RESPUESTA=PT1M ./mvnw -pl web spring-boot:run`.
- **Registro de la solicitud y su instancia (§4.1.1).** Guardar el curso, iniciar la instancia y guardar su id en el
  curso van en una sola transacción: si Flowable falla, no queda el curso sin instancia. La transacción no puede
  deshacer lo hecho en Flowable, así que si fallara el último `UPDATE` quedaría una instancia sin curso. Se acepta
  porque es una actualización simple sobre una fila recién insertada.  

- **Solicitud con sesión de empresa (§4.1, §4.1.2).** Si una empresa inició sesión, `/solicitar` precarga su CUIT y
  razón social y los muestra bloqueados, para que solicite a su nombre. Sin sesión, el formulario sigue siendo público.

- **Fecha de inicio no anterior a hoy (§3.5, §4.2.2).** La app de comunicaciones rechaza una fecha de inicio anterior
  al día actual. La de hoy sí se acepta, aunque su hora ya haya pasado: el timer "fecha programada" dispara
  enseguida, que es lo que usan las pruebas y la demo ("fechas cercanas", §3.5).  
  
## Pendientes

- **Falta de aulas físicas (escenario 7 de la defensa).** No se modela por ahora: el enunciado no dice qué pasa
  después de la excepción. Se consulta al profesor.

## Limitaciones de Flowable 6.8 y soluciones

Comportamientos del motor (y de su editor) que se probaron en el entorno del proyecto y afectan al modelo.

1. **Cancelación durante la preparación (§1.3).** El patrón del diagrama no funciona en Flowable 6.8. Ese patrón
   es: subproceso de evento que compensa y termina con un fin de error, atrapado por un borde de error en
   *Preparar curso*. Pasan dos cosas:
   - la compensación lanzada desde adentro del subproceso de evento no compensa nada;
   - el fin de error no llega al borde de error y la instancia queda trabada.

   **Solución (aplicada en el modelo):** el subproceso de evento solo marca `resultado = CANCELADO` y termina, con
   lo que *Preparar curso* se corta. Después del subproceso va un XOR "¿Preparación cancelada?":
   - **No:** *Enviar confirmación*, el camino normal.
   - **Sí:** compensación "Liberar recursos", lanzada afuera del subproceso, donde sí funciona y libera solo los
     recursos ya gestionados; después la espera de la liberación del equipamiento (limitación 2), *Notificar cierre*
     y fin.

   El modelo no tiene fin de error ni borde de error.

2. **El flujo no espera a las tareas de usuario de una compensación.** *Liberar equipamiento* es una tarea de usuario.
   Al lanzar la compensación Flowable la crea, pero el flujo que la lanzó sigue de largo: lo que viene después se
   ejecuta enseguida. Esto afecta distinto a los dos cierres:
   - **Cierre normal.** Después de *Liberar recursos* solo hay un evento de fin. Alcanza con eso: la instancia no
     termina mientras la tarea siga activa, así que concluye recién al completarla, como pide el §1.4.
   - **Cancelación.** Después de *Liberar recursos* hay una actividad, *Notificar cierre*, que no puede salir antes
     de que terminen las compensaciones (§1.3). Sin una espera explícita, la empresa recibiría el aviso con la
     liberación del equipamiento todavía pendiente.

   **Solución (aplicada en el modelo), solo en la cancelación:**
   - la variable `equipamientoContratado` se fija en `true` con un task listener `complete` de *Contratar
     equipamiento*;
   - después de *Liberar recursos*, el XOR "¿Se contrató equipamiento?" manda a un evento intermedio de captura de
     señal (`equipamientoLiberado`) si es **sí**, y directo a la unión si es **no**;
   - un task listener `complete` de *Liberar equipamiento* (`EquipamientoLiberadoListener`) envía esa señal;
   - la señal se entrega solo a la ejecución de la propia instancia que la espera, no a todas las que estén
     esperando: liberar el equipamiento de un curso no desbloquea la cancelación de otro. En el cierre normal no hay
     quien la espere y el listener no hace nada.

   La espera la resuelve el modelo y no requiere que nadie complete tareas de relleno. Mientras la liberación esté
   pendiente la instancia sigue abierta: el portal de la empresa muestra el curso como "Cancelándose: liberando
   recursos" (o "Finalizando: liberando recursos" en el cierre normal) y `curso.estado` pasa a su valor final cuando la
   instancia termina.

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
