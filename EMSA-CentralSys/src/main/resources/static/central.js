let tareaActual = null;
let variablesActuales = {};
const mensaje = document.querySelector("#mensaje");
const formulario = document.querySelector("#formulario");

async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) }
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(body?.message || `HTTP ${response.status}`);
  return body;
}

async function cargarTareas() {
  try {
    const grupo = document.querySelector("#grupo").value;
    const tareas = await request(`/task?candidateGroup=${encodeURIComponent(grupo)}`);
    const lista = document.querySelector("#tareas");
    lista.replaceChildren();
    for (const tarea of tareas) {
      const item = document.createElement("li");
      const boton = document.createElement("button");
      boton.textContent = `${tarea.name} · ${tarea.id}`;
      boton.onclick = () => seleccionarTarea(tarea);
      item.append(boton);
      lista.append(item);
    }
    mensaje.textContent = `${tareas.length} tarea(s) disponibles.`;
  } catch (error) { mensaje.textContent = error.message; }
}

function valorVariable(key, component) {
  const variable = variablesActuales[key];
  if (variable && variable.value !== null && variable.value !== undefined) {
    return variable.value;
  }
  return component.defaultValue ?? "";
}

function opcionesDe(component) {
  if (!Array.isArray(component.values)) return [];
  return component.values.map(option => {
    if (option && typeof option === "object" && "value" in option) {
      return { label: option.label ?? String(option.value), value: option.value };
    }
    return { label: String(option), value: option };
  });
}

function renderComponent(component, container) {
  if (!component || typeof component !== "object") return;
  if (component.type === "group" || Array.isArray(component.components)) {
    const group = document.createElement("fieldset");
    const legend = document.createElement("legend");
    legend.textContent = component.label || "Sección";
    group.append(legend);
    for (const child of component.components || []) renderComponent(child, group);
    container.append(group);
    return;
  }
  if (component.type === "text" || component.type === "html") {
    const text = document.createElement("p");
    text.textContent = component.text || component.content || component.label || "";
    container.append(text);
    return;
  }
  if (!component.key) return;

  const wrapper = document.createElement("div");
  wrapper.className = "form-field";
  const label = document.createElement("label");
  label.textContent = component.label || component.key;
  const value = valorVariable(component.key, component);
  const type = component.type || "textfield";
  let input;

  if (type === "checkbox") {
    input = document.createElement("input");
    input.type = "checkbox";
    input.checked = value === true;
    label.prepend(input);
  } else if (type === "select" || type === "radio") {
    input = document.createElement("select");
    for (const option of opcionesDe(component)) {
      const element = document.createElement("option");
      element.textContent = option.label;
      element.value = JSON.stringify(option.value);
      element.selected = JSON.stringify(option.value) === JSON.stringify(value);
      input.append(element);
    }
    if (value !== "" && !opcionesDe(component).some(option =>
      JSON.stringify(option.value) === JSON.stringify(value))) {
      const element = document.createElement("option");
      element.textContent = String(value);
      element.value = JSON.stringify(value);
      element.selected = true;
      input.append(element);
    }
  } else {
    input = document.createElement(type === "textarea" ? "textarea" : "input");
    if (input.tagName === "INPUT") {
      input.type = type === "number" ? "number"
        : type === "date" ? "date"
          : type === "datetime" || type === "datetime-local" ? "datetime-local" : "text";
    }
    input.value = value == null ? "" : String(value);
  }

  input.dataset.variableKey = component.key;
  input.dataset.camundaType = type;
  input.disabled = component.disabled === true;
  const validate = component.validate || {};
  input.required = validate.required === true;
  if (validate.minLength != null) input.minLength = validate.minLength;
  if (validate.maxLength != null) input.maxLength = validate.maxLength;
  if (validate.min != null) input.min = validate.min;
  if (validate.max != null) input.max = validate.max;
  if (input.tagName === "INPUT" && input.type === "checkbox") {
    wrapper.append(label);
  } else {
    wrapper.append(label, input);
  }
  container.append(wrapper);
}

function renderFormulario(form, variables) {
  variablesActuales = variables || {};
  formulario.replaceChildren();
  const components = Array.isArray(form?.components) ? form.components : [];
  if (components.length === 0) {
    const empty = document.createElement("p");
    empty.textContent = "Este formulario desplegado no contiene campos interactivos.";
    formulario.append(empty);
    return;
  }
  for (const component of components) renderComponent(component, formulario);
}

function tipoVariable(input, value) {
  const existingType = variablesActuales[input.dataset.variableKey]?.type;
  if (existingType && existingType !== "Null") return existingType;
  if (input.type === "checkbox") return "Boolean";
  if (input.type === "number") return Number.isInteger(Number(value)) ? "Long" : "Double";
  if (typeof value === "boolean") return "Boolean";
  if (typeof value === "number") return Number.isInteger(value) ? "Long" : "Double";
  return "String";
}

function leerVariablesFormulario() {
  if (!formulario.reportValidity()) {
    throw new Error("Complete los campos obligatorios del formulario.");
  }
  const variables = {};
  for (const input of formulario.querySelectorAll("[data-variable-key]")) {
    if (input.disabled) continue;
    const key = input.dataset.variableKey;
    let value;
    if (input.type === "checkbox") value = input.checked;
    else if (input.tagName === "SELECT") value = JSON.parse(input.value);
    else if (input.type === "number") value = Number(input.value);
    else value = input.value;
    variables[key] = { value, type: tipoVariable(input, value) };
  }
  return variables;
}

async function seleccionarTarea(tarea) {
  try {
    tareaActual = tarea;
    document.querySelector("#titulo").textContent = tarea.name;
    const [form, variables] = await Promise.all([
      request(`/task/${encodeURIComponent(tarea.id)}/deployed-form`),
      request(`/task/${encodeURIComponent(tarea.id)}/variables`)
    ]);
    renderFormulario(form, variables);
    mensaje.textContent = "";
  } catch (error) { mensaje.textContent = error.message; }
}

document.querySelector("#cargar").onclick = cargarTareas;
document.querySelector("#reclamar").onclick = async () => {
  try {
    if (!tareaActual) throw new Error("Seleccione una tarea.");
    const username = prompt("Usuario Camunda autorizado para este carril:");
    if (!username) return;
    await request(`/task/${encodeURIComponent(tareaActual.id)}/claim`, {
      method: "POST", body: JSON.stringify({ username })
    });
    mensaje.textContent = "Tarea reclamada.";
    await cargarTareas();
  } catch (error) { mensaje.textContent = error.message; }
};
document.querySelector("#completar").onclick = async () => {
  try {
    if (!tareaActual) throw new Error("Seleccione una tarea.");
    await request(`/task/${encodeURIComponent(tareaActual.id)}/complete`, {
      method: "POST", body: JSON.stringify({ variables: leerVariablesFormulario() })
    });
    mensaje.textContent = "Tarea completada y estado de la solicitud sincronizado.";
    tareaActual = null;
    formulario.replaceChildren();
    document.querySelector("#titulo").textContent = "Seleccione una tarea";
    await cargarTareas();
  } catch (error) { mensaje.textContent = error.message; }
};
cargarTareas();
