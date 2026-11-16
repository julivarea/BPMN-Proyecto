package ar.edu.unrc.gtpn.web.empresa;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ar.edu.unrc.gtpn.web.flowable.Actividad;

/**
 * Estado que la empresa ve de su curso (W-04, W-05). Mientras la instancia de Flowable está activa el estado se deriva
 * de sus actividades en curso; cuando termina, es el {@code curso.estado} que dejó el proceso (docs/07 §1).
 */
public enum EstadoDelCurso {

    EN_NEGOCIACION("En negociación"),
    EN_PREPARACION("En preparación"),
    CONFIRMADO("Confirmado y pendiente de inicio"),
    EN_DESARROLLO("En desarrollo"),
    EN_ELABORACION_DEL_INFORME("En elaboración del informe"),
    EN_CURSO("En curso"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado"),
    RECHAZADO("Rechazado"),
    SIN_RESPUESTA("Sin respuesta"),
    NO_VIABLE("No viable");

    /** Actividades de *Preparar curso*, incluidas las ramas paralelas (docs/06 §2). */
    private static final Set<String> PREPARACION = Set.of(
            "prepararCurso", "inicioPrepararCurso", "iniciarPreparacion", "nominaRecibida",
            "confeccionarListaDefinitiva", "evaluarRecursos", "seleccionarRecursos", "reservarAula",
            "solicitarAulaVirtual", "contratarEquipamiento", "recursosListos", "prepararMateriales",
            "preparacionCompleta", "finPrepararCurso", "habilitarAccesoEmpresa");

    /** Confirmación de la propuesta, docente asignado y espera de la fecha de inicio. */
    private static final Set<String> CONFIRMACION = Set.of(
            "enviarConfirmacion", "definirDocente", "fechaProgramada");

    /** Curso en ejecución. */
    private static final Set<String> DESARROLLO = Set.of("desarrollarCurso");

    /** Informe de resultados y cierre del expediente. */
    private static final Set<String> INFORME = Set.of(
            "elaborarInformeResultados", "enviarInformeYArchivarExpediente", "liberarRecursos", "notificarCierre");

    /** Subproceso de cancelación, que interrumpe el curso en cualquier etapa. */
    private static final Set<String> CANCELACION = Set.of("cancelacion", "cancelacionRecibida", "preparacionCancelada");

    /** Tipos de actividad que se le muestran a la empresa: tareas y esperas, no compuertas ni subprocesos. */
    private static final Set<String> TIPOS_VISIBLES = Set.of(
            "userTask", "serviceTask", "intermediateCatchEvent", "receiveTask");

    private final String etiqueta;

    EstadoDelCurso(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Texto que ve la empresa. */
    public String etiqueta() {
        return etiqueta;
    }

    /** Estado persistido por el proceso; si no es uno conocido (o todavía no hay), queda {@code EN_CURSO}. */
    static EstadoDelCurso desdePersistido(String valor) {
        if (valor == null || valor.isBlank()) {
            return EN_CURSO;
        }
        try {
            return valueOf(valor);
        } catch (IllegalArgumentException error) {
            return EN_CURSO;
        }
    }

    /**
     * Estado de una instancia activa, según sus actividades en curso. La cancelación gana sobre todo lo demás; si no
     * hay ninguna actividad conocida, la instancia está en la negociación previa a la aceptación.
     */
    static EstadoDelCurso derivar(List<Actividad> actividades) {
        Set<String> ids = actividades.stream()
                .map(Actividad::id)
                .collect(Collectors.toSet());
        if (contieneAlguna(ids, CANCELACION)) {
            return CANCELADO;
        }
        if (contieneAlguna(ids, DESARROLLO)) {
            return EN_DESARROLLO;
        }
        if (contieneAlguna(ids, INFORME)) {
            return EN_ELABORACION_DEL_INFORME;
        }
        if (contieneAlguna(ids, CONFIRMACION)) {
            return CONFIRMADO;
        }
        if (contieneAlguna(ids, PREPARACION)) {
            return EN_PREPARACION;
        }
        return EN_NEGOCIACION;
    }

    /**
     * Actividades de las ramas paralelas de *Preparar curso* que están en curso, sin repetir y en el orden en que las
     * informa Flowable.
     */
    static List<Actividad> actividadesDePreparacion(List<Actividad> actividades) {
        return actividades.stream()
                .filter(actividad -> TIPOS_VISIBLES.contains(actividad.tipo()))
                .filter(actividad -> actividad.id() != null && PREPARACION.contains(actividad.id()))
                .distinct()
                .collect(Collectors.toList());
    }

    private static boolean contieneAlguna(Set<String> ids, Set<String> buscadas) {
        return ids.stream().anyMatch(buscadas::contains);
    }
}
