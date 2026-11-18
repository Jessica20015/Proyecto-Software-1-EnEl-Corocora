// =============================================================================
//  C7 · Atender una PQR del usuario — simulador de entradas
//
//  Hace el papel del usuario y de los canales: radica PQR por el portal
//  (mensaje MsgPQRPortal) o por ventanilla/teléfono (inicio con el mismo
//  contenido del formulario «Radicar PQR»), y permite presentar a mano un
//  recurso, una comparecencia o un acceso electrónico. Las tareas humanas se
//  completan en Tasklist.
//
//  Requiere la aplicación Spring Boot en marcha (mvn spring-boot:run): las
//  tareas de servicio son JavaDelegate dentro de la app y ya no hay workers de
//  Node que levantar. Solo usa la REST API del motor (CAMUNDA_URL).
//
//  Uso (desde simuladores/, o bien «npm run c7 -- <argumentos>»):
//    node c7-simular-entradas.js                       ayuda y lista de escenarios
//    node c7-simular-entradas.js <escenario>           radica la PQR del escenario
//    node c7-simular-entradas.js todos                 radica todos los escenarios
//    node c7-simular-entradas.js recurso <expediente> [apelacion]
//    node c7-simular-entradas.js comparecer <expediente>
//    node c7-simular-entradas.js acceso <expediente>
//  Opción: --suministro SUM-123456 | --suministro auto   (o C7_SUMINISTRO)
//  <expediente> = radicado inicial (PQR-AAAA-NNNNNN), clave de negocio del caso.
// =============================================================================
import { pathToFileURL } from "node:url";
const BASE_URL = process.env.CAMUNDA_URL || "http://localhost:8081/engine-rest";
const SCRIPT = "node c7-simular-entradas.js";
const PATRON_SUMINISTRO = /^SUM-[0-9]{6}$/; // el mismo del formulario «Radicar PQR»
const PATRON_RADICADO = /^PQR-\d{4}-\d+$/;
// Solo para los textos de ayuda: valor por defecto de emsa.c7.segundos-por-dia (application.yaml).
const SEG_DIA_DEMO = 30;
const CANALES = { PORTAL: "portal", TELEFONICO: "teléfono", PRESENCIAL: "ventanilla" };

const tipar = (obj) =>
  Object.fromEntries(
    Object.entries(obj).map(([k, val]) => {
      let type = "String";
      if (typeof val === "number") type = Number.isInteger(val) ? "Integer" : "Double";
      if (typeof val === "boolean") type = "Boolean";
      return [k, { value: val, type }];
    })
  );

const haceMeses = (n) => {
  const d = new Date();
  d.setMonth(d.getMonth() - n);
  return d.toISOString().slice(0, 10);
};

// Datos ficticios: no se usan datos personales reales (ruta de 90 días, §10.2).
const base = {
  nombrePeticionario: "Usuario de prueba",
  calidadPeticionario: "SUSCRIPTOR",
  direccionNotificacion: "Calle 40 # 30-15, Villavicencio",
  autorizaTratamientoDatos: true,
  requiereAccesibilidad: false,
};

