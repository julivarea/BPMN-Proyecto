package ar.edu.unrc.gtpn.materiales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.TimeZone;

import org.flowable.common.engine.impl.persistence.StrongUuidGenerator;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.jupiter.api.Test;

/**
 * El motor de la app comparte la base con el servidor: estos tests fallan si alguien cambia la configuración que lo
 * hace seguro (docs/09-app-materiales.md §3). No se conectan a ninguna base.
 */
class MotorFlowableTest {

    private final ProcessEngineConfiguration configuracion = MotorFlowable.configuracion();

    @Test
    void noModificaLasTablasDelServidor() {
        assertEquals(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE, configuracion.getDatabaseSchemaUpdate());
    }

    @Test
    void noEjecutaTimersNiTareasAsincronas() {
        assertFalse(configuracion.isAsyncExecutorActivate());
    }

    @Test
    void trabajaEnUtcComoElServidorParaQueLasFechasDelHistorialSeanCoherentes() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));

            MotorFlowable.usarUtc();

            assertEquals("UTC", TimeZone.getDefault().getID());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void generaLosIdsConElMismoFormatoQueElServidor() {
        assertInstanceOf(StrongUuidGenerator.class, ((ProcessEngineConfigurationImpl) configuracion).getIdGenerator());
    }
}
