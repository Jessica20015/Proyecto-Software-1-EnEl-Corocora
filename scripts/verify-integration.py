#!/usr/bin/env python3
"""Verify the running ENEL applications through their public HTTP APIs."""

import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


PORTAL = os.environ.get("ENEL_PORTAL_URL", "http://localhost:8082").rstrip("/")
CENTRAL = os.environ.get("ENEL_CENTRAL_URL", "http://localhost:8083").rstrip("/")
CORE = os.environ.get("ENEL_CORE_URL", "http://localhost:8084").rstrip("/")
ENGINE = os.environ.get("ENEL_ENGINE_URL", "http://localhost:8081/engine-rest").rstrip("/")

# CentralSys exige sesión: las cookies se conservan entre peticiones (los usuarios de prueba tienen clave 1234)
OPENER = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def request(method, url, payload=None, expected=(200,)):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with OPENER.open(req, timeout=15) as response:
            status = response.status
            body = response.read()
    except urllib.error.HTTPError as error:
        status = error.code
        body = error.read()
    text = body.decode("utf-8") if body else ""
    if status not in expected:
        raise AssertionError(
            "{} {} returned HTTP {} (expected {}): {}".format(
                method, url, status, expected, text
            )
        )
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return text


def login_central(username, password="1234", expected=(200,)):
    return request(
        "POST",
        CENTRAL + "/api/sesion",
        {"username": username, "password": password},
        expected=expected,
    )


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def wait_for_task(process_instance_id, timeout=30):
    query = urllib.parse.urlencode(
        {"processInstanceId": process_instance_id, "active": "true"}
    )
    deadline = time.time() + timeout
    while time.time() < deadline:
        tasks = request("GET", ENGINE + "/task?" + query)
        if tasks:
            return tasks[0]
        time.sleep(1)
    raise AssertionError(
        "No active Camunda task appeared for process {}".format(process_instance_id)
    )


def wait_for_variable(process_instance_id, variable_name, timeout=30):
    url = ENGINE + "/process-instance/{}/variables?deserializeValues=false".format(
        process_instance_id
    )
    deadline = time.time() + timeout
    while time.time() < deadline:
        variables = request("GET", url)
        if variable_name in variables:
            return variables[variable_name]
        time.sleep(1)
    raise AssertionError(
        "Process {} did not produce variable {}".format(
            process_instance_id, variable_name
        )
    )


