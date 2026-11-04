# Cómo trabajamos

## Organización del trabajo

- El trabajo se organiza en **issues de GitHub**, agrupados en **milestones** por componente (proceso, web, apps de
  escritorio, entrega). Se gestionan con [`gh`](https://cli.github.com/), por ejemplo:
  `gh issue create --title "Formulario de solicitud" --milestone "Web"` y `gh issue list --milestone "Web"`.
- Cada issue cita los IDs de requisitos que cubre (`W-02`, `I-04`…, ver
  [docs/01-requerimientos.md](docs/01-requerimientos.md)).

## Ramas

1. Rama corta desde `main` actualizada: `tipo/descripcion-corta`, por ejemplo `feat/web-formulario-solicitud`.
2. Al terminar: `./mvnw verify` en verde, traer los últimos cambios de `main`, mergear la rama a `main`, hacer push
   y borrar la rama.
3. Un commit en `main` que diga `Closes #12` cierra el issue 12 automáticamente.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/es/) en español:
`tipo(alcance): qué cambia`, en imperativo y en minúscula.

- Tipos: `feat`, `fix`, `test`, `docs`, `refactor`, `build`, `chore`, `ci`.
- Alcances: `proceso`, `web`, `materiales`, `comunicaciones`, `infra`, `docs`.
- Ejemplos: `feat(proceso): agregar listener de asignación por carga`, `fix(web): validar al menos un recurso`.

## Reglas del proyecto

- **Enunciado primero.** Si algo no está claro o hay que desviarse del enunciado, se documenta y se justifica en
  [docs/decisiones.md](docs/decisiones.md), en el mismo commit que el cambio.
- **Modelo BPMN: un solo editor a la vez.** Asignarse el issue antes de tocarlo, revisar el `git diff` (IDs, tildes,
  atributos `flowable:`) y correr `./mvnw -pl proceso verify`. Ver [docs/03-guia-flowable.md §2](docs/03-guia-flowable.md).
- **IDs de tareas, mensajes y variables del modelo:** la web y las apps los usan; cambiarlos rompe a otros, así que se
  avisa al grupo.
- **Migraciones de BD.** Una migración ya pusheada **no se edita**: se agrega `V<n+1>__descripcion.sql`.
- **Módulo `proceso` en Java 11** (lo exige la imagen del servidor). El resto, Java 21.
- **Sin secretos en el repo.** Las credenciales de prueba son públicas a propósito; las reales irían en `.env`
  (ignorado por git).

## Código limpio

- **Nombres:** el dominio en español (`Curso`, `Empresa`, `docenteAsignado`); los sufijos de patrones y frameworks en
  su término habitual (`Listener`, `Delegate`, `Controller`, `Service`, `Repository`). Nombres que expliquen la
  intención, sin abreviaturas.
- **Condiciones autodescriptivas:** extraer a variables o métodos con nombre
  (`boolean credencialesValidas = …; if (!credencialesValidas) …`). Nada de números mágicos: constantes con nombre.
- **Métodos cortos que hacen una sola cosa,** con un solo nivel de abstracción. Retorno temprano en vez de `if`
  anidados.
- **Comentarios:** explican el *por qué* (una restricción de Flowable, un requisito del enunciado), nunca el *qué*.
  Cada clase pública lleva una línea de Javadoc con su responsabilidad y, si aplica, la sección del enunciado. Nada de
  código comentado ni `TODO` sin issue.
- **SOLID aplicado acá:**
  - *Single Responsibility:* un listener o delegate hace una sola cosa. En la web, el controller maneja HTTP, el
    servicio el caso de uso y el repositorio el SQL.
  - *Open/Closed:* la variación se agrega sin tocar lo existente (un delegate configurable por campos; las
    comunicaciones de la app como un `enum`).
  - *Liskov:* los dobles de test cumplen el mismo contrato que la implementación real.
  - *Interface Segregation:* interfaces chicas, con lo que usa quien las consume.
  - *Dependency Inversion:* se depende de interfaces (repositorios, cliente de Flowable) inyectadas por constructor.
    El único lugar que conoce URLs y JSON de Flowable en la web es el paquete `flowable`.
- **Sin sobreingeniería:** no crear interfaces, fábricas ni configuraciones "por si acaso". Se agregan cuando aparece
  la segunda implementación o el segundo uso.
- **Tests:** el nombre describe el comportamiento (`conCincoCursosEnEjecucionLaSolicitudNoEsViableYFinaliza`) y la
  estructura es preparar → ejecutar → verificar.
- **Formato:** respetar `.editorconfig` (UTF-8, LF, 4 espacios; 2 en BPMN, DMN y YAML).
