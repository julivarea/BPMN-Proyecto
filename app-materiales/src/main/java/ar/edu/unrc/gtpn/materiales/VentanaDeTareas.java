package ar.edu.unrc.gtpn.materiales;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

import org.flowable.task.api.Task;

import com.formdev.flatlaf.util.UIScale;

/** Lista de tareas "Preparar materiales" del usuario, con acciones de reclamar y completar. */
final class VentanaDeTareas extends JFrame {

    private static final String[] COLUMNAS = {"Solicitud", "Tarea", "Estado", "Creada"};

    private final transient ServicioDeTareas servicio;
    private final String usuario;
    private transient List<Task> tareas = List.of();
    private final DefaultTableModel filas = new DefaultTableModel(COLUMNAS, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tabla = new JTable(filas);
    private final JButton reclamar = new JButton("Reclamar");
    private final JButton completar = new JButton("Completar");

    VentanaDeTareas(ServicioDeTareas servicio, String usuario, Runnable alCerrar) {
        super(MaterialesApp.TITULO + " · " + usuario);
        this.servicio = servicio;
        this.usuario = usuario;

        JButton actualizar = new JButton("Actualizar");
        actualizar.putClientProperty("JButton.buttonType", "roundRect");
        actualizar.addActionListener(evento -> cargarTareas());
        reclamar.putClientProperty("JButton.buttonType", "roundRect");
        reclamar.setEnabled(false);
        reclamar.addActionListener(evento -> reclamar());
        completar.putClientProperty("JButton.buttonType", "roundRect");
        completar.setEnabled(false);
        completar.addActionListener(evento -> completar());
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIScale.scale(8), 0));
        acciones.add(reclamar);
        acciones.add(completar);
        acciones.add(actualizar);

        JLabel titulo = new JLabel("Preparar materiales");
        titulo.putClientProperty("FlatLaf.styleClass", "h2");
        JLabel subtitulo = new JLabel("Tareas asignadas a vos o disponibles para tu grupo");
        subtitulo.putClientProperty("FlatLaf.styleClass", "light");
        JPanel encabezado = new JPanel(new BorderLayout(0, 4));
        encabezado.add(titulo, BorderLayout.NORTH);
        encabezado.add(subtitulo, BorderLayout.SOUTH);

        JPanel contenido = new JPanel(new BorderLayout(0, 12));
        int margen = UIScale.scale(20);
        contenido.setBorder(BorderFactory.createEmptyBorder(margen, margen, margen, margen));
        contenido.add(encabezado, BorderLayout.NORTH);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.getSelectionModel().addListSelectionListener(evento -> {
            if (!evento.getValueIsAdjusting()) {
                actualizarAcciones();
            }
        });
        contenido.add(new JScrollPane(tabla), BorderLayout.CENTER);
        contenido.add(acciones, BorderLayout.SOUTH);
        setContentPane(contenido);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent evento) {
                alCerrar.run();
            }
        });
        setSize(UIScale.scale(900), UIScale.scale(480));
        setLocationRelativeTo(null);
        cargarTareas();
    }

    private void cargarTareas() {
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        String tareaAnterior = tareaSeleccionada() == null ? null : tareaSeleccionada().getId();
        tareas = servicio.tareasDe(usuario);
        filas.setRowCount(0);
        for (Task tarea : tareas) {
            filas.addRow(new Object[] {
                    servicio.solicitudDe(tarea),
                    tarea.getName(),
                    usuario.equals(tarea.getAssignee()) ? "Asignada a vos" : "Disponible para tu grupo",
                    formato.format(tarea.getCreateTime())
            });
        }
        if (tareaAnterior != null) {
            for (int fila = 0; fila < tareas.size(); fila++) {
                if (tareas.get(fila).getId().equals(tareaAnterior)) {
                    tabla.setRowSelectionInterval(fila, fila);
                    return;
                }
            }
        }
        tabla.clearSelection();
        actualizarAcciones();
    }

    private Task tareaSeleccionada() {
        int fila = tabla.getSelectedRow();
        return fila < 0 ? null : tareas.get(fila);
    }

    private void actualizarAcciones() {
        Task tarea = tareaSeleccionada();
        reclamar.setEnabled(tarea != null && tarea.getAssignee() == null);
        completar.setEnabled(tarea != null && usuario.equals(tarea.getAssignee()));
    }

    private void reclamar() {
        Task tarea = tareaSeleccionada();
        if (tarea == null) {
            return;
        }
        try {
            servicio.reclamar(tarea.getId(), usuario);
            JOptionPane.showMessageDialog(this, "Tarea reclamada correctamente.", MaterialesApp.TITULO,
                    JOptionPane.INFORMATION_MESSAGE);
            cargarTareas();
        } catch (RuntimeException error) {
            JOptionPane.showMessageDialog(this, "No se pudo reclamar la tarea: " + error.getMessage(),
                    MaterialesApp.TITULO, JOptionPane.WARNING_MESSAGE);
            cargarTareas();
        }
    }

    private void completar() {
        Task tarea = tareaSeleccionada();
        if (tarea == null || JOptionPane.showConfirmDialog(this, "¿Los materiales ya están listos?",
                MaterialesApp.TITULO, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            servicio.completar(tarea.getId(), usuario);
            JOptionPane.showMessageDialog(this, "Tarea completada correctamente.", MaterialesApp.TITULO,
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException error) {
            JOptionPane.showMessageDialog(this, "No se pudo completar la tarea: " + error.getMessage(),
                    MaterialesApp.TITULO, JOptionPane.WARNING_MESSAGE);
        }
        cargarTareas();
    }
}
