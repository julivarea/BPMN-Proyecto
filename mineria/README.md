# Minería de procesos (§4.4)

Análisis de la etapa de negociación del proceso (la previa a la preparación del curso) con PM4Py.

Contenido:
- `generar_ejecuciones.py`: genera historial de prueba con todos los resultados posibles (issue #33).
- `exportar_history.py`: script de la cátedra, adaptado al proceso `organizarCurso`; genera el CSV a partir del
  historial de Flowable. (issue #34)
- `analizar.py`: script de la cátedra; gráfico del comportamiento observado y los tres recorridos más frecuentes.
- El CSV utilizado, una captura de los resultados y la conclusión que compara con el modelo BPMN.
- `conclusion.md`: comparación con el modelo BPMN y limitaciones del análisis.

Preparación del entorno de Python: ver [docs/04-entorno-desarrollo.md](../docs/04-entorno-desarrollo.md).

## Generar ejecuciones de prueba

Crea solicitudes con el formulario de la web (así quedan la empresa y el curso en la base de la institución) y las
lleva por distintos caminos con la API REST de Flowable. Al final verifica que estén los cinco resultados.

1. Entorno levantado y proceso desplegado (README principal). Si Flowable o la web no están en los puertos por defecto,
   se indican con `FLOWABLE_URL` (sin `/process-api`) y `WEB_URL`; `exportar_history.py` también lee `FLOWABLE_URL`.
2. La web, con un plazo de respuesta corto para que los recordatorios venzan en segundos (desde la raíz del repo):
   ```bash
   PLAZO_RESPUESTA=PT15S ./mvnw -pl web spring-boot:run                     # bash / WSL / Git Bash
   ```
   ```powershell
   $env:PLAZO_RESPUESTA = 'PT15S'; .\mvnw.cmd -pl web spring-boot:run        # PowerShell
   ```
3. En otra terminal:
   ```bash
   cd mineria
   source .venv/bin/activate      # PowerShell: .venv\Scripts\Activate.ps1
   python generar_ejecuciones.py
   ```

Tarda uno o dos minutos. Genera diez instancias:

| Caminos | Resultado |
|---------|-----------|
| Rechazo directo; modificaciones y rechazo | `RECHAZADO` |
| Sin respuesta (dos recordatorios) | `SIN_RESPUESTA` |
| Dos solicitudes con cinco cursos en ejecución | `NO_VIABLE` |
| Aceptación (con y sin modificaciones), preparación con distintos recursos, dictado y cierre | `FINALIZADO` |
| Cancelación antes de gestionar recursos, y con aula y equipamiento ya gestionados (el generador completa *Liberar equipamiento* y *Confirmar compensaciones*) | `CANCELADO` |

Las tareas de grupo se reclaman antes de completarlas, alternando administrativos y docentes, para que las consultas
del §4.3.3 tengan tareas de distintas personas.

**Ojo:** el script abre cinco cursos en preparación a la vez para alcanzar el límite de viabilidad. Si al empezar ya hay
alguno en ejecución, las solicitudes que quiere aceptar salen `NO_VIABLE` y el script se detiene esperando *Registrar y
elaborar propuesta*. Para empezar de cero: `docker compose down -v` y repetir la "Primera vez" del README principal (o
correr `scripts/escenarios/limpiar.py`).

## Exportar el historial de la negociación

Desde `mineria/`, con el entorno de Python activo (pide usuario y contraseña de Flowable: `admin` / `test`):

```bash
python exportar_history.py
```

Genera `eventos.csv`: una fila por actividad (caso, actividad y momento), solo de la etapa de negociación. Es el
script de la cátedra con tres adaptaciones:

- **Proceso:** `organizarCurso`.
- **Actividades de la negociación** (`docs/06` §10): desde *recibir solicitud de curso* hasta *Habilitar acceso a
  la empresa* o un fin anterior (no viable o solicitud archivada). Se excluyen los gateways: son decisiones y
  uniones del diagrama, no actividades, y en el gráfico serían ruido.
- **Eventos cancelados:** al llegar a *Esperar respuesta* (gateway de eventos), Flowable activa los cuatro eventos
  posibles (aceptación, rechazo, modificaciones y el plazo de 5 días) y, cuando ocurre uno, cancela los otros. Los
  cancelados también quedan en el historial con fecha de fin, y la API REST no informa que fueron cancelados. Sin
  filtrarlos, cada espera aparecería como si hubieran pasado las cuatro cosas. Un evento se exporta solo si el
  proceso siguió por su flecha de salida (`flujo_<evento>_<destino>`) en la misma ejecución.

## Analizar la negociación

Desde `mineria/`, con el entorno de Python activo y `eventos.csv` ya generado:

```bash
python analizar.py
```

Es el script de la cátedra, con un agregado: además de imprimir los resultados, los guarda en `resultados.txt` en UTF-8
(en Windows, redirigir la salida con `| tee` falla porque Python no puede escribir la flecha `→` en la codificación de la
consola). Genera:

- `resultados.txt`: cantidad de instancias, recorridos distintos y los tres más frecuentes con cantidad y porcentaje.
- `recorridos.png`: grafo de sucesión directa (cuántas veces una actividad siguió a otra).
- `modelo_descubierto.png` y `modelo_descubierto.bpmn`: modelo BPMN descubierto a partir de las ejecuciones.

La comparación con el modelo y las limitaciones del análisis están en [conclusion.md](conclusion.md).

## Reproducir todo el análisis

1. Entorno levantado y web con plazo corto (ver "Generar ejecuciones de prueba").
2. `python generar_ejecuciones.py`
3. `python exportar_history.py` (usuario `admin`, contraseña `test`)
4. `python analizar.py`