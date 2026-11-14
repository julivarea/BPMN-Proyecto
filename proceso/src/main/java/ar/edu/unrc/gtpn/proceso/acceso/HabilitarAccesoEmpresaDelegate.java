package ar.edu.unrc.gtpn.proceso.acceso;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;
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
 * <p>Si la empresa nunca contrató, crea su {@code cuenta_empresa} con un usuario y una clave aleatoria
 * encriptada con BCrypt; el envío de las credenciales se simula con un log. Si ya tiene cuenta por una
 * contratación anterior, la reutiliza y no toca la base. En los dos casos deja {@code accesoHabilitado = true},
 * que es lo que cuenta *Cursos en ejecución*.
 */
public class HabilitarAccesoEmpresaDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(HabilitarAccesoEmpresaDelegate.class);

    /** Bytes al azar de la clave: 12 bytes en Base64 URL-safe dan 16 caracteres. */
    private static final int BYTES_DE_CLAVE = 12;
    /** Largo de {@code cuenta_empresa.usuario}. */
    private static final int LARGO_MAXIMO_USUARIO = 100;

    private static final SecureRandom AZAR = new SecureRandom();

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
                new BCryptPasswordEncoder(), HabilitarAccesoEmpresaDelegate::claveAleatoria);
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
            String usuario = usuarioDe(empresa);
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
     * Usuario legible y único a partir de la razón social: sin acentos, en minúsculas, con guiones, y con el
     * id de la empresa al final para que dos empresas con el mismo nombre no choquen. Ejemplo:
     * {@code "Instituto ABC S.A."} (id 3) → {@code "instituto-abc-s-a-3"}.
     */
    static String usuarioDe(EmpresaDelCurso empresa) {
        String base = Normalizer.normalize(empresa.razonSocial(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "") // saca los acentos
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        String sufijo = "-" + empresa.id();
        int maximo = LARGO_MAXIMO_USUARIO - sufijo.length();
        if (base.length() > maximo) {
            base = base.substring(0, maximo).replaceAll("-+$", "");
        }
        return (base.isEmpty() ? "empresa" : base) + sufijo;
    }

    /** Clave aleatoria en Base64 URL-safe (letras, dígitos, {@code -} y {@code _}). */
    static String claveAleatoria() {
        byte[] bytes = new byte[BYTES_DE_CLAVE];
        AZAR.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
