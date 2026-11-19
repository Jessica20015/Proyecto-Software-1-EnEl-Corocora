package com.enel.central.seguridad;

import com.enel.central.api.TaskController;
import com.enel.central.integration.CamundaTaskClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Inicio y cierre de sesión de los funcionarios. Las credenciales son las de los usuarios de Camunda
 * (Admin / CamundaUsuariosConfig) y se verifican contra el motor; los grupos de la sesión son los carriles
 * de negocio a los que pertenece el usuario.
 */
@RestController
@RequestMapping("/api/sesion")
public class SesionController {

    private final CamundaTaskClient camunda;

    public SesionController(CamundaTaskClient camunda) {
        this.camunda = camunda;
    }

    @PostMapping
    public Funcionario iniciar(@RequestBody LoginRequest login, HttpServletRequest request) {
        String usuario = login.username() == null ? "" : login.username().trim();
        String clave = login.password() == null ? "" : login.password();
        if (usuario.isEmpty() || clave.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario y clave son obligatorios.");
        }
        if (!camunda.verificar(usuario, clave)) {
            // Camunda también rechaza durante unos segundos tras un intento fallido (protección contra fuerza bruta)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Usuario o clave incorrectos. Tras un intento fallido espere unos segundos antes de reintentar.");
        }
        List<String> grupos = camunda.grupos(usuario).stream().filter(TaskController.GROUPS::contains).sorted().toList();
        if (grupos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El usuario «" + usuario + "» no pertenece a ningún carril de CentralSys.");
        }
        HttpSession sesion = request.getSession(true);
        request.changeSessionId();   // evita la fijación de sesión
        Funcionario funcionario = new Funcionario(usuario, grupos);
        sesion.setAttribute(Funcionario.ATRIBUTO_SESION, funcionario);
        return funcionario;
    }

    @GetMapping
    public Funcionario actual(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);   // consultar no crea una sesión vacía
        if (sesion != null && sesion.getAttribute(Funcionario.ATRIBUTO_SESION) instanceof Funcionario f) {
            return f;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No hay sesión iniciada.");
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrar(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Error" : e.getReason()));
    }

    public record LoginRequest(String username, String password) {}
}
