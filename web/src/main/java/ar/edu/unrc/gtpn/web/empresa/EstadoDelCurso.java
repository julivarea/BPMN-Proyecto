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
    /** Cierre normal con la liberación de recursos todavía pendiente (por ejemplo, *Liberar equipamiento*). */
    EN_CIERRE("Finalizando: liberando recursos"),
    /** Cancelación en curso: se compensan los recursos y falta confirmarlo antes de notificar el cierre. */
    EN_CANCELACION("Cancelándose: liberando recursos"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado"),
    RECHAZADO("Rechazado"),
    SIN_RESPUESTA("Sin respuesta"),
    NO_VIABLE("No viable");

    /** Actividades de *Preparar curso*, incluidas las ramas paralelas (docs/06 §2). */
    private static final Set<String> PREPARACION = Set.of(
            "prepararCurso", "inicioPrepararCurso", "iniciarPreparacion", "nominaRecibida",
            "confeccionarListaDefinitiva", "evaluarRecursos", "seleccionarRecursos", "reservarAula", "confirmarAulaDisponible",
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

    /**
     * Liberación de recursos al cerrar el curso con normalidad. Mientras *Liberar equipamiento* (tarea de usuario)
     * siga pendiente la instancia está abierta: se muestra como "finalizando" hasta que termina.
     */
    private static final Set<String> LIBERACION = Set.of("liberarAula", "liberarAulaVirtual", "liberarEquipamiento");

    /**
     * Subproceso de cancelación. Mientras está activo se lanzan las compensaciones y se espera a que terminen
     * (*Liberar equipamiento* y *Confirmar compensaciones*): el curso se está cancelando. Cuando termina, el proceso
     * notifica el cierre y la instancia concluye con el estado persistido.
     */
    private static final Set<String> CANCELACION = Set.of(
            "cancelacion", "cancelacionRecibida", "lanzarCompensacionAula", "lanzarCompensacionAulaVirtual",
            "lanzarCompensacionEquipamiento", "confirmarCompensaciones", "preparacionCancelada");

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
     * Si en este estado el curso conserva su aula. Se reserva durante la preparación y se libera al cerrar el curso o al
     * cancelarlo, así que en el cierre y la cancelación ya no corresponde mostrarla.
     */
    boolean conservaElAula() {
        return this == EN_PREPARACION || this == CONFIRMADO || this == EN_DESARROLLO || this == EN_ELABORACION_DEL_INFORME;
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
            return EN_CANCELACION;
        }
        if (contieneAlguna(ids, DESARROLLO)) {
            return EN_DESARROLLO;
        }
        if (contieneAlguna(ids, LIBERACION)) {
            return EN_CIERRE;
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