export const ESCENARIOS = {
  "reclamo-portal": {
    descripcion: "Reclamo por facturación desde el portal con adjuntos. C4 aporta la evidencia y la notificación es electrónica. Si el analista decide FAVORABLE con ajuste, no pasa por apoyo jurídico.",
    canal: "PORTAL",
    datos: {
      tipoPQR: "RECLAMO", asunto: "FACTURACION", codigoSuministro: "SUM-100201", numeroFactura: "FAC-2026-10-100201", fechaExpedicionFactura: haceMeses(1),
      descripcionPQR: "La factura de octubre cobra el doble del consumo habitual sin cambios en el inmueble.", pretension: "Revisar la factura y ajustar el cobro.",
      documentosAportados: "Fotografía del medidor (adjunto 1); facturas de los tres meses anteriores (adjunto 2).",
      autorizaNotificacionElectronica: true, correoNotificacion: "usuario.prueba@example.com",
      simularEvidencia: "RESPONDE", simularNotificacion: "ACCESO_ELECTRONICO", simularRecurso: "NINGUNO",
    },
  },
  "reclamo-telefono-recurso": {
    descripcion: "Reclamo por recuperación de consumos por teléfono. C6 aporta la evidencia, el usuario se notifica en persona y presenta reposición con apelación en subsidio. La reposición se notifica por aviso.",
    canal: "TELEFONICO",
    datos: {
      tipoPQR: "RECLAMO", asunto: "RECUPERACION_CONSUMOS", codigoSuministro: "SUM-100202", numeroFactura: "FAC-2026-09-100202", fechaExpedicionFactura: haceMeses(2),
      descripcionPQR: "Me cobran seis meses de energía recuperada sin que me hayan mostrado el acta de revisión.", pretension: "Anular el cobro de la recuperación de consumos.",
      autorizaNotificacionElectronica: false,
      simularEvidencia: "RESPONDE", simularNotificacion: "COMPARECE", simularRecurso: "REPOSICION_Y_APELACION",
      simularNotificacionReposicion: "NINGUNA", simularAvisoReposicion: "ENTREGADO",
    },
  },
  "queja-tipificacion-manual": {
    descripcion: "Queja en ventanilla con asunto «Otro»: el orientador la tipifica. El usuario no se notifica, el aviso se devuelve y se publica.",
    canal: "PRESENCIAL",
    datos: {
      tipoPQR: "QUEJA", asunto: "OTRO", codigoSuministro: "SUM-100203",
      descripcionPQR: "El funcionario de la ventanilla no me quiso recibir los documentos del trámite.", pretension: "Que se me atienda y se revise el trato recibido.",
      autorizaNotificacionElectronica: false,
      simularNotificacion: "NINGUNA", simularAviso: "DEVUELTO",
    },
  },
  "evidencia-tardia": {
    descripcion: "Reclamo por medición: C3 no entrega la evidencia a tiempo y se escala al dueño del proceso.",
    canal: "PORTAL",
    datos: {
      tipoPQR: "RECLAMO", asunto: "MEDICION", codigoSuministro: "SUM-100204",
      descripcionPQR: "El medidor marca consumo con el inmueble desocupado desde hace dos meses.", pretension: "Revisar el medidor y corregir la lectura.",
      autorizaNotificacionElectronica: true, correoNotificacion: "usuario.prueba@example.com",
      simularEvidencia: "NO_RESPONDE", simularNotificacion: "ACCESO_ELECTRONICO",
    },
  },
  "silencio-positivo": {
    descripcion: "Reclamo por facturación que nadie decide: salta la alerta del 70 % y, al vencer el término, opera el silencio administrativo positivo. NO complete la tarea del analista.",
    canal: "PORTAL",
    datos: {
      tipoPQR: "RECLAMO", asunto: "FACTURACION", codigoSuministro: "SUM-100205", numeroFactura: "FAC-2026-10-100205", fechaExpedicionFactura: haceMeses(1),
      descripcionPQR: "Me facturaron una lectura estimada muy superior al promedio.", pretension: "Facturar con la lectura real.",
      autorizaNotificacionElectronica: true, correoNotificacion: "usuario.prueba@example.com",
      simularEvidencia: "RESPONDE", simularNotificacion: "ACCESO_ELECTRONICO",
    },
  },
  "factura-antigua": {
    descripcion: "Reclamo sobre una factura de hace ocho meses: la tabla DMN alerta la posible improcedencia (art. 154) y exige revisión jurídica, pero no rechaza.",
    canal: "PRESENCIAL",
    datos: {
      tipoPQR: "RECLAMO", asunto: "FACTURACION", codigoSuministro: "SUM-100206", numeroFactura: "FAC-2026-03-100206", fechaExpedicionFactura: haceMeses(8),
      descripcionPQR: "La factura de marzo incluyó un cobro que nunca entendí.", pretension: "Devolver el valor cobrado de más.",
      autorizaNotificacionElectronica: false,
      simularEvidencia: "RESPONDE", simularNotificacion: "COMPARECE",
    },
  },
  "suspension": {
    descripcion: "Reclamo por suspensión con el pago al día: prioridad alta, evidencia de C5 y revisión jurídica.",
    canal: "TELEFONICO",
    datos: {
      tipoPQR: "RECLAMO", asunto: "SUSPENSION_RECONEXION", codigoSuministro: "SUM-100207",
      descripcionPQR: "Me suspendieron el servicio aunque pagué la factura dos días antes.", pretension: "Reconectar el servicio de inmediato.",
      autorizaNotificacionElectronica: true, correoNotificacion: "usuario.prueba@example.com",
      simularEvidencia: "RESPONDE", simularNotificacion: "ACCESO_ELECTRONICO",
    },
  },
};

