package orquitas.servidor.navegacion;

/**
 * Estados operativos de la Orquita según el Diagrama de Estados. Se informan por la consola del Servidor
 * cuando cambian (no viajan como mensaje). Los dueños son: Navegacion (los lleva y los informa),
 * EnlaceOrquita (informa CAPTURA y ERROR_SEGURO) y ModoOperativo (BUSQUEDA, ESCAPE y LIBERADA = FINALIZACION).
 */
public enum EstadoOrquita { BUSQUEDA, APROXIMACION, CAPTURA, ESCAPE, FINALIZACION, ERROR_SEGURO }
