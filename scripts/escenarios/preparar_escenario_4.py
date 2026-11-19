"""Escenario 4 de la defensa: cierre por falta de respuesta, luego de los recordatorios (§1.1).

Crea una solicitud con su propuesta enviada y un plazo de respuesta de un minuto solo para esa instancia, así la web
puede seguir con un plazo largo para los demás escenarios. En unos 3 minutos el proceso envía los dos recordatorios
y pasa a Archivar solicitud.
"""
from comun import ejecuciones, exigir_lugar_para

NUMERO = 401
PLAZO_CORTO = "PT1M"


def acortar_plazo(instancia):
    """Cambia el plazo solo en esta instancia. El timer lo lee recién cuando se envía la propuesta."""
    ejecuciones.flowable.put(f"{ejecuciones.FLOWABLE}/runtime/process-instances/{instancia}/variables", json=[
        {"name": "plazoRespuesta", "type": "string", "value": PLAZO_CORTO},
    ]).raise_for_status()


def main():
    exigir_lugar_para(1)
    instancia, curso = ejecuciones.crear_solicitud(NUMERO)
    acortar_plazo(instancia)
    ejecuciones.completar(instancia, "registrarYElaborarPropuesta", ejecuciones.PROPUESTA)
    print(f"Listo: curso {curso} con la propuesta enviada y un plazo de {PLAZO_CORTO}.")
    print("No respondas: en unos 3 minutos aparece «Archivar solicitud» en la bandeja de jefe_admin.")


if __name__ == "__main__":
    main()