"""Recalcula evidencias/metricas-modelo.json a partir de evidencias/predicciones.csv."""
import csv, json
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
CLASES = ["LIBRE", "OBJETO", "LOBITO"]
COLUMNAS = CLASES + ["NO_RECONOCIDO"]

def grupo_de(nombre):
    return "umbral_todos_los_sectores" if nombre.startswith("umbral_") else nombre

def main():
    grupos = {}
    with (RAIZ / "evidencias/predicciones.csv").open(encoding="utf-8") as f:
        for fila in csv.DictReader(f, delimiter=";"):
            g = grupos.setdefault(grupo_de(fila["grupo"]), {"n": 0, "aciertos": 0,
                                  "matriz": {c: {k: 0 for k in COLUMNAS} for c in CLASES}})
            g["n"] += 1
            g["matriz"][fila["esperado"]][fila["predicho"]] += 1
            g["aciertos"] += fila["esperado"] == fila["predicho"]
    salida = {
        "_descripcion": "Filas de 'matriz' = clase real, columnas = prediccion; NO_RECONOCIDO = salida ganadora bajo el umbral de confianza (50 %). "
                        "umbral_todos_los_sectores usa como referencia 1050 mm (medio de la franja 900-1200 mm sin ejemplos de entrenamiento); "
                        "mide donde cae el cambio, no un error de clasificacion.",
        "_modelo": "MLP 12-6-3, entrada cercania clamp((1500-d)/1500,0,1), umbral de confianza 0.5",
    }
    for nombre, g in grupos.items():
        g["exactitud"] = round(100.0 * g["aciertos"] / g["n"], 2)
        salida[nombre] = g
    (RAIZ / "evidencias/metricas-modelo.json").write_text(
        json.dumps(salida, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

if __name__ == "__main__":
    main()
