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
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.flowable.task.api.Task;

import com.formdev.flatlaf.util.UIScale;

/** Lista de tareas "Preparar materiales" del usuario. Reclamar y completar se agregan en el issue #24. */
final class VentanaDeTareas extends JFrame {

    private static final String[] COLUMNAS = {"Solicitud", "Tarea", "Estado", "Creada"};

    private final transient ServicioDeTareas servicio;
    private final String usuario;
    private final DefaultTableModel filas = new DefaultTableModel(COLUMNAS, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };

    VentanaDeTareas(ServicioDeTareas servicio, String usuario, Runnable alCerrar) {
        super(MaterialesApp.TITULO + " · " + usuario);
        this.servicio = servicio;
        this.usuario = usuario;

        JButton actualizar = new JButton("Actualizar");
        actualizar.putClientProperty("JButton.buttonType", "roundRect");
        actualizar.addActionListener(evento -> cargarTareas());
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
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
        contenido.add(new JScrollPane(new JTable(filas)), BorderLayout.CENTER);
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
        filas.setRowCount(0);
        for (Task tarea : servicio.tareasDe(usuario)) {
            filas.addRow(new Object[] {
                    servicio.solicitudDe(tarea),
                    tarea.getName(),
                    usuario.equals(tarea.getAssignee()) ? "Asignada a vos" : "Disponible para tu grupo",
                    formato.format(tarea.getCreateTime())
            });
        }
    }
}
