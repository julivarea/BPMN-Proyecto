package ar.edu.unrc.gtpn.materiales;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Optional;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.flowable.engine.ProcessEngine;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * App de escritorio para que un administrativo prepare materiales usando la API Java de Flowable (enunciado 4.2.1):
 * identificarse, consultar las tareas "Preparar materiales" asignadas o reclamables, reclamarlas y completarlas.
 */
public final class MaterialesApp {

    static final String TITULO = "Preparación de materiales";

    private MaterialesApp() {
    }

    public static void main(String[] args) {
        ProcessEngine motor = MotorFlowable.conectar();
        ServicioDeTareas servicio = new ServicioDeTareas(motor);
        SwingUtilities.invokeLater(() -> {
            usarAspectoModerno();
            Optional<String> usuario = ingresar(servicio);
            if (usuario.isPresent()) {
                new VentanaDeTareas(servicio, usuario.get(), motor::close).setVisible(true);
            } else {
                motor.close();
            }
        });
    }

    /**
     * Aspecto FlatLaf, con bordes redondeados y tablas más espaciadas. Todo (texto, botones, márgenes) se agranda un
     * 25 %; se cambia al ejecutar con {@code -Dflatlaf.uiScale=1.5}, por ejemplo.
     */
    static void usarAspectoModerno() {
        if (System.getProperty("flatlaf.uiScale") == null) {
            System.setProperty("flatlaf.uiScale", "1.25");
        }
        FlatLightLaf.setup();
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("OptionPane.okButtonText", "Aceptar");
        UIManager.put("OptionPane.cancelButtonText", "Cancelar");
    }

    /** Pide usuario y contraseña hasta que sean válidos. Vacío si el usuario cancela. */
    private static Optional<String> ingresar(ServicioDeTareas servicio) {
        JTextField campoUsuario = new JTextField(20);
        JPasswordField campoClave = new JPasswordField(20);
        campoUsuario.putClientProperty("JTextField.placeholderText", "Usuario de Flowable");
        JPanel formulario = new JPanel(new GridLayout(0, 1, 0, 4));
        formulario.add(new JLabel("Usuario"));
        formulario.add(campoUsuario);
        formulario.add(new JLabel("Contraseña"));
        formulario.add(campoClave);

        while (JOptionPane.showConfirmDialog(
                null, formulario, TITULO, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                == JOptionPane.OK_OPTION) {
            String usuario = campoUsuario.getText().trim();
            if (servicio.credencialesValidas(usuario, new String(campoClave.getPassword()))) {
                return Optional.of(usuario);
            }
            JOptionPane.showMessageDialog(null, "Usuario o contraseña incorrectos.", TITULO, JOptionPane.ERROR_MESSAGE);
            campoClave.setText("");
        }
        return Optional.empty();
    }
}
