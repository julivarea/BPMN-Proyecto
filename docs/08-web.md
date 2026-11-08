# 08 · Sitio web

Cómo está armado el sitio de la institución (§4.1) y cómo se le agregan funcionalidades. Las tecnologías y el porqué
están en [02-arquitectura.md](02-arquitectura.md) §2; la API REST de Flowable, en
[03-guia-flowable.md](03-guia-flowable.md) §3.

## 1. Levantarla

Con el entorno Docker levantado ([04-entorno-desarrollo.md](04-entorno-desarrollo.md)):

```bash
./mvnw -pl web spring-boot:run      # http://localhost:8081 · se corta con Ctrl+C
```

- Necesita **PostgreSQL**, porque al arrancar aplica las migraciones de la base `institucion`, y **Flowable**, para
  todo lo que use el proceso.
- Las páginas y el CSS se cargan al arrancar: después de cambiarlos, reiniciar la web.

## 2. Organización

Paquetes por funcionalidad ([02-arquitectura.md](02-arquitectura.md) §5). Lo que existe hoy:

```
web/src/main/
├── java/ar/edu/unrc/gtpn/web/
│   ├── WebApplication.java       arranque
│   ├── Secciones.java            URL → página de inicio de cada sección
│   └── flowable/                 cliente REST de Flowable (§4)
└── resources/
    ├── application.yml           puerto, base de la institución y conexión con Flowable
    ├── db/migration/             migraciones Flyway (§5)
    ├── static/css/estilos.css    estilos de todo el sitio
    └── templates/
        ├── fragmentos.html       partes comunes de las páginas (§3)
        ├── index.html            sección pública
        ├── empresa/  administracion/  docentes/   una carpeta por sección
        └── error.html            página de error
```

Cada funcionalidad nueva va en su paquete (`solicitud/`, `propuesta/`, `seguridad/`…) con su controller, su servicio
y, si accede a la base, su repositorio. Sus páginas van en `templates/<funcionalidad>/`.

## 3. Páginas

**Secciones.** El sitio tiene cuatro: pública (`/`), empresas (`/empresas`), administración (`/administracion`) y
docentes (`/docentes`). `Secciones.java` asocia cada URL con su página de inicio. Cuando una sección tenga su propio
controller, se saca su línea de ahí.

**Fragmentos.** Toda página arranca así, con las partes comunes de `fragmentos.html`:

```html
<!DOCTYPE html>
<html lang="es" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragmentos :: cabecera('Mis tareas')}"></head>
<body>
<header th:replace="~{fragmentos :: encabezado('administracion')}"></header>
<main>
    <div th:replace="~{fragmentos :: mensajes}"></div>
    <h1>Mis tareas</h1>
    …
</main>
</body>
</html>
```

| Fragmento | Qué hace |
|-----------|----------|
| `cabecera(titulo)` | `<head>`: título de la pestaña y hoja de estilos |
| `encabezado(seccion)` | Barra superior con la navegación. `seccion` (`inicio`, `empresas`, `administracion`, `docentes` o `''`) marca el enlace de la página actual |
| `mensajes` | Muestra los atributos `mensaje` (éxito) y `error` del modelo, si existen |

**Mensajes al usuario.** Después de un formulario se hace *redirect* (para que recargar la página no lo reenvíe) y el
resultado viaja como *flash attribute*:

```java
@PostMapping("/administracion/tareas/{id}/completar")
String completar(@PathVariable String id, RedirectAttributes redirect) {
    …
    redirect.addFlashAttribute("mensaje", "Tarea completada");
    return "redirect:/administracion";
}
```

**Errores.** Cualquier error no manejado (404, 500…) muestra `error.html` con el diseño del sitio.

**Estilos.** Todo está en `estilos.css`. Los colores son variables al principio del archivo (`--encabezado`,
`--texto`, `--fondo`), y en pantallas chicas la navegación pasa a una grilla de 2 × 2.

## 4. Conexión con Flowable

### El cliente

`flowable/ClienteFlowable` es el **único lugar de la web que conoce las URLs y el JSON de Flowable**. El resto del
código usa sus métodos, con nombres del dominio, y recibe objetos propios (`Tarea`, mapas de variables). Si hace
falta algo nuevo de Flowable, se agrega un método acá (§6), no una llamada HTTP en otro lado.

| Método | Llamada REST | Para qué |
|--------|--------------|----------|
| `iniciarPorMensaje(mensaje, businessKey, variables)` | `POST /runtime/process-instances` | Formulario de solicitud: inicia la instancia y devuelve su ID |
| `tareasAsignadas(usuario)` | `GET /runtime/tasks?assignee=…` | Tareas del usuario |
| `tareasDisponibles(usuario)` | `GET /runtime/tasks?candidateUser=…` | Tareas de sus grupos que puede reclamar |
| `reclamar(tareaId, usuario)` | `POST /runtime/tasks/{id}` (`claim`) | Asignarse una tarea de grupo |
| `completar(tareaId, variables)` | `POST /runtime/tasks/{id}` (`complete`) | Completar una tarea con sus datos |
| `variables(instanciaId)` | `GET /runtime/process-instances/{id}/variables` | Leer los datos de una instancia |

