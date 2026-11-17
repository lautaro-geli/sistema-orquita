# E7: barrido corto con el Maestro actual y con el Maestro v2 (17/11/2026)

Corrido en la PC del usuario con `herramientas_pc/orquita_experimentos_5`, commit `4b0b3e9` de la rama `maestro-v2`. Configuración de E2 (600 recorridos, 80 épocas, 2 rondas DAgger, entradas CRUDA, repetición 2), con barrido de 1,91 s y ruido por ROI ×1,51 / ×1,73 / ×1,94. Datos SINTÉTICOS del simulador 2D.

- **E7a:** enseña el Maestro actual.
- **E7b:** enseña el Maestro v2 (girar y avanzar en el mismo barrido, avance seguro, tolerancia del embudo); la red se evalúa girando y avanzando.

Medido después en las 300 bahías de evaluación de los reportes (semillas 900000 en adelante), con candado, barrido de 1,91 s y el mismo ruido. Promedio de 3 semillas:

| Red | Misiones | ≤1 min | ≤4 min | Choques | Decisiones | Puntaje |
|---|---|---|---|---|---|---|
| E2 (entrenada con 4,8 s) | 80 % | 0 % | 57 % | 3,6 | 98 | 53 |
| E7a | 73 % | 0 % | 52 % | 4,1 | 104 | 46 |
| E7b | 73 % | 2 % | 54 % | 6,4 | 91 | 35 |
| Maestro v2 (referencia) | 98 % | ~38 % | ~92 % | 0,1 | 34 | 84 |

**Conclusiones:**
- Imitar al Maestro v2 no bajó las decisiones de la red: hace 91, contra 34 del Maestro que imita, y choca casi el doble que E2.
- Entrenar con el barrido corto y su ruido no mejoró a E2.
- Ninguna red termina en 1 minuto. E2 sigue siendo la mejor.
