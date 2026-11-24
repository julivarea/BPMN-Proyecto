# Organización de cursos de capacitación


**UNRC** · Facultad de Ciencias Exactas, Físico-Químicas y Naturales<br>
**Materia:** Gestión y Tecnologías de Procesos de Negocios (3371)<br>
**Profesor:** Ariel González

Proyecto de **Gestión y Tecnologías de Procesos de Negocios** (UNRC, 2026): implementación en Flowable del proceso de
organización de cursos de capacitación para empresas, con un sitio web y dos aplicaciones auxiliares de escritorio.

**Integrantes:**

- Alvarez Joel
- Buzzacchi Victoria
- Calle Galeano Jhonatan
- Quintero Samuel
- Varea Grosso Julián Lucas

## Estructura

```
├── proceso/             Modelo BPMN, regla DMN y componentes Java que se incorporan al motor Flowable
├── web/                 Sitio web de la institución (API REST de Flowable)
├── app-materiales/      App de escritorio: preparación de materiales (API Java de Flowable)
├── app-comunicaciones/  App de escritorio: simulación de comunicaciones de la empresa (API REST de Flowable)
├── db/                  Base de datos de la institución
├── scripts/             Usuarios y grupos, despliegue del proceso y preparación de los escenarios de la defensa
├── mineria/             Minería de procesos de la etapa de negociación (Python + PM4Py)
└── docs/                Documentación, enunciado y diagrama de referencia
```

Cada módulo sigue la convención de Maven: código en `src/main/java` y tests en `src/test/java`.

## Inicio rápido

**Requisitos:** Git, Docker Desktop y **JDK 21** (`java -version` tiene que decir 21). Maven no hace falta: el repo
trae `./mvnw`. En Windows, los `.sh` se corren con **Git Bash** (o WSL) y `./mvnw` se reemplaza por `mvnw.cmd`.

### Primera vez

```bash
./mvnw -pl proceso clean package    # 1. compila el jar con nuestras clases para el motor
docker compose up -d                # 2. levanta PostgreSQL + Flowable

# 3. esperar a que Flowable termine de arrancar (la primera vez tarda 1-2 minutos)
until curl -sf -u admin:test http://localhost:8080/flowable-ui/process-api/repository/deployments >/dev/null; do sleep 5; done

bash scripts/setup-identidades.sh   # 4. crea usuarios, grupos y privilegios
bash scripts/desplegar-proceso.sh   # 5. despliega el BPMN y el DMN de proceso/src/main/resources/procesos
```

Listo: la UI de Flowable está en <http://localhost:8080/flowable-ui>.

> **Ojo con el paso 4:** `setup-identidades.sh` usa `docker compose exec`, así que se corre desde una terminal donde
> funcione el comando `docker` (con Docker Desktop, Git Bash sirve; si Docker está instalado solo en WSL, hay que
> correrlo desde WSL).

### Cuentas del personal

Son cuentas locales de desarrollo; las crea `scripts/setup-identidades.sh`.

| Usuario | Contraseña | Rol | Grupo |
|---------|------------|-----|-------|
| `admin` | `test` | Administrador de Flowable | — |
| `jefe_admin` | `clave123` | Jefe administrativo | `administracion` |
| `administrativo` | `clave123` | Administrativo | `administracion` |
| `jefe_docente` | `clave123` | Jefe del equipo docente | `docentes` |
| `docente1` | `clave123` | Docente | `docentes` |
| `docente2` | `clave123` | Docente | `docentes` |

### Aplicaciones

Con el entorno levantado, cada comando en su propia terminal (la web y las apps quedan corriendo; se cortan con
Ctrl+C o cerrando la ventana):

```bash
./mvnw -pl web spring-boot:run                 # sitio web en http://localhost:8081
./mvnw -pl app-materiales compile exec:java     # app de escritorio de materiales
./mvnw -pl app-comunicaciones compile exec:java # app de escritorio de comunicaciones de la empresa
```