// -----------------------------------------------------------------------------
//  Código de suministro forzado (C7_SUMINISTRO o --suministro). RadicarPqr marca
//  una reapertura si hay un expediente cerrado con el mismo suministro y asunto
//  en los últimos 30 días (15 min en demo); con un código nuevo se repite un
//  escenario sin provocarla. «auto» genera un código distinto para cada PQR.
// -----------------------------------------------------------------------------
let suministroForzado = (process.env.C7_SUMINISTRO || "").trim() || null;

function validarSuministro(valor) {
  if (/^(auto|aleatorio)$/i.test(valor)) return "auto";
  if (!PATRON_SUMINISTRO.test(valor)) {
    throw new Error(`Código de suministro no válido: «${valor}». Use el formato SUM-000000 o «auto».`);
  }
  return valor;
}

// SUM-9xxxxx: no choca con los códigos fijos de los escenarios (SUM-1002xx)
const suministroAleatorio = () => `SUM-9${String(Math.floor(Math.random() * 1e5)).padStart(5, "0")}`;

// -----------------------------------------------------------------------------
//  REST
// -----------------------------------------------------------------------------
async function pedir(ruta, opciones = {}) {
  try {
    return await fetch(`${BASE_URL}${ruta}`, opciones);
  } catch (err) {
    throw new Error(`No se pudo conectar con el motor en ${BASE_URL} (${err.cause?.code || err.message}).\n`
      + "  ¿Está corriendo la aplicación Spring Boot? Arránquela con «mvn spring-boot:run» en EMSA_springboot.");
  }
}

const enviar = (ruta, cuerpo) =>
  pedir(ruta, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(cuerpo) });

// lee el cuerpo una sola vez; un error HTTP sin JSON no debe ocultar el código de estado
async function leer(res) {
  const texto = await res.text();
  try {
    return { texto, cuerpo: texto ? JSON.parse(texto) : {} };
  } catch {
    return { texto, cuerpo: {} };
  }
}

// La clave de negocio (= radicado inicial) la asigna el listener de fin de «Radicar la PQR».
// Normalmente ya viene en la respuesta del inicio; si no, se consulta durante unos segundos.
async function expedienteDeInstancia(id, plazoMs = 5000) {
  const limite = Date.now() + plazoMs;
  try {
    for (;;) {
      let res = await pedir(`/process-instance/${encodeURIComponent(id)}`);
      if (res.status === 404) res = await pedir(`/history/process-instance/${encodeURIComponent(id)}`);
      if (res.ok) {
        const { businessKey } = await res.json();
        if (businessKey) return businessKey;
      }
      if (Date.now() >= limite) return null;
      await new Promise((r) => setTimeout(r, 300));
    }
  } catch {
    return null; // la PQR ya quedó radicada: no se falla solo por no poder mostrar el radicado
  }
}

// Qué comandos manuales tienen sentido para el caso recién radicado
function siguientesPasos(d, expediente) {
  const exp = expediente || "<expediente>";
  const lineas = ["Siguientes pasos (las tareas humanas se completan en Tasklist):"];
  if (d.simularRecurso && d.simularRecurso !== "NINGUNO") {
    lineas.push(`  recurso: lo presenta la app sola 6 s después de la notificación (simularRecurso = ${d.simularRecurso}); no lo repita.`);
  } else if (d.tipoPQR === "RECLAMO" && d.asunto !== "ATENCION_SERVICIO") {
    lineas.push(`  ${SCRIPT} recurso ${exp} [apelacion]`,
      `      tras notificarse la decisión y antes de fechaLimiteRecursos (5 días hábiles = ${5 * SEG_DIA_DEMO} s en demo)`);
  } else {
    lineas.push("  recurso: en principio no procede para este tipo de PQR (tabla DeterminarRecursosProcedentes).");
  }
  const notificacion = d.simularNotificacion ?? (d.autorizaNotificacionElectronica ? "ACCESO_ELECTRONICO" : "COMPARECE");
  const manuales = [`  ${SCRIPT} comparecer ${exp}   (notificación personal)`,
    `  ${SCRIPT} acceso ${exp}       (notificación electrónica)`];
  const ventana = `${5 * SEG_DIA_DEMO} s en demo desde la citación; si no, se notifica por aviso`;
  if (notificacion === "NINGUNA") {
    lineas.push(...manuales, `      mientras C7.1 espera la notificación: ${ventana}`);
  } else if (d.simularNotificacionReposicion === "NINGUNA") {
    lineas.push(`  notificación de la primera decisión: la simula la app (${notificacion}).`, ...manuales,
      `      solo al notificar la decisión de la reposición: ${ventana}`);
  } else {
    lineas.push(`  notificación: la simula la app 3 s después de la citación (${notificacion}); comparecer/acceso no hacen falta.`);
  }
  return lineas;
}

