import csv, sys, openpyxl
from collections import OrderedDict
csv_viejo, xlsx_nuevo, salida, grupos = sys.argv[1:5]
filas = []  # (grupo, etiqueta, [39])
# CSV viejo: paredes (OBJETO). Clave: (Lote, Telemetría).
tel = OrderedDict()
with open(csv_viejo, encoding='utf-8') as f:
    for r in csv.DictReader(f):
        clave = (r['Lote'], r['Telemetría'])
        tel.setdefault(clave, {})[int(r['Ángulo (°)'])] = [int(r['ROI 4x4']), int(r['ROI 8x8']), int(r['ROI 16x16'])]
for (lote, t), por_angulo in tel.items():
    assert sorted(por_angulo) == list(range(0, 181, 15)), (lote, t)
    valores = [v for a in range(0, 181, 15) for v in por_angulo[a]]
    filas.append(("viejo:" + lote, "OBJETO", valores))
# XLSX nuevo: lobitos (LOBITO). Hoja "Todas las mediciones": Grupo, Telemetría, Ángulo, 4x4, 8x8, 16x16.
wb = openpyxl.load_workbook(xlsx_nuevo, data_only=True)
tel = OrderedDict()
for r in list(wb['Todas las mediciones'].iter_rows(values_only=True))[1:]:
    if r[0] is None: continue
    tel.setdefault((int(r[0]), int(r[1])), {})[int(r[2])] = [int(r[3]), int(r[4]), int(r[5])]
for (g, t), por_angulo in tel.items():
    assert sorted(por_angulo) == list(range(0, 181, 15)), (g, t)
    valores = [v for a in range(0, 181, 15) for v in por_angulo[a]]
    filas.append((f"nuevo:Grupo {g}", "LOBITO", valores))
with open(salida, 'w', encoding='utf-8') as f, open(grupos, 'w', encoding='utf-8') as fg:
    for grupo, etiqueta, v in filas:
        assert len(v) == 39 and all(x == -1 or 0 <= x <= 4000 for x in v)
        f.write(etiqueta + "," + ",".join(map(str, v)) + "\n")
        fg.write(grupo + "\n")
from collections import Counter
print(Counter(e for _, e, _ in filas), "grupos:", len(set(g for g, _, _ in filas)))
todos = [x for _, _, v in filas for x in v if x != -1]
print("rango mm:", min(todos), max(todos), " -1:", sum(x == -1 for _, _, v in filas for x in v))
