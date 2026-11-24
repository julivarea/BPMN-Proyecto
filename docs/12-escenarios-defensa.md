# 12 · Escenarios de la defensa

Cómo preparar y recorrer los 11 escenarios del §5 del enunciado (`01-requerimientos.md` §3). Los que no arrancan de
cero se dejan listos con los scripts de `scripts/escenarios/`. En cada escenario, **Preparación** dice qué deja listo y
después el comando. El orden para recorrerlos seguidos está al final.

| # | Escenario | Preparación | Aplicaciones |
|---|-----------|-------------|--------------|
| 1 | Aceptación y finalización normal, con liberación de recursos | `preparar_escenario_1.py` | Web, UI de Flowable |
| 2 | Modificaciones y nueva propuesta | A mano: solicitud + propuesta | Web, comunicaciones |
| 3 | Rechazo de la propuesta | A mano: solicitud + propuesta | Web, comunicaciones |
| 4 | Cierre por falta de respuesta | `preparar_escenario_4.py` | Web |
| 5 | Límite de cursos en ejecución | `preparar_escenario_5.py` | Web |
| 6 | Cancelación con compensaciones | `preparar_escenario_6.py` | Comunicaciones, web |
| 7 | Falta de aulas | Flowable con 3 aulas + `preparar_escenario_7.py` | Web, comunicaciones |
| 8 | Asignación, reclamo y finalización de tareas | Durante el 1, el 6 o el 9 | Web, materiales |
| 9 | Solicitud desde el sitio e inicio de su instancia | Nada | Web |
| 10 | Acceso de la empresa y consulta de sus cursos | Un curso aceptado (el 2, el 1 o el 6) | Web |
| 11 | Materiales por API Java y comunicaciones por API REST | Un curso en preparación | Materiales, comunicaciones |

## Antes de empezar

- **Entorno:** `docker compose up -d` y, en terminales aparte, la web y las dos apps:
  ```bash
  PLAZO_RESPUESTA=PT30M ./mvnw -pl web spring-boot:run
  ./mvnw -pl app-comunicaciones compile exec:java
  ./mvnw -pl app-materiales compile exec:java
  ```
- **Scripts de preparación:** son de Python y necesitan la librería `requests`. Se corren desde la raíz del repo, en una
  terminal desde la que lleguen la web (`curl -I http://localhost:8081`) y Flowable (`localhost:8080`). Con el entorno de
  `mineria/` activado (`source mineria/.venv/bin/activate`; en PowerShell, `mineria\.venv\Scripts\Activate.ps1`; cómo
  crearlo: `04-entorno-desarrollo.md`, "Minería de procesos") o con cualquier Python que tenga `requests`
  (`pip install requests`), los comandos de abajo se escriben tal cual:
  ```bash
  python scripts/escenarios/preparar_escenario_6.py
  ```
- **Comandos de Docker:** los `docker compose` se corren en la terminal donde está Docker (WSL, en Windows), desde la
  raíz del repo.
- **Plazo de respuesta:** se fija al crear cada solicitud. Con `PT30M` hay tiempo de sobra para recorrer a mano; con el
  de producción (`P5D`) o uno de pocos minutos, una solicitud a la que no se responde termina archivada (escenario 4).
