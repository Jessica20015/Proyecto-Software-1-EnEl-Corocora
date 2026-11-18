# Servicios Core ENEL

Aplicación Spring Boot sin interfaz gráfica en el puerto **8084**. Provee servicios REST para operaciones
automatizadas de C2/C4 y otras integraciones de negocio. Su API está en `com.enel.core.api.CoreServiceController`.

## API

| Método y ruta | Función |
|---|---|
| `POST /api/core/liquidaciones` | Calcula energía, ajuste tarifario y total. |
| `POST /api/core/ordenes-tecnicas` | Registra una orden vinculada a `codigoSolicitud`. |
| `GET /api/core/ordenes-tecnicas?codigoSolicitud=...` | Consulta órdenes persistidas. |
| `GET /api/core/suministros/{codigo}/validacion` | Valida el formato `SUM-000000`. |
| `PUT /api/core/solicitudes/{codigo}/estado` | Cambia el estado permitido de una solicitud de negocio. |

El motor Camunda consume las operaciones que necesita desde delegates mediante HTTP; no se debe invocar
el Core desde el navegador. El esquema compartido de solicitudes se gestiona en
[EMSA-Dominio](../EMSA-Dominio/README.md).

## Configuración y ejecución

Requiere Java 17+ y Maven 3.9+. Desde la raíz ejecute `mvn clean install -DskipTests`; después, desde esta
carpeta, ejecute `mvn spring-boot:run`.

La URL predeterminada de dominio es `jdbc:h2:file:../data/enel_negocio;AUTO_SERVER=TRUE`, compartida con Portal
y CentralSys. Use `ENEL_NEGOCIO_JDBC_URL`, `ENEL_NEGOCIO_JDBC_DRIVER`, `ENEL_NEGOCIO_DB_USER` y
`ENEL_NEGOCIO_DB_PASSWORD` para usar otra base. La guía de los flujos y dependencias está en
[docs/arquitectura-distribuida.md](../docs/arquitectura-distribuida.md).
