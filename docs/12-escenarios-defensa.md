# 12 · Escenarios de la defensa


Cómo preparar y recorrer los 11 escenarios del §5 del enunciado (`01-requerimientos.md` §3). Los que no arrancan de
cero se dejan listos con los scripts de `scripts/escenarios/` (cómo correrlos: `04-entorno-desarrollo.md`).

| # | Escenario | Preparación | Aplicaciones |
|---|-----------|-------------|--------------|
| 1 | Aceptación y finalización normal, con liberación de recursos | `preparar_escenario_1.py` | Web, UI de Flowable |
| 2 | Modificaciones y nueva propuesta | A mano: solicitud + propuesta | Web, comunicaciones |
| 3 | Rechazo de la propuesta | A mano: solicitud + propuesta | Web, comunicaciones |
| 4 | Cierre por falta de respuesta | `preparar_escenario_4.py` | Web |
| 5 | Límite de cursos en ejecución | `preparar_escenario_5.py` | Web |
| 6 | Cancelación con compensaciones | `preparar_escenario_6.py` | Comunicaciones, web |
| 7 | Falta de aulas | **Pendiente** (#36) | — |
| 8 | Asignación, reclamo y finalización de tareas | Durante el 1, el 6 o el 9 | Web, materiales |
| 9 | Solicitud desde el sitio e inicio de su instancia | Nada | Web |
| 10 | Acceso de la empresa y consulta de sus cursos | Un curso aceptado (el 1 o el 6) | Web |
| 11 | Materiales por API Java y comunicaciones por API REST | Un curso en preparación | Materiales, comunicaciones |

## Antes de empezar

- **Entorno:** `docker compose up -d` y, en terminales aparte, la web y las dos apps:
  ```bash
  PLAZO_RESPUESTA=PT30M ./mvnw -pl web spring-boot:run
  ./mvnw -pl app-comunicaciones compile exec:java
  ./mvnw -pl app-materiales compile exec:java
  ```
- **Plazo de respuesta:** se fija al crear cada solicitud. Con `PT30M` hay tiempo de sobra para recorrer a mano; con el
  de producción (`P5D`) o uno de pocos minutos, una solicitud a la que no se responde termina archivada (escenario 4).
- **Usuarios:** `jefe_admin` y `administrativo` (Administración), `jefe_docente`, `docente1` y `docente2` (Docentes),
  todos con clave `clave123`. La UI de Flowable (http://localhost:8080/flowable-ui): `admin` / `test`.
- **Una ventana de incógnito por usuario**, para no cerrar sesión al cambiar de rol.
- **Credenciales de la empresa:** *Habilitar acceso* las simula escribiéndolas en el log de Flowable:
  `docker compose logs flowable 2>&1 | grep -i acceso | tail -3`.
- **La app de comunicaciones solo muestra las solicitudes abiertas** y, en cada una, solo las comunicaciones que el
  proceso espera en ese momento. El estado final de un curso se ve en el portal de la empresa.
- **Fecha de inicio:** al aceptar, usar la de hoy (no se admiten fechas anteriores): el timer "fecha programada"
  dispara enseguida y *Desarrollar curso* aparece sin esperar.
- **Entre ensayos:** `mineria/.venv/bin/python scripts/escenarios/limpiar.py`.

## 1. Aceptación y finalización normal, con liberación de recursos

**Muestra:** el camino completo después de la preparación, y la compensación que libera los recursos al final (§1.4).
**Preparación:** `preparar_escenario_1.py` deja un curso esperando *Definir docente*, con aula física y equipamiento.
Para recorrerlo entero desde la solicitud, empezar por el 9 y seguir el 2 (aceptando) y el 11.

1. `jefe_docente` (web → Docentes): *Definir docente*, elegir `docente1`.
2. `docente1` (web → Docentes): *Desarrollar curso*.
3. `jefe_docente` (web o UI de Flowable): *Elaborar informe de resultados*.
4. `jefe_admin` (web → Administración): ver el informe y *Enviar informe y archivar expediente*.
5. Un administrativo (web): reclamar y completar *Liberar equipamiento*. El aula se libera sola.

**Mirar:** el portal de la empresa muestra el curso **finalizado**; en la app de materiales, "Resultados" suma un
`FINALIZADO`.

## 2. Modificaciones y nueva propuesta

**Muestra:** la negociación con más de una versión de la propuesta (§1.1).
**Preparación:** una solicitud desde la web (escenario 9) y *Registrar y elaborar propuesta* como `jefe_admin`.

1. App de comunicaciones: elegir la solicitud y **Pedir modificaciones**, con observaciones.
2. `jefe_admin` (web): *Revisar propuesta*. La pantalla muestra lo que pidió la empresa; se genera la versión 2 del PDF.
3. App de comunicaciones: **Aceptar** (sigue como el 1) o **Rechazar** (sigue como el 3).

**Mirar:** las observaciones de la empresa en *Revisar propuesta* y el PDF de la versión 2.

## 3. Rechazo de la propuesta

**Preparación:** como en el 2.

1. App de comunicaciones: **Rechazar propuesta**.
2. `jefe_admin` (web): *Archivar solicitud*.

**Mirar:** en la app de materiales, "Resultados" suma un `RECHAZADO`.

## 4. Cierre por falta de respuesta

**Muestra:** los dos recordatorios y el archivo de la solicitud (§1.1).
**Preparación:** `preparar_escenario_4.py` crea una solicitud con la propuesta enviada y un plazo de un minuto solo
para ella: la web sigue con el plazo largo. Conviene correrlo al principio de la defensa y volver después: tarda
unos 3 minutos.

1. No responder. Cada minuto se envía un recordatorio (dos en total); al vencer el tercer plazo, el proceso pasa a
   *Archivar solicitud*.
2. `jefe_admin` (web): *Archivar solicitud*.

**Mirar:** los recordatorios en el log (`docker compose logs flowable 2>&1 | grep -i recordatorio`) y un
`SIN_RESPUESTA` en "Resultados".

## 5. Límite de cursos en ejecución

**Preparación:** `preparar_escenario_5.py` (crea solo los cursos que falten para llegar a 5).

1. Desde la web (`/solicitar`, sin sesión), enviar una solicitud nueva.

**Mirar:** la web avisa que no se puede continuar; en "Resultados", un `NO_VIABLE`.

## 6. Cancelación con compensaciones

**Preparación:** `preparar_escenario_6.py`: un curso en preparación con el aula reservada y el equipamiento contratado.

1. App de comunicaciones: elegir el curso que indicó el script y **Cancelar curso**.
2. Un administrativo (web): reclamar y completar *Liberar equipamiento*.

**Mirar:** la liberación del aula en el log (`docker compose logs flowable 2>&1 | grep -i aula`), la tarea *Liberar
equipamiento* y el curso **cancelado** en el portal. Solo se compensan los recursos que se gestionaron.

## 7. Falta de aulas

**Pendiente:** el enunciado no dice qué pasa después de la excepción y se consultó al profesor (#36,
`05-decisiones.md`, "Pendientes").

## 8. Asignación, reclamo y finalización de tareas

**Muestra:** las responsabilidades del §2. Se recorre durante el 1, el 6 o el 9.

- **Tareas de un jefe** (*Registrar propuesta*, *Definir docente*…): aparecen asignadas, sin reclamar.
- **Tareas de grupo** (*Evaluar recursos*, *Preparar materiales*…): aparecen como disponibles para el grupo; hay que
  reclamarlas antes de completarlas, y después solo las completa quien las reclamó.
- ***Confeccionar lista definitiva***: el listener la asigna al administrativo con menos tareas pendientes.
- ***Desarrollar curso***: solo la ve el docente elegido en *Definir docente*.
- Un docente no ve las tareas de Administración, ni al revés.

**Mirar:** en la app de materiales, "Tareas por persona" refleja quién completó cada tarea.

## 9. Solicitud desde el sitio e inicio de su instancia

1. En `/solicitar`, sin sesión, completar y enviar el formulario.

**Mirar:** la confirmación; la tarea *Registrar y elaborar propuesta* en la bandeja de `jefe_admin`, con la empresa y
el curso en la columna Solicitud. Con sesión de empresa, el CUIT y la razón social aparecen precargados.

## 10. Acceso de la empresa y consulta de sus cursos

**Preparación:** un curso aceptado (el 1 o el 6).

1. Buscar las credenciales en el log (ver "Antes de empezar") y entrar a `/empresas`.

**Mirar:** solo aparecen los cursos de esa empresa (la URL del curso de otra da 404); el estado de cada uno y,
durante la preparación, las actividades en curso de las tres ramas a la vez.

## 11. Materiales por API Java y comunicaciones por API REST

**Preparación:** un curso en preparación (el 6, antes de cancelar, o uno aceptado a mano).

1. App de materiales (API Java): entrar como `administrativo`, reclamar y completar *Preparar materiales*.
2. App de comunicaciones (API REST): mostrar que solo están habilitadas las comunicaciones que el proceso espera, y
   enviar la nómina.

**Mirar:** las pestañas de consultas de la app de materiales (§4.3): resultados, duración de las ramas y tareas
por persona.

## Orden sugerido para un ensayo

1. `limpiar.py`.
2. `preparar_escenario_4.py` Escenario 4 (en 3 min aparece archivar solicitud).
3. 9 → 2 → 10 → 11, con la misma solicitud.
4. `preparar_escenario_6.py` → escenario 6.
5. `preparar_escenario_1.py` → escenario 1 (y el 8 a lo largo de todo).
6. Volver al escenario 4: completar *Archivar solicitud*.
7. `preparar_escenario_5.py` → escenario 5, al final, porque deja el cupo lleno.