# 10 · App de comunicaciones

Cómo está armada la aplicación de escritorio que simula a la empresa (§4.2.2) y cómo se le agregan las comunicaciones.
La API REST de Flowable está en [03-guia-flowable.md](03-guia-flowable.md) §3.

## 1. Para qué sirve

La empresa se comunica con la institución durante la negociación y la preparación del curso. Esta app **simula a la
empresa**: se elige una solicitud y se envía la comunicación que corresponda (§4.2.2, X-03):

| Comunicación | Mensaje | Datos que lleva |
|--------------|---------|-----------------|
| Aceptación de la propuesta | `aceptacionPropuesta` | `fechaInicioCurso` (fecha acordada) |
| Rechazo de la propuesta | `rechazoPropuesta` | — |
| Solicitud de modificaciones | `modificacionesPropuesta` | `observacionesEmpresa` |
| Nómina de participantes | `nominaParticipantes` | `participantes` |
| Cancelación del curso | `cancelacionCurso` | — |

Solo se pueden enviar las comunicaciones **habilitadas en el estado actual** de la solicitud (X-04): por ejemplo, la
aceptación solo mientras el proceso espera la respuesta a una propuesta. La negociación y la nómina se simulan solo
desde esta app, no desde el sitio. Los nombres de los mensajes y de las variables son los de
[06-guia-implementacion-modelo.md](06-guia-implementacion-modelo.md) §3.

## 2. Cómo se conecta

Como la empresa está afuera de la institución, la app usa la **API REST** del servidor Flowable, igual que la web: no
tiene motor propio ni acceso a la base. Usa el cliente HTTP del JDK (`java.net.http`) y Jackson para el JSON.

La conexión se toma de las mismas variables de entorno que la web, con valores por defecto para el entorno Docker:

| Variable | Por defecto |
|----------|-------------|
| `FLOWABLE_URL` (sin `/process-api`) | `http://localhost:8080/flowable-ui` |
| `FLOWABLE_USUARIO` | `admin` |
| `FLOWABLE_CLAVE` | `test` |

Usa una cuenta técnica porque la empresa no es un usuario de Flowable: es una simulación de lo que llegaría desde
afuera.

## 3. Levantarla

Con el entorno Docker levantado ([04-entorno-desarrollo.md](04-entorno-desarrollo.md)):

```bash
./mvnw -pl app-comunicaciones compile exec:java
```

Muestra las solicitudes en curso (las instancias activas de `organizarCurso`) como `businessKey · curso · vN`: el nombre del curso (variable `nombreCurso`, que la web carga al iniciar la instancia) y la última versión de la propuesta enviada (`versionPropuesta`). Se leen con `includeProcessVariables=true`.
Al elegir una, se habilitan solo los botones de las comunicaciones que esa instancia está esperando en ese momento
(§6). *Actualizar* vuelve a consultar. El aspecto es el mismo que el de la app de materiales (FlatLaf, un 25 % más
grande; se cambia agregando, por ejemplo, `-Dflatlaf.uiScale=1.5` al comando).

## 4. Organización

```
app-comunicaciones/src/main/java/ar/edu/unrc/gtpn/comunicaciones/
├── ComunicacionesApp.java          arranque y aspecto
├── Comunicacion.java               las cinco comunicaciones de la empresa: mensaje BPMN y dato que lleva cada una (§6)
├── ComunicacionesDeLaEmpresa.java  cuáles están habilitadas y el envío del mensaje (§6)
├── FlowableRest.java               cliente REST de Flowable (§5)
├── Solicitud.java                  una solicitud en curso: instancia, businessKey, fecha de inicio, curso y versión de la propuesta
└── VentanaDeSolicitudes.java       lista de solicitudes y botones de comunicación
```

## 5. El cliente REST

`FlowableRest` es el **único lugar de la app que conoce las URLs y el JSON de Flowable**. Expone métodos con nombres
del dominio (`solicitudesEnCurso()`, `ejecucionEsperando(...)` y `enviarMensaje(...)`) y por dentro todos pasan por
`enviar(metodo, ruta, cuerpo)`, que:

- agrega la autenticación básica con la cuenta técnica;
- convierte el cuerpo a JSON y la respuesta a un árbol JSON;
- lanza `ErrorDeFlowable`, con el código y el mensaje de Flowable, si la respuesta no es 2xx.

