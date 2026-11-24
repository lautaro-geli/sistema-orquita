"""Análisis de la toma guiada del 24/11/2026 (ver ../Toma_Guiada_20261124.md).

Lee red_e2_espejado.txt: cada barrido de deteccion.csv con los sectores dados vuelta (s -> 12 - s, porque
el robot los mandaba espejados) y pasado por la red E2 con la corrección del piso (VolcarToma.java.txt).
Uso: python3 -c "from analizar import *; F = leer('red_e2_espejado.txt'); print(tabla_matriz(matriz(F)))"
"""
import math, sys, itertools, json
from collections import Counter, defaultdict

ET = ['LIBRE', 'PARED', 'OBSTACULO', 'LOBITO', 'SALIDA']
ANCHO_OBST_CM = 9.0
MIN_SOLAPE = 5.0          # grados del sector tapados por el obstáculo para contarlo
PARED_MM = 1000

def ang(s): return 15 * s - 90
def sec(a): return max(0, min(12, round((a + 90) / 15)))
def lado(s):
    a = ang(s); return 'frente' if a == 0 else (f'izq {a}' if a > 0 else f'der {-a}')

def leer(path):
    filas = []
    for l in open(path):
        b, nota, t, c, et, p = l.rstrip('\n').split('|')
        filas.append(dict(barrido=int(b), nota=nota, t=list(map(int, t.split(','))), c=list(map(int, c.split(','))),
                          et=et.split(','), p=list(map(float, p.split(',')))))
    # escenas: cambia la nota o el barrido no es consecutivo
    esc = 0
    for i, f in enumerate(filas):
        if i > 0 and (f['nota'] != filas[i-1]['nota'] or f['barrido'] != filas[i-1]['barrido'] + 1): esc += 1
        f['escena'] = esc
    return filas

def parse(nota):
    objs = []
    for parte in nota.split(';'):
        w = parte.split()
        if not w: continue
        if w[0] == 'vacio': continue
        if w[0] == 'salida': objs.append(('SALIDA', int(w[1]), int(w[2])))
        else: objs.append(('LOBITO' if w[0] == 'lobito' else 'OBSTACULO', int(w[1]), int(w[2])))
    return objs

def sectores_obstaculo(a, d_cm):
    media = math.degrees(math.atan(ANCHO_OBST_CM / 2 / d_cm))
    r = []
    for s in range(13):
        c = ang(s)
        solape = min(a + media, c + 7.5) - max(a - media, c - 7.5)
        if solape >= MIN_SOLAPE or (a - media <= c <= a + media): r.append(s)
    return r

def verdad(f):
    """Etiqueta real por sector + datos (distancia, rol)."""
    v = [None] * 13; info = [dict() for _ in range(13)]
    objs = parse(f['nota'])
    # primero salida y obstáculos, después lobito (el lobito gana: está adelante o es lo que importa)
    for tipo, a, b in objs:
        if tipo == 'SALIDA':
            for s in range(sec(a), sec(b) + 1): v[s] = 'SALIDA'
    for tipo, a, d in objs:
        if tipo == 'OBSTACULO':
            ss = sectores_obstaculo(a, d)
            for s in ss:
                if v[s] is None or v[s] == 'SALIDA':
                    v[s] = 'OBSTACULO'
                    info[s] = dict(dist=d, centro=(s == sec(a)), borde=(s in (min(ss), max(ss)) and len(ss) > 1), frontal=(a == 0))
    for tipo, a, d in objs:
        if tipo == 'LOBITO':
            s = sec(a); v[s] = 'LOBITO'; info[s] = dict(dist=d)
    for s in range(13):
        if v[s] is None:
            v[s] = 'PARED' if any(0 <= x < PARED_MM for x in f['t'][3*s:3*s+3]) else 'LIBRE'
    return v, info

# ---------------------------------------------------------------- filtro de forma
SIN = 9999
def rasgos(c, s):
    d4, d16 = c[3*s], c[3*s+2]
    if d4 < 0: return None
    forma = SIN if d16 < 0 else d16 - d4
    menor = SIN
    for n in (s-1, s+1):
        if 0 <= n < 13:
            for r in range(3):
                d = c[3*n+r]
                if d >= 0: menor = min(menor, abs(d - d4))
    return d4, forma, menor

def pasa(c, s, A, B, C):
    r = rasgos(c, s)
    if r is None: return False
    d4, forma, aisl = r
    return d4 < A and forma >= B and aisl >= C

GRILLA = [(A, B, C) for A in (250, 300, 350, 400, 500) for B in (-SIN, 0, 10, 20, 30, 50, 80) for C in (-SIN, 0, 10, 20, 30, 50, 80)]

# ---------------------------------------------------------------- informes
def pct(a, b): return f"{a}/{b} ({100*a/b:.0f} %)" if b else "—"

def aplicar_filtro(f, params):
    """Etiquetas de la red con el filtro: un LOBITO que no pasa pasa a la 2.ª etiqueta más probable."""
    if params is None: return list(f['et'])
    et = list(f['et'])
    for s in range(13):
        if et[s] == 'LOBITO' and not pasa(f['c'], s, *params):
            p = f['p'][5*s:5*s+5]
            otras = [(p[e], ET[e]) for e in range(5) if ET[e] != 'LOBITO']
            et[s] = max(otras)[1]
    return et

