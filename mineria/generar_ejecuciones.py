"""Genera ejecuciones de prueba del proceso organizarCurso (issue #33).

Crea solicitudes con el formulario de la web y las lleva por distintos caminos usando la API REST de Flowable,
para tener historial con todos los resultados posibles (enunciado §4.3 y §4.4).

Requiere el entorno levantado, el proceso desplegado y la web corriendo con un plazo de respuesta corto:
    PLAZO_RESPUESTA=PT15S ./mvnw -pl web spring-boot:run
"""
import re
import sys
import time
from collections import Counter
from datetime import datetime

import requests

WEB = "http://localhost:8081"
FLOWABLE = "http://localhost:8080/flowable-ui/process-api"
PROCESO = "organizarCurso"
RESULTADOS_POSIBLES = {"FINALIZADO", "CANCELADO", "RECHAZADO", "NO_VIABLE", "SIN_RESPUESTA"}

# La web identifica el formulario por la sesión (cookie) y el token CSRF, así que se usa siempre la misma sesión.
web = requests.Session()
flowable = requests.Session()
flowable.auth = ("admin", "test")

TOKEN_CSRF = re.compile(r'name="_csrf"\s+value="([^"]+)"')

ESPERA_MAXIMA = 120  # segundos
INTERVALO = 1

PROPUESTA = {
    "programa": "Módulo 1: introducción. Módulo 2: práctica.",
    "presupuesto": 150000,
    "fechasPosibles": "Lunes y miércoles por la mañana",
}

# Se alternan para que el historial tenga tareas de distintas personas (§4.3.3).
ADMINISTRATIVOS = ["administrativo", "jefe_admin"]
DOCENTES = ["docente1", "docente2", "jefe_docente"]


# --- Web y Flowable ---------------------------------------------------------------------------------------------

def crear_solicitud(numero):
    """Envía el formulario público como lo haría una empresa. Devuelve el id de la instancia y su businessKey."""
    formulario = web.get(f"{WEB}/solicitar")
    formulario.raise_for_status()
    token = TOKEN_CSRF.search(formulario.text).group(1)

    respuesta = web.post(f"{WEB}/solicitar", data={
        "_csrf": token,
        "cuit": f"30{numero:08d}9",  # 11 dígitos, distinto en cada solicitud
        "razonSocial": f"Empresa de prueba {numero}",
        "contactoNombre": "Contacto de prueba",
        "contactoEmail": f"contacto{numero}@prueba.com",
        "nombre": f"Curso de prueba {numero}",
        "descripcion": "Generado por mineria/generar_ejecuciones.py",
        "participantesEstimados": "10",
        "fechasPreferidas": "Cualquier día hábil",
    })
    respuesta.raise_for_status()
    # Una solicitud viable termina en la confirmación; una no viable vuelve al formulario con un aviso.
    aceptada = respuesta.url.endswith("/solicitar/enviada")
    no_viable = respuesta.url.endswith("/solicitar") and 'class="aviso"' in respuesta.text
    if not (aceptada or no_viable):
        raise RuntimeError(f"La web no aceptó la solicitud {numero}: revisá la consola de la web.")
    return instancia_mas_reciente()


def instancia_mas_reciente():
    """La última instancia iniciada. Se busca en el historial porque las no viables terminan enseguida."""
    respuesta = flowable.get(f"{FLOWABLE}/history/historic-process-instances", params={
        "processDefinitionKey": PROCESO,
        "sort": "startTime",
        "order": "desc",
        "size": 1,
    })
    respuesta.raise_for_status()
    instancia = respuesta.json()["data"][0]
    return instancia["id"], instancia["businessKey"]


def esperar(descripcion, consulta):
    """Repite la consulta hasta que devuelva algo: los timers y las tareas asíncronas tardan en aparecer."""
    limite = time.monotonic() + ESPERA_MAXIMA
    while time.monotonic() < limite:
        resultado = consulta()
        if resultado:
            return resultado
        time.sleep(INTERVALO)
    raise TimeoutError(f"Pasaron {ESPERA_MAXIMA} s esperando {descripcion}.")


def primero(ruta, parametros):
    """Primer elemento de una consulta de Flowable, o None si no hay ninguno."""
    respuesta = flowable.get(f"{FLOWABLE}{ruta}", params=parametros)
    respuesta.raise_for_status()
    datos = respuesta.json()["data"]
    return datos[0] if datos else None


def como_lista(variables):
    """Formato de Flowable para las variables: una lista de nombre y valor. Las listas viajan como JSON."""
    lista = []
    for nombre, valor in variables.items():
        variable = {"name": nombre, "value": valor}
        if isinstance(valor, list):
            variable["type"] = "json"
        lista.append(variable)
    return lista


