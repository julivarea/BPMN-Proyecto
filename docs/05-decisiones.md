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

## Aclaraciones e interpretaciones

- **Cursos en ejecución (§1.1, §3.1).** Por aclaración del profesor, para evaluar la viabilidad cuentan los cursos
  que ya pasaron por *Habilitar acceso de la empresa*. No cuentan todas las instancias sin finalizar, como sugiere
  el texto del §3.1.
- **"Aplicación móvil" (§1.4).** *Definir docente* es una tarea de usuario como cualquier otra. El §4 solo pide un
  sitio web y dos apps de escritorio, y el §4.1.4 ubica esta función en el sitio. No cambia el modelo.

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
     recursos ya gestionados; después *Notificar cierre* y fin.

   El modelo ya no tiene fin de error ni borde de error.

2. **La compensación no espera a las tareas de usuario.** Si hay que *Liberar equipamiento* (tarea de usuario),
   Flowable crea la tarea y sigue sin esperarla: *Notificar cierre* se ejecuta antes de que alguien la complete.
   El proceso sí termina recién cuando se completa. **Se acepta** y se menciona en el informe.

3. **No se pueden usar eventos intermedios de envío de mensaje.** Flowable 6.8 rechaza el despliegue si el modelo
   tiene un evento intermedio que *lanza* un mensaje. **Solución:** los envíos a la empresa (*Enviar propuesta*,
   *Enviar recordatorio*, *Enviar confirmación*, *Notificar cierre*, *Comunicar imposibilidad*) son tareas que
   simulan el envío. Es lo mismo que hacía el diagrama original.

4. **Business rule task.** En Flowable es para reglas Drools. **Solución:** *Evaluar viabilidad* pasa a ser una
   *decision task* (tarea de servicio de tipo DMN), que es lo que pide el §3.1.

5. **Compensaciones dibujadas en el editor de Flowable.** Si en el editor se dibujan o se mueven las asociaciones
   entre los eventos de compensación y las tareas *Liberar…*, al exportar quedan como flechas de secuencia. El
   modelo se despliega igual, pero **falla al ejecutar la compensación** ("Parent activity for boundary
   compensation event could not be found"). **Solución:** no tocar esas tres asociaciones en el editor y revisar
   el XML exportado antes de commitear. Si se rompen, se corrigen en el XML y se reimporta.

6. **La importación al editor de Flowable puede romper conexiones.** Puede dejar flechas sin destino, eventos de
   borde sin enganchar a su tarea o data objects sin nombre (Flowable rechaza el despliegue). **Solución:** revisar
   el XML exportado y que los tests del proceso lo cubran.

7. **Flowable no controla quién completa una tarea.** Cualquier usuario con acceso a la API REST puede completar
   una tarea asignada a otro. **Solución:** la web y la app de materiales verifican, antes de llamar al motor, que
   la tarea sea del usuario o que la haya reclamado (§2).

8. **El motor embebido de la app de materiales no tiene nuestras clases.** Al completar *Preparar materiales*
   desde esa app, lo que sigue en el proceso se ejecuta dentro de la app. Si llega a una clase Java nuestra (por
   ejemplo, *Enviar confirmación* cuando es la última rama en terminar), falla. **Solución:** marcar *Enviar
   confirmación* como asíncrona, para que la ejecute el servidor.
