# Minería de la etapa de negociación (§4.4)

## Datos
- `eventos.csv`: 15 instancias terminadas del proceso `organizarCurso`, generadas con `generar_ejecuciones.py`
  (10) y durante su desarrollo (5). Solo actividades de la negociación: desde la solicitud hasta *Habilitar
  acceso a la empresa* o un fin anterior.
- `analizar.py` (script evelia) considera solo tareas de usuario y automáticas: los eventos (mensajes de la
  empresa y el plazo de 5 días) no aparecen en los recorridos, pero cada respuesta se reconoce por la tarea que
  sigue (*Habilitar acceso* = aceptación, *Revisar propuesta* = modificaciones, *Archivar* sin recordatorios =
  rechazo, *Enviar recordatorio* = sin respuesta).

## Resultados
Captura: `resultados.txt`, `recorridos.png` (grafo de sucesión directa) y `modelo_descubierto.png`.

Hay 6 recorridos distintos:

| Recorrido (después de *Evaluar viabilidad → Registrar → Enviar propuesta*) | Casos | % |
|-----------------------------------------------------------------------------|-------|------|
| Revisar propuesta → Enviar propuesta → Habilitar acceso (modificaciones y aceptación) | 3 | 20,0 |
| Habilitar acceso (aceptación directa) | 3 | 20,0 |
| Archivar solicitud (rechazo directo) | 3 | 20,0 |
| Revisar propuesta → Enviar propuesta → Archivar (modificaciones y rechazo) | 2 | 13,3 |
| Enviar recordatorio → Enviar recordatorio → Archivar (sin respuesta) | 2 | 13,3 |
| *Evaluar viabilidad → Comunicar imposibilidad* (no viable) | 2 | 13,3 |

Los tres más frecuentes empatan con 3 casos (20 %) cada uno: el script de generación recorre cada camino una
cantidad fija de veces.

## Comparación con el modelo BPMN
**Coincidencias.** El modelo descubierto tiene la misma estructura que la negociación del modelo: la decisión de
viabilidad, el ciclo de modificaciones (*Revisar propuesta* → *Enviar propuesta*), las tres salidas después del
envío (aceptación, rechazo, recordatorios) y *Archivar solicitud* como punto común del rechazo y la falta de
respuesta.

**Diferencias.**
- Donde el modelo tiene un gateway de eventos (la empresa responde o vence el plazo), el descubierto tiene
  gateways exclusivos: la minería muestra qué camino se siguió, no que fue una espera ni quién lo decidió.
- El descubierto admite cualquier cantidad de recordatorios; el modelo envía exactamente dos. Esa regla depende de
  una variable (`recordatoriosEnviados`) que no está en el log.
- El descubierto no admite responder después de un recordatorio, que el modelo sí permite: ese caso no aparece en
  los datos. El modelo descubierto solo representa lo observado.

## Limitaciones
1. **Datos generados:** las frecuencias reflejan cómo se armó el script de generación, no a empresas reales.
2. **Tiempos artificiales:** las tareas las completa un script en milisegundos y el plazo se redujo a 15 s. El
   análisis sirve para ver qué caminos se recorren, no cuánto tardan.
3. **Eventos cancelados:** al esperar la respuesta, Flowable registra como terminados también los eventos que
   cancela el gateway de eventos. `exportar_history.py` los descarta reconstruyendo, a partir de las flechas
   recorridas, cuál ocurrió (depende de la convención de nombres `flujo_<origen>_<destino>`).
4. **Solo instancias terminadas:** la negociación de cursos todavía en preparación no se incluiría.
5. **Pocas instancias (15):** los caminos no observados no aparecen en el modelo descubierto.

## Reproducir
Ver `README.md` de esta carpeta: generar ejecuciones, exportar el historial y `python analizar.py | tee resultados.txt`.