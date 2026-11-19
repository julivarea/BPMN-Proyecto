"""Deja el entorno listo para otro ensayo de la defensa, sin reiniciar Docker (issue #43).

Borra todas las instancias del proceso organizarCurso, con su historial, y los datos de la base de la institución
(empresas, cuentas, cursos y propuestas). No toca los usuarios ni los grupos de Flowable ni el proceso desplegado.
Para un reinicio total sigue estando `docker compose down -v`.
"""
import os
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


def vaciar_base_de_la_institucion():
    usuario = os.environ.get("INSTITUCION_DB_USER", "flowable")
    clave = os.environ.get("INSTITUCION_DB_PASSWORD", "flowable")
    with psycopg.connect(f"postgresql://{usuario}:{clave}@localhost:5432/institucion") as base:
        base.execute(f"TRUNCATE {TABLAS} RESTART IDENTITY")


def main():
    print("Esto borra TODAS las solicitudes: instancias e historial en Flowable, y empresas, cuentas, cursos y")
    print("propuestas en la base de la institución. Los usuarios, los grupos y el proceso no se tocan.")
    if input("Escribí «si» para continuar: ").strip().lower() != "si":
        sys.exit("No se borró nada.")

    en_curso = borrar_instancias("/runtime/process-instances")
    terminadas = borrar_instancias("/history/historic-process-instances")
    vaciar_base_de_la_institucion()
    print(f"Listo: {en_curso} instancias en curso y {terminadas} del historial borradas, y la base vaciada.")


if __name__ == "__main__":
    main()