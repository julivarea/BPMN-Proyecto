# Organización de cursos de capacitación

Proyecto de **Gestión y Tecnologías de Procesos de Negocio** (UNRC, 2026): implementación en Flowable del proceso de
organización de cursos de capacitación para empresas, con un sitio web y dos aplicaciones auxiliares de escritorio.

**Integrantes:** Varea Grosso Julián Lucas · Calle Galeano Jhonatan · Quintero Samuel · Buzzacchi Victoria

La única fuente de verdad es el [enunciado](docs/enunciado/ProyectoCursos_V2.pdf). El diagrama BPMN propuesto por la
cátedra está en [docs/referencia/diagrama-profesor.bpmn](docs/referencia/diagrama-profesor.bpmn). Todo desvío respecto
del enunciado se documenta y se justifica.

## Estructura

```
├── proceso/             Modelo BPMN, regla DMN y componentes Java que se incorporan al motor Flowable
├── web/                 Sitio web de la institución (API REST de Flowable)
├── app-materiales/      App de escritorio: preparación de materiales (API Java de Flowable)
├── app-comunicaciones/  App de escritorio: simulación de comunicaciones de la empresa (API REST de Flowable)
├── db/                  Base de datos de la institución
├── scripts/             Configuración de usuarios y grupos, y despliegue del proceso
└── docs/                Documentación, enunciado y diagrama de referencia
```

Cada módulo sigue la convención de Maven: código en `src/main/java` y tests en `src/test/java`.
