# Minería de procesos (§4.4)

Análisis de la etapa de negociación del proceso (la previa a la preparación del curso) con PM4Py.

Contenido:
- `generar_ejecuciones.py`: genera historial de prueba con todos los resultados posibles (issue #33).
- `exportar_history.py`: script de la cátedra, adaptado al proceso `organizarCurso`; genera el CSV a partir del
  historial de Flowable. *(pendiente, #34)*
- `analizar.py`: script de la cátedra; gráfico del comportamiento observado y los tres recorridos más frecuentes.
  *(pendiente, #35)*
- El CSV utilizado, una captura de los resultados y la conclusión que compara con el modelo BPMN. *(pendiente, #35)*

Preparación del entorno de Python: ver [docs/04-entorno-desarrollo.md](../docs/04-entorno-desarrollo.md).

## Generar ejecuciones de prueba

Crea solicitudes con el formulario de la web (así quedan la empresa y el curso en la base de la institución) y las
lleva por distintos caminos con la API REST de Flowable. Al final verifica que estén los cinco resultados.

1. Entorno levantado y proceso desplegado (README principal).
2. La web, con un plazo de respuesta corto para que los recordatorios venzan en segundos (desde la raíz del repo):
   ```bash
   PLAZO_RESPUESTA=PT15S ./mvnw -pl web spring-boot:run
   ```
3. En otra terminal:
   ```bash
   cd mineria
   source .venv/bin/activate
   python generar_ejecuciones.py
   ```

Tarda uno o dos minutos. Genera diez instancias:

| Caminos | Resultado |
|---------|-----------|
| Rechazo directo; modificaciones y rechazo | `RECHAZADO` |
| Sin respuesta (dos recordatorios) | `SIN_RESPUESTA` |
| Dos solicitudes con cinco cursos en ejecución | `NO_VIABLE` |
| Aceptación (con y sin modificaciones), preparación con distintos recursos, dictado y cierre | `FINALIZADO` |
| Cancelación antes de gestionar recursos, y con aula y equipamiento ya gestionados | `CANCELADO` |

Las tareas de grupo se reclaman antes de completarlas, alternando administrativos y docentes, para que las consultas
del §4.3.3 tengan tareas de distintas personas.

**Ojo:** el conteo de viabilidad incluye los cursos en ejecución que ya hubiera. Si al empezar hay cinco o más, las
solicitudes que el script quiere aceptar salen `NO_VIABLE` y el script se detiene esperando *Registrar y elaborar
propuesta*. Para empezar de cero: `docker compose down -v` y repetir la "Primera vez" del README principal.