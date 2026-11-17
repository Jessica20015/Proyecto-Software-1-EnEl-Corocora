// =============================================================================
//  C7 · Atender una PQR del usuario — simulador de entradas
//
//  Hace el papel del usuario y de los canales: radica PQR por el portal
//  (mensaje MsgPQRPortal) o por ventanilla/teléfono (inicio con el mismo
//  contenido del formulario «Radicar PQR»), y permite presentar a mano un
//  recurso o una comparecencia. Las tareas humanas se completan en Tasklist.
//
//  Uso:
//    node simular-entradas.js                       lista los escenarios
//    node simular-entradas.js <escenario>           radica la PQR del escenario
//    node simular-entradas.js todos                 radica todos los escenarios
//    node simular-entradas.js recurso <expediente> [apelacion]
//    node simular-entradas.js comparecer <expediente>
//    node simular-entradas.js acceso <expediente>
// =============================================================================
import { pathToFileURL } from "node:url";
const BASE_URL = process.env.CAMUNDA_URL || "http://localhost:8080/engine-rest";

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

export async function radicar(nombre, extra = {}) {
  const e = ESCENARIOS[nombre];
  const datos = { ...base, ...e.datos, ...extra };
  let res;
  if (e.canal === "PORTAL") {
    res = await fetch(`${BASE_URL}/message`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ messageName: "MsgPQRPortal", processVariables: tipar({ ...datos, canalRadicacion: "VIRTUAL" }), resultEnabled: true }),
    });
  } else {
    // mismo efecto que diligenciar el formulario de inicio «Radicar PQR» en Tasklist
    res = await fetch(`${BASE_URL}/process-definition/key/C7_AtenderPQR/submit-form`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ variables: tipar({ ...datos, canalRadicacion: e.canal }) }),
    });
  }
  const cuerpo = await res.json();
  if (!res.ok) throw new Error(`${nombre}: ${res.status} ${JSON.stringify(cuerpo)}`);
  const id = Array.isArray(cuerpo) ? cuerpo[0]?.processInstance?.id : cuerpo.id;
  console.log(`✔ ${nombre} (${e.canal}) → instancia ${id}\n    ${e.descripcion}`);
  return id;
}

async function mensaje(nombre, expediente, variables = {}) {
  const res = await fetch(`${BASE_URL}/message`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ messageName: nombre, businessKey: expediente, processVariables: tipar(variables), all: false }),
  });
  if (!res.ok) throw new Error(`${nombre}: ${res.status} ${await res.text()}`);
  console.log(`✔ ${nombre} correlacionado con el expediente ${expediente}`);
}

const esPrincipal = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
const [cmd, arg1, arg2] = esPrincipal ? process.argv.slice(2) : ["--importado"];
if (cmd === "--importado") {
  // importado como módulo: no ejecuta la línea de comandos
} else if (!cmd) {
  console.log("Escenarios disponibles:\n");
  for (const [k, e] of Object.entries(ESCENARIOS)) console.log(`  ${k.padEnd(28)} ${e.descripcion}\n`);
  console.log("Otros comandos: todos · recurso <expediente> [apelacion] · comparecer <expediente> · acceso <expediente>");
} else if (cmd === "todos") {
  for (const k of Object.keys(ESCENARIOS)) await radicar(k);
} else if (cmd === "recurso") {
  await mensaje("MsgRecursoReposicion", arg1, { apelacionSubsidiaria: arg2 === "apelacion", argumentosRecurso: "Recurso presentado desde el simulador." });
} else if (cmd === "comparecer") {
  await mensaje("MsgComparecenciaNotificacion", arg1, { fechaComparecencia: new Date().toISOString() });
} else if (cmd === "acceso") {
  await mensaje("MsgAccesoElectronico", arg1, { fechaAccesoElectronico: new Date().toISOString() });
} else if (ESCENARIOS[cmd]) {
  await radicar(cmd);
} else {
  console.error(`Escenario desconocido: ${cmd}`);
  process.exit(1);
}