def completar(instancia, clave, variables=None, reclamar_como=None):
    """Espera a que la tarea esté pendiente y la completa. Las de grupo se reclaman antes, como pide el §2."""
    tarea = esperar(f"la tarea {clave}",
                    lambda: primero("/runtime/tasks", {"processInstanceId": instancia, "taskDefinitionKey": clave}))
    if reclamar_como:
        accion_sobre_tarea(tarea["id"], {"action": "claim", "assignee": reclamar_como})
    accion_sobre_tarea(tarea["id"], {"action": "complete", "variables": como_lista(variables or {})})


def accion_sobre_tarea(tarea_id, cuerpo):
    flowable.post(f"{FLOWABLE}/runtime/tasks/{tarea_id}", json=cuerpo).raise_for_status()


def enviar_mensaje(instancia, mensaje, variables=None):
    """Simula una comunicación de la empresa: espera a que la instancia la esté esperando y se la entrega."""
    ejecucion = esperar(f"que la instancia espere el mensaje {mensaje}",
                        lambda: primero("/runtime/executions",
                                        {"processInstanceId": instancia, "messageEventSubscriptionName": mensaje}))
    flowable.put(f"{FLOWABLE}/runtime/executions/{ejecucion['id']}", json={
        "action": "messageEventReceived",
        "messageName": mensaje,
        "variables": como_lista(variables or {}),
    }).raise_for_status()


def resultado(instancia):
    """Resultado con el que terminó la instancia (variable resultado del historial)."""
    variable = primero("/history/historic-variable-instances",
                       {"processInstanceId": instancia, "variableName": "resultado"})
    return variable["variable"]["value"]


def esperar_fin(instancia):
    """Espera a que la instancia termine y devuelve su resultado."""
    esperar("que termine la instancia",
            lambda: primero("/history/historic-process-instances", {"processInstanceId": instancia, "finished": "true"}))
    return resultado(instancia)


# --- Negociación sin aceptación ---------------------------------------------------------------------------------

def camino_rechazo(numero):
    """La empresa rechaza la propuesta y el jefe administrativo archiva la solicitud → RECHAZADO."""
    instancia, curso = crear_solicitud(numero)
    completar(instancia, "registrarYElaborarPropuesta", PROPUESTA)
    enviar_mensaje(instancia, "rechazoPropuesta")
    completar(instancia, "archivarSolicitud")
    return curso, esperar_fin(instancia)


def camino_sin_respuesta(numero):
    """La empresa no responde: dos recordatorios y, al vencer el tercer plazo, se archiva → SIN_RESPUESTA."""
    instancia, curso = crear_solicitud(numero)
    completar(instancia, "registrarYElaborarPropuesta", PROPUESTA)
    completar(instancia, "archivarSolicitud")  # aparece recién después de los tres plazos
    return curso, esperar_fin(instancia)


def camino_modificaciones_y_rechazo(numero):
    """La empresa pide cambios, recibe la nueva versión y la rechaza → RECHAZADO, con una vuelta de negociación."""
    instancia, curso = crear_solicitud(numero)
    completar(instancia, "registrarYElaborarPropuesta", PROPUESTA)
    enviar_mensaje(instancia, "modificacionesPropuesta", {"observacionesEmpresa": "Bajar el presupuesto."})
    completar(instancia, "revisarPropuesta", PROPUESTA | {"presupuesto": 120000})
    enviar_mensaje(instancia, "rechazoPropuesta")
    completar(instancia, "archivarSolicitud")
    return curso, esperar_fin(instancia)


# --- Aceptación, preparación y desarrollo -----------------------------------------------------------------------

def ahora():
    """Fecha de inicio del curso: la actual, así el timer "fecha programada" dispara enseguida (docs/06 §5)."""
    return datetime.now().strftime("%Y-%m-%dT%H:%M:%S")


def llevar_a_preparacion(numero, con_modificaciones=False):
    """Negocia y acepta la propuesta: queda habilitado el acceso y empieza la preparación."""
    instancia, curso = crear_solicitud(numero)
    completar(instancia, "registrarYElaborarPropuesta", PROPUESTA)
    if con_modificaciones:
        enviar_mensaje(instancia, "modificacionesPropuesta", {"observacionesEmpresa": "Cambiar las fechas."})
        completar(instancia, "revisarPropuesta", PROPUESTA | {"fechasPosibles": "Martes y jueves"})
    enviar_mensaje(instancia, "aceptacionPropuesta", {"fechaInicioCurso": ahora()})
    return instancia, curso


def gestionar_recursos(instancia, numero, aula, aula_virtual, equipamiento):
    """Rama de recursos: evaluar y realizar las acciones elegidas. El aula física se reserva sola."""
    administrativo = ADMINISTRATIVOS[numero % 2]
    completar(instancia, "evaluarRecursos", {
        "requiereAula": aula,
        "requiereAulaVirtual": aula_virtual,
        "requiereEquipamiento": equipamiento,
    }, reclamar_como=administrativo)
    if aula_virtual:
        completar(instancia, "solicitarAulaVirtual", reclamar_como=administrativo)
    if equipamiento:
        completar(instancia, "contratarEquipamiento", reclamar_como=administrativo)