**Web con el plazo de respuesta en 1 minuto (para demos).** El proceso espera 5 días la respuesta de la empresa antes de
cada recordatorio. Para acortarlo, se lanza la web con la variable `PLAZO_RESPUESTA` (duración ISO 8601) **en lugar
del comando de la web de arriba**:

```bash
PLAZO_RESPUESTA=PT1M ./mvnw -pl web spring-boot:run                  # bash / WSL / Git Bash
```

```powershell
$env:PLAZO_RESPUESTA = 'PT1M'; .\mvnw.cmd -pl web spring-boot:run     # PowerShell
```

Vale para las solicitudes que se creen después; las que ya existen conservan su plazo. Con `PT1M`, una solicitud sin
respuesta se cierra en unos 3 minutos (dos recordatorios y el tercer vencimiento). Más detalle en
[docs/04](docs/04-entorno-desarrollo.md).

### Minería de procesos (§4.4)

Necesita Python 3 y Graphviz (`sudo apt install python3-venv graphviz`; en Windows, los instaladores de Python y de
Graphviz). El entorno se prepara una sola vez, dentro de `mineria/`:

```bash
cd mineria
python3 -m venv .venv
source .venv/bin/activate        # PowerShell: .venv\Scripts\Activate.ps1
pip install pm4py pandas requests
```

Para tener historial que analizar, con el entorno levantado y la web corriendo **con `PLAZO_RESPUESTA=PT15S`** (mismo
comando que arriba, cambiando `PT1M` por `PT15S`; si ya hay cursos en ejecución, mejor empezar de cero con
`docker compose down -v`), desde `mineria/` y con el entorno de Python activo:

```bash
python generar_ejecuciones.py          # crea solicitudes y las lleva por todos los caminos (1 o 2 minutos)
python exportar_history.py             # genera eventos.csv; pide usuario y clave de Flowable (admin / test)
python analizar.py                     # imprime los resultados y los guarda en resultados.txt
```

En Windows, si `pip install` falla con "No such file or directory", suele ser por el límite de rutas largas: conviene
tener el repo en una carpeta de ruta corta (por ejemplo `C:\proyecto`).

Genera `eventos.csv`, `resultados.txt` (los tres recorridos más frecuentes), `recorridos.png`, `modelo_descubierto.png` y
`modelo_descubierto.bpmn`. La comparación con el modelo está en [`mineria/conclusion.md`](mineria/conclusion.md) y el
detalle de cada script en [`mineria/README.md`](mineria/README.md).

### Día a día

| Qué | Comando |
|-----|---------|
| Compilar y correr los tests | `./mvnw verify` |
| Cambié Java del módulo `proceso` | `./mvnw -pl proceso clean package && docker compose restart flowable` |
| Cambié el BPMN o el DMN | `bash scripts/desplegar-proceso.sh` |
| Ver los logs de Flowable | `docker compose logs -f flowable` |
| Apagar (los datos se conservan) | `docker compose down` |
| Volver a levantar | `docker compose up -d` (no hace falta repetir el setup) |
| Borrar todos los datos y empezar de cero | `docker compose down -v` y repetir "Primera vez" |

Usuarios de prueba, pgAdmin, puertos y más comandos: [docs/04-entorno-desarrollo.md](docs/04-entorno-desarrollo.md).

## Dónde está cada entregable

Lo que pide el §5 del enunciado, con su ubicación en el repo:

