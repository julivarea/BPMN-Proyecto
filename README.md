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
├── scripts/             Configuración de usuarios y grupos, y despliegue del proceso
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

Con el entorno levantado:

```bash
./mvnw -pl web spring-boot:run                 # sitio web en http://localhost:8081
./mvnw -pl app-materiales compile exec:java     # app de escritorio de materiales
./mvnw -pl app-comunicaciones compile exec:java # app de escritorio de comunicaciones de la empresa
```

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
| [Informe técnico](docs/11-informe-tecnico.md) | Análisis consolidado del stack, arquitectura, modelo BPMN, base de datos, ejecución y pendientes |
| [Cómo trabajamos](CONTRIBUTING.md) | Issues, ramas, commits y convenciones de código |
