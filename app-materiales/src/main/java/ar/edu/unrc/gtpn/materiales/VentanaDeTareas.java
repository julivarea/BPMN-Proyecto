package ar.edu.unrc.gtpn.materiales;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

import org.flowable.task.api.Task;

import com.formdev.flatlaf.util.UIScale;

import ar.edu.unrc.gtpn.materiales.historial.ConsultasHistoricas;
import ar.edu.unrc.gtpn.materiales.historial.ConsultasHistoricas.DuracionRamas;
import ar.edu.unrc.gtpn.materiales.historial.ConsultasHistoricas.ResultadoSolicitud;
import ar.edu.unrc.gtpn.materiales.historial.ConsultasHistoricas.TareaPorPersona;

/**
 * Ventana de la app: una pestaña con las tareas "Preparar materiales" del usuario, con acciones de reclamar y
 * completar (#24), otra con los resultados de las solicitudes terminadas (§4.3.1, #30), otra con la duración promedio
 * de las ramas de la preparación (§4.3.2, #31) y otra con las tareas completadas por persona (§4.3.3, #32).
 */
final class VentanaDeTareas extends JFrame {

    private static final String[] COLUMNAS = {"Solicitud", "Tarea", "Estado", "Creada"};
    private static final String[] COLUMNAS_RESULTADOS = {"Resultado", "Cantidad", "Porcentaje (%)"};
    private static final String[] COLUMNAS_DURACIONES = {"Rama", "Duración Promedio", "Instancias"};
    private static final String[] COLUMNAS_PERSONAS = {"Usuario", "Tareas Completadas"};

    private final transient ServicioDeTareas servicio;
    private final transient ConsultasHistoricas consultas;
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
    private final DefaultTableModel filasResultados = new DefaultTableModel(COLUMNAS_RESULTADOS, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tablaResultados = new JTable(filasResultados);
    private final JLabel sinResultados = new JLabel("No hay instancias terminadas para mostrar.");
    private final DefaultTableModel filasDuraciones = new DefaultTableModel(COLUMNAS_DURACIONES, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tablaDuraciones = new JTable(filasDuraciones);
    private final JLabel sinDuraciones = new JLabel("No hay preparaciones completadas para calcular promedios.");
    private final DefaultTableModel filasPersonas = new DefaultTableModel(COLUMNAS_PERSONAS, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tablaPersonas = new JTable(filasPersonas);
    private final JLabel sinPersonas = new JLabel("No hay tareas completadas para mostrar.");

    VentanaDeTareas(ServicioDeTareas servicio, ConsultasHistoricas consultas, String usuario, Runnable alCerrar) {
        super(MaterialesApp.TITULO + " · " + usuario);
        this.servicio = servicio;
        this.consultas = consultas;
        this.usuario = usuario;

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Mis tareas", panelDeTareas());
        pestanas.addTab("Resultados de Solicitudes (§4.3.1)", panelDeResultados());
        pestanas.addTab("Duración de Ramas (§4.3.2)", panelDeDuraciones());
        pestanas.addTab("Tareas por Persona (§4.3.3)", panelDePersonas());
        setContentPane(pestanas);

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
        cargarResultados();
        cargarDuraciones();
        cargarPersonas();
    }

    private JPanel panelDeTareas() {
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
        return contenido;
    }

    private JPanel panelDeResultados() {
        JLabel titulo = new JLabel("Resultados de Solicitudes");
        titulo.putClientProperty("FlatLaf.styleClass", "h2");
        JLabel subtitulo = new JLabel("Solicitudes terminadas agrupadas por resultado (§4.3.1)");
        subtitulo.putClientProperty("FlatLaf.styleClass", "light");
        JPanel encabezado = new JPanel(new BorderLayout(0, 4));
        encabezado.add(titulo, BorderLayout.NORTH);
        encabezado.add(subtitulo, BorderLayout.SOUTH);

        tablaResultados.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sinResultados.putClientProperty("FlatLaf.styleClass", "light");
        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.add(new JScrollPane(tablaResultados), BorderLayout.CENTER);
        centro.add(sinResultados, BorderLayout.SOUTH);

        JButton actualizar = new JButton("Actualizar");
        actualizar.putClientProperty("JButton.buttonType", "roundRect");
        actualizar.addActionListener(evento -> cargarResultados());
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIScale.scale(8), 0));
        acciones.add(actualizar);

        JPanel contenido = new JPanel(new BorderLayout(0, 12));
        int margen = UIScale.scale(20);
        contenido.setBorder(BorderFactory.createEmptyBorder(margen, margen, margen, margen));
        contenido.add(encabezado, BorderLayout.NORTH);
        contenido.add(centro, BorderLayout.CENTER);
        contenido.add(acciones, BorderLayout.SOUTH);
        return contenido;
    }

    private JPanel panelDeDuraciones() {
        JLabel titulo = new JLabel("Duración de Ramas");
        titulo.putClientProperty("FlatLaf.styleClass", "h2");
        JLabel subtitulo = new JLabel("Promedio de las ramas de la preparación (§4.3.2)");
        subtitulo.putClientProperty("FlatLaf.styleClass", "light");
        JPanel encabezado = new JPanel(new BorderLayout(0, 4));
        encabezado.add(titulo, BorderLayout.NORTH);
        encabezado.add(subtitulo, BorderLayout.SOUTH);

        tablaDuraciones.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sinDuraciones.putClientProperty("FlatLaf.styleClass", "light");
        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.add(new JScrollPane(tablaDuraciones), BorderLayout.CENTER);
        centro.add(sinDuraciones, BorderLayout.SOUTH);

        JButton actualizar = new JButton("Actualizar");
        actualizar.putClientProperty("JButton.buttonType", "roundRect");
        actualizar.addActionListener(evento -> cargarDuraciones());
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIScale.scale(8), 0));
        acciones.add(actualizar);

