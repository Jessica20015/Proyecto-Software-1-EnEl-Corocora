// Ruta relativa: funciona en localhost, 127.0.0.1, otra IP u otro puerto (mismo origen, sin CORS).
const API = "/engine-rest";
let procesoActivo = null;
let sesion = null;
let tareaActual = null;
let mapaDefiniciones = {}; // { processDefinitionId: processDefinitionKey }
let temporizadorRefresco = null; // refresco automático de la cola y del monitor mientras hay sesión
let refrescoEnCurso = false;
let operacionEnCurso = false; // evita el doble clic en «Radicar» / «Finalizar» (dos instancias o dos envíos)
let temporizadorToast = null;
// Camunda Forms (form-js) montados: uno para el formulario de inicio y otro para la tarea abierta.
const formulariosCamunda = { inicio: null, tarea: null };
const turnosFormulario = { inicio: 0, tarea: 0 }; // descarta una carga que otra más reciente ya reemplazó
const CONTENEDORES_FORMULARIO = { inicio: "formInicioCamunda", tarea: "formTareaCamunda" };

const HTML_PANEL_TAREA_VACIO = `
                    <p style="color: var(--muted); text-align: center; padding: 30px 0;">
                        Selecciona una tarea de tu cola izquierda para visualizar los datos del caso y completarla.
                    </p>`;

