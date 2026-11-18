# Portal Ciudadano ENEL

Aplicación Spring Boot independiente en el puerto **8082** para crear borradores de solicitudes C2 y PQR C7,
editarlos, consultar el historial y radicarlos ante Camunda. La interfaz está en `src/main/resources/static/`;
la API en `com.enel.portal.api.SolicitudController`. La interfaz construye controles de captura a partir de las
plantillas C2/C7, conserva el código/estado del borrador y deja las solicitudes radicadas en modo de solo lectura.

## API principal

| Método y ruta | Función |
|---|---|
| `GET /api/solicitudes/plantillas/{C2\|C7}` | Devuelve una plantilla de captura. |
| `POST /api/solicitudes` | Crea un borrador y devuelve el código `SOL-...`. |
| `GET /api/solicitudes` y `GET /api/solicitudes/{codigo}` | Consulta historial o una solicitud. |
| `PUT /api/solicitudes/{codigo}` | Actualiza los datos mientras continúa en borrador. |
| `POST /api/solicitudes/{codigo}/radicar` | Valida requeridos, inicia C2 o correlaciona `MsgPQRPortal` para C7, y cambia el estado a `RADICADA`. |

C2 acepta las variables `nombre`, `domicilio` y `falla`. C7 valida los campos del formulario `RadicarPQR`.
El código de solicitud se envía al motor como `businessKey` y variable `codigoSolicitud`. Los campos no
reconocidos se rechazan; los requeridos se validan antes de invocar Camunda.

## Configuración y ejecución

Requiere Java 17+ y Maven 3.9+. Desde la raíz del repositorio ejecute `mvn clean install -DskipTests` una vez;
después, desde esta carpeta, ejecute `mvn spring-boot:run`. Camunda debe estar disponible en 8081 para radicar.

La base de dominio predeterminada es `jdbc:h2:file:../data/enel_negocio;AUTO_SERVER=TRUE`. Puede configurarse
con `ENEL_NEGOCIO_JDBC_URL`, `ENEL_NEGOCIO_JDBC_DRIVER`, `ENEL_NEGOCIO_DB_USER` y `ENEL_NEGOCIO_DB_PASSWORD`.
La URL del motor se configura con `ENEL_CAMUNDA_URL` (por defecto `http://localhost:8081/engine-rest`).

La aplicación comparte el esquema de solicitudes de [EMSA-Dominio](../EMSA-Dominio/README.md). La guía de
arquitectura y ejecución integrada está en el [README general](../README.md) y
[docs/arquitectura-distribuida.md](../docs/arquitectura-distribuida.md).