def preparar(instancia, numero, aula, aula_virtual, equipamiento):
    """Las tres ramas en paralelo: inscripciones, materiales y recursos (§1.2)."""
    enviar_mensaje(instancia, "nominaParticipantes", {"participantes": ["Ana Gómez", "Luis Pérez", "Sofía Díaz"]})
    completar(instancia, "confeccionarListaDefinitiva", {"listaDefinitiva": "Ana Gómez\nLuis Pérez\nSofía Díaz"})
    completar(instancia, "prepararMateriales", reclamar_como=ADMINISTRATIVOS[numero % 2])
    gestionar_recursos(instancia, numero, aula, aula_virtual, equipamiento)


def desarrollar(instancia, numero, equipamiento):
    """Docente, dictado, informe y cierre del expediente; al final se liberan los recursos (§1.4)."""
    completar(instancia, "definirDocente", {"docenteAsignado": DOCENTES[numero % 3]})
    completar(instancia, "desarrollarCurso")
    completar(instancia, "elaborarInformeResultados", {"informeResultados": "Curso dictado sin inconvenientes."})
    completar(instancia, "enviarInformeYArchivarExpediente")
    if equipamiento:
        completar(instancia, "liberarEquipamiento", reclamar_como=ADMINISTRATIVOS[numero % 2])


def finalizar(instancia, numero, aula=False, aula_virtual=False, equipamiento=False):
    """Prepara, dicta y cierra un curso que ya está en preparación → FINALIZADO."""
    preparar(instancia, numero, aula, aula_virtual, equipamiento)
    desarrollar(instancia, numero, equipamiento)
    return esperar_fin(instancia)


def cancelar(instancia, numero, con_recursos_gestionados):
    """La empresa cancela durante la preparación; se compensan los recursos ya gestionados (§1.3) → CANCELADO."""
    if con_recursos_gestionados:
        gestionar_recursos(instancia, numero, aula=True, aula_virtual=False, equipamiento=True)
    enviar_mensaje(instancia, "cancelacionCurso")
    if con_recursos_gestionados:
        completar(instancia, "liberarEquipamiento", reclamar_como=ADMINISTRATIVOS[numero % 2])
    # La cancelación siempre termina con esta tarea; Flowable no deja completarla antes que las liberaciones.
    completar(instancia, "confirmarCompensaciones", reclamar_como=ADMINISTRATIVOS[numero % 2])
    return esperar_fin(instancia)


# --- Comando --------------------------------------------------------------------------------------------------

def main():
    resultados = Counter()

    def informar(curso, final, como):
        resultados[final] += 1
        print(f"  curso {curso:>4}  {final:<14} {como}")

    print("1. Negociación sin aceptación")
    for numero, camino in enumerate([camino_rechazo, camino_sin_respuesta, camino_modificaciones_y_rechazo], start=1):
        curso, final = camino(numero)
        informar(curso, final, camino.__doc__.split(" →")[0])

    print("2. Cinco cursos aceptados, en preparación (cuentan como cursos en ejecución)")
    en_preparacion = []
    for numero in range(4, 9):
        instancia, curso = llevar_a_preparacion(numero, con_modificaciones=numero % 2 == 0)
        en_preparacion.append((numero, instancia, curso))
        print(f"  curso {curso:>4}  en preparación")

    print("3. Solicitudes con cinco cursos en ejecución")
    for numero in (9, 10):
        instancia, curso = crear_solicitud(numero)
        informar(curso, esperar_fin(instancia), "la regla de viabilidad la rechaza")

    print("4. Cierre de los cursos en preparación")
    finales = [
        {"aula": True},
        {"aula_virtual": True, "equipamiento": True},
        {"aula": True, "aula_virtual": True, "equipamiento": True},
    ]
    for (numero, instancia, curso), recursos in zip(en_preparacion, finales):
        informar(curso, finalizar(instancia, numero, **recursos), f"recursos: {', '.join(recursos)}")
    for (numero, instancia, curso), con_recursos in zip(en_preparacion[3:], [False, True]):
        como = "con aula y equipamiento ya gestionados" if con_recursos else "antes de gestionar recursos"
        informar(curso, cancelar(instancia, numero, con_recursos), f"cancelado {como}")

    print("\nResultados:", dict(resultados))
    faltantes = RESULTADOS_POSIBLES - resultados.keys()
    if faltantes:
        sys.exit(f"Faltan resultados: {', '.join(sorted(faltantes))}")
    print("Están los cinco resultados posibles.")


if __name__ == "__main__":
    main()