/** Escapa texto que viene del engine (o del usuario) antes de meterlo en innerHTML. */
function esc(valor) {
  return String(valor ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/** Mensaje de error legible a partir de la respuesta REST de Camunda ({ type, message }). */
async function mensajeError(res) {
  const texto = await res.text();
  try { return JSON.parse(texto).message || texto; } catch { return texto || `HTTP ${res.status}`; }
}

const CATALOGO_PROCESOS = {
  C2: {
    codigo: "PROCESO C2",
    nombre: "Restablecimiento del Servicio tras Falla",
    processKeys: ["restablecerServicioTrasFalla", "realizarVisitaTecnica"],
    actores: [
      { user: "operador1", rol: "Operador Centro de Control", grupo: "operador-control" },
      { user: "cuadrilla1", rol: "Cuadrilla Técnica", grupo: "cuadrilla" },
      { user: "coordinador1", rol: "Coordinador de Zona", grupo: "coordinador-zona" }
    ],
    inicio: {
      grupoPermitido: "operador-control",
      titulo: "Nuevo Reporte de Falla Eléctrica (Proceso C2)",
      tipoInicio: "start",
      processKey: "restablecerServicioTrasFalla",
      campos: [
        { key: "nombre", label: "Nombre del ciudadano", placeholder: "Ej. Juan Pérez", type: "String", input: "text" },
        { key: "domicilio", label: "Dirección del suministro", placeholder: "Ej. Calle 15 # 32-10", type: "String", input: "text" },
        { key: "falla", label: "Descripción de la falla", placeholder: "Detalle lo reportado por el usuario...", type: "String", input: "textarea" }
      ]
    }
  },
  C4: {
    codigo: "PROCESO C4",
    nombre: "Facturación del Servicio de Energía",
    processKeys: ["C4_FacturarServicioEnergia", "C4_EntregarFactura"],
    actores: [
      { user: "analista1", rol: "Analista de Facturación", grupo: "analistas-facturacion" },
      { user: "calidad1", rol: "Control de Calidad de Facturación", grupo: "calidad-facturacion" },
      { user: "liquidador1", rol: "Liquidador Tarifario", grupo: "liquidadores-tarifarios" }
    ],
    // Las tareas de C4 y C4.1 se muestran con su Camunda Form desplegado (.form, con sus defaultValue),
    // igual que en Tasklist. El inicio sigue siendo por mensaje: C4 no tiene formulario de inicio.
    usarFormulariosCamunda: true,
    inicio: {
      grupoPermitido: "analistas-facturacion",
      titulo: "Simular Lectura Validada (Iniciar Proceso C4)",
      tipoInicio: "message",
      messageName: "MsgConsumoValidado",
      // Variables fijas que solo usa C4 (antes se enviaban en el inicio de TODOS los procesos)
      variablesFijas: {
        estimacionesConsecutivas: { value: 0, type: "Long" },
        devolucionesC3: { value: 0, type: "Long" },
        canalPreferido: { value: "DIGITAL", type: "String" }
      },
      campos: [
        { key: "codigoSuministro", label: "Código de Suministro", value: "SUM-100101", type: "String", input: "text" },
        { key: "periodo", label: "Periodo de facturación", value: "2026-11", type: "String", input: "text" },
        { key: "usoServicio", label: "Uso del servicio", type: "String", input: "select", opciones: [["RESIDENCIAL", "Residencial"], ["COMERCIAL", "Comercial"], ["INDUSTRIAL", "Industrial"]] },
        { key: "estrato", label: "Estrato", type: "Double", input: "select", opciones: [["2", "Estrato 2"], ["1", "Estrato 1"], ["3", "Estrato 3"], ["4", "Estrato 4"], ["5", "Estrato 5"], ["6", "Estrato 6"]] },
        { key: "tipoLectura", label: "Tipo de lectura (define ruta)", type: "String", input: "select", opciones: [["REAL", "REAL (va directo a Calidad)"], ["ESTIMADA", "ESTIMADA (va a Analista)"]] },
        { key: "consumoKwh", label: "Consumo actual (kWh)", value: "185", type: "Double", input: "number" },
        { key: "consumoPromedioKwh", label: "Consumo promedio histórico (kWh)", value: "180", type: "Double", input: "number" }
      ]
    }
  },
  C7: {
    codigo: "PROCESO C7",
    nombre: "Atención de PQR",
    // C7 y su subproceso C7.1 (Notificar la decisión, invocado por Call Activity)
    processKeys: ["C7_AtenderPQR", "C7_NotificarDecision"],
    // Los grupos son los camunda:candidateGroups de C7_AtenderPQR.bpmn y C7_NotificarDecision.bpmn (un carril cada uno)
    actores: [
      { user: "orientador1", rol: "Orientador y enrutador de usuarios", grupo: "orientadores-pqr" },
      { user: "analistapqr1", rol: "Analista de PQR", grupo: "analistas-pqr" },
      { user: "juridica1", rol: "Apoyo jurídico de PQR", grupo: "juridica-pqr" },
      // pqr1 pertenece a los tres grupos de C7: sirve para recorrer el proceso completo con un solo usuario
      { user: "pqr1", rol: "Coordinación de la Oficina de PQR (los tres roles)", grupo: "orientadores-pqr" }
    ],
    // Las tareas de C7 se muestran con su Camunda Form desplegado (.form), igual que en Tasklist.
    usarFormulariosCamunda: true,
    inicio: {
      grupoPermitido: "orientadores-pqr",
      titulo: "Radicar PQR en ventanilla o por teléfono (Proceso C7)",
      tipoInicio: "camunda-form", // formulario de inicio desplegado: RadicarPQR.form (con sus defaultValue)
      processKey: "C7_AtenderPQR",
      // camunda:initiator="orientadorRadicador": /engine-rest no autentica, así que se envía explícito
      variablesExtra: () => ({ orientadorRadicador: { value: sesion.username, type: "String" } })
    }
  }
};

const FORMULARIOS_TAREA = {
  ActivityValidarAlarmas: [
    { key: "alarmaValida", label: "¿El reporte corresponde a una falla real?", type: "Boolean", input: "select", opciones: [["true", "Sí, incidente válido (agendar visita)"], ["false", "No, falsa alarma (cerrar caso)"]] },
    { key: "observacionOperador", label: "Observaciones del Centro de Control", type: "String", input: "textarea" }
  ],
  agendarVisita: [
    { key: "fechaVisita", label: "Fecha programada de visita", type: "String", input: "date" },
    { key: "franjaHorario", label: "Franja horaria", type: "String", input: "select", opciones: [["manana", "Mañana (08:00 - 12:00)"], ["tarde", "Tarde (14:00 - 18:00)"]] }
  ],
  ValidarVisita: [
    { key: "visita", label: "¿Se logró acceder al predio y realizar la visita?", type: "Boolean", input: "select", opciones: [["true", "Sí, visita realizada"], ["false", "No, predio cerrado / sin acceso"]] }
  ],
  Activity_1sawny7: [
    { key: "nivelDano", label: "Clasificación del daño en terreno (DMN)", type: "String", input: "select", opciones: [["Leve", "Leve (reparación inmediata)"], ["Moderado", "Moderado (requiere repuestos)"], ["Severo", "Severo (requiere brigada mayor)"]] },
    { key: "diagnosticoTecnico", label: "Diagnóstico técnico de cuadrilla", type: "String", input: "textarea" }
  ],
  realizarReporteVisita: [
    { key: "servicioRestablecido", label: "¿Quedó restablecido el servicio?", type: "Boolean", input: "select", opciones: [["true", "Sí, servicio normalizado"], ["false", "No, pendiente intervención"]] },
    { key: "actaTrabajo", label: "Resumen del trabajo ejecutado", type: "String", input: "textarea" }
  ]
  // C4 no tiene entradas aquí: usa sus Camunda Forms desplegados (usarFormulariosCamunda en CATALOGO_PROCESOS.C4)
};

// ------------------------- Camunda Forms (form-js) -------------------------
// Se renderiza el MISMO .form desplegado que usa Tasklist (grupos, campos condicionales, checklist,
// validación required/minLength/pattern/email y defaultValue). form-js 1.8.7 = schemaVersion 16 (Tasklist 7.24).

// form-js 1.8 solo trae los mensajes de validación en inglés: se traducen al vuelo.
const TRADUCCIONES_FORM_JS = [
  [/^Field is required\.$/, "Este campo es obligatorio."],
  [/^Field must match pattern [\s\S]*\.$/, "El valor no tiene el formato esperado."],
  [/^Field must have minimum length of (\d+)\.$/, "Debe tener al menos $1 caracteres."],
  [/^Field must have maximum length of (\d+)\.$/, "Debe tener como máximo $1 caracteres."],
  [/^Field must have minimum value of (.+)\.$/, "El valor mínimo permitido es $1."],
  [/^Field must have maximum value of (.+)\.$/, "El valor máximo permitido es $1."],
  [/^Field must be a valid email\.$/, "Debe ser un correo electrónico válido."],
  [/^Field must be a valid\s+international phone number[\s\S]*$/, "Debe ser un número telefónico internacional válido (p. ej., +573001234567)."],
  [/^Value is not a number\.$/, "El valor no es un número."],
  [/^Value is expected to be an integer\.$/, "El valor debe ser un número entero."],
  [/^Value is expected to have at most (\d+) decimal digits?\.$/, "El valor admite como máximo $1 decimales."],
  [/^Please select a valid value, the two nearest valid values are (.+) and (.+)\.$/, "Valor no válido: los valores válidos más cercanos son $1 y $2."]
];

function traducirMensajeFormJs(mensaje) {
  for (const [patron, traduccion] of TRADUCCIONES_FORM_JS) {
    if (patron.test(mensaje)) return mensaje.replace(patron, traduccion);
  }
  return mensaje;
}

/** Envuelve el validador de form-js para que los errores salgan en español (si falla, quedan en inglés). */
function traducirValidaciones(form) {
  try {
    const validador = form.get("validator");
    ["validateField", "validateFieldInstance"].forEach(metodo => {
      if (typeof validador[metodo] !== "function") return;
      const original = validador[metodo].bind(validador);
      validador[metodo] = (...args) => (original(...args) || []).map(traducirMensajeFormJs);
    });
  } catch (e) {
    console.warn("No se pudieron traducir los mensajes de validación de form-js:", e);
  }
}

/** submit() solo ve los campos ya pintados: justo después de importSchema devuelve {} sin errores. */
async function esperarCamposRenderizados(form) {
  const registro = form.get("formFieldInstanceRegistry");
  for (let i = 0; i < 40 && registro.getAllKeyed().length === 0; i++) {
    await new Promise(r => setTimeout(r, 50));
  }
}

async function montarFormularioCamunda(slot, contenedor, urlSchema, datos) {
  destruirFormularioCamunda(slot);
  const turno = turnosFormulario[slot];
  if (typeof FormViewer === "undefined") {
    contenedor.innerHTML = `<p style="color:var(--danger);">No se cargó form-js (vendor/form-js/form-viewer.umd.js).</p>`;
    throw new Error("form-js no disponible");
  }
  const res = await fetch(urlSchema);
  if (!res.ok) throw new Error(await mensajeError(res));
  const schema = await res.json();
  if (turno !== turnosFormulario[slot]) return null; // otra carga más reciente lo reemplazó
  contenedor.innerHTML = "";
  const form = new FormViewer.Form({ container: contenedor });
  traducirValidaciones(form);
  await form.importSchema(schema, datos || {});
  await esperarCamposRenderizados(form);
  if (turno !== turnosFormulario[slot]) {
    form.destroy();
    return null;
  }
  formulariosCamunda[slot] = form;
  return form;
}

function mostrarErrorFormulario(contenedor, mensaje) {
  if (contenedor) contenedor.innerHTML = `<p style="color:var(--danger);">${esc(mensaje)}</p>`;
}

function destruirFormularioCamunda(slot) {
  turnosFormulario[slot]++;
  if (formulariosCamunda[slot]) {
    formulariosCamunda[slot].destroy();
    formulariosCamunda[slot] = null;
  }
}

/**
 * Valida y convierte los datos del formulario en variables REST, con el mismo criterio de Tasklist 7.24:
 * objetos/arreglos (checklist) → Json; primitivos sin tipo (el engine infiere String/Boolean/Integer/Double).
 * Solo se envían los campos visibles y habilitados. Devuelve null si hay errores (form-js los pinta en rojo).
 */
function leerFormularioCamunda(slot) {
  const form = formulariosCamunda[slot];
  if (!form) {
    mostrarToast("El formulario todavía se está cargando. Intenta de nuevo en un momento.");
    return null;
  }
  // form-js aplica lo tecleado con "debounce"; al quitar el foco se vuelca el último valor.
  if (document.activeElement && typeof document.activeElement.blur === "function") document.activeElement.blur();
  const { data, errors } = form.submit();
  if (Object.keys(errors).length > 0) {
    mostrarToast("Revisa los campos marcados en rojo del formulario.");
    setTimeout(() => { // form-js pinta los errores en el siguiente render
      const contenedor = document.getElementById(CONTENEDORES_FORMULARIO[slot]);
      const primerError = contenedor && contenedor.querySelector(".fjs-has-errors");
      if (primerError) primerError.scrollIntoView({ behavior: "smooth", block: "center" });
    }, 100);
    return null;
  }
  const variables = {};
  for (const [k, v] of Object.entries(data)) {
    variables[k] = (v !== null && typeof v === "object")
      ? { value: JSON.stringify(v), type: "Json" }
      : { value: v };
  }
  return variables;
}

/** Variables de la tarea para precargar el formulario (igual que Tasklist: sin deserializar, JSON parseado). */
async function datosParaFormulario(taskId) {
  const res = await fetch(`${API}/task/${taskId}/form-variables?deserializeValues=false`);
  if (!res.ok) throw new Error(await mensajeError(res));
  const datos = {};
  for (const [k, v] of Object.entries(await res.json())) {
    const esJson = v.type === "Json" || (v.valueInfo && v.valueInfo.serializationDataFormat === "application/json");
    if (esJson) {
      try { datos[k] = JSON.parse(v.value); } catch { datos[k] = v.value; }
    } else if (!["Object", "Bytes", "File"].includes(v.type)) {
      datos[k] = v.value;
    }
  }
  return datos;
}

/** Texto para el aviso de radicación: número de radicado si ya existe, si no el expediente o la instancia. */
async function referenciaRadicacion(instancia) {
  try {
    const res = await fetch(`${API}/process-instance/${instancia.id}/variables/radicado?deserializeValue=false`);
    if (res.ok) {
      const variable = await res.json();
      if (variable.value) return `radicado ${variable.value}`;
    }
  } catch (e) { /* se usa la referencia de respaldo */ }
  return instancia.businessKey ? `expediente ${instancia.businessKey}` : `instancia ${instancia.id}`;
}

async function cargarMapaDefiniciones() {
  const res = await fetch(`${API}/process-definition`);
  if (!res.ok) return;
  const defs = await res.json();
  mapaDefiniciones = {};
  defs.forEach(d => {
    mapaDefiniciones[d.id] = d.key;
  });
}

function perteneceAProcesoActivo(processDefinitionId) {
  if (!processDefinitionId) return false;
  const keysProceso = CATALOGO_PROCESOS[procesoActivo].processKeys;
  const keyReal = mapaDefiniciones[processDefinitionId];
  if (keyReal) return keysProceso.includes(keyReal);
  return keysProceso.some(k => processDefinitionId.startsWith(`${k}:`));
}

/** Escribe el HTML solo si cambió: el refresco automático no parpadea ni se come el clic en «Gestionar». */
function pintar(contenedor, html) {
  if (contenedor._ultimoHtml === html) return;
  contenedor.innerHTML = html;
  contenedor._ultimoHtml = html;
}

function seleccionarProceso(codigo) {
  procesoActivo = codigo;
  const info = CATALOGO_PROCESOS[codigo];

  document.getElementById("loginProcessBadge").textContent = info.codigo;
  document.getElementById("loginProcessTitle").textContent = info.nombre;
  document.getElementById("loginUser").value = "";
  document.getElementById("loginPass").value = "1234";
  document.getElementById("loginError").textContent = "";

  const contenedor = document.getElementById("listaActoresProceso");
  contenedor.innerHTML = info.actores.map(a => `
    <button type="button" class="actor-chip-btn" onclick="autocompletarActor('${a.user}')">
      <span><strong>${a.rol}</strong> <small style="color:var(--muted);">(${a.user})</small></span>
      <span class="badge">Seleccionar</span>
    </button>
  `).join("");

  document.getElementById("processSelectView").classList.add("hidden");
  document.getElementById("appView").classList.add("hidden");
  document.getElementById("loginView").classList.remove("hidden");
}

function autocompletarActor(username) {
  document.getElementById("loginUser").value = username;
  document.getElementById("loginPass").value = "1234";
}

function detenerRefresco() {
  if (temporizadorRefresco) clearInterval(temporizadorRefresco);
  temporizadorRefresco = null;
}

function cerrarFormulariosCamunda() {
  destruirFormularioCamunda("inicio");
  destruirFormularioCamunda("tarea");
}

function volverASelector() {
  detenerRefresco();
  cerrarFormulariosCamunda();
  procesoActivo = null;
  sesion = null;
  tareaActual = null;
  document.getElementById("loginView").classList.add("hidden");
  document.getElementById("appView").classList.add("hidden");
  document.getElementById("userBar").classList.add("hidden");
  document.getElementById("processSelectView").classList.remove("hidden");
}

function cambiarDeProceso() {
  volverASelector();
}

async function iniciarSesion() {
  const username = document.getElementById("loginUser").value.trim();
  const password = document.getElementById("loginPass").value.trim();
  const errorBox = document.getElementById("loginError");
  errorBox.textContent = "";

  if (!username) {
    errorBox.textContent = "Selecciona o escribe un usuario autorizado.";
    return;
  }

  let data;
  try {
    const res = await fetch(`${API}/identity/verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });
    if (!res.ok) {
      errorBox.textContent = `El engine rechazó la verificación: ${await mensajeError(res)}`;
      return;
    }
    data = await res.json();
  } catch (e) {
    errorBox.textContent = "No hay conexión con el engine (/engine-rest). ¿Está corriendo la aplicación?";
    return;
  }

  if (!data.authenticated) {
    errorBox.textContent = "Credenciales inválidas. Verifica usuario y contraseña.";
    return;
  }

  const resGrupos = await fetch(`${API}/group?member=${encodeURIComponent(username)}`);
  const gruposUsuario = (await resGrupos.json()).map(g => g.id);

  const infoProceso = CATALOGO_PROCESOS[procesoActivo];
  const gruposPermitidos = infoProceso.actores.map(a => a.grupo);
  if (!gruposUsuario.some(g => gruposPermitidos.includes(g))) {
    errorBox.textContent = `El usuario "${username}" no tiene un rol asignado dentro de ${infoProceso.codigo}.`;
    return;
  }

  sesion = { username, grupos: gruposUsuario };
  tareaActual = null;
  await cargarMapaDefiniciones();

  document.getElementById("loginView").classList.add("hidden");
  document.getElementById("appView").classList.remove("hidden");
  document.getElementById("userBar").classList.remove("hidden");
  document.getElementById("userInfo").innerHTML = `
    <span class="badge" style="background:#38bdf8; color:#0f172a; margin-right:8px;">${infoProceso.codigo}</span>
    <strong>${esc(username)}</strong> <span class="badge badge-role">${esc(gruposUsuario.join(", "))}</span>
  `;
  // No dejar a la vista la tarea de la sesión (o del proceso) anterior
  document.getElementById("panelDetalleTarea").innerHTML = HTML_PANEL_TAREA_VACIO;

  renderizarPanelInicio();
  // Los procesos avanzan solos (en C7: mensajes simulados a los 3 s y plazos de 30 s por día hábil en modo demo):
  // se refrescan la cola y el monitor cada 5 s; el formulario abierto no se toca.
  detenerRefresco();
  temporizadorRefresco = setInterval(refrescarEnSegundoPlano, 5000);
  await actualizarTodo();
}

async function refrescarEnSegundoPlano() {
  if (!sesion || refrescoEnCurso) return;
  refrescoEnCurso = true;
  try {
    await actualizarTodo();
  } catch (e) {
    console.warn("Refresco automático fallido:", e);
  } finally {
    refrescoEnCurso = false;
  }
}

function cerrarSesion() {
  detenerRefresco();
  cerrarFormulariosCamunda();
  sesion = null;
  tareaActual = null;
  document.getElementById("toast").classList.add("hidden");
  document.getElementById("appView").classList.add("hidden");
  document.getElementById("userBar").classList.add("hidden");
  seleccionarProceso(procesoActivo);
}

function renderizarPanelInicio() {
  const contenedor = document.getElementById("panelInicioProcesos");
  const cfgInicio = CATALOGO_PROCESOS[procesoActivo].inicio;
  destruirFormularioCamunda("inicio");

  if (!cfgInicio || !sesion.grupos.includes(cfgInicio.grupoPermitido)) {
    contenedor.classList.add("hidden");
    return;
  }

  contenedor.classList.remove("hidden");
  const esCamundaForm = cfgInicio.tipoInicio === "camunda-form";
  const cuerpo = esCamundaForm
    ? `<div id="formInicioCamunda" class="form-camunda"><p style="color:var(--muted);">Cargando formulario de inicio…</p></div>`
    : `<div class="form-grid">${cfgInicio.campos.map(c => renderizarCampoHTML("init", c)).join("")}</div>`;
  contenedor.innerHTML = `
    <div class="card-header">
      <h3>${cfgInicio.titulo}</h3>
      <span class="badge">Rol iniciador: ${cfgInicio.grupoPermitido}</span>
    </div>
    ${cuerpo}
    <button onclick="iniciarProcesoActivo()">Radicar e Iniciar Flujo</button>
  `;
  if (esCamundaForm) {
    // Sin datos iniciales: así se aplican los defaultValue del .form desplegado (igual que «Start process» en Tasklist)
    const contenedorForm = document.getElementById("formInicioCamunda");
    montarFormularioCamunda("inicio", contenedorForm,
      `${API}/process-definition/key/${cfgInicio.processKey}/deployed-start-form`, {})
      .catch(e => mostrarErrorFormulario(contenedorForm, `No se pudo cargar el formulario de inicio: ${e.message}`));
  }
}

async function iniciarProcesoActivo() {
  if (operacionEnCurso) return;
  const cfg = CATALOGO_PROCESOS[procesoActivo].inicio;
  if (cfg.tipoInicio === "camunda-form") {
    await radicarConFormularioCamunda(cfg);
    return;
  }

  const variables = { ...(cfg.variablesFijas || {}) };

  cfg.campos.forEach(c => {
    let val = document.getElementById(`init_${c.key}`).value;
    if (c.type === "Long") val = Number(val || 0);
    if (c.type === "Double") val = Number(val || 0);
    variables[c.key] = { value: val, type: c.type };
  });

  operacionEnCurso = true;
  try {
    let res;
    if (cfg.tipoInicio === "message") {
      const businessKey = document.getElementById("init_codigoSuministro")?.value || `SUM-${Date.now()}`;
      res = await fetch(`${API}/message`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          messageName: cfg.messageName,
          businessKey,
          processVariables: variables
        })
      });
    } else {
      res = await fetch(`${API}/process-definition/key/${cfg.processKey}/start`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ variables })
      });
    }

    if (res.ok) {
      mostrarToast("Instancia creada. Revisa tu cola o el estado del proceso abajo.");
      await actualizarTodo();
    } else {
      const msg = await mensajeError(res);
      console.error("Error iniciando proceso:", msg);
      mostrarToast(`No se pudo iniciar el proceso: ${msg}`, 8000);
    }
  } finally {
    operacionEnCurso = false;
  }
}

/** Igual que «Start process» en Tasklist: POST /process-definition/key/{key}/submit-form con los datos del .form. */
async function radicarConFormularioCamunda(cfg) {
  const variables = leerFormularioCamunda("inicio");
  if (!variables) return;
  Object.assign(variables, cfg.variablesExtra ? cfg.variablesExtra() : {});

  operacionEnCurso = true;
  try {
    const res = await fetch(`${API}/process-definition/key/${cfg.processKey}/submit-form`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ variables })
    });
    if (!res.ok) {
      const msg = await mensajeError(res);
      console.error("Error iniciando proceso:", msg);
      mostrarToast(`No se pudo radicar la PQR: ${msg}`, 8000);
      return;
    }
    const instancia = await res.json();
    mostrarToast(`PQR radicada (${await referenciaRadicacion(instancia)}). Seguirá en la cola del rol que corresponda.`, 6000);
    renderizarPanelInicio(); // formulario limpio (con sus valores por defecto) para la siguiente PQR
    await actualizarTodo();
  } finally {
    operacionEnCurso = false;
  }
}

async function actualizarTodo() {
  if (Object.keys(mapaDefiniciones).length === 0) {
    await cargarMapaDefiniciones();
  }
  await Promise.all([cargarColaTareas(), cargarEstadoServiceTasks()]);
}

async function cargarColaTareas() {
  const sesionConsulta = sesion;
  if (!sesionConsulta) return;
  const mapaTareas = new Map();

  // 1. Tareas asignadas directamente al usuario
  const resAsignadas = await fetch(`${API}/task?assignee=${encodeURIComponent(sesionConsulta.username)}`);
  (await resAsignadas.json()).forEach(t => mapaTareas.set(t.id, t));

  // 2. Tareas en cola de los grupos del usuario
  for (const grupo of sesionConsulta.grupos) {
    const resGrupo = await fetch(`${API}/task?candidateGroup=${encodeURIComponent(grupo)}`);
    (await resGrupo.json()).forEach(t => mapaTareas.set(t.id, t));
  }

  // Consultar también todas las tareas activas de este proceso para informar si pasaron a otro rol
  const resTodas = await fetch(`${API}/task`);
  const todas = await resTodas.json();
  if (sesion !== sesionConsulta) return; // la sesión cambió mientras se consultaba

  // Filtrar por el proceso activo usando el mapa real de IDs -> Keys
  const lista = Array.from(mapaTareas.values()).filter(t => perteneceAProcesoActivo(t.processDefinitionId));
  const todasProceso = todas.filter(t => perteneceAProcesoActivo(t.processDefinitionId));
  const enOtrosRoles = todasProceso.filter(t => !mapaTareas.has(t.id));

  document.getElementById("contadorTareas").textContent = `${lista.length} pendiente(s)`;
  const contenedor = document.getElementById("listaTareas");

  let html = "";
  if (lista.length === 0) {
    html += `<p style="color:var(--muted);">No tienes tareas pendientes en tu rol dentro de ${CATALOGO_PROCESOS[procesoActivo].codigo}.</p>`;
  } else {
    html += lista.map(t => `
      <div class="task-item ${tareaActual && tareaActual.id === t.id ? "active" : ""}">
        <div>
          <strong>${esc(t.name)}</strong><br>
          <small style="color:var(--muted);">ID tarea: ${esc(t.taskDefinitionKey)} · ${t.assignee ? "Asignada a ti" : "En cola de grupo"}</small>
        </div>
        <button class="btn-outline" onclick="seleccionarTarea('${t.id}', '${t.taskDefinitionKey}', '${t.name}', '${t.assignee || ""}')">Gestionar</button>
      </div>
    `).join("");
  }

  if (enOtrosRoles.length > 0) {
    html += `
      <div style="margin-top:14px; padding:12px; background:#eff6ff; border:1px solid #bfdbfe; border-radius:8px; font-size:13px; color:#1e3a8a;">
        <strong>Instancias activas en otro rol de ${CATALOGO_PROCESOS[procesoActivo].codigo} (${enOtrosRoles.length}):</strong><br>
        ${enOtrosRoles.map(t => `• <em>${esc(t.name)}</em> (cambia de usuario para atenderla)`).join("<br>")}
      </div>
    `;
  }

  pintar(contenedor, html);
}

async function cargarEstadoServiceTasks() {
  const sesionConsulta = sesion;
  if (!sesionConsulta) return;
  const keysProceso = CATALOGO_PROCESOS[procesoActivo].processKeys;
  const [resExt, resInc] = await Promise.all([
    fetch(`${API}/external-task`),
    fetch(`${API}/incident`)
  ]);

  const externas = await resExt.json();
  const incidentes = await resInc.json();
  if (sesion !== sesionConsulta) return; // la sesión cambió mientras se consultaba
  const externalTasks = externas.filter(et => keysProceso.includes(et.processDefinitionKey));
  const incidents = incidentes.filter(inc => perteneceAProcesoActivo(inc.processDefinitionId));

  const contenedor = document.getElementById("listaServiceTasks");
  if (externalTasks.length === 0 && incidents.length === 0) {
    pintar(contenedor, `<small style="color:var(--muted);">No hay Service Tasks detenidos ni errores técnicos en ${CATALOGO_PROCESOS[procesoActivo].codigo}.</small>`);
    return;
  }

  const htmlExternas = externalTasks.map(et => `
    <div class="task-item">
      <div>
        <span class="badge badge-warn">Service Task Externo en espera</span>
        <strong style="margin-left:8px;">Topic: ${esc(et.topicName)}</strong><br>
        <small style="color:var(--muted);">Actividad: ${esc(et.activityId)} · Esperando worker externo.</small>
      </div>
      <button class="btn-warning" onclick="completarServiceTaskExterno('${et.id}', '${et.topicName}')">Ejecutar y Avanzar</button>
    </div>
  `).join("");

  const htmlIncidentes = incidents.map(inc => `
    <div class="task-item">
      <div>
        <span class="badge badge-err">Error en Service Task</span>
        <strong style="margin-left:8px;">${esc(inc.activityId)}</strong><br>
        <small style="color:var(--danger);">${esc(inc.incidentMessage || "Error interno en JavaDelegate")}</small>
      </div>
    </div>
  `).join("");

  pintar(contenedor, htmlExternas + htmlIncidentes);
}

async function completarServiceTaskExterno(taskId, topicName) {
  await fetch(`${API}/external-task/fetchAndLock`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      workerId: "portal-web-emulador",
      maxTasks: 1,
      topics: [{ topicName, lockDuration: 10000 }]
    })
  });

  const res = await fetch(`${API}/external-task/${taskId}/complete`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ workerId: "portal-web-emulador" })
  });

  if (res.ok) {
    mostrarToast("Service Task completado. Siguiente tarea asignada.");
    await actualizarTodo();
  } else {
    mostrarToast(`No se pudo completar el Service Task: ${await mensajeError(res)}`, 8000);
  }
}

async function seleccionarTarea(id, taskDefKey, nombre, assignee) {
  destruirFormularioCamunda("tarea");
  const tarea = { id, taskDefKey, camundaForm: false };
  tareaActual = tarea;
  // Si el usuario abre otra tarea (o cierra sesión) mientras esta carga, se abandona esta carga
  const vigente = () => tareaActual === tarea;

  if (!assignee) {
    const resClaim = await fetch(`${API}/task/${id}/claim`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId: sesion.username })
    });
    if (!resClaim.ok) {
      const msg = await mensajeError(resClaim);
      if (!vigente()) return;
      mostrarToast(`No se pudo reclamar la tarea: ${msg}`, 8000);
      tareaActual = null;
      await cargarColaTareas();
      return;
    }
  }

  // ¿La tarea tiene un Camunda Form (camunda:formRef)? → se usa el formulario real desplegado.
  const campos = FORMULARIOS_TAREA[taskDefKey] || [];
  let usaCamundaForm = false;
  if (campos.length === 0 && CATALOGO_PROCESOS[procesoActivo].usarFormulariosCamunda) {
    const resForm = await fetch(`${API}/task/${id}/form`);
    usaCamundaForm = resForm.ok && !!(await resForm.json()).camundaFormRef;
  }
  if (!vigente()) return;
  tarea.camundaForm = usaCamundaForm;

  // Sin deserializar: un objeto Java serializado no debe tumbar la consulta (los Object no se muestran)
  const resVars = await fetch(`${API}/task/${id}/variables?deserializeValues=false`);
  const vars = resVars.ok ? await resVars.json() : {};
  if (!vigente()) return;
  const fichasVars = Object.entries(vars)
    .filter(([, v]) => v.type !== "Object") // p. ej. resultadoTipificacion (mapa Java del DMN)
    .map(([k, v]) => `<div class="context-item"><span>${esc(k)}</span><strong>${esc(v.value !== null && typeof v.value === "object" ? JSON.stringify(v.value) : (v.value ?? "-"))}</strong></div>`)
    .join("");
  const gridVars = `<div class="context-grid">${fichasVars || "<small>Sin datos previos registrados</small>"}</div>`;

  const htmlCampos = usaCamundaForm
    ? `<div id="formTareaCamunda" class="form-camunda"><p style="color:var(--muted);">Cargando formulario…</p></div>`
    : campos.length > 0
      ? `<div class="form-grid">${campos.map(c => renderizarCampoHTML("task", c)).join("")}</div>`
      : `<p style="color:var(--muted);">Confirma la ejecución de esta actividad para avanzar el flujo al siguiente interesado.</p>`;

  document.getElementById("panelDetalleTarea").innerHTML = `
    <h4 style="margin-top:0; color:var(--primary);">${esc(nombre)}</h4>
    ${usaCamundaForm ? `<details style="margin-bottom:14px;"><summary style="cursor:pointer; color:var(--muted);">Ver todas las variables del caso</summary>${gridVars}</details>` : gridVars}
    ${htmlCampos}
    <button class="btn-success" onclick="completarTarea()">Finalizar y Enviar al Siguiente Responsable</button>
  `;

  await cargarColaTareas();

  if (usaCamundaForm && vigente()) {
    const contenedorForm = document.getElementById("formTareaCamunda");
    try {
      const datos = await datosParaFormulario(id);
      if (!vigente()) return;
      await montarFormularioCamunda("tarea", contenedorForm, `${API}/task/${id}/deployed-form`, datos);
    } catch (e) {
      if (vigente()) mostrarErrorFormulario(contenedorForm, `No se pudo cargar el formulario: ${e.message}`);
    }
  }
}

async function completarTarea() {
  const tarea = tareaActual;
  if (!tarea || operacionEnCurso) return;
  let variables = {};
  let endpoint = "complete";

  if (tarea.camundaForm) {
    // Igual que Tasklist: valida con las reglas del .form y envía con submit-form
    variables = leerFormularioCamunda("tarea");
    if (!variables) return;
    endpoint = "submit-form";
  } else {
    const campos = FORMULARIOS_TAREA[tarea.taskDefKey] || [];
    campos.forEach(c => {
      let valor = document.getElementById(`task_${c.key}`).value;
      if (c.type === "Boolean") valor = (valor === "true");
      if (c.type === "Long" || c.type === "Double") valor = Number(valor || 0);
      variables[c.key] = { value: valor, type: c.type };
    });
  }

  operacionEnCurso = true;
  try {
    const res = await fetch(`${API}/task/${tarea.id}/${endpoint}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ variables })
    });

    if (res.ok) {
      mostrarToast("Tarea completada y enrutada correctamente.");
      if (tareaActual === tarea) {
        destruirFormularioCamunda("tarea");
        document.getElementById("panelDetalleTarea").innerHTML = `<p style="color:var(--success); font-weight:600; text-align:center; padding:30px 0;">Actividad completada. El flujo continuó al siguiente paso.</p>`;
        tareaActual = null;
      }
      await actualizarTodo();
    } else {
      const msg = await mensajeError(res);
      console.error("Error completando la tarea:", msg);
      mostrarToast(`No se pudo completar la tarea: ${msg}`, 8000);
    }
  } finally {
    operacionEnCurso = false;
  }
}

