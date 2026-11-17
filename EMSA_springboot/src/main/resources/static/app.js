const API = "http://localhost:8080/engine-rest";
let procesoActivo = null;
let sesion = null;
let tareaActual = null;
let mapaDefiniciones = {}; // { processDefinitionId: processDefinitionKey }

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
    inicio: {
      grupoPermitido: "analistas-facturacion",
      titulo: "Simular Lectura Validada (Iniciar Proceso C4)",
      tipoInicio: "message",
      messageName: "MsgConsumoValidado",
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
    processKeys: ["C7_AtenderPQR", "C7_NotificarDecision"],
    actores: [
      { user: "pqr1", rol: "Agente de Atención PQR", grupo: "atencion-pqr" }
    ],
    inicio: {
      grupoPermitido: "atencion-pqr",
      titulo: "Radicar Nueva PQR en Ventanilla (Proceso C7)",
      tipoInicio: "start",
      processKey: "C7_AtenderPQR",
      campos: [
        { key: "nombrePeticionario", label: "Nombre del peticionario", placeholder: "Nombre completo", type: "String", input: "text" },
        { key: "tipoSolicitud", label: "Tipo de trámite", type: "String", input: "select", opciones: [["QUEJA", "Queja por facturación"], ["PETICION", "Petición general"], ["RECURSO", "Recurso de reposición"]] },
        { key: "descripcionPqr", label: "Hechos y solicitud", placeholder: "Describa la petición...", type: "String", input: "textarea" }
      ]
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
  ],
  Activity_RevisarExcepcion: [
    { key: "decisionAnalista", label: "Decisión sobre la desviación de consumo", type: "String", input: "select", opciones: [["FACTURAR", "Aprobar consumo y facturar"], ["DEVOLVER_C3", "Devolver a revisión de lectura (C3)"]] },
    { key: "justificacionAnalista", label: "Justificación del análisis", type: "String", input: "textarea" }
  ],
  Activity_VerificarCalidad: [
    { key: "resultadoVerificacion", label: "Dictamen de control de calidad", type: "String", input: "select", opciones: [["APROBADA", "Aprobada (emitir factura)"], ["CORREGIR", "Rechazada (enviar a corrección)"]] },
    { key: "observacionCalidad", label: "Notas de auditoría", type: "String", input: "textarea" }
  ],
  Activity_CorregirLiquidacion: [
    { key: "motivoCorreccion", label: "Detalle de la reliquidación aplicada", type: "String", input: "textarea" }
  ]
};

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

function volverASelector() {
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

  const res = await fetch(`${API}/identity/verify`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  });
  const data = await res.json();

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
  await cargarMapaDefiniciones();

  document.getElementById("loginView").classList.add("hidden");
  document.getElementById("appView").classList.remove("hidden");
  document.getElementById("userBar").classList.remove("hidden");
  document.getElementById("userInfo").innerHTML = `
    <span class="badge" style="background:#38bdf8; color:#0f172a; margin-right:8px;">${infoProceso.codigo}</span>
    <strong>${username}</strong> <span class="badge badge-role">${gruposUsuario.join(", ")}</span>
  `;

  renderizarPanelInicio();
  await actualizarTodo();
}

function cerrarSesion() {
  sesion = null;
  tareaActual = null;
  document.getElementById("appView").classList.add("hidden");
  document.getElementById("userBar").classList.add("hidden");
  seleccionarProceso(procesoActivo);
}

function renderizarPanelInicio() {
  const contenedor = document.getElementById("panelInicioProcesos");
  const cfgInicio = CATALOGO_PROCESOS[procesoActivo].inicio;

  if (!cfgInicio || !sesion.grupos.includes(cfgInicio.grupoPermitido)) {
    contenedor.classList.add("hidden");
    return;
  }

  contenedor.classList.remove("hidden");
  contenedor.innerHTML = `
    <div class="card-header">
      <h3>${cfgInicio.titulo}</h3>
      <span class="badge">Rol iniciador: ${cfgInicio.grupoPermitido}</span>
    </div>
    <div class="form-grid">
      ${cfgInicio.campos.map(c => renderizarCampoHTML("init", c)).join("")}
    </div>
    <button onclick="iniciarProcesoActivo()">Radicar e Iniciar Flujo</button>
  `;
}

