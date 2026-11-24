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
    │   │   ├── InicioController.java      la raíz (/) lleva a la pantalla de trabajo de cada usuario; /login con sesión vuelve a la raíz (§3)
    │   │   ├── BarraFinalFilter.java      redirige /solicitar/ a /solicitar (Spring MVC ya no reconoce la barra final) (§3)
    │   │   ├── empresa/                   portal de empresas: sus cursos y su estado (§3, §5)
    │   │   │   ├── PortalEmpresaController.java       páginas /empresas y /empresas/cursos/{id}: listado y ficha
    │   │   │   ├── CursosDeLaEmpresaRepository.java   SQL de los cursos, siempre filtrado por empresa
    │   │   │   ├── CursoDeLaEmpresa.java              un curso con los datos que ve la empresa
    │   │   │   ├── CursoConEstado.java                el curso con su estado y las actividades en curso
    │   │   │   └── EstadoDelCurso.java                estados del portal y su derivación de Flowable
    │   │   ├── flowable/                  conexión con Flowable (§4)
    │   │   │   ├── ClienteFlowable.java       único lugar que llama a la API REST de Flowable
    │   │   │   ├── FlowableProperties.java    URL y cuenta técnica (propiedades flowable.* de application.yml)
    │   │   │   ├── Actividad.java             una actividad en curso: ID, nombre y tipo BPMN
    │   │   │   └── Tarea.java                 una tarea pendiente: nombre, clave, asignado, fecha y solicitud
    │   │   ├── propuestas/                propuesta económica del curso y su PDF (§3, §6)
    │   │   │   ├── Propuesta.java             una versión de la propuesta: programa, presupuesto y fechas
    │   │   │   ├── PropuestasRepository.java  última versión de un curso y búsqueda por curso y versión
    │   │   │   └── GeneradorPdfPropuesta.java el PDF de una propuesta (OpenPDF)
    │   │   ├── seguridad/                 login del personal y de las empresas, y reglas de acceso (§5)
    │   │   │   ├── AutenticacionContraFlowable.java   valida usuario y contraseña del personal contra Flowable; grupos → roles
    │   │   │   ├── AutenticacionEmpresa.java          valida a las empresas contra cuenta_empresa (BCrypt); rol EMPRESA
    │   │   │   ├── CuentaEmpresa.java                 una cuenta de empresa: empresa, usuario y hash de la clave
    │   │   │   ├── CuentaEmpresaRepository.java       consulta de cuentas por usuario (interfaz)
    │   │   │   ├── CuentaEmpresaRepositoryJdbc.java   la consulta, con JdbcClient
    │   │   │   ├── ReglasDeAcceso.java                qué rol requiere cada ruta: la única definición
    │   │   │   ├── IngresoExitoso.java                adónde va cada usuario después de ingresar
    │   │   │   └── SeguridadConfig.java               aplica las reglas, página de login, salida y sesión vencida
    │   │   ├── solicitudes/               formulario público de solicitud de capacitación (§3, §6)
    │   │   │   ├── SolicitudController.java       página /solicitar: valida, registra y redirige a la confirmación (o vuelve con un aviso si no es viable)
    │   │   │   ├── SolicitudFormulario.java       datos del formulario y sus reglas de validación
    │   │   │   ├── SolicitudesDeCapacitacion.java registra empresa y curso, inicia la instancia e informa si es viable
    │   │   │   ├── SolicitudesRepository.java     SQL de empresa y curso, y la descripción de cada solicitud
    │   │   │   ├── SolicitudesProperties.java     plazo de respuesta (propiedad solicitudes.plazo-respuesta)
    │   │   │   ├── Empresa.java                   empresa ya registrada: id, CUIT y razón social
    │   │   │   ├── Curso.java                     curso ya registrado: los datos de la solicitud (§6)
    │   │   │   ├── CuitDeOtraEmpresa.java         el CUIT ya existe con otra razón social (05-decisiones)
    │   │   │   ├── SolicitudRegistrada.java       el resultado del registro: curso e instancia, y si es viable
    │   │   │   └── SolicitudNoRegistrada.java     Flowable no inició la instancia: no se guarda nada
    │   │   └── tareas/                    bandeja de tareas (§4)
    │   │       ├── ArchivarSolicitudController.java  pantalla /tareas/{id}/archivar-solicitud: motivo y confirmación
    │   │       ├── BandejaDeTareas.java       reglas para reclamar y completar: toda pantalla de tareas pasa por acá
    │   │       ├── PantallasDeTareas.java     lo común de las pantallas: sección de retorno, tarea verificada y curso
    │   │       ├── RegistroDePropuestas.java  guarda la versión de la propuesta y completa la tarea como una sola operación
    │   │       ├── BandejaController.java     páginas de Administración y Docentes, y botones Reclamar y Completar
    │   │       ├── ConfeccionarListaDefinitivaController.java  pantalla /tareas/{id}/confeccionar-lista-definitiva: la nómina editable
    │   │       ├── ElaborarPropuestaController.java  pantalla /tareas/{id}/elaborar-propuesta: formulario, guardado y PDF
    │   │       ├── EvaluarRecursosController.java    pantalla /tareas/{id}/evaluar-recursos: recursos que necesita el curso
    │   │       ├── RevisarPropuestaController.java   pantalla /tareas/{id}/revisar-propuesta: las observaciones de la empresa y la nueva versión
    │   │       ├── DefinirDocenteController.java     pantalla /tareas/{id}/definir-docente: elige el docente del curso
    │   │       ├── ElaborarInformeController.java    pantalla /tareas/{id}/elaborar-informe: el informe de resultados
    │   │       ├── EnviarInformeController.java      pantalla /tareas/{id}/enviar-informe: consulta, envía y archiva
    │   │       └── AccionNoPermitida.java     regla no cumplida, con el mensaje que ve el usuario
    │   └── resources/
    │       ├── application.yml            puerto 8081, base de la institución, conexión con Flowable y plazo de respuesta
    │       ├── db/migration/
    │       │   └── V1__esquema_inicial.sql    tablas empresa, cuenta_empresa, curso y propuesta (§6)
    │       ├── static/css/
    │       │   └── estilos.css            estilos de todo el sitio: colores, encabezado, tablas, formularios (§3)
    │       └── templates/                 páginas Thymeleaf
    │           ├── fragmentos.html        partes comunes: cabecera, encabezado con navegación y sesión, mensajes (§3)
    │           ├── login.html             login del personal (§5)
    │           ├── error.html             página de error (404, 403, 500…)
    │           ├── empresa/
    │           │   ├── cursos.html         cursos de la empresa con fecha de solicitud y estado
    │           │   └── curso-detalle.html  ficha del curso y actividades paralelas de la preparación
    │           ├── solicitudes/
    │           │   ├── formulario.html    formulario público de solicitud (§3)
    │           │   └── enviada.html       confirmación de la solicitud
    │           └── tareas/
    │               ├── archivar-solicitud.html   la solicitud, el motivo del archivo y la confirmación
    │               ├── bandeja.html       bandeja: tareas asignadas y disponibles, con la empresa y el curso (§4)
    │               ├── confeccionar-lista-definitiva.html  datos del curso y la nómina editable de inscriptos
    │               ├── elaborar-propuesta.html  datos del curso, formulario de la propuesta y enlace a su PDF
    │               ├── evaluar-recursos.html   datos de la solicitud y las tres casillas de recursos
    │               ├── revisar-propuesta.html   la última propuesta con su PDF, las observaciones de la empresa y el formulario de la nueva versión
    │               ├── definir-docente.html     selección del docente que desarrollará el curso
    │               ├── elaborar-informe.html    datos del curso y el informe de resultados editable
    │               └── enviar-informe.html      datos del curso, el informe y la confirmación del archivo
    └── test/java/ar/edu/unrc/gtpn/web/    tests (§7)
        ├── empresa/
        │   └── PortalEmpresaControllerTest.java          aislamiento por empresa y estado derivado de Flowable
        ├── flowable/
        │   ├── ClienteFlowableTest.java               cada llamada del cliente, contra un servidor HTTP simulado
        │   └── ClienteFlowableContraDockerTest.java   el cliente contra el Flowable real (solo con FLOWABLE_DOCKER=si)
        ├── seguridad/
        │   ├── AutenticacionContraFlowableTest.java   grupos → roles y credenciales incorrectas
        │   ├── AutenticacionEmpresaTest.java          cuenta de empresa válida, inexistente y con clave incorrecta
        │   └── SeguridadTest.java                     reglas de acceso de las secciones y login
        ├── solicitudes/
        │   ├── SolicitudesDeCapacitacionTest.java     registro, CUIT, razón social distinta, Flowable caído y descripciones
        │   └── SolicitudControllerTest.java           el formulario desde el navegador: validación, errores y CSRF
        └── tareas/
            ├── ArchivarSolicitudControllerTest.java   la pantalla de archivo: formulario y confirmación
            ├── BandejaDeTareasTest.java               reglas para reclamar y completar
            ├── PantallasDeTareasTest.java             sección de retorno, verificación de la tarea y curso de la solicitud
            ├── RegistroDePropuestasTest.java          guardar y completar en una transacción: commit, rollback y orden
            ├── BandejaControllerTest.java             la bandeja desde el navegador
            ├── ConfeccionarListaDefinitivaControllerTest.java  la lista definitiva: nómina inicial, lista vacía y guardado
            ├── DefinirDocenteControllerTest.java      el jefe docente elige al docente del curso
            ├── ElaborarPropuestaControllerTest.java   la pantalla de propuesta: formulario, guardado, PDF y permisos
            ├── EvaluarRecursosControllerTest.java     la pantalla de recursos: formulario, al menos un recurso y confirmación
            ├── RevisarPropuestaControllerTest.java    la pantalla de revisión: observaciones de la empresa y nueva versión
            ├── ElaborarInformeControllerTest.java     el informe de resultados: formulario, texto obligatorio y guardado
            └── EnviarInformeControllerTest.java       el envío y archivo: informe, curso, confirmación y permisos
