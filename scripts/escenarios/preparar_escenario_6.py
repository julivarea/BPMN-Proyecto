"""Escenario 6 de la defensa: cancelación durante la preparación, con compensaciones (§1.3).

Deja un curso en preparación con Evaluar recursos completada (aula física y equipamiento), el aula reservada y el
equipamiento contratado, para cancelarlo en vivo desde la app de comunicaciones.
"""
from comun import ejecuciones, exigir_lugar_para

NUMERO = 601


def main():
    exigir_lugar_para(1)
    instancia, curso = ejecuciones.llevar_a_preparacion(NUMERO)
    ejecuciones.gestionar_recursos(instancia, NUMERO, aula=True, aula_virtual=False, equipamiento=True)
    print(f"Listo: curso {curso} en preparación, con el aula reservada y el equipamiento contratado.")
    print("En la app de comunicaciones, elegí ese curso y enviá «Cancelar curso».")
    print("Después: el aula se libera sola y aparece «Liberar equipamiento» para un administrativo.")


if __name__ == "__main__":
    main()