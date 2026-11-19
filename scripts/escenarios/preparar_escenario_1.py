"""Escenario 1 de la defensa: aceptación y finalización normal del curso (§1.4).

Deja un curso con la preparación completa, esperando Definir docente, para recorrer en vivo el desarrollo, el
informe, el cierre y la liberación de recursos.
"""
from comun import ejecuciones, exigir_lugar_para

NUMERO = 101


def main():
    exigir_lugar_para(1)
    instancia, curso = ejecuciones.llevar_a_preparacion(NUMERO)
    ejecuciones.preparar(instancia, NUMERO, aula=True, aula_virtual=False, equipamiento=True)
    ejecuciones.esperar("la tarea Definir docente", lambda: ejecuciones.primero(
        "/runtime/tasks", {"processInstanceId": instancia, "taskDefinitionKey": "definirDocente"}))
    print(f"Listo: curso {curso} esperando «Definir docente».")
    print("Seguí en la web como jefe_docente → Definir docente, y después el dictado, el informe y el cierre.")


if __name__ == "__main__":
    main()