Para una operación nueva se agrega un método que llame a `enviar` con su ruta (`GET`, `POST` o `PUT`) y su test en
`FlowableRestTest`. Las listas piden hasta 500 resultados, porque Flowable devuelve 10 por defecto.

## 6. Comunicaciones habilitadas y envío (#16)

`Comunicacion` (enum) es la tabla de §1 en código: cada constante lleva el nombre del mensaje del modelo BPMN, el
texto del botón y, si corresponde, la variable del proceso y lo que hay que pedirle al usuario. `ComunicacionesDeLaEmpresa`
usa el cliente para habilitarlas y enviarlas:

- **Cuáles están habilitadas:** una comunicación está habilitada si la instancia tiene una ejecución esperando su
  mensaje: `GET /runtime/executions?processInstanceId={id}&messageEventSubscriptionName={mensaje}`. Lista vacía = no
  habilitada. La ventana muestra un botón por comunicación y habilita solo esos; si la instancia no espera ninguno
  (por ejemplo, mientras la institución elabora la propuesta), todos quedan deshabilitados.
- **Enviar:** se piden los datos de la comunicación (fecha acordada u observaciones) y se entrega el mensaje a esa
  ejecución con `PUT /runtime/executions/{executionId}` y
  `{"action": "messageEventReceived", "messageName": …, "variables": […]}`. Después se vuelve a consultar, porque las
  comunicaciones habilitadas cambian.
- **Datos:** la aceptación convierte la fecha acordada al ISO-8601 con el desfase de la zona de la app que espera el
  timer (`2026-12-01T09:00:00-03:00`); se acepta `AAAA-MM-DD`, `DD/MM/AAAA` y los mismos con hora (`HH:mm`). Sin hora:
  un día futuro empieza a las 09:00 y **hoy empieza dentro de 1 minuto**, para que el curso quede habilitado apenas
  termina la preparación (pruebas y demos). Una fecha anterior a hoy se rechaza; hoy con una hora ya pasada se acepta.
  Las modificaciones envían las observaciones tal cual; la nómina acepta un participante por línea o separados por coma
  y la envía como lista JSON; el rechazo y la cancelación no llevan datos.
- **Camino que se destraba:** aceptación → preparación del curso con la fecha programada; rechazo → *Archivar
  solicitud*; modificaciones → *Revisar propuesta*. Los tres caminos están cubiertos en
  `proceso/src/test/java/ar/edu/unrc/gtpn/proceso/NegociacionTest.java` (rechazo y modificaciones) y en
  `CancelacionYCompensacionesTest` (aceptación con `fechaInicioCurso`).

Una comunicación nueva se agrega con el mismo mecanismo: una constante más en `Comunicacion`.

Para probar sin la app, las mismas acciones se pueden hacer desde la UI de Flowable
([03-guia-flowable.md](03-guia-flowable.md) §2, "Probar desde la UI de Flowable").

## 7. Tests

| Test | Qué prueba | Cómo se corre |
|------|------------|---------------|
| `FlowableRestTest` | Que el cliente arme bien las llamadas (ruta, método y autenticación), lea bien las respuestas, entregue los mensajes con sus variables e informe los errores, contra un servidor HTTP simulado (el del JDK) | `./mvnw -pl app-comunicaciones verify` (también en CI) |
| `ComunicacionesDeLaEmpresaTest` | Las comunicaciones: solo queda habilitada la que la instancia espera, el envío lleva el mensaje y la variable correctos (fecha en ISO, observaciones, nómina JSON, rechazo y cancelación sin datos), no se envía lo no habilitado y la fecha se entiende en sus formatos | `./mvnw -pl app-comunicaciones verify` (también en CI) |
| `FlowableRestContraDockerTest` | Que el cliente funcione contra el Flowable real: lista las solicitudes en curso | `FLOWABLE_DOCKER=si ./mvnw -pl app-comunicaciones verify`, con el entorno levantado |

## 8. Estado

**Cerrados: #16 y #23**, las cinco comunicaciones de la empresa: las de la negociación (aceptación, rechazo y
modificaciones) y las de la preparación (nómina y cancelación), habilitadas solo cuando la instancia espera su mensaje
(§6).