- **Variables:** se pasan y se reciben como `Map<String, Object>`. Flowable deduce el tipo del valor JSON (texto,
  número o booleano). Las fechas van como texto ISO-8601 (`2026-12-01T09:00:00`). Los nombres son los de la guía 06
  §2.
- **Resultados:** las listas piden hasta 500 elementos, porque Flowable devuelve 10 por defecto.
- **Errores:** si Flowable responde con un error (4xx o 5xx), el cliente lanza `RestClientResponseException`. El
  controller la atrapa cuando el usuario puede hacer algo al respecto y muestra un `error`; si no, termina en la página
  de error.

### Credenciales

El cliente usa una **cuenta técnica**, configurada en `application.yml`:

| Propiedad | Variable de entorno | Valor de desarrollo |
|-----------|---------------------|---------------------|
| `flowable.url` | `FLOWABLE_URL` (sin `/process-api`) | `http://localhost:8080/flowable-ui` |
| `flowable.usuario` | `FLOWABLE_USUARIO` | `admin` |
| `flowable.clave` | `FLOWABLE_CLAVE` | `test` |

El login del personal (#6) es otra cosa: valida usuario y contraseña con una llamada a Flowable hecha con esas
credenciales ([02-arquitectura.md](02-arquitectura.md) §4), pero las operaciones las sigue haciendo el cliente con la
cuenta técnica.

### Reclamar y completar tareas

Como todas las llamadas usan la cuenta técnica, **Flowable no sabe qué persona está usando la web**, y además no impide
completar una tarea ajena ([05-decisiones.md](05-decisiones.md), limitación 7). Por eso la web aplica las reglas del
§2 (A-06) antes de llamar al cliente:

1. **Tarea asignada:** solo la completa su asignado (`tarea.asignado()` igual al usuario logueado).
2. **Tarea de grupo:** primero se reclama (`reclamar(tarea, usuario)`) y recién entonces se completa.
3. **Nunca se completa una tarea sin asignado.** Lo que registra a la persona en el historial es la asignación, y de
   eso dependen las consultas de tareas por persona (§4.3.3).

Estas verificaciones van en un solo servicio, el de la bandeja de tareas (#7), y las demás pantallas lo usan.

## 5. Base de la institución

- **Esquema:** lo crea Flyway con las migraciones de `db/migration` al arrancar la web. Qué guarda cada tabla y quién
  escribe cada dato: [07-base-de-datos.md](07-base-de-datos.md).
- **Una migración pusheada no se edita.** Los cambios van en una nueva: `V2__descripcion.sql`, `V3__…`.
- **Acceso:** con `JdbcClient` y SQL explícito, desde el repositorio de cada funcionalidad.
- **Vínculo con el proceso:** el curso se guarda antes de iniciar la instancia. La instancia se inicia con
  `businessKey = curso.id` y su ID se guarda en `curso.process_instance_id`.

## 6. Tests

| Test | Qué prueba | Cómo se corre |
|------|------------|---------------|
| `ClienteFlowableTest` | Que cada método del cliente arme bien la llamada y lea bien la respuesta, contra un servidor HTTP simulado (`MockRestServiceServer`) | `./mvnw -pl web verify` (también en CI) |
| `ClienteFlowableContraDockerTest` | Que el cliente funcione contra el Flowable real: lista las tareas de `jefe_admin` | `FLOWABLE_DOCKER=si ./mvnw -pl web verify`, con el entorno levantado |

Al agregar un método al cliente, se agrega su test en `ClienteFlowableTest`: qué URL y qué JSON se esperan, y qué se
devuelve con una respuesta de ejemplo. El formato de cada llamada está en [03-guia-flowable.md](03-guia-flowable.md)
§3.

## 7. Lo que falta

Las funcionalidades se construyen sobre esta base, en los issues de cada milestone:

| Issue | Qué agrega |
|-------|------------|
| #6 | Login del personal contra Flowable y roles por grupo (paquete `seguridad/`) |
| #7 | Bandeja de tareas con las reglas de §4 |
| #12 | Formulario público de solicitud: guarda empresa y curso e inicia la instancia |
| #13, #14 | Registrar y revisar la propuesta, con su PDF |
| #15, #21, #22 | Archivar solicitud, evaluar recursos y confeccionar la lista definitiva |
| #26, #27 | Tareas docentes, informe y cierre del expediente |
| #28, #29 | Login de empresas y portal con sus cursos y su estado |
