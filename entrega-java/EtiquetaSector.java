package orquitas.servidor.navegacion;

/**
 * Lo que hay en un sector del barrido (13 sectores de 15°). La red de percepción pone una etiqueta
 * por sector, así un mismo barrido puede tener pared, obstáculo, Lobito y salida a la vez.
 * El orden coincide con las 5 salidas por sector de la red: LIBRE, PARED, OBSTACULO, LOBITO, SALIDA.
 * LIBRE: nada dentro del alcance del sensor. SALIDA: el sector mira hacia el Estrecho de Salida.
 */
public enum EtiquetaSector { LIBRE, PARED, OBSTACULO, LOBITO, SALIDA }
