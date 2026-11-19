"""Funciones compartidas por los scripts de escenarios de la defensa (issue #43).

Reusa las de mineria/generar_ejecuciones.py (#33): crear solicitudes por el formulario de la web y avanzarlas por
la API REST de Flowable.
"""
import sys
from pathlib import Path

RAIZ_DEL_REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RAIZ_DEL_REPO / "mineria"))

import generar_ejecuciones as ejecuciones  # noqa: E402  (el import va después de ajustar la ruta)


def cursos_en_ejecucion():
    """Instancias en curso con el acceso habilitado: las que cuenta la regla de viabilidad (§3.1)."""
    respuesta = ejecuciones.flowable.post(f"{ejecuciones.FLOWABLE}/query/process-instances", json={
        "processDefinitionKey": ejecuciones.PROCESO,
        "variables": [{"name": "accesoHabilitado", "value": True, "operation": "equals", "type": "boolean"}],
    })
    respuesta.raise_for_status()
    return respuesta.json()["total"]