export async function radicar(nombre, extra = {}) {
  const e = ESCENARIOS[nombre];
  if (!e) throw new Error(`Escenario desconocido: ${nombre}`);
  const datos = { ...base, ...e.datos };
  const forzado = suministroForzado && validarSuministro(suministroForzado);
  if (forzado) datos.codigoSuministro = forzado === "auto" ? suministroAleatorio() : forzado;
  Object.assign(datos, extra);
  let res;
  if (e.canal === "PORTAL") {
    res = await enviar("/message", { messageName: "MsgPQRPortal", processVariables: tipar({ ...datos, canalRadicacion: "VIRTUAL" }), resultEnabled: true });
  } else {
    // mismo efecto que diligenciar el formulario de inicio «Radicar PQR» en Tasklist
    res = await enviar("/process-definition/key/C7_AtenderPQR/submit-form", { variables: tipar({ ...datos, canalRadicacion: e.canal }) });
  }
  const { texto, cuerpo } = await leer(res);
  if (!res.ok) throw new Error(`${nombre}: ${res.status} ${texto}`);
  const pi = Array.isArray(cuerpo) ? cuerpo[0]?.processInstance : cuerpo;
  const id = pi?.id;
  const expediente = pi?.businessKey || (id ? await expedienteDeInstancia(id) : null);
  console.log(`✔ ${nombre} (${e.canal}) → expediente ${expediente || "(aún sin radicado: búsquelo en Cockpit)"} · instancia ${id}`);
  console.log(`    ${envolver(e.descripcion, 100, 4)}`);
  console.log(`    Suministro ${datos.codigoSuministro}${forzado ? " (forzado)" : ""}`);
  for (const linea of siguientesPasos(datos, expediente)) console.log(`    ${linea}`);
  return id;
}

// -----------------------------------------------------------------------------
//  Recurso, comparecencia y acceso electrónico (siempre dirigidos a un expediente)
// -----------------------------------------------------------------------------
const CUANDO_NOTIFICACION = `solo se recibe mientras C7.1 espera en «¿Cómo se surte la notificación?» `
  + `(5 días hábiles desde la citación; ${5 * SEG_DIA_DEMO} s en demo) y si la app no la simula `
  + "(simularNotificacion = NINGUNA, p. ej. queja-tipificacion-manual).";
const CUANDO = {
  MsgRecursoReposicion: "El recurso solo se recibe mientras el caso espera en «¿Llega un recurso o vence el término "
    + "para recurrir?»: tras notificar una decisión de primera instancia contra la que proceden recursos y antes de "
    + `fechaLimiteRecursos (5 días hábiles; ${5 * SEG_DIA_DEMO} s en demo). Si el escenario trae simularRecurso, ya lo presentó la app.`,
  MsgComparecenciaNotificacion: `La comparecencia ${CUANDO_NOTIFICACION}`,
  MsgAccesoElectronico: `El acceso electrónico ${CUANDO_NOTIFICACION}`,
};

async function contarCasos(expediente) {
  const res = await pedir(`/process-instance/count?businessKey=${encodeURIComponent(expediente)}`);
  return res.ok ? (await res.json()).count : null;
}

// Acepta el radicado o, por comodidad, el id de una instancia (C7 o C7.1), que se traduce a su clave de negocio.
async function resolverExpediente(valor, cmd) {
  if (!valor) {
    throw new Error("Falta el expediente (clave de negocio = radicado inicial). No se envía un mensaje sin destino.\n"
      + `  Uso: ${SCRIPT} ${cmd} PQR-AAAA-NNNNNN${cmd === "recurso" ? " [apelacion]" : ""}`);
  }
  if (PATRON_RADICADO.test(valor)) return valor;
  const res = await pedir(`/process-instance/${encodeURIComponent(valor)}`);
  if (res.ok) {
    const { businessKey } = await res.json();
    if (businessKey) {
      console.log(`  (instancia ${valor} → expediente ${businessKey})`);
      return businessKey;
    }
  }
  if ((await contarCasos(valor)) > 0) return valor; // clave de negocio con otro formato
  throw new Error(`«${valor}» no es un radicado (PQR-AAAA-NNNNNN) ni el id de una instancia activa con radicado.`);
}