def main():
    request("GET", PORTAL + "/api/solicitudes/plantillas/C2")
    request("GET", CENTRAL + "/task?candidateGroup=operador-control", expected=(401,))
    # con otro usuario: Camunda bloquea unos segundos al usuario tras un intento fallido
    login_central("coordinador1", "clave-incorrecta", expected=(401,))
    session = login_central("operador1")
    check("operador-control" in session.get("grupos", []),
          "CentralSys session does not contain the user's lanes")
    request("GET", CENTRAL + "/task?candidateGroup=operador-control")
    request("GET", CENTRAL + "/task?candidateGroup=calidad-facturacion", expected=(403,))
    print("PASS CentralSys login: no session 401, bad password 401, foreign lane 403")

    supply = request("GET", CORE + "/api/core/suministros/SUM-100208/validacion")
    check(supply.get("formatoValido") is True, "Core rejected a valid supply code")
    print("PASS application endpoints and supply validation")

    order_code = "SOL-TEST-" + uuid.uuid4().hex[:12].upper()
    request(
        "POST",
        CORE + "/api/core/ordenes-tecnicas",
        {
            "codigoSolicitud": order_code,
            "codigoSuministro": "SUM-100208",
            "nivelDano": "LEVE",
        },
        expected=(201,),
    )
    orders = request(
        "GET",
        CORE
        + "/api/core/ordenes-tecnicas?"
        + urllib.parse.urlencode({"codigoSolicitud": order_code}),
    )
    check(orders and orders[0]["codigoSolicitud"] == order_code,
          "Core did not persist the technical order")
    print("PASS Core order persistence")

    c2 = request(
        "POST",
        PORTAL + "/api/solicitudes",
        {
            "proceso": "C2",
            "datos": {
                "nombre": "Integration test",
                "domicilio": "Calle de prueba 1",
                "falla": "Interrupcion de servicio para prueba integral",
            },
        },
        expected=(201,),
    )
    bad_draft = request(
        "POST",
        PORTAL + "/api/solicitudes",
        {"proceso": "C2", "datos": {"campoNoValido": "x"}},
        expected=(400,),
    )
    check("message" in bad_draft, "Portal did not describe invalid draft data")
    c2 = request("POST", PORTAL + "/api/solicitudes/" + c2["codigoSolicitud"] + "/radicar")
    check(c2["estado"] == "RADICADA", "C2 request did not reach RADICADA")
    edit_denied = request(
        "PUT",
        PORTAL + "/api/solicitudes/" + c2["codigoSolicitud"],
        {"datos": c2["datos"]},
        expected=(400,),
    )
    check("BORRADOR" in edit_denied.get("message", ""),
          "Portal allowed editing a request after radication")
    instances = request(
        "GET",
        ENGINE
        + "/process-instance?"
        + urllib.parse.urlencode({"businessKey": c2["codigoSolicitud"]}),
    )
    check(instances, "Camunda did not create the C2 process instance")
    process_instance_id = instances[0]["id"]
    check(c2["instanciaProceso"] == process_instance_id,
          "Portal and Camunda process instance IDs differ")

    task = wait_for_task(process_instance_id)
    task_id = task["id"]
    form = request("GET", CENTRAL + "/task/" + task_id + "/deployed-form")
    check(form.get("id"), "CentralSys did not return the deployed Camunda form")
    variables = request("GET", CENTRAL + "/task/" + task_id + "/variables")
    check(variables.get("codigoSolicitud", {}).get("value") == c2["codigoSolicitud"],
          "C2 business variable was not propagated")
    request(
        "POST",
        CENTRAL + "/task/" + task_id + "/claim",
        {"username": "operador1"},
        expected=(204,),
    )
    request(
        "POST",
        CENTRAL + "/task/" + task_id + "/complete",
        {
            "variables": {
                "alarmaValida": {"value": True, "type": "Boolean"},
                "observacionOperador": {
                    "value": "Validacion integral automatizada",
                    "type": "String",
                },
            }
        },
        expected=(204,),
    )
    c2_after = request(
        "GET", PORTAL + "/api/solicitudes/" + c2["codigoSolicitud"]
    )
    check(c2_after["estado"] == "EN_GESTION",
          "CentralSys did not synchronize the C2 request state")
    print("PASS C2 portal -> Camunda -> CentralSys -> shared database")

    c4_supply = "SUM-{:06d}".format(uuid.uuid4().int % 1000000)
    c4_key = c4_supply + "-2026-11"
    c4_result = request(
        "POST",
        ENGINE + "/message",
        {
            "messageName": "MsgConsumoValidado",
            "businessKey": c4_key,
            "resultEnabled": True,
            "processVariables": {
                "codigoSuministro": {"value": c4_supply, "type": "String"},
                "periodo": {"value": "2026-11", "type": "String"},
                "usoServicio": {"value": "RESIDENCIAL", "type": "String"},
                "estrato": {"value": 2, "type": "Double"},
                "canalPreferido": {"value": "DIGITAL", "type": "String"},
                "tipoLectura": {"value": "REAL", "type": "String"},
                "estimacionesConsecutivas": {"value": 0, "type": "Integer"},
                "consumoKwh": {"value": 152, "type": "Double"},
                "consumoPromedioKwh": {"value": 150, "type": "Double"},
            },
        },
    )
    check(isinstance(c4_result, list) and c4_result,
          "C4 message did not return its process instance")
    c4_instance_id = c4_result[0].get("processInstance", {}).get("id")
    check(c4_instance_id, "C4 message response did not contain a process instance")
    energy = wait_for_variable(c4_instance_id, "valorEnergia")
    total = wait_for_variable(c4_instance_id, "valorTotal")
    check(energy.get("value", 0) > 0 and total.get("value", 0) > 0,
          "C4 Core liquidation returned an invalid amount")
    print("PASS C4 message -> JavaDelegate -> Core liquidation -> Camunda variables")

    c7 = request(
        "POST",
        PORTAL + "/api/solicitudes",
        {
            "proceso": "C7",
            "datos": {
                "canalRadicacion": "VIRTUAL",
                "tipoPQR": "RECLAMO",
                "asunto": "FACTURACION",
                "actoRecurrido": "",
                "apelacionSubsidiaria": False,
                "codigoSuministro": "SUM-100208",
                "calidadPeticionario": "SUSCRIPTOR",
                "nombrePeticionario": "Integration test",
                "descripcionPQR": "Solicitud de prueba integral del flujo de radicacion ENEL.",
                "pretension": "Revisar el cobro de la factura",
                "numeroFactura": "FAC-TEST-100208",
                "fechaExpedicionFactura": "2026-11-05",
                "documentosAportados": "",
                "autorizaNotificacionElectronica": False,
                "correoNotificacion": "",
                "direccionNotificacion": "Calle de prueba 1",
                "requiereAccesibilidad": False,
                "ajusteAccesibilidad": "",
                "autorizaTratamientoDatos": True,
            },
        },
        expected=(201,),
    )
    c7 = request("POST", PORTAL + "/api/solicitudes/" + c7["codigoSolicitud"] + "/radicar")
    check(c7["estado"] == "RADICADA", "C7 request did not reach RADICADA")
    c7_instances = request(
        "GET",
        ENGINE
        + "/process-instance?"
        + urllib.parse.urlencode({"businessKey": c7["codigoSolicitud"]}),
    )
    check(c7_instances, "Camunda did not correlate the C7 portal message")
    c7_task = wait_for_task(c7_instances[0]["id"])
    login_central("pqr1")  # coordinación de PQR: pertenece a los tres carriles de C7
    c7_form = request(
        "GET", CENTRAL + "/task/" + c7_task["id"] + "/deployed-form"
    )
    check(c7_form.get("id"), "CentralSys did not return the C7 deployed form")
    print("PASS C7 portal message -> Camunda -> CentralSys task/form")
    print("Integration verification completed successfully.")


if __name__ == "__main__":
    main()
