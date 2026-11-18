# ENEL La Corocora (EMSA) · Camunda 7 embebido en Spring Boot

En la arquitectura distribuida el motor escucha en el puerto **8081** (la configuración anterior usaba
8080). La guía para compilar e iniciar las cuatro aplicaciones está en el
[README de la raíz](../README.md#enel--arquitectura-distribuida). `ENEL_CAMUNDA_JDBC_URL` permite cambiar
el H2 local por PostgreSQL.

Integración de los procesos **C2**, **C4** y **C7** del repositorio
`Jessica20015/Avance-proyecto-Software` en **una sola aplicación Spring Boot** con el motor de
Camunda embebido, siguiendo la estructura del proyecto de ejemplo (`EMSA proyect`).

Antes, las *service tasks* eran **External Tasks** atendidas por workers de Node.js
(`camunda-external-task-client-js`). Ahora son **`JavaDelegate`** dentro de la misma aplicación:
no hay que levantar ningún worker aparte.

**Versiones:** Camunda Platform **7.24.0** y Spring Boot **3.5.7**. La primera versión usaba Camunda 7.20,
pero su Tasklist trae un form-js antiguo (esquema 8) que no abre los formularios de C7: falla con
*«form field of type &lt;group&gt; not supported»*. Los modelos se hicieron para 7.24 (esquema de formularios 16).

## Cómo ejecutarlo

Requisitos: **Java 17+** y **Maven**.

```bash
mvn clean spring-boot:run
```

(`clean` evita que una copia vieja de los modelos en `target/classes` se despliegue junto con la nueva.)

* Portal Ciudadano ENEL: <http://localhost:8082/>
* CentralSys de funcionarios ENEL: <http://localhost:8083/>
* Camunda Cockpit / Tasklist / Admin: <http://localhost:8081/camunda/app/> — usuario `demo`, clave `demo`
* REST API: <http://localhost:8081/engine-rest>
* Al arrancar se despliegan solos los 6 procesos, 5 DMN y 20 formularios (un único *deployment*,
  como exige la regla de despliegue del README original).
* Tasklist trae tres filtros: «Tareas de mis grupos», «Mis tareas» y «Todas las tareas». Los usuarios de
  prueba de cada carril están en el [README principal](../README.md#usuarios-de-prueba) (clave `1234`).

> En la arquitectura distribuida el motor usa H2 local en `./data/enel_camunda`; al reiniciar no se
> eliminan las instancias ni el historial.
> Los expedientes `.jsonl` y el consecutivo de radicados sí quedan en disco, en `./expediente/`.
> Para empezar una demostración desde cero, borre esa carpeta con la aplicación detenida.

## Cómo iniciar cada proceso

| Proceso | Cómo se inicia |
|---|---|
| **C2** Restablecer el servicio tras una falla | Tasklist → *Start process* → «Restablecer el servicio tras una falla», o el portal con `operador1` |
| **C7** Atender una PQR (ventanilla/teléfono) | Tasklist → *Start process* → «C7 · Atender una PQR del usuario», o el portal con `orientador1` |
| **C4** Facturar el servicio (por mensaje) | `cd simuladores && node c4-simular-entradas.js normal`, o el portal con `analista1` |
| **C7** por portal virtual (mensaje `MsgPQRPortal`) | `cd simuladores && node c7-simular-entradas.js reclamo-portal` |
| **C7** recurso, comparecencia o acceso electrónico | `node c7-simular-entradas.js recurso PQR-AAAA-NNNNNN [apelacion]` (y `comparecer`, `acceso`) |

Los simuladores (`simuladores/`) solo requieren Node 18+, sin `npm install`. Sin argumentos muestran la
ayuda y los escenarios. El de C7 imprime el radicado de cada caso y los comandos de seguimiento.

## C7 · Atender una PQR

* Modelos en `src/main/resources/static.bpmn`, `static.dmn` y `static.form` (2 BPMN, 2 DMN y 6 forms), junto
  a los de C2 y C4, y delegates en
  `src/main/java/com/soft1/c7/`.
* Carriles y usuarios: `orientador1` (orientadores-pqr), `analistapqr1` (analistas-pqr), `juridica1`
  (juridica-pqr) y `pqr1` (coordinación, en los tres grupos).
* Los 6 formularios traen **valores por defecto**. Aceptándolos, el caso recorre: radicación de un reclamo de
  facturación → evidencia de C4 (simulada) → respuesta PARCIAL con ajuste en C4 → revisión jurídica (APROBAR)
  → notificación personal en C7.1 → término para recurrir → cierre. `RadicarPqr` ya no crea en vacío
  `ordenaAjusteC4`, `valorAjuste`, `resultadoRevision` ni `observacionesRevision`: en Tasklist una variable
  existente reemplaza el *Default value* del formulario.
* Tiempos en modo demo (`emsa.c7.segundos-por-dia: 30`): término de respuesta 15 días hábiles = 7,5 min,
  alerta al 70 % ≈ 5¼ min, plazo para recurrir 150 s. Si una tarea del analista queda sin atender 7,5 min,
  opera el silencio administrativo positivo (está modelado así).
* Reapertura: si se radica otra PQR con el mismo suministro y asunto antes de 30 días (15 min en demo) del
  cierre de una anterior, el caso se marca como reapertura y pasa por revisión jurídica. Como el formulario
  trae por defecto el suministro `SUM-100208`, dos PQR seguidas con los valores por defecto muestran este
  comportamiento (el camino es el mismo, porque la respuesta parcial ya pasa por revisión jurídica). Para
  evitarlo basta con cambiar el código de suministro.
* En un ciclo de devolución (revisión jurídica → DEVOLVER → nueva proyección), al terminar la proyección se
  limpian `resultadoRevision` y `observacionesRevision`, así la segunda revisión vuelve a mostrar los
  valores por defecto. La compuerta «¿Ordena ajuste en C4?» tolera que `ordenaAjusteC4` no venga (por
  ejemplo, si la tarea se completa por REST sin ese dato).

| Mensaje | Lo recibe | Lo envía (simulado) |
|---|---|---|
| `MsgPQRPortal` | inicio de C7 (portal virtual) | simulador `c7-simular-entradas.js` |
| `MsgEvidenciaPQR` | C7, espera de evidencia | `SolicitarEvidencia` (proceso fuente C2–C6) |
| `MsgRecursoReposicion` | C7, espera del recurso | `RegistrarPruebaNotificacion` o el simulador (`recurso`) |
| `MsgComparecenciaNotificacion` / `MsgAccesoElectronico` | C7.1, espera de notificación | `EnviarComunicacion` o el simulador (`comparecer` / `acceso`) |

Mensajes que solo se lanzan (constancias y avisos a otros procesos): `MsgConstanciaRadicado`,
`MsgSolicitudEvidencia`, `MsgRefacturacionAutorizada`, `MsgRevisionActuacionC6`, `MsgRemisionApelacionSSPD`,
`MsgCausaRaizPQR` y `MsgComunicacionDecision`.

## Portal del proyecto (`static/`)

`index.html` y `app.js` son una bandeja de trabajo por proceso y por rol. Para C7 el portal muestra los
**formularios reales de Camunda** (los mismos `.form` desplegados, con sus valores por defecto y
validaciones) usando form-js 1.8.7, que va incluido en `static/vendor/form-js` con su licencia, así que
funciona sin internet. C2 y C4 siguen usando sus listas de campos propias.

## Qué cambió en los modelos

En los `.bpmn` solo cambiaron las 29 tareas/eventos que eran externos:

```xml
<!-- antes -->
<bpmn:serviceTask id="..." camunda:type="external" camunda:topic="liquidar-factura"/>
<!-- ahora -->
<bpmn:serviceTask id="..." camunda:delegateExpression="${liquidarFactura}"/>
```

Los `.dmn` están intactos. A los `.form` de C4 y C7 solo se les agregó `defaultValue` (los datos por defecto
que pide la entrega). Todos los modelos (C2, C4 y C7) están en las carpetas `static.bpmn`, `static.dmn` y
`static.form`. Se usa `delegateExpression` (y no `camunda:class` como en el ejemplo)
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
* `common/MensajeriaSimulada` — simula a los participantes externos (en C4: C3, C5, G3 y el operador de
  entrega; en C7: los procesos fuente de la evidencia, el operador de notificación y el usuario que recurre).
  Unos segundos después correlaciona el mensaje correspondiente (antes: `setTimeout` + `POST /message`).
  El envío se programa solo cuando la transacción del delegate se confirma, y solo se reintenta si la
  instancia aún no llegó al evento (o hay un conflicto de concurrencia). Otro error se registra en el log
  con nivel ERROR y no se repite.
* `common/DespuesDelCommit` — ejecuta una acción cuando la transacción del motor se confirma (registro del
  expediente de C7 y mensajes simulados), para no dejar rastros de trabajo revertido.
* `common/CamundaUsuariosConfig` — grupos y usuarios de prueba de cada carril y los filtros de Tasklist.
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

Además: `camunda.bpm.filter.create` crea el filtro «Todas las tareas» de Tasklist y
`camunda.bpm.webapp.index-redirect-enabled: false` evita redirigir la raíz del motor a Tasklist.

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
