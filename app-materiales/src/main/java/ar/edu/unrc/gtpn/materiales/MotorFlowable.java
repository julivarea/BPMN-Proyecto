package ar.edu.unrc.gtpn.materiales;

import java.util.TimeZone;

import org.flowable.common.engine.impl.persistence.StrongUuidGenerator;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;

/**
 * Motor Flowable embebido que opera sobre la misma base de datos que el servidor (enunciado 4.2.1).
 * La configuración es delicada porque comparte la base con el servidor: ver docs/09-app-materiales.md §3.
 */
final class MotorFlowable {

    /** Zona horaria de la máquina, para mostrar las fechas. Se toma al cargar la clase, antes de pasar a UTC. */
    private static final TimeZone ZONA_LOCAL = TimeZone.getDefault();

    private MotorFlowable() {
    }

    /** Conecta el motor a la BD del servidor Flowable. */
    static ProcessEngine conectar() {
        usarUtc();
        return configuracion().buildProcessEngine();
    }

    /**
     * Las fechas del historial se guardan como {@code timestamp} sin zona. El servidor Flowable corre en UTC; si la app
     * escribiera en la hora local (Argentina es UTC-3), las fechas que ella completa (por ejemplo el fin de *Preparar
     * materiales*) quedarían 3 horas antes que las del servidor, y las duraciones del §4.3.2 saldrían negativas. Por eso
     * el motor trabaja en UTC, igual que el servidor.
     */
    static void usarUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    /** Zona horaria local de la máquina, para mostrar las fechas al usuario (el motor trabaja en UTC). */
    static TimeZone zonaLocal() {
        return ZONA_LOCAL;
    }

    static ProcessEngineConfiguration configuracion() {
        ProcessEngineConfiguration configuracion = ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration()
                .setJdbcDriver("org.postgresql.Driver")
                .setJdbcUrl(variableDeEntorno("FLOWABLE_DB_URL", "jdbc:postgresql://localhost:5432/flowable"))
                .setJdbcUsername(variableDeEntorno("FLOWABLE_DB_USER", "flowable"))
                .setJdbcPassword(variableDeEntorno("FLOWABLE_DB_PASSWORD", "flowable"))
                // Las tablas son del servidor: no se crean ni se actualizan. Si las versiones no coinciden, falla al conectar.
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE)
                // Timers y tareas asíncronas los ejecuta solo el servidor, que es quien tiene las clases del proceso.
                .setAsyncExecutorActivate(false);
        // Mismo formato de IDs que el servidor (UUID).
        configuracion.setIdGenerator(new StrongUuidGenerator());
        return configuracion;
    }

    private static String variableDeEntorno(String nombre, String valorPorDefecto) {
        return System.getenv().getOrDefault(nombre, valorPorDefecto);
    }
}
