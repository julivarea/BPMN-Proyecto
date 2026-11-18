package ar.edu.unrc.gtpn.comunicaciones;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

import com.formdev.flatlaf.util.UIScale;

/**
 * Lista de solicitudes en curso para elegir sobre cuál actuar, y las comunicaciones de la negociación y preparación:
 * solo se habilita el botón del mensaje que la instancia está esperando en ese momento (X-04).
 */
final class VentanaDeSolicitudes extends JFrame {

    private static final String[] COLUMNAS = {"Solicitud", "Iniciada"};
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final transient ComunicacionesDeLaEmpresa comunicaciones;
    private final transient FlowableRest flowable;
    private transient List<Solicitud> solicitudes = List.of();
    private final Map<Comunicacion, JButton> botones = new EnumMap<>(Comunicacion.class);
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
        this.comunicaciones = new ComunicacionesDeLaEmpresa(flowable);

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

        // Cuadrícula y no FlowLayout: con cinco botones largos, un FlowLayout no baja de línea dentro del panel y los
        // últimos (la nómina y la cancelación) quedan cortados fuera de la ventana.
        JPanel botonesDeComunicacion = new JPanel(new GridLayout(0, 3, UIScale.scale(8), UIScale.scale(8)));
        JPanel acciones = new JPanel(new BorderLayout(0, UIScale.scale(6)));
        JLabel etiquetaAcciones = new JLabel("Comunicaciones:");
        etiquetaAcciones.putClientProperty("FlatLaf.styleClass", "light");
        acciones.add(etiquetaAcciones, BorderLayout.NORTH);
        acciones.add(botonesDeComunicacion, BorderLayout.CENTER);
        for (Comunicacion comunicacion : Comunicacion.values()) {
            JButton boton = new JButton(comunicacion.etiqueta());
            boton.putClientProperty("JButton.buttonType", "roundRect");
            boton.setEnabled(false);
            boton.addActionListener(evento -> enviar(comunicacion));
            botones.put(comunicacion, boton);
            botonesDeComunicacion.add(boton);
        }

        JPanel sur = new JPanel(new BorderLayout(0, UIScale.scale(10)));
        sur.add(acciones, BorderLayout.NORTH);
        sur.add(pie, BorderLayout.SOUTH);

        JPanel contenido = new JPanel(new BorderLayout(0, UIScale.scale(12)));
        int margen = UIScale.scale(20);
        contenido.setBorder(BorderFactory.createEmptyBorder(margen, margen, margen, margen));
        contenido.add(encabezado, BorderLayout.NORTH);
        contenido.add(new JScrollPane(tabla), BorderLayout.CENTER);
        contenido.add(sur, BorderLayout.SOUTH);
        setContentPane(contenido);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(UIScale.scale(820), UIScale.scale(560));
        setLocationRelativeTo(null);
        cargarSolicitudes();
    }

    /** Solicitud elegida en la lista, o {@code null} si no hay ninguna. */
    Solicitud seleccionada() {
        int fila = tabla.getSelectedRow();
        return fila < 0 ? null : solicitudes.get(fila);
    }

    private void cargarSolicitudes() {
        Solicitud anterior = seleccionada();
        try {
            solicitudes = flowable.solicitudesEnCurso();
        } catch (RuntimeException error) {
            solicitudes = List.of();
            JOptionPane.showMessageDialog(this, "No se pudieron cargar las solicitudes: " + error.getMessage(),
                    ComunicacionesApp.TITULO, JOptionPane.ERROR_MESSAGE);
        }
        filas.setRowCount(0);
        for (Solicitud solicitud : solicitudes) {
            filas.addRow(new Object[] {solicitud.descripcion(), FORMATO_FECHA.format(solicitud.iniciada())});
        }
        // Se conserva la solicitud elegida si sigue en curso, para no perder el contexto tras enviar.
        boolean restaurada = false;
        if (anterior != null) {
            for (int fila = 0; fila < solicitudes.size() && !restaurada; fila++) {
                if (solicitudes.get(fila).instanciaId().equals(anterior.instanciaId())) {
                    tabla.setRowSelectionInterval(fila, fila); // el listener actualiza los botones
                    restaurada = true;
                }
            }
        }
        if (!restaurada) {
            mostrarSeleccion();
        }
    }

    private void mostrarSeleccion() {
        Solicitud solicitud = seleccionada();
        seleccion.setText(solicitud == null
                ? "Elegí una solicitud de la lista."
                : "Solicitud seleccionada: " + solicitud.descripcion());
        habilitarComunicaciones(solicitud);
    }

    /** Habilita solo los botones cuyo mensaje está esperando la instancia elegida. */
    private void habilitarComunicaciones(Solicitud solicitud) {
        Set<Comunicacion> habilitadas = Set.of();
        if (solicitud != null) {
            try {
                habilitadas = comunicaciones.habilitadas(solicitud);
            } catch (RuntimeException error) {
                JOptionPane.showMessageDialog(this, "No se pudo consultar el estado de la solicitud: "
                        + error.getMessage(), ComunicacionesApp.TITULO, JOptionPane.ERROR_MESSAGE);
            }
        }
        for (Map.Entry<Comunicacion, JButton> boton : botones.entrySet()) {
            boton.getValue().setEnabled(habilitadas.contains(boton.getKey()));
        }
    }

    private void enviar(Comunicacion comunicacion) {
        Solicitud solicitud = seleccionada();
        if (solicitud == null) {
            return;
        }
        String dato = "";
        if (comunicacion == Comunicacion.CANCELACION_CURSO
                && JOptionPane.showConfirmDialog(this, "¿Está seguro de cancelar el curso?",
                        comunicacion.etiqueta(), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) {
            return;
        }
        Optional<String> pedido = comunicacion.pedido();
        if (pedido.isPresent()) {
            dato = pedirDato(comunicacion, pedido.get());
            if (dato == null) {
                return;
            }
            if (dato.isBlank()) {
                JOptionPane.showMessageDialog(this, "Hace falta completar el dato para enviar la comunicación.",
                        ComunicacionesApp.TITULO, JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        try {
            comunicaciones.enviar(solicitud, comunicacion, dato);
            JOptionPane.showMessageDialog(this, "Comunicación enviada: " + comunicacion.etiqueta() + ".",
                    ComunicacionesApp.TITULO, JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException error) {
            JOptionPane.showMessageDialog(this, "No se pudo enviar la comunicación: " + error.getMessage(),
                    ComunicacionesApp.TITULO, JOptionPane.ERROR_MESSAGE);
        }
        // Después de enviar, las comunicaciones habilitadas cambian: se vuelve a consultar.
        cargarSolicitudes();
    }

    private String pedirDato(Comunicacion comunicacion, String mensaje) {
        if (comunicacion != Comunicacion.NOMINA_PARTICIPANTES) {
            return JOptionPane.showInputDialog(this, mensaje, comunicacion.etiqueta(), JOptionPane.QUESTION_MESSAGE);
        }
        JTextArea entrada = new JTextArea(8, 35);
        entrada.setLineWrap(true);
        entrada.setWrapStyleWord(true);
        int resultado = JOptionPane.showConfirmDialog(this, new JScrollPane(entrada), mensaje,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        return resultado == JOptionPane.OK_OPTION ? entrada.getText() : null;
    }
}
