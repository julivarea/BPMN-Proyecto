# 04 · Entorno de desarrollo

## Requisitos

- Git, Docker Desktop y **JDK 21** (por ejemplo Temurin). No hace falta instalar Maven: se usa `./mvnw`.
- En Windows, los scripts `.sh` se corren con **Git Bash** (o WSL), y `mvnw` se reemplaza por `mvnw.cmd`.

## Levantar el entorno

```bash
./mvnw -pl proceso clean package     # 1. jar con nuestras clases para el motor
docker compose up -d                 # 2. Postgres + Flowable (la primera vez tarda 1-2 minutos)
# 3. esperar a que Flowable responda antes de seguir
until curl -sf -u admin:test http://localhost:8080/flowable-ui/process-api/repository/deployments >/dev/null; do sleep 5; done
bash scripts/setup-identidades.sh    # 4. usuarios, grupos y privilegios (se puede repetir; usa `docker compose exec`)
bash scripts/desplegar-proceso.sh    # 5. despliega los BPMN y DMN de proceso/src/main/resources/procesos
```

- UI de Flowable: <http://localhost:8080/flowable-ui> (`admin` / `test`).
- API REST: <http://localhost:8080/flowable-ui/process-api>.

Aplicaciones (con el entorno levantado):

```bash
./mvnw -pl web spring-boot:run                  # sitio en http://localhost:8081
./mvnw -pl app-materiales compile exec:java     # app de escritorio de materiales
./mvnw -pl app-comunicaciones compile exec:java # app de escritorio de comunicaciones de la empresa
```

Para una demo se acorta el plazo de respuesta de la propuesta (por defecto 5 días) con la variable `PLAZO_RESPUESTA`, en
formato ISO-8601, al lanzar la web:

```bash
PLAZO_RESPUESTA=PT1M ./mvnw -pl web spring-boot:run                       # bash / WSL
```

```powershell
$env:PLAZO_RESPUESTA = 'PT1M'; .\mvnw.cmd -pl web spring-boot:run          # PowerShell
```

Vale para las solicitudes que se creen después; las que ya existen conservan su plazo. Con `PT1M`, una solicitud sin
respuesta se cierra en unos 3 minutos (dos recordatorios y el tercer vencimiento). Ver
[05-decisiones.md](05-decisiones.md).

## Usuarios de prueba

Los crea `scripts/setup-identidades.sh`.

| Usuario | Rol | Grupo |
|---------|-----|-------|
| `admin` / `test` | Administrador de Flowable | — |
| `jefe_admin` | Jefe administrativo | `administracion` |
| `administrativo` | Administrativo | `administracion` |
| `jefe_docente` | Jefe del equipo docente | `docentes` |
| `docente1`, `docente2` | Docentes | `docentes` |

Contraseña de todo el personal: `clave123` (se cambia con `CLAVE_DEMO=... bash scripts/setup-identidades.sh`).

## Comandos frecuentes

| Qué | Comando |
|-----|---------|
| Compilar y correr todos los tests | `./mvnw verify` |
| Solo los tests del proceso (sin Docker) | `./mvnw -pl proceso verify` |
| Probar los clientes REST (web y app de comunicaciones) contra el Flowable de Docker | `FLOWABLE_DOCKER=si ./mvnw -pl web,app-comunicaciones verify` |
| Cambié Java del módulo `proceso` | `./mvnw -pl proceso clean package && docker compose restart flowable` |
| Cambié un BPMN o un DMN | `bash scripts/desplegar-proceso.sh` |
| Logs del servidor | `docker compose logs -f flowable` |
| pgAdmin en <http://localhost:5050> | `docker compose --profile herramientas up -d` |
| Apagar (conserva los datos) | `docker compose down` |
| Borrar **todos** los datos y empezar de cero | `docker compose down -v` y repetir "Levantar el entorno" |

Los puertos y las credenciales de Postgres se cambian en `.env` (ver `.env.example`).

## Minería de procesos (§4.4)

Los scripts de la cátedra (`exportar_history.py`, `analizar.py`) usan Python con PM4Py. El entorno se prepara una
sola vez, dentro de `mineria/` (en Windows, si `pip install` falla con "No such file or directory", suele ser por el límite
de rutas largas: conviene tener el repo en una carpeta de ruta corta):

```bash
sudo apt install python3-venv graphviz     # en Windows: Python 3 y Graphviz desde sus instaladores
cd mineria
python3 -m venv .venv
source .venv/bin/activate                  # en Windows: .venv\Scripts\activate
pip install pm4py pandas requests
```

La carpeta `.venv/` no se commitea. Con el entorno levantado y la web corriendo con `PLAZO_RESPUESTA=PT15S` (para que los
recordatorios venzan en segundos; ver arriba cómo lanzarla), desde `mineria/` y con el entorno de Python activo:

```bash
python generar_ejecuciones.py              # crea solicitudes y las lleva por todos los caminos (1 o 2 minutos)
python exportar_history.py                 # genera eventos.csv; pide usuario y clave de Flowable (admin / test)
python analizar.py                         # imprime los resultados y los guarda en resultados.txt
```

`generar_ejecuciones.py` abre cinco cursos en preparación a la vez para probar el límite de viabilidad: si al empezar ya hay
alguno en ejecución, las solicitudes que quiere aceptar salen no viables. Para empezar de cero, `docker compose down -v` y
repetir "Levantar el entorno" (o correr `scripts/escenarios/limpiar.py`). Qué hace cada script y qué archivos genera:
[`mineria/README.md`](../mineria/README.md).

## Escenarios de la defensa

Los scripts de `scripts/escenarios/` dejan el sistema en el estado inicial de los escenarios que no arrancan de
cero. Crean las solicitudes con el formulario de la web (así existen en la base de la institución) y las avanzan
por la API REST de Flowable. Usan el entorno de Python de `mineria/`, con un paquete más:

```bash
mineria/.venv/bin/pip install "psycopg[binary]"    # para limpiar.py
```

Con el entorno levantado y la web corriendo, desde la raíz del repo:

| Script | Deja listo |
|--------|-----------|
| `preparar_escenario_1.py` | 1 curso esperando *Definir docente* |
| `preparar_escenario_4.py` | 1 solicitud con la propuesta enviada y un plazo de 1 minuto: se archiva sola en unos 3 minutos |
| `preparar_escenario_5.py` | 5 cursos en preparación: una solicitud nueva termina como no viable |
| `preparar_escenario_6.py` | 1 curso en preparación con el aula reservada y el equipamiento contratado, para cancelarlo |
| `limpiar.py` | Borra todas las solicitudes (Flowable y base de la institución) para otro ensayo |

```bash
mineria/.venv/bin/python scripts/escenarios/preparar_escenario_5.py
```

Si el entorno no está en los puertos del README, los scripts (y los de `mineria/`) leen las mismas variables que la web
y las apps: `FLOWABLE_URL` (la base de Flowable, sin `/process-api`), `WEB_URL` (por defecto `http://localhost:8081`) y
`INSTITUCION_DB_URL`, o `POSTGRES_PORT`, para la base de la institución (`limpiar.py`; antes de borrar muestra a qué
Flowable y a qué base apunta).

El límite de la regla de viabilidad es de 5 cursos en ejecución: el 6 y el 1 avisan si no hay lugar. `limpiar.py`
pide confirmación y no toca usuarios, grupos ni el proceso desplegado; también borra el historial que usa la minería
(`generar_ejecuciones.py` lo vuelve a generar). Cómo recorrer cada escenario: `docs/12-escenarios-defensa.md`.
