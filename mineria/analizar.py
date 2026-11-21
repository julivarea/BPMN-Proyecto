import sys

import pandas as pd
import pm4py


class SalidaConArchivo:
    """Imprime en la consola y guarda lo mismo en resultados.txt (UTF-8), sin depender de redirigir la salida."""

    def __init__(self, consola, archivo):
        self.consola = consola
        self.archivo = archivo

    def write(self, texto):
        self.consola.write(texto)
        self.archivo.write(texto)

    def flush(self):
        self.consola.flush()
        self.archivo.flush()


# En Windows, con la salida redirigida, Python usa cp1252 y no puede escribir "→": se fuerza UTF-8.
sys.stdout.reconfigure(encoding="utf-8")
resultados = open("resultados.txt", "w", encoding="utf-8")
sys.stdout = SalidaConArchivo(sys.stdout, resultados)

# Leer el CSV. Mantener los identificadores como texto.
df = pd.read_csv("eventos.csv", dtype=str)

# Conservar solamente tareas de usuario y automáticas.
df = df[df["tipo"].isin(["userTask", "serviceTask"])].copy()


# Convertir fechas y conservar el orden del CSV ante empates.
df["fecha_hora"] = pd.to_datetime(df["fecha_hora"], utc=True)
df = df.sort_values(
    ["instancia", "fecha_hora"], kind="stable"
)

# Adaptar las columnas al formato utilizado por PM4Py.
log = pm4py.format_dataframe(
    df,
    case_id="instancia",
    activity_key="actividad",
    timestamp_key="fecha_hora",
)

# 1. Recorridos o variantes más frecuentes.
variantes = pm4py.get_variants_as_tuples(log)
total = log["case:concept:name"].nunique()

print(f"\nInstancias analizadas: {total}")
print(f"Recorridos diferentes: {len(variantes)}")

print("\nLos tres recorridos más frecuentes:")
for recorrido, cantidad in sorted(
    variantes.items(),
    key=lambda elemento: elemento[1],
    reverse=True,
)[:3]:
    print(f"\n{cantidad} casos ({100 * cantidad / total:.1f} %)")
    print(" → ".join(recorrido))

# 2. Grafo de sucesión directa:
# cada flecha indica cuantas veces una tarea siguió a otra.
dfg, inicios, finales = pm4py.discover_dfg(log)
pm4py.save_vis_dfg(
    dfg, inicios, finales, "recorridos.png"
)

# 3. Modelo BPMN descubierto a partir de las ejecuciones.
modelo = pm4py.discover_bpmn_inductive(log)
pm4py.save_vis_bpmn(modelo, "modelo_descubierto.png")
pm4py.write_bpmn(modelo, "modelo_descubierto.bpmn")

print("\nArchivos generados:")
print("- resultados.txt")
print("- recorridos.png")
print("- modelo_descubierto.png")
print("- modelo_descubierto.bpmn")

sys.stdout = sys.stdout.consola
resultados.close()
