package ar.edu.unrc.gtpn.web.empresa;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import ar.edu.unrc.gtpn.web.flowable.Actividad;
import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.propuestas.Propuesta;
import ar.edu.unrc.gtpn.web.propuestas.PropuestasRepository;
import ar.edu.unrc.gtpn.web.seguridad.CuentaEmpresa;
import ar.edu.unrc.gtpn.web.seguridad.CuentaEmpresaRepository;

/**
 * Portal de empresas (W-04, W-05): cada empresa ve sus cursos, el estado de cada uno y, durante la preparación, las
 * actividades que la institución está haciendo en paralelo.
 *
 * <p>La empresa se resuelve por el usuario autenticado, contra {@code cuenta_empresa} (issue #28). Todas las consultas
 * filtran por su ID, así que un curso de otra empresa no se puede abrir ni armando la URL a mano (escenario 10).
 */
@Controller
public class PortalEmpresaController {

    private final CuentaEmpresaRepository cuentas;
    private final CursosDeLaEmpresaRepository cursos;
    private final ClienteFlowable flowable;
    private final PropuestasRepository propuestas;

    public PortalEmpresaController(
            CuentaEmpresaRepository cuentas,
            CursosDeLaEmpresaRepository cursos,
            ClienteFlowable flowable,
            PropuestasRepository propuestas) {
        this.cuentas = cuentas;
        this.cursos = cursos;
        this.flowable = flowable;
        this.propuestas = propuestas;
    }

    /** Los cursos de la empresa logueada. */
    @GetMapping("/empresas")
    public String misCursos(Principal usuario, Model modelo) {
        CuentaEmpresa cuenta = cuentaDe(usuario);
        List<CursoConEstado> misCursos = cursos.listar(cuenta.empresaId()).stream()
                .map(curso -> conEstado(curso, false))
                .toList();
        modelo.addAttribute("seccion", "empresas");
        modelo.addAttribute("cursos", misCursos);
        return "empresa/cursos";
    }

    /** Ficha de un curso, con las actividades paralelas si está en preparación. */
    @GetMapping("/empresas/cursos/{id}")
    public String detalle(@PathVariable long id, Principal usuario, Model modelo) {
        CuentaEmpresa cuenta = cuentaDe(usuario);
        CursoDeLaEmpresa curso = cursos.buscar(cuenta.empresaId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El curso no pertenece a tu empresa."));
        modelo.addAttribute("seccion", "empresas");
        modelo.addAttribute("curso", conEstado(conLoAcordado(curso), true));
        return "empresa/curso-detalle";
    }

    /**
     * Fecha de inicio, programa y presupuesto acordados. El proceso los guarda en la base recién al terminar, así que
     * con el curso en marcha se toman de la instancia: la fecha es la que la empresa fijó al aceptar
     * ({@code fechaInicioCurso}) y el programa y el presupuesto, los de la última propuesta. Sin esa fecha todavía no
     * hay nada acordado y se devuelve el curso como está.
     */
    private CursoDeLaEmpresa conLoAcordado(CursoDeLaEmpresa curso) {
        if (curso.instanciaId() == null || curso.fechaInicio() != null) {
            return curso;
        }
        try {
            Map<String, Object> variables = flowable.variables(curso.instanciaId());
            Object fecha = variables.get("fechaInicioCurso");
            if (fecha == null) {
                return curso;
            }
            Object informe = variables.get("informeResultados");
            List<Propuesta> versiones = propuestas.buscarPorCurso(curso.id());
            Propuesta ultima = versiones.isEmpty() ? null : versiones.get(versiones.size() - 1);
            return curso.conAcuerdo(LocalDateTime.parse(fecha.toString(), DateTimeFormatter.ISO_DATE_TIME),
                    ultima == null ? null : ultima.programa(), ultima == null ? null : ultima.presupuesto(),
                    informe == null ? null : informe.toString());
        } catch (RestClientException | DateTimeParseException error) {
            return curso; // instancia terminada o Flowable caído: se muestra lo que hay en la base
        }
    }

    /** La cuenta de la empresa logueada. Un usuario sin cuenta de empresa no entra al portal. */
    private CuentaEmpresa cuentaDe(Principal usuario) {
        return cuentas.buscarPorUsuario(usuario.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Tu usuario no tiene una empresa asociada."));
    }

    /**
     * Estado del curso y, si se pide y corresponde, las actividades de las ramas paralelas. Con la instancia terminada
     * no hay actividades activas: vale el estado persistido. Si Flowable no responde, el portal sigue funcionando con
     * ese mismo estado en lugar de romper el listado.
     */
    private CursoConEstado conEstado(CursoDeLaEmpresa curso, boolean conActividades) {
        EstadoDelCurso persistido = EstadoDelCurso.desdePersistido(curso.estado());
        if (curso.instanciaId() == null) {
            return new CursoConEstado(curso, persistido, List.of());
        }

        List<Actividad> activas;
        try {
            activas = flowable.actividadesActivas(curso.instanciaId());
        } catch (RestClientException error) {
            return new CursoConEstado(curso, persistido, List.of());
        }

        if (activas.isEmpty()) {
            return new CursoConEstado(curso, persistido, List.of());
        }

        EstadoDelCurso estado = EstadoDelCurso.derivar(activas);
        List<Actividad> visibles = conActividades && estado == EstadoDelCurso.EN_PREPARACION
                ? EstadoDelCurso.actividadesDePreparacion(activas)
                : List.of();
        return new CursoConEstado(curso, estado, visibles, estado.conservaElAula() ? aulaDe(curso) : null);
    }

    /**
     * Aula física asignada al curso: la que guardó *Reservar aula* en la variable {@code aulaAsignada} de la instancia.
     * Es {@code null} si todavía no se reservó o si Flowable no responde (el portal sigue funcionando sin ella).
     */
    private String aulaDe(CursoDeLaEmpresa curso) {
        try {
            Object aula = flowable.variables(curso.instanciaId()).get("aulaAsignada");
            return aula == null || aula.toString().isBlank() ? null : aula.toString();
        } catch (RestClientException error) {
            return null;
        }
    }
}
