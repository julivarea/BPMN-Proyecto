package ar.edu.unrc.gtpn.proceso.acceso;

import java.util.function.Predicate;
import java.util.function.Supplier;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Habilita el acceso de la empresa al sitio después de aceptada la propuesta (enunciado 4.1.2, issue #17).
 *
 * <p>Si la empresa nunca contrató, crea su {@code cuenta_empresa}: el usuario es su razón social y la clave es
 * {@link #CLAVE_DE_LAS_EMPRESAS}, encriptada con BCrypt (clave fija para facilitar las pruebas y la demo; el
 * enunciado permite simular la comunicación de credenciales, que acá se hace con un log). Si ya tiene cuenta por una
 * contratación anterior, la reutiliza y no toca la base. En los dos casos deja {@code accesoHabilitado = true},
 * que es lo que cuenta *Cursos en ejecución*.
 */
public class HabilitarAccesoEmpresaDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(HabilitarAccesoEmpresaDelegate.class);

    /** Clave de todas las cuentas de empresa: es un entorno de demostración, no hay envío real de credenciales. */
    static final String CLAVE_DE_LAS_EMPRESAS = "clave123";
    /** Largo de {@code cuenta_empresa.usuario}. */
    private static final int LARGO_MAXIMO_USUARIO = 100;

    /**
     * Repositorio que usa el constructor sin argumentos. Flowable instancia los delegates por reflexión, así que
     * los tests del proceso lo reemplazan desde acá con un doble en vez de necesitar PostgreSQL.
     */
    private static volatile CuentasDeEmpresa repositorioParaTests;

    private final CuentasDeEmpresa cuentas;
    private final PasswordEncoder encriptador;
    private final Supplier<String> claves;

    /** Flowable instancia los delegates con el constructor sin argumentos. */
    public HabilitarAccesoEmpresaDelegate() {
        this(repositorioParaTests != null ? repositorioParaTests : CuentasDeEmpresaJdbc.desdeVariablesDeEntorno(),
                new BCryptPasswordEncoder(), () -> CLAVE_DE_LAS_EMPRESAS);
    }

    /**
     * Solo para los tests del proceso: reemplaza el repositorio del constructor sin argumentos
     * ({@code null} vuelve al repositorio real).
     */
    public static void usarRepositorio(CuentasDeEmpresa repositorio) {
        repositorioParaTests = repositorio;
    }

    /** Para los tests: repositorio, encriptador y claves simulados. */
    public HabilitarAccesoEmpresaDelegate(CuentasDeEmpresa cuentas, PasswordEncoder encriptador,
            Supplier<String> claves) {
        this.cuentas = cuentas;
        this.encriptador = encriptador;
        this.claves = claves;
    }

    @Override
    public void execute(DelegateExecution execution) {
        long cursoId = cursoDe(execution);
        EmpresaDelCurso empresa = cuentas.buscarPorCurso(cursoId)
                .orElseThrow(() -> new IllegalStateException("No existe el curso " + cursoId
                        + " de la solicitud " + execution.getProcessInstanceId()));

        if (empresa.tieneCuenta()) {
            log.info("La empresa {} ya tiene cuenta (usuario {}): se reutiliza en el curso {}",
                    empresa.razonSocial(), empresa.usuario().orElseThrow(), cursoId);
        } else {
            String usuario = usuarioDe(empresa, cuentas::existeUsuario);
            String clave = claves.get();
            cuentas.crear(empresa.id(), usuario, encriptador.encode(clave));
            // El enunciado permite simular la comunicación: la clave en claro solo va al log.
            log.info("Cuenta creada para la empresa {} (curso {}). Se simula el envío de las credenciales:"
                    + " usuario {} / clave {}", empresa.razonSocial(), cursoId, usuario, clave);
        }

        execution.setVariable("accesoHabilitado", true);
    }

    /** El {@code businessKey} de la instancia es {@code curso.id} (docs/07-base-de-datos.md §3). */
    private static long cursoDe(DelegateExecution execution) {
        String businessKey = execution.getProcessInstanceBusinessKey();
        try {
            return Long.parseLong(businessKey == null ? "" : businessKey.trim());
        } catch (NumberFormatException error) {
            throw new IllegalStateException("La solicitud " + execution.getProcessInstanceId()
                    + " no tiene un curso como businessKey: " + businessKey, error);
        }
    }

    /**
     * Usuario de la empresa: su razón social, tal como la escribió en el formulario. Si otra cuenta ya usa ese
     * usuario (dos empresas con el mismo nombre y distinto CUIT), se le agrega el id de la empresa al final.
     * Ejemplo: {@code "JV Enterprises"} o, si estaba ocupado, {@code "JV Enterprises 4"}.
     */
    static String usuarioDe(EmpresaDelCurso empresa, Predicate<String> yaExiste) {
        String base = recortar(empresa.razonSocial(), LARGO_MAXIMO_USUARIO);
        if (base.isEmpty()) {
            base = "empresa";
        }
        if (!yaExiste.test(base)) {
            return base;
        }
        String sufijo = " " + empresa.id();
        return recortar(base, LARGO_MAXIMO_USUARIO - sufijo.length()) + sufijo;
    }

    private static String recortar(String texto, int maximo) {
        String limpio = texto.strip();
        return (limpio.length() > maximo ? limpio.substring(0, maximo) : limpio).strip();
    }
}
