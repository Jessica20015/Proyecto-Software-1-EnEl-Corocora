# ENEL La Corocora (EMSA) · Camunda 7 embebido en Spring Boot

Integración de los procesos **C2**, **C4** y **C7** del repositorio
`Jessica20015/Avance-proyecto-Software` en **una sola aplicación Spring Boot** con el motor de
Camunda embebido, siguiendo la estructura del proyecto de ejemplo (`EMSA proyect`).

Antes, las *service tasks* eran **External Tasks** atendidas por workers de Node.js
(`camunda-external-task-client-js`). Ahora son **`JavaDelegate`** dentro de la misma aplicación:
no hay que levantar ningún worker aparte.

## Cómo ejecutarlo

Requisitos: **Java 17+** y **Maven**.

```bash
mvn spring-boot:run
```

* Camunda Cockpit / Tasklist / Admin: <http://localhost:8080> — usuario `demo`, clave `demo`
* REST API: <http://localhost:8080/engine-rest>
* Al arrancar se despliegan solos los 6 procesos, 5 DMN y 20 formularios (un único *deployment*,
  como exige la regla de despliegue del README original).

> La base de datos es H2 en memoria (igual que el ejemplo): al reiniciar se pierden las instancias.
> Los expedientes `.jsonl` y el consecutivo de radicados sí quedan en disco, en `./expediente/`.

## Cómo iniciar cada proceso

| Proceso | Cómo se inicia |
|---|---|
| **C2** Restablecer el servicio tras una falla | Tasklist → *Start process* → «Restablecer el servicio tras una falla» |
| **C7** Atender una PQR (ventanilla/teléfono) | Tasklist → *Start process* → «C7 · Atender una PQR del usuario» |
| **C4** Facturar el servicio (por mensaje) | `cd simuladores && node c4-simular-entradas.js normal` |
| **C7** por portal / recursos | `cd simuladores && node c7-simular-entradas.js reclamo-portal` |

Los simuladores (`simuladores/`) son los mismos `simular-entradas.js` de antes, sin cambios de lógica
(solo requieren Node 18+, sin `npm install`). Sin argumentos listan todos los escenarios.

## Qué cambió en los modelos

Solo se tocaron los `.bpmn`, y únicamente en las 29 tareas/eventos que eran externos:

```xml
<!-- antes -->
<bpmn:serviceTask id="..." camunda:type="external" camunda:topic="liquidar-factura"/>
<!-- ahora -->
<bpmn:serviceTask id="..." camunda:delegateExpression="${liquidarFactura}"/>
```

Los `.dmn` y `.form` están intactos. Se usa `delegateExpression` (y no `camunda:class` como en el ejemplo)
porque los delegates necesitan que Spring les inyecte `RuntimeService`, la configuración y los
almacenes de archivos; con `camunda:class` Camunda crea la clase por reflexión y nada se inyecta.
Cada clase sigue el mismo patrón del ejemplo: `@Component("nombre") … implements JavaDelegate`.

## Mapa topic → clase Java

