const mensaje = document.querySelector("#mensaje");
const codigo = document.querySelector("#codigo");
const estado = document.querySelector("#estado");
const campos = document.querySelector("#campos");

const etiquetas = {
  nombre: "Nombre del ciudadano",
  domicilio: "Dirección del suministro",
  falla: "Descripción de la falla",
  canalRadicacion: "Canal de radicación",
  tipoPQR: "Tipo de PQR",
  asunto: "Asunto",
  actoRecurrido: "Acto recurrido",
  apelacionSubsidiaria: "Apelación subsidiaria",
  codigoSuministro: "Código de suministro",
  calidadPeticionario: "Calidad del peticionario",
  nombrePeticionario: "Nombre del peticionario",
  descripcionPQR: "Hechos y descripción de la PQR",
  pretension: "Pretensión del peticionario",
  numeroFactura: "Número de factura",
  fechaExpedicionFactura: "Fecha de expedición de la factura",
  documentosAportados: "Documentos aportados",
  autorizaNotificacionElectronica: "Autoriza notificación electrónica",
  correoNotificacion: "Correo para notificaciones",
  direccionNotificacion: "Dirección para notificaciones",
  requiereAccesibilidad: "Requiere ajuste de accesibilidad",
  ajusteAccesibilidad: "Ajuste de accesibilidad solicitado",
  autorizaTratamientoDatos: "Autoriza tratamiento de datos"
};
const textareas = new Set([
  "falla", "descripcionPQR", "pretension", "documentosAportados",
  "actoRecurrido", "ajusteAccesibilidad"
]);

async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) }
  });
  const body = response.status === 204 ? null : await response.json();
  if (!response.ok) throw new Error(body?.message || `HTTP ${response.status}`);
  return body;
}

function renderFields(values, editable) {
  campos.replaceChildren();
  Object.entries(values).forEach(([key, value], index) => {
    const wrapper = document.createElement("div");
    wrapper.className = "field";
    const label = document.createElement("label");
    const id = `campo-${index}`;
    label.htmlFor = id;
    label.textContent = etiquetas[key] || key;

    let input;
    if (typeof value === "boolean") {
      input = document.createElement("input");
      input.type = "checkbox";
      input.checked = value;
      label.prepend(input);
      input.id = id;
      input.name = key;
      input.disabled = !editable;
      wrapper.append(label);
      campos.append(wrapper);
      return;
    }

    input = document.createElement(textareas.has(key) ? "textarea" : "input");
    if (input.tagName === "INPUT") {
      input.type = key.startsWith("fecha") ? "date" : "text";
    }
    input.id = id;
    input.name = key;
    input.value = value == null ? "" : String(value);
    input.disabled = !editable;
    wrapper.append(label, input);
    campos.append(wrapper);
  });
}

function mostrarSolicitud(solicitud) {
  codigo.value = solicitud.codigoSolicitud;
  estado.value = solicitud.estado;
  document.querySelector("#proceso").value = solicitud.proceso;
  const editable = solicitud.estado === "BORRADOR";
  document.querySelector("#proceso").disabled = true;
  document.querySelector("#guardar").disabled = !editable;
  document.querySelector("#radicar").disabled = !editable;
  renderFields(solicitud.datos, editable);
}

function leerDatos() {
  return Object.fromEntries(
    [...campos.querySelectorAll("[name]")].map(input => [
      input.name,
      input.type === "checkbox" ? input.checked : input.value
    ])
  );
}

async function historial() {
  const solicitudes = await request("/api/solicitudes");
  const lista = document.querySelector("#historial");
  lista.replaceChildren();
  for (const solicitud of solicitudes) {
    const item = document.createElement("li");
    const boton = document.createElement("button");
    boton.textContent = `${solicitud.codigoSolicitud} · ${solicitud.proceso} · ${solicitud.estado}`;
    boton.onclick = () => {
      mostrarSolicitud(solicitud);
      mensaje.textContent = "";
    };
    item.append(boton);
    lista.append(item);
  }
}

document.querySelector("#nuevo").onclick = async () => {
  try {
    document.querySelector("#proceso").disabled = false;
    const proceso = document.querySelector("#proceso").value;
    const plantilla = await request(`/api/solicitudes/plantillas/${proceso}`);
    const solicitud = await request("/api/solicitudes", {
      method: "POST",
      body: JSON.stringify({ proceso, datos: plantilla })
    });
    mostrarSolicitud(solicitud);
    mensaje.textContent = "Borrador creado. Complete los campos y guarde antes de radicar.";
    await historial();
  } catch (error) { mensaje.textContent = error.message; }
};

document.querySelector("#guardar").onclick = async () => {
  try {
    if (!codigo.value) throw new Error("Cree primero un borrador.");
    const solicitud = await request(`/api/solicitudes/${encodeURIComponent(codigo.value)}`, {
      method: "PUT",
      body: JSON.stringify({ datos: leerDatos() })
    });
    mostrarSolicitud(solicitud);
    mensaje.textContent = "Borrador actualizado.";
    await historial();
  } catch (error) { mensaje.textContent = error.message; }
};

document.querySelector("#radicar").onclick = async () => {
  try {
    if (!codigo.value) throw new Error("Cree primero un borrador.");
    await request(`/api/solicitudes/${encodeURIComponent(codigo.value)}`, {
      method: "PUT",
      body: JSON.stringify({ datos: leerDatos() })
    });
    const solicitud = await request(
      `/api/solicitudes/${encodeURIComponent(codigo.value)}/radicar`,
      { method: "POST" }
    );
    mostrarSolicitud(solicitud);
    mensaje.textContent = `Solicitud ${solicitud.codigoSolicitud} radicada.`;
    await historial();
  } catch (error) { mensaje.textContent = error.message; }
};

historial().catch(error => { mensaje.textContent = error.message; });