| Entregable | Dónde está |
|------------|------------|
| Modelo BPMN ejecutable | [`proceso/src/main/resources/procesos/organizarCurso.bpmn20.xml`](proceso/src/main/resources/procesos/organizarCurso.bpmn20.xml); diagrama en [`docs/diagramas/`](docs/diagramas) |
| Reglas de negocio (viabilidad, §3.1) | [`proceso/src/main/resources/procesos/viabilidad.dmn`](proceso/src/main/resources/procesos/viabilidad.dmn) |
| Código de los componentes incorporados al motor (listeners y delegates, §3.4) | [`proceso/src/main/java`](proceso/src/main/java) |
| Código del sitio web, que usa la API REST de Flowable (§4.1) | [`web/`](web) |
| Código de las dos apps de escritorio (§4.2) | [`app-materiales/`](app-materiales) (API Java) y [`app-comunicaciones/`](app-comunicaciones) (API REST) |
| Base de datos de la institución (§3.2) | Creación en [`db/init/`](db/init) y tablas en [`web/src/main/resources/db/migration/`](web/src/main/resources/db/migration); explicada en [docs/07](docs/07-base-de-datos.md) |
| Instrucciones para configurar usuarios y grupos y ejecutar la solución | "Inicio rápido" de este README y [docs/04](docs/04-entorno-desarrollo.md); los usuarios y grupos los crea [`scripts/setup-identidades.sh`](scripts/setup-identidades.sh) |
| Instrucciones de las tres aplicaciones externas | "Aplicaciones" de este README, [docs/08](docs/08-web.md) (web), [docs/09](docs/09-app-materiales.md) (materiales) y [docs/10](docs/10-app-comunicaciones.md) (comunicaciones) |
| Informe breve: variables, listeners y decisiones de implementación | [docs/11](docs/11-informe-implementacion.md); el detalle, en [docs/06](docs/06-guia-implementacion-modelo.md) (variables, listeners y delegates) y [docs/05](docs/05-decisiones.md) (decisiones) |
| Consultas sobre el historial (§4.3) | En la app de materiales: [`ConsultasHistoricas`](app-materiales/src/main/java/ar/edu/unrc/gtpn/materiales/historial/ConsultasHistoricas.java) y sus pestañas; explicadas en [docs/09](docs/09-app-materiales.md) §8 |
| Minería de procesos (§4.4): CSV, código, captura y conclusión | [`mineria/`](mineria): `eventos.csv`, los scripts, `recorridos.png` y `modelo_descubierto.png`, `resultados.txt` y [`conclusion.md`](mineria/conclusion.md); cómo reproducirlo en [`mineria/README.md`](mineria/README.md) |
| Escenarios de la demostración (§5) | [docs/12](docs/12-escenarios-defensa.md) y los scripts de [`scripts/escenarios/`](scripts/escenarios) |

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [Enunciado](docs/enunciado/ProyectoCursos_V3.pdf) | Enunciado del proyecto |
| [Diagrama de la cátedra](docs/referencia/diagrama-profesor.bpmn) | Diagrama BPMN propuesto por el profesor |
| [Requerimientos](docs/01-requerimientos.md) | Qué pide el enunciado, con IDs de requisitos y escenarios de la defensa |
| [Arquitectura](docs/02-arquitectura.md) | Componentes, tecnologías elegidas y cómo se comunican |
| [Guía de Flowable](docs/03-guia-flowable.md) | Conceptos, cómo editar el modelo, API REST y API Java, errores comunes |
| [Entorno de desarrollo](docs/04-entorno-desarrollo.md) | Instalación, usuarios de prueba y comandos |
| [Decisiones](docs/05-decisiones.md) | Interpretaciones del enunciado y cambios respecto del diagrama de la cátedra |
| [Implementación del modelo](docs/06-guia-implementacion-modelo.md) | Variables, mensajes, condiciones, timers, listeners y delegates del modelo |
| [Base de datos](docs/07-base-de-datos.md) | Esquema de la base de la institución y quién escribe cada dato |
| [Sitio web](docs/08-web.md) | Cómo está armada la web, conexión con Flowable, reglas para reclamar y completar tareas, tests |
| [App de materiales](docs/09-app-materiales.md) | Motor embebido y su configuración, login, tareas *Preparar materiales*, consultas del §4.3 |
| [App de comunicaciones](docs/10-app-comunicaciones.md) | Cliente REST, solicitudes en curso y cómo se habilitan y envían las comunicaciones de la empresa |
| [Informe breve de implementación](docs/11-informe-implementacion.md) | Las variables, los listeners y las decisiones de implementación, en un resumen |
| [Escenarios de la defensa](docs/12-escenarios-defensa.md) | Cómo preparar y recorrer los 11 escenarios del §5, con los scripts de `scripts/escenarios/` |
| [Cómo trabajamos](CONTRIBUTING.md) | Issues, ramas, commits y convenciones de código |