def matriz(F, params=None):
    m = defaultdict(Counter)
    for f in F:
        v, _ = verdad(f); et = aplicar_filtro(f, params)
        for s in range(13): m[v[s]][et[s]] += 1
    return m

def tabla_matriz(m):
    out = ["| Real \\ Red | " + " | ".join(ET) + " | Total | Acierto |", "|---" * 8 + "|"]
    for r in ET:
        tot = sum(m[r].values())
        if not tot: continue
        out.append(f"| **{r}** | " + " | ".join(str(m[r][e]) for e in ET) + f" | {tot} | {100*m[r][r]/tot:.0f} % |")
    return "\n".join(out)

def metricas(F, params=None, escenas=None):
    """Los tres números pedidos + lobitos detectados."""
    d = Counter()
    for f in F:
        if escenas is not None and f['escena'] not in escenas: continue
        v, info = verdad(f); et0 = f['et']; et = aplicar_filtro(f, params)
        es_salida = any(o[0] == 'SALIDA' for o in parse(f['nota']))
        if es_salida: d['barridos_salida'] += 1
        for s in range(13):
            if v[s] == 'LOBITO':
                d['lobitos'] += 1
                d['lobitos_vistos'] += et[s] == 'LOBITO'
                d['lobitos_perdidos_filtro'] += (et0[s] == 'LOBITO' and et[s] != 'LOBITO')
            else:
                if et[s] == 'LOBITO':
                    d['falsos'] += 1
                    if v[s] == 'OBSTACULO': d['obst_lobito'] += 1
                    if es_salida: d['falsos_salida'] += 1
            if v[s] == 'OBSTACULO': d['obst_sectores'] += 1
        d['barridos'] += 1
        d['no_lobito'] += sum(1 for x in v if x != 'LOBITO')
    return d

def puntaje(d):
    # % de lobitos que la red marcaba y el filtro conserva − % de LOBITO falsos que quedan
    base = d['_lob_red']; fal = d['_fal_red']
    keep = d['lobitos_vistos'] / base if base else 1
    quedan = d['falsos'] / fal if fal else 0
    return keep - quedan

def evaluar(F, params, escenas):
    d = metricas(F, params, escenas); d0 = metricas(F, None, escenas)
    d['_lob_red'] = d0['lobitos_vistos']; d['_fal_red'] = d0['falsos']
    return d

def mejor(F, escenas):
    best = None
    for g in GRILLA:
        sc = puntaje(evaluar(F, g, escenas))
        # desempate: el filtro más suave (umbral A más alto, B y C más bajos)
        key = (round(sc, 6), g[0], -g[1], -g[2])
        if best is None or key > best[0]: best = (key, g)
    return best[1]

def loso(F):
    escenas = sorted({f['escena'] for f in F})
    elegidos = {}
    for k in escenas:
        elegidos[k] = mejor(F, set(escenas) - {k})
    return elegidos

def informe_red(F, params=None):
    out = []
    # obstáculos
    c = defaultdict(Counter)
    for f in F:
        v, info = verdad(f); et = aplicar_filtro(f, params)
        for s in range(13):
            if v[s] == 'OBSTACULO':
                k1 = 'de frente (ángulo 0)' if info[s]['frontal'] else 'de costado'
                k2 = 'centro' if info[s]['centro'] else 'borde'
                for k in (k1, f"{k1}, {k2}"):
                    c[k]['n'] += 1; c[k]['lob'] += et[s] == 'LOBITO'; c[k]['obs'] += et[s] == 'OBSTACULO'
    out.append("| Obstáculo | Sectores | Marcados LOBITO | Marcados OBSTACULO |\n|---|---|---|---|")
    for k in sorted(c): out.append(f"| {k} | {c[k]['n']} | {pct(c[k]['lob'], c[k]['n'])} | {pct(c[k]['obs'], c[k]['n'])} |")
    # salida
    out.append("\n| Escena de salida | Barridos | LOBITO falsos | Por barrido |\n|---|---|---|---|")
    por = defaultdict(lambda: [0, 0]); tot = [0, 0]
    for f in F:
        if not any(o[0] == 'SALIDA' for o in parse(f['nota'])): continue
        et = aplicar_filtro(f, params); n = sum(e == 'LOBITO' for e in et)
        k = f"{f['escena']+1}: {f['nota']}"; por[k][0] += 1; por[k][1] += n; tot[0] += 1; tot[1] += n
    for k, (b, n) in por.items(): out.append(f"| {k} | {b} | {n} | {n/b:.1f} |")
    out.append(f"| **Total** | {tot[0]} | {tot[1]} | **{tot[1]/tot[0]:.1f}** |")
    # juntos
    out.append("\n| Escena con canica y obstáculo | Barrido | LOBITO en la canica | LOBITO en el obstáculo | LOBITO en otro lado |\n|---|---|---|---|---|")
    for f in F:
        if ';' not in f['nota']: continue
        v, _ = verdad(f); et = aplicar_filtro(f, params)
        a = sum(1 for s in range(13) if v[s] == 'LOBITO' and et[s] == 'LOBITO')
        b = sum(1 for s in range(13) if v[s] == 'OBSTACULO' and et[s] == 'LOBITO')
        o = sum(1 for s in range(13) if v[s] not in ('LOBITO', 'OBSTACULO') and et[s] == 'LOBITO')
        out.append(f"| {f['escena']+1}: {f['nota']} | {f['barrido']} | {'sí' if a else 'no'} | {b} | {o} |")
    return "\n".join(out)