async function mensaje(nombre, expediente, variables = {}) {
  if (!expediente) throw new Error(`${nombre}: falta el expediente; no se correlaciona sin clave de negocio.`);
  const res = await enviar("/message", { messageName: nombre, businessKey: expediente, processVariables: tipar(variables), all: false });
  if (!res.ok) {
    const { texto, cuerpo } = await leer(res);
    let causa = `¿El expediente ${expediente} está esperando ese mensaje?`;
    try {
      const n = await contarCasos(expediente);
      if (n === 0) causa = `No hay ninguna instancia activa con el expediente ${expediente} (¿radicado mal escrito, caso cerrado o app reiniciada?).`;
      else if (n > 0) causa = `El expediente ${expediente} existe, pero ahora no espera ese mensaje.`;
    } catch { /* se deja la pista genérica */ }
    throw new Error(`${nombre}: ${res.status} ${cuerpo.message || texto}\n  ${causa}\n  ${CUANDO[nombre]}`);
  }
  console.log(`✔ ${nombre} correlacionado con el expediente ${expediente}`);
}

// -----------------------------------------------------------------------------
//  Línea de comandos
// -----------------------------------------------------------------------------
function envolver(texto, ancho, sangria) {
  const lineas = [];
  let actual = "";
  for (const palabra of texto.split(/\s+/)) {
    if (actual && actual.length + 1 + palabra.length > ancho) {
      lineas.push(actual);
      actual = palabra;
    } else {
      actual = actual ? `${actual} ${palabra}` : palabra;
    }
  }
  if (actual) lineas.push(actual);
  return lineas.join(`\n${" ".repeat(sangria)}`);
}

function ayuda() {
  const s = (dias) => `${dias * SEG_DIA_DEMO} s`;
  const escenarios = Object.entries(ESCENARIOS)
    .map(([k, e]) => `  ${k.padEnd(26)} ${envolver(`[${CANALES[e.canal] || e.canal}] ${e.descripcion}`, 72, 29)}`)
    .join("\n");
  console.log(`C7 · Atender una PQR del usuario — simulador de entradas

Requisitos
  · La aplicación Spring Boot debe estar en marcha («mvn spring-boot:run» en EMSA_springboot).
    Las tareas de servicio son JavaDelegate dentro de la app: ya no hay workers de Node que levantar.
  · Motor: ${BASE_URL}  (se cambia con la variable de entorno CAMUNDA_URL).
  · Las tareas humanas se completan en el portal ${BASE_URL.replace(/\/engine-rest\/?$/, "")}/ o en Tasklist
    ${BASE_URL.replace(/\/engine-rest\/?$/, "")}/camunda/app/tasklist/ (orientador1, analistapqr1, juridica1 o pqr1; clave 1234).

Uso  ${SCRIPT} <comando> [--suministro …]
     (desde simuladores/; también «npm run c7 -- <comando>»)
  (sin comando)                      esta ayuda
  <escenario>                        radica la PQR del escenario
  todos                              radica todos los escenarios
  recurso <expediente> [apelacion]   recurso de reposición (y apelación en subsidio)
  comparecer <expediente>            el usuario se presenta a notificarse en persona
  acceso <expediente>                constancia de acceso electrónico al acto

  <expediente> es el radicado inicial (PQR-AAAA-NNNNNN), la clave de negocio del caso (columna
  «Business Key» en Cockpit). El simulador lo imprime al radicar; también acepta el id de la instancia.

Opciones
  --suministro SUM-123456   radica con ese código de suministro en lugar del fijo del escenario
  --suministro auto         genera un código distinto para cada PQR (recomendado con «todos»)
  Equivale a la variable de entorno C7_SUMINISTRO. Sirve para repetir un escenario sin provocar una
  reapertura falsa: la app marca esReapertura (y exige revisión jurídica) si hay un expediente cerrado
  con el mismo suministro y asunto en los últimos 30 días (${s(30)} = 15 min en demo). Ese registro está
  en EMSA_springboot/expediente/ y sobrevive a los reinicios de la app.

Escenarios
${escenarios}

Tiempos en modo demo (emsa.c7.segundos-por-dia = ${SEG_DIA_DEMO} en application.yaml: 1 día hábil = ${SEG_DIA_DEMO} s)
  · Término de respuesta: 15 días hábiles = ${s(15)} desde la radicación; alerta al 70 %: ${s(10.5)}.
  · Plazo para notificarse (C7.1): 5 días hábiles = ${s(5)} desde la citación; después, aviso.
  · Plazo para recurrir: 5 días hábiles = ${s(5)} desde la notificación (fechaLimiteRecursos).
  · Ventana de reapertura: 30 días = ${s(30)} desde el cierre del expediente.
  Si cambia segundos-por-dia, multiplique los días por el nuevo valor. Con emsa.c7.modo = produccion
  son días hábiles reales (calendario de Colombia).

Cuándo sirven los comandos manuales
  recurso      Solo mientras el caso espera en «¿Llega un recurso o vence el término para recurrir?»:
               después de notificar una decisión de primera instancia contra la que proceden recursos
               (en estos escenarios, los reclamos) y antes de fechaLimiteRecursos (${s(5)} en demo).
               En reclamo-telefono-recurso (simularRecurso) la app lo presenta sola a los 6 s:
               no lo repita.
  comparecer   Solo mientras C7.1 espera en «¿Cómo se surte la notificación?» (${s(5)} en demo desde la
  acceso       citación) y si la app no simula la notificación (simularNotificacion = NINGUNA):
               queja-tipificacion-manual, o la notificación de la reposición en
               reclamo-telefono-recurso. En los demás escenarios la app envía sola el acceso o la
               comparecencia a los 3 s.`);
}

