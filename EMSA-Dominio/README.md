# EMSA-Dominio

Biblioteca Maven Java compartida por Portal Ciudadano, CentralSys y Servicios Core. No es un quinto servicio
y no escucha en un puerto. Conserva la entidad `BusinessRequest`, el repositorio y la capa de servicio que
mantienen las solicitudes y sincronizan sus estados.

Las aplicaciones externas comparten la base configurada con `ENEL_NEGOCIO_JDBC_URL`. En local usan
`jdbc:h2:file:../data/enel_negocio;AUTO_SERVER=TRUE`; el motor Camunda usa su base separada
`enel_camunda`. El esquema se crea/actualiza con JPA al iniciar.

Desde la raíz del repositorio compile la biblioteca y las aplicaciones con `mvn clean install -DskipTests`.
La arquitectura completa está documentada en [docs/arquitectura-distribuida.md](../docs/arquitectura-distribuida.md).
