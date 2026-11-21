import csv
import os
from datetime import datetime
from getpass import getpass
from collections import Counter

import requests


# ConfiguraciÃ³n
BASE_URL = os.environ.get("FLOWABLE_URL", "http://localhost:8080/flowable-ui").rstrip("/") + "/process-api"

# ID del elemento <process id="..."> del BPMN.
PROCESS_KEY = "organizarCurso"

# Opcional: copiar aquÃ­ los IDs BPMN de las actividades y eventos
# de negociaciÃ³n que se quieran analizar.
# VacÃ­o = exportar todos los elementos histÃ³ricos finalizados.
ACTIVIDADES = {
    "solicitudRecibida",
    "evaluarViabilidad",
    "comunicarImposibilidad",
    "finNoViable",
    "registrarYElaborarPropuesta",
    "enviarPropuesta",
    "plazoRespuestaVencido",
    "enviarRecordatorio",
    "modificacionesRecibidas",
    "revisarPropuesta",
    "rechazoRecibido",
    "aceptacionRecibida",
    "archivarSolicitud",
    "finSolicitudArchivada",
    "habilitarAccesoEmpresa",
}
# Ejemplo ilustrativo:
# ACTIVIDADES = {"enviarPropuesta", "revisarPropuesta", "recordatorio"}

ARCHIVO_SALIDA = "eventos.csv"


def consultar_todos(session, recurso, filtros):
    """Recupera todas las pÃ¡ginas de una consulta REST."""
    inicio = 0

    while True:
        respuesta = session.get(
            f"{BASE_URL}/{recurso}",
            params={
                **filtros,
                "start": inicio,
                "size": 100,
            },
            timeout=30,
        )
        respuesta.raise_for_status()
        pagina = respuesta.json()
        registros = pagina["data"]

        if not registros:
            break

        yield from registros
        inicio += len(registros)

        if inicio >= pagina["total"]:
            break


def fecha_iso(valor):
    return datetime.fromisoformat(valor.replace("Z", "+00:00"))

def eventos_ocurridos(actividades):
    """IDs de los eventos de captura que ocurrieron de verdad.

    Al llegar al gateway de eventos, Flowable activa todos los eventos posibles (aceptación, rechazo,
    modificaciones, plazo) y, cuando ocurre uno, cancela los demás. Los cancelados también quedan en el
    historial con fecha de fin, y la API REST no informa que fueron cancelados. Un evento ocurrió si el
    proceso siguió por su flecha de salida (flujo_<evento>_<destino>, convención del modelo) en la misma
    ejecución, después de que el evento terminó.
    """
    eventos = [
        a for a in actividades
        if a["activityType"] == "intermediateCatchEvent" and a.get("endTime")
    ]
    ocurridos = set()
    for flecha in actividades:
        if flecha["activityType"] != "sequenceFlow":
            continue
        candidatos = [
            evento for evento in eventos
            if flecha["activityId"].startswith(f"flujo_{evento['activityId']}_")
            and evento["executionId"] == flecha["executionId"]
            and fecha_iso(evento["endTime"]) <= fecha_iso(flecha["startTime"])
        ]
        if candidatos:
            # La flecha sale del último evento que terminó antes que ella en esa ejecución.
            ocurrido = max(candidatos, key=lambda evento: fecha_iso(evento["endTime"]))
            ocurridos.add(ocurrido["id"])
    return ocurridos

def resumir(eventos):
    """Muestra cuántas veces aparece cada actividad, para revisar el CSV de un vistazo."""
    conteo = Counter(evento["actividad"] for evento in eventos)
    ancho = max(len(actividad) for actividad in conteo)
    print(f"\n{'Actividad':<{ancho}}  Veces")
    print(f"{'-' * ancho}  -----")
    for actividad, veces in conteo.most_common():
        print(f"{actividad:<{ancho}}  {veces:>5}")


def main():
    usuario = input("Usuario de Flowable: ")
    clave = getpass("Contraseña: ")

    eventos = []

    with requests.Session() as session:
        session.auth = (usuario, clave)

        instancias = consultar_todos(
            session,
            "history/historic-process-instances",
            {
                "processDefinitionKey": PROCESS_KEY,
                "finished": "true",
            },
        )

        for instancia in instancias:
            # Excluir instancias eliminadas administrativamente.
            if instancia.get("deleteReason"):
                continue

            actividades = list(consultar_todos(
                session,
                "history/historic-activity-instances",
                {"processInstanceId": instancia["id"]},
            ))
            ocurridos = eventos_ocurridos(actividades)

            for actividad in actividades:
                # No exportar elementos sin fecha de finalización.
                if not actividad.get("endTime"):
                    continue

                # No exportar los eventos que el gateway de eventos canceló.
                if (
                    actividad["activityType"] == "intermediateCatchEvent"
                    and actividad["id"] not in ocurridos
                ):
                    continue

                if (
                    ACTIVIDADES
                    and actividad["activityId"] not in ACTIVIDADES
                ):
                    continue

                eventos.append({
                    "instancia": instancia["id"],
                    "definicion": actividad["processDefinitionId"],
                    "evento_id": actividad["id"],
                    "actividad_id": actividad["activityId"],
                    "actividad": (
                        actividad.get("activityName")
                        or actividad["activityId"]
                    ),
                    "tipo": actividad["activityType"],
                    "inicio": actividad["startTime"],
                    "fecha_hora": actividad["endTime"],
                    "recurso": actividad.get("assignee") or "",
                })

    # Ordenar por instancia y fecha de finalizaciÃ³n.
    # El ID da un orden estable si coinciden las marcas temporales.
    eventos.sort(
        key=lambda evento: (
            evento["instancia"],
            fecha_iso(evento["fecha_hora"]),
            fecha_iso(evento["inicio"]),
            evento["evento_id"],
        )
    )

    columnas = [
        "instancia", "definicion", "evento_id",
        "actividad_id", "actividad", "tipo",
        "inicio", "fecha_hora", "recurso",
    ]

    with open(
        ARCHIVO_SALIDA, "w", newline="", encoding="utf-8"
    ) as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=columnas, lineterminator="\n")
        escritor.writeheader()
        escritor.writerows(eventos)

    cantidad_instancias = len({
        evento["instancia"] for evento in eventos
    })

    print(
        f"Generado {ARCHIVO_SALIDA}: "
        f"{len(eventos)} eventos de {cantidad_instancias} instancias."
    )
    resumir(eventos)


if __name__ == "__main__":
    main()
