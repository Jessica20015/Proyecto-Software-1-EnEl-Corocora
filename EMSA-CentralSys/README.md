# CentralSys ENEL

Aplicación Spring Boot independiente en el puerto **8083** para funcionarios de los carriles de Centro de
Control, Cuadrillas, Coordinación de Zona, Facturación y PQR. La interfaz está en
`src/main/resources/static/`; la API en `com.enel.central.api.TaskController`.

## API principal

| Método y ruta | Función |
|---|---|
| `POST /api/sesion` | Inicia sesión con `{"username":"...","password":"..."}` (usuarios de Camunda). Devuelve el funcionario y sus carriles. |
| `GET /api/sesion` · `DELETE /api/sesion` | Consulta la sesión actual · la cierra. |
| `GET /task?candidateGroup={grupo}` | Lista las tareas sin reclamar de uno de **sus** carriles. |
| `GET /task/mias` | Lista las tareas que el funcionario reclamó. |
| `GET /task/{id}/deployed-form` | Consulta el formulario que Camunda desplegó para la tarea. |
| `GET /task/{id}/variables` | Consulta variables del proceso. |
| `POST /task/{id}/claim` | Reclama la tarea a nombre del funcionario con sesión. |
| `POST /task/{id}/complete` | Completa una tarea reclamada, con las variables de Camunda en `{"variables":{...}}`. |

### Ingreso y permisos

Todas las rutas `/task` exigen sesión (si no hay, responden **401**). Las credenciales se verifican contra
el motor (`/engine-rest/identity/verify`) y la sesión guarda los carriles del usuario (sus grupos de Camunda).
Con eso CentralSys solo deja:

- consultar y reclamar tareas de los carriles del funcionario (si no, **403**);
- ver el formulario y las variables de una tarea libre de sus carriles o de una que él reclamó (si no, **403**);
- completar solo tareas que él mismo reclamó (si no, **409**). El usuario del reclamo sale de la sesión.

Usuarios de prueba (clave `1234`): `operador1`, `cuadrilla1`, `coordinador1` (C2); `analista1`, `calidad1`,
`liquidador1` (C4); `orientador1`, `analistapqr1`, `juridica1` y `pqr1` (C7). La cookie de sesión es
`HttpOnly` y `SameSite=Strict`, y expira tras 30 minutos sin uso.

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

El inicio de sesión protege CentralSys; el REST del motor (`/engine-rest`) sigue sin autenticación propia,
como en la demostración local del motor. Véase la guía
de [arquitectura](../docs/arquitectura-distribuida.md) y el
[README general](../README.md).
