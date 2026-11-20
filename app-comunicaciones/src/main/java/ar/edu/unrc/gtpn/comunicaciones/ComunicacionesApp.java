package ar.edu.unrc.gtpn.comunicaciones;

import java.awt.Dimension;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * App de escritorio que simula las comunicaciones de la empresa mediante la API REST de Flowable (enunciado 4.2.2):
 * seleccionar una solicitud y enviar, solo si están habilitadas en su estado actual, la aceptación (con fecha
 * acordada), el rechazo, la solicitud de modificaciones (con observaciones), la nómina o la cancelación.
 */
public final class ComunicacionesApp {

    static final String TITULO = "Comunicaciones de la empresa";

    private ComunicacionesApp() {
    }

    public static void main(String[] args) {
        FlowableRest flowable = FlowableRest.desdeVariablesDeEntorno();
        SwingUtilities.invokeLater(() -> {
            usarAspectoModerno();
            new VentanaDeSolicitudes(flowable).setVisible(true);
        });
    }

    /**
     * Aspecto FlatLaf, igual al de la app de materiales. Todo se agranda un 25 %; se cambia al ejecutar con
     * {@code -Dflatlaf.uiScale=1.5}, por ejemplo.
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
        UIManager.put("OptionPane.yesButtonText", "Sí");
        UIManager.put("OptionPane.noButtonText", "No");
        UIManager.put("OptionPane.okButtonText", "Aceptar");
        UIManager.put("OptionPane.cancelButtonText", "Cancelar");
    }
}
