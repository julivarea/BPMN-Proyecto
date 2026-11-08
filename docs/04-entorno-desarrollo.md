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
bash scripts/setup-identidades.sh    # 4. usuarios, grupos y privilegios (se puede repetir)
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
| Probar el cliente REST de la web contra el Flowable de Docker | `FLOWABLE_DOCKER=si ./mvnw -pl web verify` |
| Cambié Java del módulo `proceso` | `./mvnw -pl proceso clean package && docker compose restart flowable` |
| Cambié un BPMN o un DMN | `bash scripts/desplegar-proceso.sh` |
| Logs del servidor | `docker compose logs -f flowable` |
| pgAdmin en <http://localhost:5050> | `docker compose --profile herramientas up -d` |
| Apagar (conserva los datos) | `docker compose down` |
| Borrar **todos** los datos y empezar de cero | `docker compose down -v` y repetir "Levantar el entorno" |

Los puertos y las credenciales de Postgres se cambian en `.env` (ver `.env.example`).

## Minería de procesos (§4.4)

Los scripts de la cátedra (`exportar_history.py`, `analizar.py`) usan Python con PM4Py. El entorno se prepara una
sola vez, dentro de `mineria/`:

```bash
sudo apt install python3-venv graphviz     # en Windows: Python 3 y Graphviz desde sus instaladores
cd mineria
python3 -m venv .venv
source .venv/bin/activate                  # en Windows: .venv\Scripts\activate
pip install pm4py pandas requests
```

Con el entorno levantado y algunas instancias ejecutadas, se exporta el historial a CSV y se analiza con esos
scripts. La carpeta `.venv/` no se commitea.
