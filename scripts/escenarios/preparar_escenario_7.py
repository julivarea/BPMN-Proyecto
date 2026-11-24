"""Escenario 7 de la defensa: falta de aulas disponibles (issue #36).

Hay que levantar Flowable con 3 aulas (AULAS_DISPONIBLES=3 docker compose up -d flowable; por defecto son 10). El script deja tres cursos en
preparación con el aula física reservada: no queda ninguna libre, así que la solicitud siguiente, hecha en vivo, falla
al reservar el aula.
"""
from comun import exigir_lugar_para, ejecuciones

AULAS = 3  # las del servidor Flowable levantado para este escenario
PRIMER_NUMERO = 701  # empresas de prueba propias de este escenario


def main():
    exigir_lugar_para(AULAS + 1)  # los tres de acá y el de la demo
    for numero in range(PRIMER_NUMERO, PRIMER_NUMERO + AULAS):
        instancia, curso = ejecuciones.llevar_a_preparacion(numero)
        ejecuciones.gestionar_recursos(instancia, numero, aula=True, aula_virtual=False, equipamiento=False)
        print(f"  curso {curso}: en preparación, con aula reservada")
    print(f"\nListo: las {AULAS} aulas están reservadas.")
    print(f"1. Registrá una solicitud nueva en {ejecuciones.WEB}/solicitar, hacela llegar a preparación y, en «Evaluar recursos», elegí aula física.")
    print("2. «Reservar aula» falla: el curso queda con «Confirmar aula disponible» pendiente para administración, y las demás ramas siguen.")
    print("3. Cancelá uno de los tres cursos de este script (app de comunicaciones) y confirmá sus compensaciones: se libera un aula.")
    print("4. Completá «Confirmar aula disponible»: la reserva se reintenta y el curso recibe el aula liberada.")
    print("Si el escenario ya se recorrió o hay aulas reservadas por otros cursos, levantá Flowable de nuevo con AULAS_DISPONIBLES=3 docker compose up -d flowable: las aulas vuelven a estar todas libres.")


if __name__ == "__main__":
    main()
