package ar.edu.unrc.gtpn.comunicaciones;

import java.awt.BorderLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

import com.formdev.flatlaf.util.UIScale;

/**
 * Lista de solicitudes en curso para elegir sobre cuál actuar. Las comunicaciones habilitadas y su envío se agregan en
 * los issues #16 y #23.
 */
final class VentanaDeSolicitudes extends JFrame {

    private static final String[] COLUMNAS = {"Solicitud", "Iniciada"};
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final transient FlowableRest flowable;
    private transient List<Solicitud> solicitudes = List.of();
    private final DefaultTableModel filas = new DefaultTableModel(COLUMNAS, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tabla = new JTable(filas);
    private final JLabel seleccion = new JLabel("Elegí una solicitud de la lista.");

    VentanaDeSolicitudes(FlowableRest flowable) {
        super(ComunicacionesApp.TITULO);
        this.flowable = flowable;

        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.getSelectionModel().addListSelectionListener(evento -> {
            if (!evento.getValueIsAdjusting()) {
                mostrarSeleccion();
            }
        });

        JLabel titulo = new JLabel("Solicitudes en curso");
        titulo.putClientProperty("FlatLaf.styleClass", "h2");
        JLabel subtitulo = new JLabel("Simula las comunicaciones que la empresa envía sobre cada solicitud");
        subtitulo.putClientProperty("FlatLaf.styleClass", "light");
        JPanel encabezado = new JPanel(new BorderLayout(0, UIScale.scale(4)));
        encabezado.add(titulo, BorderLayout.NORTH);
        encabezado.add(subtitulo, BorderLayout.SOUTH);

        JButton actualizar = new JButton("Actualizar");
        actualizar.putClientProperty("JButton.buttonType", "roundRect");
        actualizar.addActionListener(evento -> cargarSolicitudes());
        JPanel pie = new JPanel(new BorderLayout(UIScale.scale(12), 0));
        pie.add(seleccion, BorderLayout.CENTER);
        pie.add(actualizar, BorderLayout.EAST);

        JPanel contenido = new JPanel(new BorderLayout(0, UIScale.scale(12)));
        int margen = UIScale.scale(20);
        contenido.setBorder(BorderFactory.createEmptyBorder(margen, margen, margen, margen));
        contenido.add(encabezado, BorderLayout.NORTH);
        contenido.add(new JScrollPane(tabla), BorderLayout.CENTER);
        contenido.add(pie, BorderLayout.SOUTH);
        setContentPane(contenido);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(UIScale.scale(760), UIScale.scale(480));
        setLocationRelativeTo(null);
        cargarSolicitudes();
    }

    /** Solicitud elegida en la lista, o {@code null} si no hay ninguna. */
    Solicitud seleccionada() {
        int fila = tabla.getSelectedRow();
        return fila < 0 ? null : solicitudes.get(fila);
    }

    private void cargarSolicitudes() {
        try {
            solicitudes = flowable.solicitudesEnCurso();
        } catch (RuntimeException error) {
            solicitudes = List.of();
            JOptionPane.showMessageDialog(this, "No se pudieron cargar las solicitudes: " + error.getMessage(),
                    ComunicacionesApp.TITULO, JOptionPane.ERROR_MESSAGE);
        }
        filas.setRowCount(0);
        for (Solicitud solicitud : solicitudes) {
            filas.addRow(new Object[] {solicitud.clave(), FORMATO_FECHA.format(solicitud.iniciada())});
        }
        mostrarSeleccion();
    }

    private void mostrarSeleccion() {
        Solicitud solicitud = seleccionada();
        seleccion.setText(solicitud == null
                ? "Elegí una solicitud de la lista."
                : "Solicitud seleccionada: " + solicitud.clave());
    }
}