function renderizarCampoHTML(prefijo, campo) {
  const id = `${prefijo}_${campo.key}`;
  const valorDefecto = campo.value !== undefined ? `value="${campo.value}"` : "";
  if (campo.input === "select") {
    return `
      <div class="field">
        <label for="${id}">${campo.label}</label>
        <select id="${id}">
          ${campo.opciones.map(([val, txt]) => `<option value="${val}">${txt}</option>`).join("")}
        </select>
      </div>`;
  }
  if (campo.input === "textarea") {
    return `
      <div class="field" style="grid-column: 1 / -1;">
        <label for="${id}">${campo.label}</label>
        <textarea id="${id}" placeholder="${campo.placeholder || ""}"></textarea>
      </div>`;
  }
  return `
    <div class="field">
      <label for="${id}">${campo.label}</label>
      <input id="${id}" type="${campo.input || "text"}" ${valorDefecto} placeholder="${campo.placeholder || ""}">
    </div>`;
}

function mostrarToast(mensaje, duracionMs = 3200) {
  const toast = document.getElementById("toast");
  toast.textContent = mensaje;
  toast.classList.remove("hidden");
  // un aviso nuevo no debe desaparecer con el temporizador del anterior
  clearTimeout(temporizadorToast);
  temporizadorToast = setTimeout(() => toast.classList.add("hidden"), duracionMs);
}
