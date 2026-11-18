# CentralSys ENEL

Aplicación Spring Boot independiente en el puerto **8083** para funcionarios de los carriles de Centro de
Control, Cuadrillas, Coordinación de Zona, Facturación y PQR. La interfaz está en
`src/main/resources/static/`; la API en `com.enel.central.api.TaskController`.

## API principal

| Método y ruta | Función |
|---|---|
| `GET /task?candidateGroup={grupo}` | Lista tareas activas para uno de los grupos de negocio permitidos. |
| `GET /task/{id}/deployed-form` | Consulta el formulario que Camunda desplegó para la tarea. |
| `GET /task/{id}/variables` | Consulta variables del proceso. |
| `POST /task/{id}/claim` | Reclama la tarea con `{"username":"..."}`. |
| `POST /task/{id}/complete` | Completa con las variables de Camunda en `{"variables":{...}}`. |

La interfaz convierte los componentes del formulario desplegado por Camunda en controles nativos, precarga
las variables de la tarea y envía al motor los valores editables. La aplicación consulta y completa las tareas
mediante REST de Camunda; el BPMN conserva el control del orden y las compuertas. Los grupos expuestos están
limitados a `operador-control`, `cuadrilla`, `coordinador-zona`,
`analistas-facturacion`, `calidad-facturacion`, `liquidadores-tarifarios`, `orientadores-pqr`, `analistas-pqr`
y `juridica-pqr`. Al reclamar/completar se sincroniza el estado de la solicitud en la base compartida.

## Configuración y ejecución

Requiere Java 17+ y Maven 3.9+. Desde la raíz ejecute `mvn clean install -DskipTests`; después, desde esta
carpeta, ejecute `mvn spring-boot:run`. El motor debe estar disponible en 8081.

La aplicación usa `jdbc:h2:file:../data/enel_negocio;AUTO_SERVER=TRUE`, configurable mediante
`ENEL_NEGOCIO_JDBC_URL`, `ENEL_NEGOCIO_JDBC_DRIVER`, `ENEL_NEGOCIO_DB_USER` y `ENEL_NEGOCIO_DB_PASSWORD`.
Configure el motor con `ENEL_CAMUNDA_URL` (por defecto `http://localhost:8081/engine-rest`).

Esta interfaz es para demostración local: no implementa autenticación/autorización propia. Véase la guía
de [arquitectura](../docs/arquitectura-distribuida.md) y el
[README general](../README.md).
