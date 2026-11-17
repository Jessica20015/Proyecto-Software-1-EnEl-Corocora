// =============================================================================
//  Simulador de entradas de C4 · Facturar el servicio de energía
//
//  C4 arranca con eventos de mensaje (no con un formulario de inicio): el consumo
//  validado llega desde C3 y la refacturación autorizada desde C6/C7. Este script
//  hace el papel de esos procesos y correlaciona los mensajes de inicio vía REST.
//
//  Uso:  node simular-entradas.js <escenario>      (sin argumento lista los escenarios)
//        node simular-entradas.js todos
// =============================================================================
const BASE_URL = process.env.CAMUNDA_URL || "http://localhost:8080/engine-rest";

const D = (value) => ({ value, type: "Double" });
const I = (value) => ({ value, type: "Integer" });
const S = (value) => ({ value, type: "String" });

// Datos de suministro ficticios (no corresponden a usuarios reales).
const base = (codigo, extra = {}) => ({
  codigoSuministro: S(codigo),
  periodo: S("2026-11"),
  usoServicio: S("RESIDENCIAL"),
  estrato: D(2),
  canalPreferido: S("DIGITAL"),
  tipoLectura: S("REAL"),
  estimacionesConsecutivas: I(0),
  consumoKwh: D(152),
  consumoPromedioKwh: D(150),
  ...extra,
});

const ESCENARIOS = {
  normal: {
    descripcion: "Lectura real sin desviación → DMN FACTURAR → liquidación → verificación de calidad → emisión y entrega confirmada",
    mensaje: "MsgConsumoValidado",
    vars: base("SUM-100101"),
  },
  revision: {
    descripcion: "Lectura estimada (primera) → DMN REVISAR_ANALISTA → user task del analista",
    mensaje: "MsgConsumoValidado",
    vars: base("SUM-100102", { tipoLectura: S("ESTIMADA"), estimacionesConsecutivas: I(1), consumoKwh: D(149) }),
  },
  desviacion: {
    descripcion: "Desviación ≥ 100 % → DMN DEVOLVER_C3 → mensaje a C3 → event-based gateway → C3 responde con el consumo corregido",
    mensaje: "MsgConsumoValidado",
    vars: base("SUM-100103", { consumoKwh: D(420), simularRespuestaC3: S("CORREGIDO"), consumoCorregidoKwh: D(158) }),
  },
  "sin-respuesta-c3": {
    descripcion: "Desviación ≥ 100 % → devolución a C3 sin respuesta → vence el temporizador → factura provisional por promedio (art. 149)",
    mensaje: "MsgConsumoValidado",
    vars: base("SUM-100104", { consumoKwh: D(410), simularRespuestaC3: S("SIN_RESPUESTA") }),
  },
  reintento: {
    descripcion: "Entrega digital devuelta → reintento por canal físico → entrega confirmada",
    mensaje: "MsgConsumoValidado",
    vars: base("SUM-100105", { simularResultadoEntrega: S("DEVUELTA_LUEGO_CONFIRMADA") }),
  },
  "entrega-fallida": {
    descripcion: "Entrega devuelta en todos los intentos → user task 'Gestionar entrega fallida'",
    mensaje: "MsgConsumoValidado",
    vars: base("SUM-100106", { simularResultadoEntrega: S("DEVUELTA") }),
  },
  comercial: {
    descripcion: "Uso comercial → DMN de tarifa con contribución del 20 %",
    mensaje: "MsgConsumoValidado",
    vars: base("SUM-100107", { usoServicio: S("COMERCIAL"), estrato: D(0), consumoKwh: D(830), consumoPromedioKwh: D(800) }),
  },
  "regla-no-vigente": {
    descripcion: "Período sin regla tarifaria aprobada → error BPMN → mensaje de solicitud a G3",
    mensaje: "MsgConsumoValidado",
    vars: base("SUM-100108", { periodo: S("2031-01") }),
  },
  refacturacion: {
    descripcion: "Refacturación autorizada por C6 → nueva versión de factura del mismo suministro-período",
    mensaje: "MsgRefacturacionAutorizada",
    vars: base("SUM-100109", {
      periodo: S("2026-10"), consumoKwh: D(171), origenRefacturacion: S("C6"),
      numeroFacturaOriginal: S("FE-202610-SUM-100109-v1"), versionFacturaAnterior: I(1),
      autorizacionRef: S("C6-EXP-2026-0457"),
    }),
  },
};

async function iniciar(nombre) {
  const e = ESCENARIOS[nombre];
  const businessKey = `${e.vars.codigoSuministro.value}-${e.vars.periodo.value}${e.mensaje === "MsgRefacturacionAutorizada" ? "-REF" : ""}`;
  const res = await fetch(`${BASE_URL}/message`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ messageName: e.mensaje, businessKey, processVariables: e.vars, resultEnabled: true }),
  });
  const cuerpo = await res.text();
  if (!res.ok) {
    console.error(`✗ ${nombre}: HTTP ${res.status} ${cuerpo}`);
    return;
  }
  const [r] = JSON.parse(cuerpo);
  console.log(`✓ ${nombre.padEnd(17)} businessKey=${businessKey}  instancia=${r?.processInstance?.id}\n    ${e.descripcion}`);
}

const arg = process.argv[2];
if (!arg) {
  console.log("Escenarios disponibles:\n");
  for (const [k, e] of Object.entries(ESCENARIOS)) console.log(`  ${k.padEnd(17)} ${e.descripcion}`);
  console.log("\nUso: node simular-entradas.js <escenario> | todos");
} else if (arg === "todos") {
  for (const k of Object.keys(ESCENARIOS)) await iniciar(k);
} else if (ESCENARIOS[arg]) {
  await iniciar(arg);
} else {
  console.error(`Escenario desconocido: ${arg}`);
  process.exit(1);
}
