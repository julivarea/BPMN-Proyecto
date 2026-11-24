"""Deja el entorno listo para otro ensayo de la defensa, sin reiniciar Docker (issue #43).

Borra todas las instancias del proceso organizarCurso, con su historial, y los datos de la base de la institución
(empresas, cuentas, cursos y propuestas). No toca los usuarios ni los grupos de Flowable ni el proceso desplegado.
Para un reinicio total sigue estando `docker compose down -v`.
"""
import os
import re
import sys

import psycopg

from comun import ejecuciones

# Todas juntas en un solo TRUNCATE: se referencian entre sí (curso → empresa, propuesta → curso…).
TABLAS = "propuesta, curso, cuenta_empresa, empresa"


def borrar_instancias(ruta):
    """Borra todas las instancias del proceso que devuelve la ruta, de a páginas, y cuenta cuántas borró."""
    borradas = 0
    while True:
        respuesta = ejecuciones.flowable.get(f"{ejecuciones.FLOWABLE}{ruta}",
                                             params={"processDefinitionKey": ejecuciones.PROCESO, "size": 100})
        respuesta.raise_for_status()
        instancias = respuesta.json()["data"]
        if not instancias:
            return borradas
        for instancia in instancias:
            ejecuciones.flowable.delete(f"{ejecuciones.FLOWABLE}{ruta}/{instancia['id']}").raise_for_status()
            borradas += 1


def base_de_la_institucion():
    """Servidor, puerto y base que se vacían: los de INSTITUCION_DB_URL (la misma que usa la web) o, si no está, la
    base `institucion` de localhost en el puerto POSTGRES_PORT (5432 por defecto)."""
    jdbc = os.environ.get("INSTITUCION_DB_URL")
    if not jdbc:
        return "localhost", os.environ.get("POSTGRES_PORT", "5432"), "institucion"
    partes = re.fullmatch(r"jdbc:postgresql://([^:/]+)(?::(\d+))?/(\w+)", jdbc)
    if not partes:
        sys.exit(f"INSTITUCION_DB_URL no tiene la forma jdbc:postgresql://servidor:puerto/base: {jdbc}")
    return partes.group(1), partes.group(2) or "5432", partes.group(3)


def vaciar_base_de_la_institucion():
    usuario = os.environ.get("INSTITUCION_DB_USER", "flowable")
    clave = os.environ.get("INSTITUCION_DB_PASSWORD", "flowable")
    servidor, puerto, base_de_datos = base_de_la_institucion()
    with psycopg.connect(f"postgresql://{usuario}:{clave}@{servidor}:{puerto}/{base_de_datos}") as base:
        base.execute(f"TRUNCATE {TABLAS} RESTART IDENTITY")


def main():
    print("Esto borra TODAS las solicitudes: instancias e historial en Flowable, y empresas, cuentas, cursos y")
    print("propuestas en la base de la institución. Los usuarios, los grupos y el proceso no se tocan.")
    servidor, puerto, base_de_datos = base_de_la_institucion()
    print(f"Flowable: {ejecuciones.FLOWABLE}")
    print(f"Base de la institución: {servidor}:{puerto}/{base_de_datos}")
    if input("Escribí «si» para continuar: ").strip().lower() != "si":
        sys.exit("No se borró nada.")

    en_curso = borrar_instancias("/runtime/process-instances")
    terminadas = borrar_instancias("/history/historic-process-instances")
    vaciar_base_de_la_institucion()
    print(f"Listo: {en_curso} instancias en curso y {terminadas} del historial borradas, y la base vaciada.")
    print("Las aulas reservadas por esas instancias siguen ocupadas en el servicio simulado (10 por defecto): si se "
          "agotan, reiniciá Flowable (docker compose restart flowable) para volver a tener todas libres.")


if __name__ == "__main__":
    main()