| Proceso | Topic (antes) | Bean / clase (ahora) |
|---|---|---|
| C2 | `solicitud_repuestos_topic` | `solicitudRepuestos` → `c2/SolicitudRepuestos` |
| C2 | `registro_afectacion_worker` | `registroAfectacion` → `c2/RegistroAfectacion` |
| C4 | `consolidar-insumos` | `consolidarInsumos` → `c4/ConsolidarInsumos` (lanza `BpmnError ERR_REGLA_NO_VIGENTE`) |
| C4 | `devolver-excepcion-c3` | `devolverExcepcionC3` → `c4/DevolverExcepcionC3` |
| C4 | `asignar-consumo-promedio` | `asignarConsumoPromedio` → `c4/AsignarConsumoPromedio` |
| C4 | `liquidar-factura` | `liquidarFactura` → `c4/LiquidarFactura` |
| C4 | `escalar-coordinador` | `escalarCoordinador` → `c4/EscalarCoordinador` |
| C4 | `emitir-factura` | `emitirFactura` → `c4/EmitirFactura` |
| C4 | `notificar-c5` | `notificarC5` → `c4/NotificarC5` |
| C4 | `registrar-expediente` | `registrarExpediente` → `c4/RegistrarExpediente` |
| C4 | `solicitar-regla-g3` | `solicitarReglaG3` → `c4/SolicitarReglaG3` |
| C4.1 | `enviar-factura` | `enviarFactura` → `c4/EnviarFactura` |
| C4.1 | `registrar-prueba-entrega` | `registrarPruebaEntrega` → `c4/RegistrarPruebaEntrega` |
| C7 | `radicar-pqr` | `radicarPqr` → `c7/RadicarPqr` |
| C7 | `abrir-expediente` | `abrirExpediente` → `c7/AbrirExpediente` |
| C7 | `enviar-constancia-radicado` | `enviarConstanciaRadicado` → `c7/EnviarConstanciaRadicado` |
| C7 | `solicitar-evidencia` | `solicitarEvidencia` → `c7/SolicitarEvidencia` |
| C7 | `escalar-evidencia` | `escalarEvidencia` → `c7/EscalarEvidencia` |
| C7 | `alertar-coordinador` | `alertarCoordinador` → `c7/AlertarCoordinador` |
| C7 | `enviar-ajuste-c4` | `enviarAjusteC4` → `c7/EnviarAjusteC4` |
| C7 | `solicitar-revision-c6` | `solicitarRevisionC6` → `c7/SolicitarRevisionC6` |
| C7 | `remitir-apelacion-sspd` | `remitirApelacionSspd` → `c7/RemitirApelacionSspd` |
| C7 | `cerrar-expediente` | `cerrarExpediente` → `c7/CerrarExpediente` |
| C7 | `reportar-causa-raiz` | `reportarCausaRaiz` → `c7/ReportarCausaRaiz` |
| C7.1 | `generar-acto-respuesta` | `generarActoRespuesta` → `c7/GenerarActoRespuesta` |
| C7.1 | `enviar-comunicacion` | `enviarComunicacion` → `c7/EnviarComunicacion` |
| C7.1 | `notificar-por-aviso` | `notificarPorAviso` → `c7/NotificarPorAviso` |
| C7.1 | `publicar-aviso` | `publicarAviso` → `c7/PublicarAviso` |
| C7.1 | `registrar-prueba-notificacion` | `registrarPruebaNotificacion` → `c7/RegistrarPruebaNotificacion` |

## Clases de apoyo

* `common/Vars` — lectura/escritura tipada de variables (reemplaza `v()`, `str()`, `dbl()`, `int()`, `bool()` de los workers).
* `common/Fechas` — fechas ISO-8601 en UTC con milisegundos, igual que `toISOString()`.
* `common/JsonlStore` — archivos de expediente `.jsonl` y consecutivo de radicados.
* `common/MensajeriaSimulada` — simula a C3, C5, G3, el operador de entrega y al usuario que recurre:
  unos segundos después correlaciona el mensaje correspondiente (antes: `setTimeout` + `POST /message`).
* `c4/C4Config` — reglas tarifarias versionadas y plazos de los temporizadores.
* `c7/C7Calendario` — días hábiles de Colombia, festivos 2026–2027, modo demo/producción.
* `c7/C7Expediente` — registro de eventos de trazabilidad y radicados `PQR-AAAA-NNNNNN`.

## Configuración (`application.yaml`)

Reemplaza las variables de entorno de los workers:

| Antes | Ahora |
|---|---|
| `C4_MODO` | `emsa.c4.modo` (`demo` \| `produccion`) |
| `C7_MODO` | `emsa.c7.modo` (`demo` \| `produccion`) |
| `C7_SEGUNDOS_POR_DIA` | `emsa.c7.segundos-por-dia` |
| carpeta `expediente/` de cada worker | `emsa.expediente-dir` |

## Diferencias de comportamiento a tener en cuenta

* **Errores:** un delegate corre dentro de la transacción de la tarea anterior. Si lanza una excepción
  (que no sea `BpmnError`), la transacción se revierte y el error le llega a quien completó la tarea. Los
  workers de JS, en cambio, creaban un incidente tras agotar reintentos. Si lo necesitan, se puede añadir
  `camunda:asyncBefore="true"` y `camunda:failedJobRetryTimeCycle` a las tareas.
* **`enviarAjusteC4`** (C7 → C4) conserva el comportamiento original: registra el evento y deja el log,
  pero no arranca una instancia de C4. Si se quiere que la refacturación se dispare de verdad, basta con
  correlacionar `MsgRefacturacionAutorizada` desde ese delegate.
* El primer registro del expediente de C7 ahora trae el `expedienteId`/`radicado` correctos (en JS leía el
  valor anterior de la variable y salía vacío).