        JPanel contenido = new JPanel(new BorderLayout(0, 12));
        int margen = UIScale.scale(20);
        contenido.setBorder(BorderFactory.createEmptyBorder(margen, margen, margen, margen));
        contenido.add(encabezado, BorderLayout.NORTH);
        contenido.add(centro, BorderLayout.CENTER);
        contenido.add(acciones, BorderLayout.SOUTH);
        return contenido;
    }

    private JPanel panelDePersonas() {
        JLabel titulo = new JLabel("Tareas por Persona");
        titulo.putClientProperty("FlatLaf.styleClass", "h2");
        JLabel subtitulo = new JLabel("Tareas completadas por cada usuario (§4.3.3)");
        subtitulo.putClientProperty("FlatLaf.styleClass", "light");
        JPanel encabezado = new JPanel(new BorderLayout(0, 4));
        encabezado.add(titulo, BorderLayout.NORTH);
        encabezado.add(subtitulo, BorderLayout.SOUTH);

        tablaPersonas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sinPersonas.putClientProperty("FlatLaf.styleClass", "light");
        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.add(new JScrollPane(tablaPersonas), BorderLayout.CENTER);
        centro.add(sinPersonas, BorderLayout.SOUTH);

        JButton actualizar = new JButton("Actualizar");
        actualizar.putClientProperty("JButton.buttonType", "roundRect");
        actualizar.addActionListener(evento -> cargarPersonas());
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIScale.scale(8), 0));
        acciones.add(actualizar);

        JPanel contenido = new JPanel(new BorderLayout(0, 12));
        int margen = UIScale.scale(20);
        contenido.setBorder(BorderFactory.createEmptyBorder(margen, margen, margen, margen));
        contenido.add(encabezado, BorderLayout.NORTH);
        contenido.add(centro, BorderLayout.CENTER);
        contenido.add(acciones, BorderLayout.SOUTH);
        return contenido;
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

    private void cargarResultados() {
        filasResultados.setRowCount(0);
        List<ResultadoSolicitud> resultados;
        try {
            resultados = consultas.resultadosDeLasSolicitudes();
        } catch (RuntimeException error) {
            sinResultados.setText("No se pudieron consultar los resultados: " + error.getMessage());
            sinResultados.setVisible(true);
            return;
        }
        for (ResultadoSolicitud resultado : resultados) {
            filasResultados.addRow(new Object[] {
                    resultado.resultado(),
                    resultado.cantidad(),
                    String.format(Locale.ROOT, "%.1f", resultado.porcentaje())
            });
        }
        sinResultados.setText("No hay instancias terminadas para mostrar.");
        sinResultados.setVisible(resultados.isEmpty());
    }

    private void cargarDuraciones() {
        filasDuraciones.setRowCount(0);
        DuracionRamas duraciones;
        try {
            duraciones = consultas.duracionPromedioDeLasRamas();
        } catch (RuntimeException error) {
            sinDuraciones.setText("No se pudieron consultar las duraciones: " + error.getMessage());
            sinDuraciones.setVisible(true);
            return;
        }
        if (duraciones.totalInstancias() > 0) {
            filasDuraciones.addRow(new Object[] {
                    "Inscripciones", formatearDuracion(duraciones.inscripciones()), duraciones.totalInstancias()});
            filasDuraciones.addRow(new Object[] {
                    "Materiales", formatearDuracion(duraciones.materiales()), duraciones.totalInstancias()});
            filasDuraciones.addRow(new Object[] {
                    "Recursos", formatearDuracion(duraciones.recursos()), duraciones.totalInstancias()});
        }
        sinDuraciones.setText("No hay preparaciones completadas para calcular promedios.");
        sinDuraciones.setVisible(duraciones.totalInstancias() == 0);
    }

    private void cargarPersonas() {
        filasPersonas.setRowCount(0);
        List<TareaPorPersona> personas;
        try {
            personas = consultas.tareasCompletadasPorPersona();
        } catch (RuntimeException error) {
            sinPersonas.setText("No se pudieron consultar las tareas por persona: " + error.getMessage());
            sinPersonas.setVisible(true);
            return;
        }
        for (TareaPorPersona persona : personas) {
            filasPersonas.addRow(new Object[] {persona.usuario(), persona.cantidad()});
        }
        sinPersonas.setText("No hay tareas completadas para mostrar.");
        sinPersonas.setVisible(personas.isEmpty());
    }

    /** Formato legible: días y horas cuando dura más de un día; si no, {@code HH:mm:ss}. */
    static String formatearDuracion(Duration duracion) {
        long dias = duracion.toDays();
        if (dias > 0) {
            return String.format(Locale.ROOT, "%d días %d h", dias, duracion.toHoursPart());
        }
        return String.format(Locale.ROOT, "%02d:%02d:%02d",
                duracion.toHoursPart(), duracion.toMinutesPart(), duracion.toSecondsPart());
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
