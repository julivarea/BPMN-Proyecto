"""Escenario 5 de la defensa: imposibilidad de continuar por el límite de cursos en ejecución (§1.1, §3.1).

Deja cinco cursos aceptados y en preparación, para registrar la sexta solicitud en vivo desde la web y ver que
termina como no viable.
"""
from comun import cursos_en_ejecucion, ejecuciones
from comun import LIMITE_DE_CURSOS as LIMITE, cursos_en_ejecucion, ejecuciones

PRIMER_NUMERO = 501  # empresas de prueba propias de este escenario, separadas de las de la minería


def main():
    actuales = cursos_en_ejecucion()
    faltan = max(0, LIMITE - actuales)
    print(f"Cursos en ejecución: {actuales}. Faltan {faltan} para llegar a {LIMITE}.")
    for numero in range(PRIMER_NUMERO, PRIMER_NUMERO + faltan):
        _, curso = ejecuciones.llevar_a_preparacion(numero)
        print(f"  curso {curso}: en preparación")
    print("\nListo. Registrá una solicitud nueva en http://localhost:8081/solicitar: tiene que terminar como no viable.")


if __name__ == "__main__":
    main()