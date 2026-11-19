package ar.edu.unrc.gtpn.web.flowable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Único punto de la web que habla con Flowable: el resto del código no conoce URLs ni el formato JSON de la API REST
 * (docs/03-guia-flowable.md §3).
 */
@Component
public class ClienteFlowable {

    // Flowable devuelve 10 resultados por defecto; 500 alcanza para el proyecto. Paginar si algún día sobra.
    private static final int MAXIMO_RESULTADOS = 500;

    private static final ParameterizedTypeReference<Pagina<Tarea>> PAGINA_DE_TAREAS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Pagina<Instancia>> PAGINA_DE_INSTANCIAS =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Pagina<Grupo>> PAGINA_DE_GRUPOS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Pagina<Usuario>> PAGINA_DE_USUARIOS =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Variable>> LISTA_DE_VARIABLES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Pagina<Actividad>> PAGINA_DE_ACTIVIDADES =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient http;

    public ClienteFlowable(RestClient.Builder builder, FlowableProperties propiedades) {
        this.http = builder
                .baseUrl(propiedades.url())
                .defaultHeaders(cabeceras -> cabeceras.setBasicAuth(propiedades.usuario(), propiedades.clave()))
                .build();
    }

    /**
     * Inicia una instancia del proceso con su mensaje de inicio y devuelve su ID y si ya terminó: una instancia que
     * no es viable termina dentro de la misma llamada (docs/06 §4).
     */
    public InstanciaIniciada iniciarPorMensaje(String mensaje, String businessKey, Map<String, Object> variables) {
        var pedido = Map.of("message", mensaje, "businessKey", businessKey, "variables", comoLista(variables));
        Instancia instancia = http.post().uri("/runtime/process-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .body(pedido)
                .retrieve()
                .body(Instancia.class);
        return new InstanciaIniciada(instancia.id(), Boolean.TRUE.equals(instancia.ended()));
    }

    /**
     * Grupos del usuario, consultados con sus propias credenciales: así la misma llamada valida la contraseña. Vacío si
     * las credenciales son incorrectas (401) o el usuario no tiene acceso a la API REST (403).
     */
    public Optional<List<String>> gruposDelUsuario(String usuario, String clave) {
        try {
            Pagina<Grupo> grupos = http.get()
                    .uri(uri -> uri.path("/identity/groups")
                            .queryParam("member", usuario)
                            .queryParam("size", MAXIMO_RESULTADOS)
                            .build())
                    .headers(cabeceras -> cabeceras.setBasicAuth(usuario, clave))
                    .retrieve()
                    .body(PAGINA_DE_GRUPOS);
            return Optional.of(grupos.data().stream().map(Grupo::id).collect(Collectors.toList()));
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden error) {
            return Optional.empty();
        }
    }

    /** Usuarios miembros de un grupo de Flowable, por ejemplo {@code docentes}. */
    public List<String> miembrosDelGrupo(String grupo) {
        return http.get()
                .uri(uri -> uri.path("/identity/users")
                        .queryParam("memberOfGroup", grupo)
                        .queryParam("size", MAXIMO_RESULTADOS)
                        .build())
                .retrieve()
                .body(PAGINA_DE_USUARIOS)
                .data()
                .stream()
                .map(Usuario::id)
                .collect(Collectors.toList());
    }

    /** Tareas pendientes asignadas al usuario. */
    public List<Tarea> tareasAsignadas(String usuario) {
        return tareas("assignee", usuario);
    }

    /** Tareas pendientes que el usuario puede reclamar por pertenecer a sus grupos. */
    public List<Tarea> tareasDisponibles(String usuario) {
        return tareas("candidateUser", usuario);
    }

    /**
     * Una tarea pendiente, con la solicitud (businessKey) a la que pertenece. Si no existe (o ya se completó), Flowable
     * responde 404; si la instancia ya terminó, la tarea queda sin solicitud.
     */
    public Tarea tarea(String tareaId) {
        Tarea tarea = http.get()
                .uri("/runtime/tasks/{id}", tareaId)
                .retrieve()
                .body(Tarea.class);

        if (tarea == null || tarea.instanciaId() == null) {
            return tarea;
        }
        try {
            Instancia instancia = http.get()
                    .uri("/runtime/process-instances/{id}", tarea.instanciaId())
                    .retrieve()
                    .body(Instancia.class);
            return tarea.conSolicitud(instancia == null ? null : instancia.businessKey());
        } catch (RestClientException error) {
            // La instancia ya terminó o no está disponible: la tarea queda sin solicitud.
            return tarea;
        }
    }

    /** Asigna la tarea al usuario. Si otro la reclamó antes, Flowable responde 409. */
    public void reclamar(String tareaId, String usuario) {
        accionSobreTarea(tareaId, Map.of("action", "claim", "assignee", usuario));
    }

    /**
     * Completa la tarea con las variables dadas. Flowable no controla quién la completa: antes de llamar a este método
     * hay que verificar que la tarea sea del usuario (05-decisiones, limitación 7).
     */
    public void completar(String tareaId, Map<String, Object> variables) {
        accionSobreTarea(tareaId, Map.of("action", "complete", "variables", comoLista(variables)));
    }

    /** Variables de una instancia en curso. */
    public Map<String, Object> variables(String instanciaId) {
        List<Variable> variables = http.get().uri("/runtime/process-instances/{id}/variables", instanciaId)
                .retrieve()
                .body(LISTA_DE_VARIABLES);
        // HashMap y no Collectors.toMap: una variable puede valer null, y toMap no lo admite.
        Map<String, Object> porNombre = new HashMap<>();
        variables.forEach(variable -> porNombre.put(variable.name(), variable.value()));
        return porNombre;
    }

    /**
     * Actividades de una instancia que todavía no terminaron (W-05, docs/03 §3): las tareas y eventos en curso,
     * incluidas las de las ramas paralelas. Con la instancia terminada la lista viene vacía.
     */
    public List<Actividad> actividadesActivas(String instanciaId) {
        return http.get()
                .uri(uri -> uri.path("/history/historic-activity-instances")
                        .queryParam("processInstanceId", instanciaId)
                        .queryParam("finished", false)
                        .queryParam("size", MAXIMO_RESULTADOS)
                        .build())
                .retrieve()
                .body(PAGINA_DE_ACTIVIDADES)
                .data();
    }

    /** Tareas pendientes según el filtro, cada una con la solicitud (businessKey) a la que pertenece. */
    private List<Tarea> tareas(String filtro, String usuario) {
        List<Tarea> tareas = http.get()
                .uri(uri -> uri.path("/runtime/tasks")
                        .queryParam(filtro, usuario)
                        .queryParam("size", MAXIMO_RESULTADOS)
                        .build())
                .retrieve()
                .body(PAGINA_DE_TAREAS)
                .data();
        if (tareas.isEmpty()) {
            return tareas;
        }
        // La API de tareas no trae la businessKey: se toma de las instancias en curso, con una sola llamada.
        Map<String, String> solicitudPorInstancia = solicitudesEnCurso();
        return tareas.stream()
                .map(tarea -> tarea.conSolicitud(solicitudPorInstancia.get(tarea.instanciaId())))
                .collect(Collectors.toList());
    }

    /** ID de cada instancia en curso del proceso → su businessKey. */
    private Map<String, String> solicitudesEnCurso() {
        List<Instancia> instancias = http.get()
                .uri(uri -> uri.path("/runtime/process-instances")
                        .queryParam("processDefinitionKey", "organizarCurso")
                        .queryParam("size", MAXIMO_RESULTADOS)
                        .build())
                .retrieve()
                .body(PAGINA_DE_INSTANCIAS)
                .data();
        Map<String, String> porId = new HashMap<>();
        instancias.forEach(instancia -> porId.put(instancia.id(), instancia.businessKey()));
        return porId;
    }

    private void accionSobreTarea(String tareaId, Map<String, Object> pedido) {
        http.post().uri("/runtime/tasks/{id}", tareaId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(pedido)
                .retrieve()
                .toBodilessEntity();
    }

    private static List<Variable> comoLista(Map<String, Object> variables) {
        return variables.entrySet().stream()
                .map(variable -> new Variable(variable.getKey(), variable.getValue()))
                .collect(Collectors.toList());
    }

    /** Formato de Flowable para las variables: una lista de pares nombre y valor. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Variable(String name, Object value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pagina<T>(List<T> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Instancia(String id, String businessKey, Boolean ended) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Grupo(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usuario(String id) {
    }
}
