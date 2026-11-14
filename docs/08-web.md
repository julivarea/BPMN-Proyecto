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

Paquetes por funcionalidad ([02-arquitectura.md](02-arquitectura.md) §5): cada paquete agrupa el controller, el
servicio y, si accede a la base, el repositorio de una funcionalidad, y sus páginas van en `templates/<funcionalidad>/`.
Todos los archivos del módulo:

```
web/
├── pom.xml                                Spring Boot 4.1: web, Thymeleaf, seguridad, cliente REST, JDBC, Flyway, validación
└── src/
    ├── main/
    │   ├── java/ar/edu/unrc/gtpn/web/
    │   │   ├── WebApplication.java        arranque de Spring Boot; lee las propiedades de application.yml
    │   │   ├── Secciones.java             URL → página de las secciones sin controller propio: /, /empresas, /login (§3)
    │   │   ├── flowable/                  conexión con Flowable (§4)
    │   │   │   ├── ClienteFlowable.java       único lugar que llama a la API REST de Flowable
    │   │   │   ├── FlowableProperties.java    URL y cuenta técnica (propiedades flowable.* de application.yml)
    │   │   │   └── Tarea.java                 una tarea pendiente: nombre, clave, asignado, fecha y solicitud
    │   │   ├── propuestas/                propuesta económica del curso y su PDF (§3, §6)
    │   │   │   ├── Propuesta.java             una versión de la propuesta: programa, presupuesto y fechas
    │   │   │   ├── PropuestasRepository.java  última versión de un curso y búsqueda por curso y versión
    │   │   │   └── GeneradorPdfPropuesta.java el PDF de una propuesta (OpenPDF)
    │   │   ├── seguridad/                 login del personal y reglas de acceso (§5)
    │   │   │   ├── AutenticacionContraFlowable.java   valida usuario y contraseña contra Flowable; grupos → roles
    │   │   │   └── SeguridadConfig.java               qué rol requiere cada ruta, página de login y salida
    │   │   ├── solicitudes/               formulario público de solicitud de capacitación (§3, §6)
    │   │   │   ├── SolicitudController.java       página /solicitar: valida, registra y redirige a la confirmación
    │   │   │   ├── SolicitudFormulario.java       datos del formulario y sus reglas de validación
    │   │   │   ├── SolicitudesDeCapacitacion.java registra empresa y curso e inicia la instancia, en una transacción
    │   │   │   ├── SolicitudesRepository.java     SQL de empresa y curso, y la descripción de cada solicitud
    │   │   │   ├── SolicitudesProperties.java     plazo de respuesta (propiedad solicitudes.plazo-respuesta)
    │   │   │   ├── Empresa.java                   empresa ya registrada: id y razón social
    │   │   │   ├── Curso.java                     curso ya registrado: los datos de la solicitud (§6)
    │   │   │   ├── CuitDeOtraEmpresa.java         el CUIT ya existe con otra razón social (05-decisiones)
    │   │   │   └── SolicitudNoRegistrada.java     Flowable no inició la instancia: no se guarda nada
    │   │   └── tareas/                    bandeja de tareas (§4)
    │   │       ├── BandejaDeTareas.java       reglas para reclamar y completar: toda pantalla de tareas pasa por acá
    │   │       ├── BandejaController.java     páginas de Administración y Docentes, y botones Reclamar y Completar
    │   │       ├── ElaborarPropuestaController.java  pantalla /tareas/{id}/elaborar-propuesta: formulario, guardado y PDF
    │   │       ├── RevisarPropuestaController.java   pantalla /tareas/{id}/revisar-propuesta: la propuesta y su decisión
    │   │       └── AccionNoPermitida.java     regla no cumplida, con el mensaje que ve el usuario
    │   └── resources/
    │       ├── application.yml            puerto 8081, base de la institución, conexión con Flowable y plazo de respuesta
    │       ├── db/migration/
    │       │   └── V1__esquema_inicial.sql    tablas empresa, cuenta_empresa, curso y propuesta (§6)
    │       ├── static/css/
    │       │   └── estilos.css            estilos de todo el sitio: colores, encabezado, tablas, formularios (§3)
    │       └── templates/                 páginas Thymeleaf
    │           ├── fragmentos.html        partes comunes: cabecera, encabezado con navegación y sesión, mensajes (§3)
    │           ├── index.html             inicio (sección pública)
    │           ├── login.html             login del personal (§5)
    │           ├── error.html             página de error (404, 403, 500…)
    │           ├── empresa/
    │           │   └── inicio.html        sección de empresas (se completa en #28 y #29)
    │           ├── solicitudes/
    │           │   ├── formulario.html    formulario público de solicitud (§3)
    │           │   └── enviada.html       confirmación de la solicitud
    │           └── tareas/
    │               ├── bandeja.html       bandeja: tareas asignadas y disponibles, con la empresa y el curso (§4)
    │               ├── elaborar-propuesta.html  datos del curso, formulario de la propuesta y enlace a su PDF
    │               └── revisar-propuesta.html   la última propuesta con su PDF, y la decisión del administrativo
    └── test/java/ar/edu/unrc/gtpn/web/    tests (§7)
        ├── flowable/
        │   ├── ClienteFlowableTest.java               cada llamada del cliente, contra un servidor HTTP simulado
        │   └── ClienteFlowableContraDockerTest.java   el cliente contra el Flowable real (solo con FLOWABLE_DOCKER=si)
        ├── seguridad/
        │   ├── AutenticacionContraFlowableTest.java   grupos → roles y credenciales incorrectas
        │   └── SeguridadTest.java                     reglas de acceso de las secciones y login
        ├── solicitudes/
        │   ├── SolicitudesDeCapacitacionTest.java     registro, CUIT, razón social distinta, Flowable caído y descripciones
        │   └── SolicitudControllerTest.java           el formulario desde el navegador: validación, errores y CSRF
        └── tareas/
            ├── BandejaDeTareasTest.java               reglas para reclamar y completar
            ├── BandejaControllerTest.java             la bandeja desde el navegador
            ├── ElaborarPropuestaControllerTest.java   la pantalla de propuesta: formulario, guardado, PDF y permisos
            └── RevisarPropuestaControllerTest.java    la pantalla de revisión: formulario y decisión del administrativo
```