```

## 3. Páginas

**Secciones.** El sitio tiene cuatro: solicitud pública (`/solicitar`), empresas (`/empresas`), administración
(`/administracion`) y docentes (`/docentes`). Administración y Docentes muestran la bandeja de tareas del usuario (§4),
que es lo que hace el personal en el sitio. Empresas muestra el portal con sus cursos (`PortalEmpresaController`, §5).

**Entrada y direcciones.** No hay página de inicio: `/` (`InicioController`) lleva a cada usuario a su pantalla de
trabajo. Con la sesión iniciada: `/administracion`, `/docentes` o `/empresas`, según el rol (si tiene varios, gana ese
orden). Sin sesión: `/solicitar`. Es también adonde vuelve el sitio después de ingresar sin una página pedida, y adonde
va `/login` si ya hay sesión. Una dirección con barra final (`/solicitar/`) se redirige a la misma sin barra
(`BarraFinalFilter`; 301 en GET, 308 con otros métodos para no perder los datos).

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
| `encabezado(seccion)` | Barra superior con la navegación. `seccion` (`solicitar`, `empresas`, `administracion`, `docentes` o `''`) marca el enlace de la página actual. Enlaces: *Solicitar curso* (sin sesión o empresa), *Mis cursos* (empresa) y *Mis tareas* (personal) |
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
4. **Antes de mostrar el formulario y antes de completar** se verifica, con
   `PantallasDeTareas.tareaDelUsuario(id, clave, queEs, usuario)`, que la tarea exista, sea la de la pantalla (por su
   clave) y esté asignada al usuario logueado. Si no, se vuelve a la bandeja con el error. Esa misma clase resuelve la
   sección de retorno (`seccion(pedida, porDefecto)`: solo `administracion` o `docentes`) y el curso detrás de la
   tarea (`cursoDe(tarea)`), así que ninguna pantalla repite esa lógica.
5. **Al enviar**, completa con `bandeja.completar(usuario, tarea, variables)`, nunca con el cliente directo, y vuelve a
   la sección de origen con el mensaje, como en el ejemplo anterior.

*Desarrollar curso*, que no carga datos, no necesita pantalla: alcanza el *Completar* de la bandeja (*Archivar
solicitud* sí tiene la suya, de confirmación, porque muestra el motivo del archivo). *Confirmar aula disponible*
(falta de aulas, escenario 7) tampoco la necesita. *Solicitar aula virtual*, *Contratar equipamiento* y *Liberar
equipamiento* no tienen pantalla en el sitio: se completan con el *Completar* de la
bandeja, o desde la UI de Flowable (§4.1.3; ver la decisión en [05-decisiones.md](05-decisiones.md)).

**Errores.** Cualquier error no manejado (404, 500…) muestra `error.html` con el diseño del sitio. En las pantallas de
tarea, además de los mensajes de negocio de `AccionNoPermitida`, los fallos inesperados incluyen `e.getMessage()` en el
aviso para facilitar las pruebas y el diagnóstico en vivo durante la demostración sin depender de revisar los logs de la
consola; en un sistema en producción correspondería registrar el detalle técnico solo en el log del servidor y mostrar
un mensaje genérico al usuario (ver [05-decisiones.md](05-decisiones.md)).

**Estilos.** Todo está en `estilos.css`. Los colores son variables al principio del archivo (`--encabezado`,
`--texto`, `--fondo`), y en pantallas chicas la navegación pasa a una grilla de 2 × 2.

**Formulario de solicitud.** `/solicitar` es pública. Valida los datos en el servidor (`SolicitudFormulario`) y, si hay
errores, vuelve a mostrar el formulario con lo que se cargó y el error junto a cada campo. Si los datos son válidos,
registra la solicitud (§6) y redirige a `/solicitar/enviada`, así recargar la confirmación no repite el envío.

**Empresa con sesión.** Quien ingresó con la cuenta de una empresa ve CUIT y razón social cargados y bloqueados. 
`SolicitudController` resuelve la empresa de la sesión (`SolicitudesDeCapacitacion.empresaConSesion`) tanto al mostrar el 
formulario como al recibir el envío: con sesión, la solicitud va a su nombre y el CUIT y la razón social enviados no se 
validan ni se usan, así que no se puede solicitar a nombre de otra empresa armando el envío a mano. Si el envío tiene 
errores en otros campos, el formulario vuelve con esos dos campos bloqueados.

**Solicitud no viable.** Con cinco o más cursos en ejecución el proceso termina dentro de la misma llamada que lo
inicia (*Comunicar imposibilidad* solo escribe en el log del servidor). La respuesta de Flowable lo indica (`ended`), y
en ese caso `SolicitudController` no va a la confirmación: vuelve a `/solicitar` con el atributo flash `aviso`, que el
fragmento `mensajes` muestra en ámbar arriba del formulario. La solicitud igual queda registrada, con estado
`NO_VIABLE`.

## 4. Conexión con Flowable

### El cliente

`flowable/ClienteFlowable` es el **único lugar de la web que conoce las URLs y el JSON de Flowable**. El resto del
código usa sus métodos, con nombres del dominio, y recibe objetos propios (`Tarea`, mapas de variables). Si hace
falta algo nuevo de Flowable, se agrega un método acá (§7), no una llamada HTTP en otro lado.

| Método | Llamada REST | Para qué |
|--------|--------------|----------|
| `gruposDelUsuario(usuario, clave)` | `GET /identity/groups?member=…`, con las credenciales del usuario | Login (§5): valida la contraseña y devuelve los grupos |
| `iniciarPorMensaje(mensaje, businessKey, variables)` | `POST /runtime/process-instances` | Formulario de solicitud: inicia la instancia y devuelve su ID y si ya terminó (`ended`: el proceso no viable termina al iniciarse) |
| `tareasAsignadas(usuario)` | `GET /runtime/tasks?assignee=…` | Tareas del usuario |
| `tareasDisponibles(usuario)` | `GET /runtime/tasks?candidateUser=…` | Tareas de sus grupos que puede reclamar |
| `miembrosDelGrupo(grupo)` | `GET /identity/users?memberOfGroup=…` | Los docentes que puede elegir el jefe docente |
| `tarea(tareaId)` | `GET /runtime/tasks/{id}` | Leer una tarea para verificar a quién está asignada |
| `tareasDeLaInstancia(instanciaId)` | `GET /runtime/tasks?processInstanceId=…` | Las tareas abiertas de una instancia: para saber si *Confirmar compensaciones* todavía espera una liberación |
| `reclamar(tareaId, usuario)` | `POST /runtime/tasks/{id}` (`claim`) | Asignarse una tarea de grupo |
| `completar(tareaId, variables)` | `POST /runtime/tasks/{id}` (`complete`) | Completar una tarea con sus datos |
| `variables(instanciaId)` | `GET /runtime/process-instances/{id}/variables` | Leer los datos de una instancia |
| `actividadesActivas(instanciaId)` | `GET /history/historic-activity-instances?processInstanceId=…&finished=false` | Actividades en curso, incluidas las de las ramas paralelas (§3, portal de empresas) |

- **Variables:** se pasan y se reciben como `Map<String, Object>`. Flowable deduce el tipo del valor JSON (texto,
  número o booleano). Las fechas van como texto ISO-8601 (`2026-12-01T09:00:00`). Los nombres son los de la guía 06
  §2.
- **Resultados:** las listas piden hasta 500 elementos, porque Flowable devuelve 10 por defecto.
- **Errores:** si Flowable responde con un error (4xx o 5xx), el cliente lanza `RestClientResponseException`. El
  controller la atrapa cuando el usuario puede hacer algo al respecto y muestra un `error`; si no, termina en la página
  de error.

### El portal de empresas

`empresa/PortalEmpresaController` resuelve la empresa por el usuario logueado (`CuentaEmpresaRepository`) y consulta
solo sus cursos. El estado (`EstadoDelCurso`) sale de `curso.estado` cuando la instancia terminó; mientras sigue
activa se deriva de `actividadesActivas`:

| Actividades en curso | Estado que ve la empresa |
|----------------------|--------------------------|
| Subproceso *Preparar curso* y sus ramas | En preparación |
| *Enviar confirmación*, *Definir docente* o la espera de la fecha | Confirmado y pendiente de inicio |
| *Desarrollar curso* | En desarrollo |
| *Elaborar informe de resultados* o el cierre | En elaboración del informe |
| *Liberar aula*, *Liberar aula virtual* o *Liberar equipamiento* pendientes, ya cerrado el curso | Finalizando: liberando recursos |
| Ninguna conocida | En negociación |
| Subproceso de *Cancelación* (compensaciones, *Liberar equipamiento*, *Confirmar compensaciones*) | Cancelándose: liberando recursos |

*Liberar equipamiento* es una tarea de usuario: mientras esté pendiente la instancia sigue abierta, por eso el portal
muestra el cierre en curso. En "Finalizando" el informe de resultados ya se puede consultar. Una cancelación se muestra
como "Cancelándose" mientras el subproceso de cancelación espera las compensaciones. Al terminar la instancia el estado
pasa a `FINALIZADO` o `CANCELADO` (el que dejó el proceso en `curso.estado`).

En el detalle de un curso en preparación se listan las tareas y esperas de las ramas paralelas, sin compuertas ni el
subproceso contenedor. Si Flowable no responde, el portal muestra el estado persistido en vez de romper el listado.

**Aula asignada.** *Mis cursos* tiene una columna **Aula** y el detalle del curso una línea "Aula asignada", con el aula
física que reservó el proceso (variable `aulaAsignada` de la instancia). Se muestra mientras el curso está en marcha: en
preparación, confirmado, en desarrollo y en elaboración del informe. No se muestra si todavía no se reservó, ni durante el
cierre o la cancelación (el aula ya se liberó, aunque la variable siga guardada), ni en un curso terminado, que figura con
un guion en el listado. Si Flowable no responde, el portal sigue funcionando sin el aula.

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
| `completar(usuario, tareaId, variables)` | Solo el asignado completa su tarea: una tarea de grupo se reclama antes. *Preparar materiales* no se completa desde la web (se hace en la app de materiales, §4.2.1). *Confirmar compensaciones* no se completa mientras haya liberaciones pendientes en su instancia (05-decisiones, limitación 2) |
| `motivoDeBloqueo(tarea)` | Por qué una tarea todavía no se puede completar (hoy, solo *Confirmar compensaciones*), o `null`. La bandeja lo muestra en lugar del botón *Completar* |

Si una regla no se cumple, el servicio lanza `AccionNoPermitida` con un mensaje para el usuario, y no llama a Flowable.
**Toda pantalla que reclame o complete una tarea lo hace a través de `BandejaDeTareas`**, nunca llamando a
`ClienteFlowable` directamente: así nadie puede completar una tarea ajena, aunque arme la URL a mano. Lo que registra a
la persona en el historial es la asignación, y de eso dependen las consultas de tareas por persona (§4.3.3).

**La bandeja** es la página de las secciones de Administración (`/administracion`) y de Docentes (`/docentes`): muestra
las tareas asignadas al usuario, con *Completar*, y las disponibles para sus grupos, con *Reclamar*. Cada tarea indica
la **solicitud** a la que pertenece: la empresa y el curso, buscados en la base con una sola consulta por la
`businessKey` de su instancia (el ID del curso), o la `businessKey` si la instancia no tiene curso en la base. *Preparar materiales*
aparece sin botones, con la indicación de la app de materiales. *Confirmar compensaciones* aparece sin el botón
*Completar* y con la indicación de qué liberación falta, hasta que se completa. Los botones envían a `/tareas/{id}/reclamar` y
`/tareas/{id}/completar` y vuelven a la sección de la que se vino.

**Tareas que cargan datos.** El *Completar* de la bandeja no envía variables. Una tarea que necesita datos para que el
proceso siga tiene su propia pantalla, según la convención de §3 ("Pantallas de tarea"). *Evaluar recursos* la tiene
(`EvaluarRecursosController`): las tres casillas `requiereAula`, `requiereAulaVirtual` y `requiereEquipamiento`, con la
regla de que al menos una quede marcada, porque sin recursos el modelo no tiene por dónde seguir. *Confeccionar lista
definitiva* también (`ConfeccionarListaDefinitivaController`): parte de la nómina que la empresa mandó en
`participantes`, la deja editar en un área de texto y guarda `listaDefinitiva`, un participante por línea. *Elaborar
informe de resultados* (`ElaborarInformeController`) es el soporte opcional de W-11: el jefe docente carga
`informeResultados` desde el sitio. *Enviar informe y archivar expediente* (`EnviarInformeController`) muestra ese
informe y los datos del curso al jefe administrativo; al confirmar completa la tarea sin variables, porque el informe
ya está en el proceso, y el flujo libera los recursos y termina con `resultado = 'FINALIZADO'`. Si una tarea con datos
se completa sin ellos, Flowable lo rechaza y la bandeja lo informa con un mensaje.

## 5. Login del personal y de las empresas

El personal entra con su **usuario y contraseña de Flowable** (§3.3): la base de la institución no guarda cuentas del
personal ([02-arquitectura.md](02-arquitectura.md) §4). Las empresas entran por el mismo `/login` con la cuenta que les
crea el proceso (*Habilitar acceso a la empresa*): `seguridad/AutenticacionEmpresa` la busca en `cuenta_empresa`, compara la
clave con su hash BCrypt y les da el rol `EMPRESA`. Si el usuario no es una cuenta de empresa, sigue el login contra
Flowable.

**Cómo funciona.** El formulario de `/login` lo procesa Spring Security, que le pasa el usuario y la contraseña a
`seguridad/AutenticacionContraFlowable`. Esa clase le pide a Flowable los grupos del usuario **con sus propias
credenciales** (`gruposDelUsuario`), así la misma llamada valida la contraseña:

| Respuesta de Flowable | Resultado |
|-----------------------|-----------|
| 200 con los grupos | Ingresa. Cada grupo se convierte en un rol: `administracion` → `ADMINISTRACION`, `docentes` → `DOCENTES` |
| 401 (contraseña incorrecta) o 403 (sin acceso a la API) | Vuelve a `/login?error` |
| Sin respuesta (Flowable caído) | Vuelve a `/login?error` |

**Reglas de acceso** (`seguridad/ReglasDeAcceso`, que `SeguridadConfig` aplica):

| Ruta | Requiere |
|------|----------|
| `/administracion/**` | Rol `ADMINISTRACION` |
| `/docentes/**` | Rol `DOCENTES` |
| `/empresas`, `/empresas/**` | Rol `EMPRESA` (la cuenta se valida contra `cuenta_empresa`) |
| `/tareas/{id}/elaborar-informe` | Rol `DOCENTES` (lo hace el jefe docente) |
| `/tareas/{id}/enviar-informe` | Rol `ADMINISTRACION` (lo hace el jefe administrativo) |
| `/tareas/**` (reclamar y completar) | Rol `ADMINISTRACION` o `DOCENTES` |
| Todo lo demás | Nada (páginas públicas) |

- Sin sesión iniciada, una sección protegida redirige a `/login`, y después del login vuelve a la página pedida.
- Con sesión pero sin el rol, muestra la página de error con un 403 ("Tu usuario no tiene acceso a esta sección").
- Una sección o ruta nueva que requiera rol se agrega con una línea en `ReglasDeAcceso`: la misma regla la usa
  `IngresoExitoso` para no llevar a nadie, después de ingresar, a una página que no puede ver.

**En las páginas.** El encabezado muestra el usuario y el botón *Salir* cuando hay sesión, o *Ingresar* cuando no
(atributos `sec:` de Thymeleaf). *Salir* es un formulario `POST /logout`, pero `/logout` se acepta con GET o POST y sin
token CSRF, y vuelve a `/login?salida`: con un token viejo (por ejemplo, tras reiniciar el servidor) daba un 403.

**Sesión vencida.** Un formulario enviado sin sesión trae un token CSRF que ya no vale. En lugar de un 403, se vuelve
al formulario público (`/solicitar?expirada`) o al login (`/login?expirada`) con un aviso. Con sesión, un rechazo sigue
siendo un 403.

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
  `businessKey = curso.id` y su ID se guarda en `curso.process_instance_id`. El curso se **confirma antes** de iniciar la
  instancia (`SolicitudesDeCapacitacion`): una instancia que termina al iniciarse, como la no viable, actualiza el estado
  del curso desde el motor y el curso tiene que existir ya. Si Flowable no inicia la instancia, se elimina el curso
  recién creado.

## 7. Tests

| Test | Qué prueba | Cómo se corre |
|------|------------|---------------|
| `ClienteFlowableTest` | Que cada método del cliente arme bien la llamada y lea bien la respuesta, contra un servidor HTTP simulado (`MockRestServiceServer`) | `./mvnw -pl web verify` (también en CI) |
| `AutenticacionContraFlowableTest` | Que el login convierta los grupos en roles y rechace las credenciales incorrectas, con un doble del cliente | `./mvnw -pl web verify` |
| `AutenticacionEmpresaTest` | Que una cuenta de empresa válida obtenga el rol `EMPRESA`, que una inexistente deje seguir al login del personal y que una clave incorrecta se rechace | `./mvnw -pl web verify` |
| `SeguridadTest` | Las reglas de acceso: sin sesión pide login, un docente no entra a administración, el login con credenciales válidas e inválidas, sin Flowable (`@WebMvcTest` con un doble del cliente) | `./mvnw -pl web verify` |
| `PortalEmpresaControllerTest` | El portal de empresas: una empresa recibe 404 al abrir el curso de otra (escenario 10), el detalle en preparación muestra las actividades de las ramas paralelas y el estado se deriva de Flowable o del persistido (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `BandejaDeTareasTest` | Las reglas: nadie completa una tarea ajena ni una de grupo sin reclamar, no se reclama lo que no está disponible, *Preparar materiales* no se completa desde la web, *Confirmar compensaciones* queda bloqueada mientras haya liberaciones pendientes, y los conflictos y rechazos de Flowable se informan | `./mvnw -pl web verify` |
| `BandejaControllerTest` | La bandeja desde el navegador: secciones de Administración y Docentes, empresa y curso de cada solicitud, completar una tarea ajena armando la URL, reclamar, vuelta a la sección, token CSRF y login (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `ElaborarPropuestaControllerTest` | La pantalla de la propuesta desde el navegador: el asignado abre el formulario, guardar completa la tarea y registra la versión, si el registro se rechaza se muestra el motivo, enlace al PDF de la versión anterior y descarga del PDF (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `RevisarPropuestaControllerTest` | La pantalla de revisión desde el navegador: el asignado ve la última propuesta con su PDF y las observaciones de la empresa, y al enviar guarda la nueva versión y completa la tarea (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `PantallasDeTareasTest` | Lo común de las pantallas de tarea: la sección de retorno solo admite las del sitio, la tarea tiene que existir, ser la de la pantalla y estar asignada al usuario, y el curso sale de la solicitud de la tarea | `./mvnw -pl web verify` |
| `RegistroDePropuestasTest` | Que guardar la versión y completar la tarea sea una sola operación: se guarda primero y se completa después, con commit; si Flowable no completa la tarea, la versión se descarta (rollback); si el guardado falla, la tarea no se completa | `./mvnw -pl web verify` |
| `EvaluarRecursosControllerTest` | La pantalla de recursos desde el navegador: el asignado ve el formulario con las tres casillas, enviarlo sin ninguna marcada vuelve a la pantalla con el error y no completa la tarea, y con al menos una completa pasando los tres booleanos (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `ConfeccionarListaDefinitivaControllerTest` | La pantalla de la lista definitiva desde el navegador: el asignado ve el formulario con la nómina que envió la empresa, una lista vacía vuelve con el error y no completa la tarea, y una lista con participantes completa pasando `listaDefinitiva` (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `ElaborarInformeControllerTest` | La pantalla del informe de resultados: el jefe docente ve el formulario, un informe vacío vuelve con el error, uno con texto completa pasando `informeResultados` y otro usuario no puede completarla (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `EnviarInformeControllerTest` | La pantalla de envío y archivo: el jefe administrativo ve el informe y los datos del curso, confirmar completa la tarea sin variables y otro usuario no puede completarla (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `DefinirDocenteControllerTest` | La pantalla del jefe docente: consulta los miembros del grupo, permite elegir un docente válido y completa `definirDocente` con `docenteAsignado` (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `ArchivarSolicitudControllerTest` | La pantalla de archivo desde el navegador: el asignado ve la solicitud con el motivo (rechazo o sin respuesta) y confirmar archiva y completa la tarea (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `SolicitudesDeCapacitacionTest` | El registro: CUIT guardado solo con dígitos, empresa reutilizada aunque cambien mayúsculas y espacios, CUIT con otra razón social rechazado, instancia iniciada con el curso como `businessKey`, Flowable sin responder y descripciones de la bandeja, con dobles del repositorio y del cliente | `./mvnw -pl web verify` |
| `SolicitudControllerTest` | El formulario desde el navegador: es público, una solicitud válida redirige a la confirmación, los datos inválidos y la razón social distinta vuelven al formulario con el error, Flowable caído se informa y token CSRF (`@WebMvcTest`) | `./mvnw -pl web verify` |
| `ClienteFlowableContraDockerTest` | Que el cliente funcione contra el Flowable real: lista las tareas de `jefe_admin` | `FLOWABLE_DOCKER=si ./mvnw -pl web verify`, con el entorno levantado |

Al agregar un método al cliente, se agrega su test en `ClienteFlowableTest`: qué URL y qué JSON se esperan, y qué se
devuelve con una respuesta de ejemplo. El formato de cada llamada está en [03-guia-flowable.md](03-guia-flowable.md)
§3.

## 8. Estado

El sitio cubre lo que pide el §4.1 del enunciado: la solicitud pública, el login del personal y de las empresas, el
portal de las empresas con sus cursos, el estado y el aula, y las pantallas de las tareas con datos: registrar y
elaborar la propuesta, revisarla con su PDF, archivar la solicitud, evaluar los recursos, confeccionar la lista
definitiva, definir el docente y elaborar y enviar el informe (todas en §3 y §7). Las consultas históricas del §4.3 están en
la app de materiales ([09-app-materiales.md](09-app-materiales.md)).