async function principal(argumentos) {
  const posicionales = [];
  for (let i = 0; i < argumentos.length; i++) {
    const a = argumentos[i];
    if (a === "--suministro") {
      suministroForzado = argumentos[++i];
      if (!suministroForzado) throw new Error("--suministro necesita un valor: SUM-000000 o auto.");
    } else if (a.startsWith("--suministro=")) {
      suministroForzado = a.slice("--suministro=".length);
    } else if (["-h", "--help", "--ayuda", "ayuda", "help"].includes(a)) {
      return ayuda();
    } else if (a.startsWith("-")) {
      throw new Error(`Opción desconocida: ${a}. Ejecute «${SCRIPT}» sin argumentos para ver la ayuda.`);
    } else {
      posicionales.push(a);
    }
  }
  if (suministroForzado) suministroForzado = validarSuministro(suministroForzado);
  const [cmd, arg1, arg2] = posicionales;
  if (!cmd) {
    ayuda();
  } else if (cmd === "todos") {
    for (const k of Object.keys(ESCENARIOS)) await radicar(k);
  } else if (cmd === "recurso") {
    if (arg2 !== undefined && !/^apelaci[oó]n$/i.test(arg2)) {
      throw new Error(`Argumento desconocido: «${arg2}». Uso: ${SCRIPT} recurso PQR-AAAA-NNNNNN [apelacion]`);
    }
    const expediente = await resolverExpediente(arg1, cmd);
    await mensaje("MsgRecursoReposicion", expediente, { apelacionSubsidiaria: arg2 !== undefined, argumentosRecurso: "Recurso presentado desde el simulador." });
  } else if (cmd === "comparecer") {
    await mensaje("MsgComparecenciaNotificacion", await resolverExpediente(arg1, cmd), { fechaComparecencia: new Date().toISOString() });
  } else if (cmd === "acceso") {
    await mensaje("MsgAccesoElectronico", await resolverExpediente(arg1, cmd), { fechaAccesoElectronico: new Date().toISOString() });
  } else if (ESCENARIOS[cmd]) {
    await radicar(cmd);
  } else {
    throw new Error(`Escenario o comando desconocido: ${cmd}. Ejecute «${SCRIPT}» sin argumentos para ver la ayuda.`);
  }
}

// importado como módulo (p. ej. por una prueba E2E) no ejecuta la línea de comandos
const esPrincipal = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (esPrincipal) {
  try {
    await principal(process.argv.slice(2));
  } catch (err) {
    console.error(`✖ ${err.message}`);
    process.exit(1);
  }
}