## 3. Páginas

**Secciones.** El sitio tiene cuatro: pública (`/`), empresas (`/empresas`), administración (`/administracion`) y
docentes (`/docentes`). Administración y Docentes muestran la bandeja de tareas del usuario (§4), que es lo que hace
el personal en el sitio. Las otras dos las asocia `Secciones.java` con su página; cuando una tenga su propio
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
@PostMapping("/tareas/{id}/evaluar-recursos")
String evaluarRecursos(@PathVariable String id, @RequestParam String seccion, Principal usuario, …,
        RedirectAttributes redirect) {
    try {
        bandeja.completar(usuario.getName(), id, Map.of("requiereAula", …));
        redirect.addFlashAttribute("mensaje", "Completaste la tarea.");
    } catch (AccionNoPermitida error) {
        redirect.addFlashAttribute("error", error.getMessage());
    }
    return "redirect:/" + seccion; // validada contra las secciones conocidas, como en BandejaController
}
```

**Pantallas de tarea.** Cada tarea que carga datos se completa en su **propia pantalla** (no en un popup sobre la
bandeja). Todas siguen la misma convención:

1. **Ruta:** `/tareas/{id}/<tarea>` (por ejemplo `/tareas/{id}/evaluar-recursos`): el `GET` muestra el formulario y el
   `POST` completa. Están bajo `/tareas/**`, así que ya requieren un rol del personal (§5).
2. **Se abre desde la bandeja:** en lugar del *Completar* genérico, la fila de esa tarea muestra un botón que lleva a
   su pantalla. La bandeja sabe qué tareas tienen pantalla por su clave (`tarea.clave()`, por ejemplo
   `evaluarRecursos`), y le pasa la sección de origen en el parámetro `seccion`, como a los demás botones.
3. **Botón para volver:** arriba a la izquierda, "← Volver a la bandeja", que lleva a la sección de origen
   (`/administracion` o `/docentes`).
4. **Antes de mostrar el formulario** se verifica que la tarea sea del usuario logueado. Si no, se vuelve a la bandeja
   con el error.
5. **Al enviar**, completa con `bandeja.completar(usuario, tarea, variables)`, nunca con el cliente directo, y vuelve a
   la sección de origen con el mensaje, como en el ejemplo anterior.

Las tareas que no cargan datos (*Archivar solicitud*, *Desarrollar curso*) no necesitan pantalla: alcanza el
*Completar* de la bandeja. *Solicitar aula virtual*, *Contratar equipamiento* y *Liberar equipamiento* pueden hacerse
desde la UI de Flowable (§4.1.3); sus pantallas son opcionales (#40, #41, #42).

**Errores.** Cualquier error no manejado (404, 500…) muestra `error.html` con el diseño del sitio.

**Estilos.** Todo está en `estilos.css`. Los colores son variables al principio del archivo (`--encabezado`,
`--texto`, `--fondo`), y en pantallas chicas la navegación pasa a una grilla de 2 × 2.

**Formulario de solicitud.** `/solicitar` es pública. Valida los datos en el servidor (`SolicitudFormulario`) y, si hay
errores, vuelve a mostrar el formulario con lo que se cargó y el error junto a cada campo. Si los datos son válidos,
registra la solicitud (§6) y redirige a `/solicitar/enviada`, así recargar la confirmación no repite el envío.

## 4. Conexión con Flowable

### El cliente

`flowable/ClienteFlowable` es el **único lugar de la web que conoce las URLs y el JSON de Flowable**. El resto del
código usa sus métodos, con nombres del dominio, y recibe objetos propios (`Tarea`, mapas de variables). Si hace
falta algo nuevo de Flowable, se agrega un método acá (§7), no una llamada HTTP en otro lado.

| Método | Llamada REST | Para qué |
|--------|--------------|----------|
| `gruposDelUsuario(usuario, clave)` | `GET /identity/groups?member=…`, con las credenciales del usuario | Login (§5): valida la contraseña y devuelve los grupos |
| `iniciarPorMensaje(mensaje, businessKey, variables)` | `POST /runtime/process-instances` | Formulario de solicitud: inicia la instancia y devuelve su ID |
| `tareasAsignadas(usuario)` | `GET /runtime/tasks?assignee=…` | Tareas del usuario |
| `tareasDisponibles(usuario)` | `GET /runtime/tasks?candidateUser=…` | Tareas de sus grupos que puede reclamar |
| `tarea(tareaId)` | `GET /runtime/tasks/{id}` | Leer una tarea para verificar a quién está asignada |
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

La única llamada que no usa la cuenta técnica es la del login (§5), que se hace con las credenciales del usuario para
validarlas. Todas las operaciones las hace el cliente con la cuenta técnica.

### Reclamar y completar tareas

Como todas las llamadas usan la cuenta técnica, **Flowable no sabe qué persona está usando la web**, y además no impide
completar una tarea ajena ([05-decisiones.md](05-decisiones.md), limitación 7). Por eso las reglas del §2 (A-06) las
aplica la web, en un solo lugar: **`tareas/BandejaDeTareas`**.

| Método | Regla |
|--------|-------|
| `reclamar(usuario, tareaId)` | Solo una tarea disponible para los grupos del usuario. Si otro la reclamó antes (Flowable responde 409), lo informa |
| `completar(usuario, tareaId, variables)` | Solo el asignado completa su tarea: una tarea de grupo se reclama antes. *Preparar materiales* no se completa desde la web (se hace en la app de materiales, §4.2.1) |

Si una regla no se cumple, el servicio lanza `AccionNoPermitida` con un mensaje para el usuario, y no llama a Flowable.
**Toda pantalla que reclame o complete una tarea lo hace a través de `BandejaDeTareas`**, nunca llamando a
`ClienteFlowable` directamente: así nadie puede completar una tarea ajena, aunque arme la URL a mano. Lo que registra a
la persona en el historial es la asignación, y de eso dependen las consultas de tareas por persona (§4.3.3).

**La bandeja** es la página de las secciones de Administración (`/administracion`) y de Docentes (`/docentes`): muestra
las tareas asignadas al usuario, con *Completar*, y las disponibles para sus grupos, con *Reclamar*. Cada tarea indica
la **solicitud** a la que pertenece: la empresa y el curso, buscados en la base con una sola consulta por la
`businessKey` de su instancia (el ID del curso), o la `businessKey` si la instancia no tiene curso en la base. *Preparar materiales*
aparece sin botones, con la indicación de la app de materiales. Los botones envían a `/tareas/{id}/reclamar` y
`/tareas/{id}/completar` y vuelven a la sección de la que se vino.

**Tareas que cargan datos.** El *Completar* de la bandeja no envía variables. Una tarea que necesita datos para que el
proceso siga (por ejemplo *Evaluar recursos*) tiene su propia pantalla, según la convención de §3 ("Pantallas de
tarea"). Mientras esa pantalla no exista, si se completa sin datos, Flowable lo rechaza y la bandeja lo informa con un
mensaje.

## 5. Login del personal

El personal entra con su **usuario y contraseña de Flowable** (§3.3): la base de la institución no guarda cuentas del
personal ([02-arquitectura.md](02-arquitectura.md) §4).

**Cómo funciona.** El formulario de `/login` lo procesa Spring Security, que le pasa el usuario y la contraseña a
`seguridad/AutenticacionContraFlowable`. Esa clase le pide a Flowable los grupos del usuario **con sus propias
credenciales** (`gruposDelUsuario`), así la misma llamada valida la contraseña:

| Respuesta de Flowable | Resultado |
|-----------------------|-----------|
| 200 con los grupos | Ingresa. Cada grupo se convierte en un rol: `administracion` → `ADMINISTRACION`, `docentes` → `DOCENTES` |
| 401 (contraseña incorrecta) o 403 (sin acceso a la API) | Vuelve a `/login?error` |
| Sin respuesta (Flowable caído) | Vuelve a `/login?error` |

**Reglas de acceso** (`seguridad/SeguridadConfig`):

| Ruta | Requiere |
|------|----------|
| `/administracion/**` | Rol `ADMINISTRACION` |
| `/docentes/**` | Rol `DOCENTES` |
| `/tareas/**` (reclamar y completar) | Rol `ADMINISTRACION` o `DOCENTES` |
| Todo lo demás | Nada (la sección de empresas tendrá su propio login, #28) |

- Sin sesión iniciada, una sección protegida redirige a `/login`, y después del login vuelve a la página pedida.
- Con sesión pero sin el rol, muestra la página de error con un 403 ("Tu usuario no tiene acceso a esta sección").
- Una sección o ruta nueva que requiera rol se agrega con una línea en `SeguridadConfig`.

**En las páginas.** El encabezado muestra el usuario y el botón *Salir* cuando hay sesión, o *Ingresar* cuando no
(atributos `sec:` de Thymeleaf). *Salir* es un formulario `POST /logout`.

**En los controllers.** El usuario logueado se recibe como parámetro: `Principal usuario` y `usuario.getName()`. Es el
que hay que comparar con el asignado de la tarea (§4).

**Formularios.** Spring Security exige un token CSRF en todo `POST`. Los formularios con `th:action` lo agregan solos;
un formulario sin `th:action` es rechazado con un 403.

## 6. Base de la institución

- **Esquema:** lo crea Flyway con las migraciones de `db/migration` al arrancar la web. Qué guarda cada tabla y quién
  escribe cada dato: [07-base-de-datos.md](07-base-de-datos.md).
- **Una migración pusheada no se edita.** Los cambios van en una nueva: `V2__descripcion.sql`, `V3__…`.
- **Acceso:** con `JdbcClient` y SQL explícito, desde el repositorio de cada funcionalidad.
- **Vínculo con el proceso:** el curso se guarda antes de iniciar la instancia. La instancia se inicia con
  `businessKey = curso.id` y su ID se guarda en `curso.process_instance_id`. Las tres operaciones van en una
  transacción (`SolicitudesDeCapacitacion`): si Flowable falla, no queda el curso sin instancia (ver 05-decisiones).

## 7. Tests

| Test | Qué prueba | Cómo se corre |
|------|------------|---------------|
| `ClienteFlowableTest` | Que cada método del cliente arme bien la llamada y lea bien la respuesta, contra un servidor HTTP simulado (`MockRestServiceServer`) | `./mvnw -pl web verify` (también en CI) |
| `AutenticacionContraFlowableTest` | Que el login convierta los grupos en roles y rechace las credenciales incorrectas, con un doble del cliente | `./mvnw -pl web verify` |
| `SeguridadTest` | Las reglas de acceso: sin sesión pide login, un docente no entra a administración, el login con credenciales válidas e inválidas, sin Flowable (`@WebMvcTest` con un doble del cliente) | `./mvnw -pl web verify` |
| `BandejaDeTareasTest` | Las reglas: nadie completa una tarea ajena ni una de grupo sin reclamar, no se reclama lo que no está disponible, *Preparar materiales* no se completa desde la web, y los conflictos y rechazos de Flowable se informan | `./mvnw -pl web verify` |
| `BandejaControllerTest` | La bandeja desde el navegador: secciones de Administración y Docentes, empresa y curso de cada solicitud, completar una tarea ajena armando la URL, reclamar, vuelta a la sección, token CSRF y login (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `ElaborarPropuestaControllerTest` | La pantalla de la propuesta desde el navegador: el asignado abre el formulario, guardar completa la tarea y registra la versión, si Flowable rechaza no se guarda nada, enlace al PDF de la versión anterior y descarga del PDF (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `RevisarPropuestaControllerTest` | La pantalla de revisión desde el navegador: el asignado ve la última propuesta con su PDF y la decisión (aceptar, pedir modificaciones o rechazar) completa la tarea (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `SolicitudesDeCapacitacionTest` | El registro: CUIT guardado solo con dígitos, empresa reutilizada aunque cambien mayúsculas y espacios, CUIT con otra razón social rechazado, instancia iniciada con el curso como `businessKey`, Flowable sin responder y descripciones de la bandeja, con dobles del repositorio y del cliente | `./mvnw -pl web verify` |
| `SolicitudControllerTest` | El formulario desde el navegador: es público, una solicitud válida redirige a la confirmación, los datos inválidos y la razón social distinta vuelven al formulario con el error, Flowable caído se informa y token CSRF (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `ClienteFlowableContraDockerTest` | Que el cliente funcione contra el Flowable real: lista las tareas de `jefe_admin` | `FLOWABLE_DOCKER=si ./mvnw -pl web verify`, con el entorno levantado |

Al agregar un método al cliente, se agrega su test en `ClienteFlowableTest`: qué URL y qué JSON se esperan, y qué se
devuelve con una respuesta de ejemplo. El formato de cada llamada está en [03-guia-flowable.md](03-guia-flowable.md)
§3.

## 8. Lo que falta

Las funcionalidades se construyen sobre esta base, en los issues de cada milestone. **Cerrados: #13 y #14**,
registrar y elaborar la propuesta desde la web (`ElaborarPropuestaController`) y revisarla con su PDF
(`RevisarPropuestaController`), ambos en §3 y §7.

| Issue | Qué agrega |
|-------|------------|
| #15, #21, #22 | Archivar solicitud, evaluar recursos y confeccionar la lista definitiva |
| #26, #27 | Tareas docentes, informe y cierre del expediente |
| #28, #29 | Login de empresas (un segundo proveedor de autenticación, contra la base) y portal con sus cursos y su estado |