- **Usuarios:** `jefe_admin` y `administrativo` (Administración), `jefe_docente`, `docente1` y `docente2` (Docentes),
  todos con clave `clave123`. La UI de Flowable (http://localhost:8080/flowable-ui): `admin` / `test`.
- **Una ventana de incógnito por usuario**, para no cerrar sesión al cambiar de rol.
- **Credenciales de la empresa:** *Habilitar acceso* las simula escribiéndolas en el log de Flowable:
  `docker compose logs flowable 2>&1 | grep -i acceso | tail -3`.
- **La app de comunicaciones solo muestra las solicitudes abiertas** y, en cada una, solo las comunicaciones que el
  proceso espera en ese momento. El estado final de un curso se ve en el portal de la empresa.
- **Fecha de inicio:** al aceptar, usar la de hoy y sin hora (no se admiten fechas anteriores): el curso empieza dentro
  de 1 minuto, el timer "fecha programada" dispara enseguida y *Desarrollar curso* aparece sin esperar.
- **Aulas:** Flowable arranca con 10 aulas, para que el uso normal no se quede sin ellas. Solo el escenario 7 necesita
  3, y por eso va al final del orden sugerido: se recrea Flowable con 3 aulas justo antes (`docs/04`, "Cantidad de
  aulas").
- **Limpiar:** `python scripts/escenarios/limpiar.py` borra todas las solicitudes y pide escribir `si`. Borra también el
  historial: las consultas de la app de materiales (*Resultados*, *Duración de las ramas*, *Tareas por persona*) hay que
  mirarlas antes. Con 10 aulas, las que dejan ocupadas las instancias borradas alcanzan para varios ensayos; si llegaran
  a agotarse, `docker compose restart flowable` las libera.

## 1. Aceptación y finalización normal, con liberación de recursos

**Muestra:** el camino completo después de la preparación, y la compensación que libera los recursos al final (§1.4).
**Preparación:** deja un curso esperando *Definir docente*, con aula física y equipamiento:

```bash
python scripts/escenarios/preparar_escenario_1.py
```

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
**Preparación:** sin script. Una solicitud desde la web en `/solicitar` (escenario 9) y, como `jefe_admin`,
*Registrar y elaborar propuesta*.

1. App de comunicaciones: elegir la solicitud y **Pedir modificaciones**, con observaciones.
2. `jefe_admin` (web): *Revisar propuesta*. La pantalla muestra lo que pidió la empresa; al completarla se genera la
   versión 2 del PDF y el aviso de éxito ofrece el enlace para abrirlo.
3. App de comunicaciones: **Aceptar** (sigue como el 10 y el 11) o **Rechazar** (sigue como el 3).

**Mirar:** las observaciones de la empresa en *Revisar propuesta* y el PDF de la versión 2.

## 3. Rechazo de la propuesta

**Preparación:** sin script. Una solicitud nueva desde la web en `/solicitar` y, como `jefe_admin`, *Registrar y
elaborar propuesta* (la misma que en el 2, pero otra solicitud si la anterior ya se aceptó).

1. App de comunicaciones: **Rechazar propuesta**.
2. `jefe_admin` (web): *Archivar solicitud*.

**Mirar:** en la app de materiales, "Resultados" suma un `RECHAZADO`.

## 4. Cierre por falta de respuesta

**Muestra:** los dos recordatorios y el archivo de la solicitud (§1.1).
**Preparación:** crea una solicitud con la propuesta enviada y un plazo de un minuto solo para ella: la web sigue con
el plazo largo. Conviene correrlo al principio de la defensa y volver después: tarda unos 3 minutos.

```bash
python scripts/escenarios/preparar_escenario_4.py
```

1. No responder. Cada minuto se envía un recordatorio (dos en total); al vencer el tercer plazo, el proceso pasa a
   *Archivar solicitud*.
2. `jefe_admin` (web): *Archivar solicitud*.

**Mirar:** los recordatorios en el log (`docker compose logs flowable 2>&1 | grep -i recordatorio`) y un
`SIN_RESPUESTA` en "Resultados".

## 5. Límite de cursos en ejecución

**Preparación:** crea solo los cursos que falten para llegar a 5 en preparación, así que una solicitud nueva sale no
viable:

```bash
python scripts/escenarios/preparar_escenario_5.py
```

1. Desde la web (`/solicitar`, sin sesión), enviar una solicitud nueva.

**Mirar:** la web avisa que no se puede continuar; en "Resultados", un `NO_VIABLE`.

## 6. Cancelación con compensaciones

**Preparación:** un curso en preparación con el aula reservada y el equipamiento contratado:

```bash
python scripts/escenarios/preparar_escenario_6.py
```

1. App de comunicaciones: elegir el curso que indicó el script y **Cancelar curso**.
2. Un administrativo (web): reclamar *Liberar equipamiento* y *Confirmar compensaciones*. Esta última no ofrece
   *Completar* mientras falte liberar el equipamiento.
3. Completar *Liberar equipamiento* y después *Confirmar compensaciones*.

**Mirar:** la liberación del aula en el log (`docker compose logs flowable 2>&1 | grep -i aula`), las tareas *Liberar
equipamiento* y *Confirmar compensaciones*, el curso como "Cancelándose" en el portal mientras faltan, y **cancelado**
al terminar. El aviso de cierre (`docker compose logs flowable 2>&1 | grep -i "Cierre del curso"`) sale recién al
confirmar. Solo se compensan los recursos que se gestionaron. El aula asignada se ve en *Mis cursos* de la empresa
mientras el curso está en preparación y deja de verse al cancelar.

## 7. Falta de aulas

**Muestra:** que reservar un aula cuando no hay ninguna libre genera la excepción, que el modelo la maneja sin
detener el resto de la preparación y que, al liberarse un aula, la reserva se reintenta (I-10).

**Preparación:** tres pasos, en este orden. El script no cambia nada en Flowable: si el servidor sigue con 10 aulas,
las tres reservas no las agotan.

1. Recrear Flowable con 3 aulas (las deja todas libres) y esperar a que termine de levantar, por ejemplo hasta que
   cargue http://localhost:8080/flowable-ui:

   ```bash
   AULAS_DISPONIBLES=3 docker compose up -d flowable
   ```

2. Borrar las solicitudes de los escenarios anteriores, para tener lugar en el límite de 5 cursos (escribir `si`):

   ```bash
   python scripts/escenarios/limpiar.py
   ```

3. Con la web corriendo, dejar tres cursos en preparación con las tres aulas reservadas:

   ```bash
   python scripts/escenarios/preparar_escenario_7.py
   ```

Al terminar, `docker compose up -d flowable` vuelve a 10 aulas.

**Limitación: hay que arrancar siempre de cero.** Las aulas viven en la memoria del servidor Flowable, no en la base ni en
las instancias, así que su estado puede no coincidir con el de los cursos:

- Borrar instancias sin que pasen por *Liberar aula* (con `limpiar.py` o desde la UI de Flowable) deja sus aulas
  ocupadas hasta reiniciar Flowable.
- Reiniciar Flowable deja todas las aulas libres aunque haya cursos en curso con aula asignada: dos cursos podrían
  compartir la misma, y el aula que un curso libere después se suma al cupo aunque ya estuviera libre.

Si esto pasa, con menos de 3 aulas libres la tercera reserva del script falla y aparece un *Confirmar aula disponible* de
más en la bandeja. Por eso el paso 1 va siempre antes del script, y conviene no usar cursos de ensayos anteriores. Si en
la bandeja de Administración hay algún *Confirmar aula disponible* antes de registrar la solicitud de la demo, se repite
desde el paso 1. Es una decisión de diseño aceptada, no un error: ver [05-decisiones.md](05-decisiones.md), "Falta de
aulas".

**Se recorre así:**

1. Registrar una solicitud nueva en la web, llevarla a preparación (propuesta y aceptación) y, como administrativo,
   completar *Evaluar recursos* con aula física.
2. Mostrar que no hay aula: *Confirmar aula disponible* aparece para Administración, el portal de la empresa la
   lista entre las actividades del curso y las demás ramas (*Preparar materiales*, lista definitiva) siguen
   disponibles. Completarla ahora repite el error y la tarea vuelve a quedar pendiente.
3. App de comunicaciones: cancelar uno de los tres cursos del script y, en la web, completar *Confirmar
   compensaciones*: el aula se libera.
4. Completar *Confirmar aula disponible*: *Reservar aula* se reintenta y el curso recibe el aula liberada.

**Mirar:** en el log (`docker compose logs flowable 2>&1 | grep -i aula`), el aviso "no hay aulas libres", la
liberación y la nueva reserva; en *Mis cursos* de la empresa, el aula asignada recién después del paso 4.

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

**Preparación:** ninguna.

1. En `/solicitar`, sin sesión, completar y enviar el formulario.

**Mirar:** la confirmación; la tarea *Registrar y elaborar propuesta* en la bandeja de `jefe_admin`, con la empresa y
el curso en la columna Solicitud. Con sesión de empresa, el CUIT y la razón social aparecen precargados y bloqueados.

## 10. Acceso de la empresa y consulta de sus cursos

**Preparación:** un curso aceptado (el de los escenarios 9 y 2, o el del 1 o el 6). Las credenciales de su empresa
salen del log de Flowable:

```bash
docker compose logs flowable 2>&1 | grep -i acceso | tail -3
```

1. Entrar a `/empresas` con esas credenciales.

**Mirar:** solo aparecen los cursos de esa empresa (la URL del curso de otra da 404); el estado de cada uno, el aula
asignada y, durante la preparación, las actividades en curso de las tres ramas a la vez.

## 11. Materiales por API Java y comunicaciones por API REST

**Preparación:** un curso en preparación: el de los escenarios 9 y 2 una vez aceptado, o uno nuevo del script del 6
(antes de cancelarlo):

```bash
python scripts/escenarios/preparar_escenario_6.py
```

1. App de materiales (API Java): entrar como `administrativo`, reclamar y completar *Preparar materiales*.
2. App de comunicaciones (API REST): mostrar que solo están habilitadas las comunicaciones que el proceso espera, y
   enviar la nómina.

**Mirar:** las pestañas de consultas de la app de materiales (§4.3): resultados, duración de las ramas y tareas
por persona.

## Orden sugerido para una demo seguida

Está pensado para esperar lo menos posible y dejar el escenario 7 (el más sensible al estado de las aulas) para el
final, con Flowable recién recreado.

1. **Antes de empezar:** el entorno de "Antes de empezar" y, si hay datos de pruebas anteriores:

   ```bash
   python scripts/escenarios/limpiar.py
   ```

2. **Escenario 4 (arranca el reloj, tarda unos 3 minutos):**

   ```bash
   python scripts/escenarios/preparar_escenario_4.py
   ```

3. **9 → 2 → 10 → 11** con la misma solicitud: solicitud desde la web, modificaciones, aceptar, portal de la empresa y,
   con el curso ya en preparación, materiales y comunicaciones.
4. **3** con otra solicitud nueva (propuesta, rechazo y archivo).
5. **Escenario 6:**

   ```bash
   python scripts/escenarios/preparar_escenario_6.py
   ```

6. **Escenario 1** (y el 8 a lo largo de todo):

   ```bash
   python scripts/escenarios/preparar_escenario_1.py
   ```

7. **Volver al escenario 4:** a esta altura ya pasaron los 3 minutos; completar *Archivar solicitud*.
8. **Escenario 5**, que deja el cupo de cursos lleno:

   ```bash
   python scripts/escenarios/preparar_escenario_5.py
   ```

9. **Consultas de la app de materiales** (*Resultados*, *Duración de las ramas*, *Tareas por persona*), con todos los
   escenarios anteriores ya registrados: el paso siguiente borra el historial.
10. **Escenario 7**, al final: seguir su sección, que recrea Flowable con 3 aulas, limpia los cursos del 5 y prepara las
    aulas (`docker compose up -d flowable` al terminar para volver a 10).