async function iniciarProcesoActivo() {
  const cfg = CATALOGO_PROCESOS[procesoActivo].inicio;
  const variables = {
    estimacionesConsecutivas: { value: 0, type: "Long" },
    devolucionesC3: { value: 0, type: "Long" },
    canalPreferido: { value: "DIGITAL", type: "String" }
  };

  cfg.campos.forEach(c => {
    let val = document.getElementById(`init_${c.key}`).value;
    if (c.type === "Long") val = Number(val || 0);
    if (c.type === "Double") val = Number(val || 0);
    variables[c.key] = { value: val, type: c.type };
  });

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
    const err = await res.text();
    console.error("Error iniciando proceso:", err);
    mostrarToast("No se pudo iniciar el proceso. Revisa la consola F12.");
  }
}

async function actualizarTodo() {
  if (Object.keys(mapaDefiniciones).length === 0) {
    await cargarMapaDefiniciones();
  }
  await Promise.all([cargarColaTareas(), cargarEstadoServiceTasks()]);
}

async function cargarColaTareas() {
  const mapaTareas = new Map();

  // 1. Tareas asignadas directamente al usuario
  const resAsignadas = await fetch(`${API}/task?assignee=${encodeURIComponent(sesion.username)}`);
  (await resAsignadas.json()).forEach(t => mapaTareas.set(t.id, t));

  // 2. Tareas en cola de los grupos del usuario
  for (const grupo of sesion.grupos) {
    const resGrupo = await fetch(`${API}/task?candidateGroup=${encodeURIComponent(grupo)}`);
    (await resGrupo.json()).forEach(t => mapaTareas.set(t.id, t));
  }

  // Filtrar por el proceso activo usando el mapa real de IDs -> Keys
  const lista = Array.from(mapaTareas.values()).filter(t => perteneceAProcesoActivo(t.processDefinitionId));

  // Consultar también todas las tareas activas de este proceso para informar si pasaron a otro rol
  const resTodas = await fetch(`${API}/task`);
  const todasProceso = (await resTodas.json()).filter(t => perteneceAProcesoActivo(t.processDefinitionId));
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
          <strong>${t.name}</strong><br>
          <small style="color:var(--muted);">ID tarea: ${t.taskDefinitionKey} · ${t.assignee ? "Asignada a ti" : "En cola de grupo"}</small>
        </div>
        <button class="btn-outline" onclick="seleccionarTarea('${t.id}', '${t.taskDefinitionKey}', '${t.name}', '${t.assignee || ""}')">Gestionar</button>
      </div>
    `).join("");
  }

  if (enOtrosRoles.length > 0) {
    html += `
      <div style="margin-top:14px; padding:12px; background:#eff6ff; border:1px solid #bfdbfe; border-radius:8px; font-size:13px; color:#1e3a8a;">
        <strong>Instancias activas en otro rol de ${CATALOGO_PROCESOS[procesoActivo].codigo} (${enOtrosRoles.length}):</strong><br>
        ${enOtrosRoles.map(t => `• <em>${t.name}</em> (cambia de usuario para atenderla)`).join("<br>")}
      </div>
    `;
  }

  contenedor.innerHTML = html;
}

async function cargarEstadoServiceTasks() {
  const keysProceso = CATALOGO_PROCESOS[procesoActivo].processKeys;
  const [resExt, resInc] = await Promise.all([
    fetch(`${API}/external-task`),
    fetch(`${API}/incident`)
  ]);

  const externalTasks = (await resExt.json()).filter(et => keysProceso.includes(et.processDefinitionKey));
  const incidents = (await resInc.json()).filter(inc => perteneceAProcesoActivo(inc.processDefinitionId));

  const contenedor = document.getElementById("listaServiceTasks");
  if (externalTasks.length === 0 && incidents.length === 0) {
    contenedor.innerHTML = `<small style="color:var(--muted);">No hay Service Tasks detenidos ni errores técnicos en ${CATALOGO_PROCESOS[procesoActivo].codigo}.</small>`;
    return;
  }

  const htmlExternas = externalTasks.map(et => `
    <div class="task-item">
      <div>
        <span class="badge badge-warn">Service Task Externo en espera</span>
        <strong style="margin-left:8px;">Topic: ${et.topicName}</strong><br>
        <small style="color:var(--muted);">Actividad: ${et.activityId} · Esperando worker externo.</small>
      </div>
      <button class="btn-warning" onclick="completarServiceTaskExterno('${et.id}', '${et.topicName}')">Ejecutar y Avanzar</button>
    </div>
  `).join("");

  const htmlIncidentes = incidents.map(inc => `
    <div class="task-item">
      <div>
        <span class="badge badge-err">Error en Service Task</span>
        <strong style="margin-left:8px;">${inc.activityId}</strong><br>
        <small style="color:var(--danger);">${inc.incidentMessage || "Error interno en JavaDelegate"}</small>
      </div>
    </div>
  `).join("");

  contenedor.innerHTML = htmlExternas + htmlIncidentes;
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
  }
}

async function seleccionarTarea(id, taskDefKey, nombre, assignee) {
  tareaActual = { id, taskDefKey };

  if (!assignee) {
    await fetch(`${API}/task/${id}/claim`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId: sesion.username })
    });
  }

  const resVars = await fetch(`${API}/task/${id}/variables`);
  const vars = await resVars.json();
  const fichasVars = Object.entries(vars)
    .map(([k, v]) => `<div class="context-item"><span>${k}</span><strong>${v.value ?? "-"}</strong></div>`)
    .join("");

  const campos = FORMULARIOS_TAREA[taskDefKey] || [];
  const htmlCampos = campos.length > 0
    ? `<div class="form-grid">${campos.map(c => renderizarCampoHTML("task", c)).join("")}</div>`
    : `<p style="color:var(--muted);">Confirma la ejecución de esta actividad para avanzar el flujo al siguiente interesado.</p>`;

  document.getElementById("panelDetalleTarea").innerHTML = `
    <h4 style="margin-top:0; color:var(--primary);">${nombre}</h4>
    <div class="context-grid">${fichasVars || "<small>Sin datos previos registrados</small>"}</div>
    ${htmlCampos}
    <button class="btn-success" onclick="completarTarea()">Finalizar y Enviar al Siguiente Responsable</button>
  `;

  await cargarColaTareas();
}

async function completarTarea() {
  const campos = FORMULARIOS_TAREA[tareaActual.taskDefKey] || [];
  const variables = {};

  campos.forEach(c => {
    let valor = document.getElementById(`task_${c.key}`).value;
    if (c.type === "Boolean") valor = (valor === "true");
    if (c.type === "Long" || c.type === "Double") valor = Number(valor || 0);
    variables[c.key] = { value: valor, type: c.type };
  });

  const res = await fetch(`${API}/task/${tareaActual.id}/complete`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ variables })
  });

  if (res.ok) {
    mostrarToast("Tarea completada y enrutada correctamente.");
    document.getElementById("panelDetalleTarea").innerHTML = `<p style="color:var(--success); font-weight:600; text-align:center; padding:30px 0;">Actividad completada. El flujo continuó al siguiente paso.</p>`;
    tareaActual = null;
    await actualizarTodo();
  } else {
    mostrarToast("Error al completar la tarea. Revisa la consola del servidor.");
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

function mostrarToast(mensaje) {
  const toast = document.getElementById("toast");
  toast.textContent = mensaje;
  toast.classList.remove("hidden");
  setTimeout(() => toast.classList.add("hidden"), 3200);
}