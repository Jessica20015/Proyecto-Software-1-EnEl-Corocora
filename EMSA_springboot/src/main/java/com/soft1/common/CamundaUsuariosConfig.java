package com.soft1.common;

import org.camunda.bpm.engine.FilterService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.filter.Filter;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.task.TaskQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CamundaUsuariosConfig implements ApplicationRunner {

    private final IdentityService identityService;
    private final FilterService filterService;
    private final TaskService taskService;
    /** Filtro con todas las tareas que crea el starter (camunda.bpm.filter.create en application.yaml). */
    private final String filtroTodas;

    public CamundaUsuariosConfig(IdentityService identityService, FilterService filterService, TaskService taskService,
                                 @Value("${camunda.bpm.filter.create:}") String filtroTodas) {
        this.identityService = identityService;
        this.filterService = filterService;
        this.taskService = taskService;
        this.filtroTodas = filtroTodas;
    }

    @Bean
    public static ProcessEnginePlugin permitirGuionesEnGruposPlugin() {
        return new AbstractProcessEnginePlugin() {
            @Override
            public void preInit(ProcessEngineConfigurationImpl config) {
                config.setGeneralResourceWhitelistPattern("[a-zA-Z0-9-]+");
            }
        };
    }

    @Override
    public void run(ApplicationArguments args) {
        // Grupos de C2 (Restablecer servicio)
        crearGrupo("operador-control", "Operador del Centro de Control");
        crearGrupo("cuadrilla", "Cuadrilla Técnica");
        crearGrupo("coordinador-zona", "Coordinador de Zona");

        // Grupos de C4 (Facturación - coinciden con tus BPMN)
        crearGrupo("analistas-facturacion", "Analista de Facturación");
        crearGrupo("calidad-facturacion", "Control de Calidad de Facturación");
        crearGrupo("liquidadores-tarifarios", "Liquidador Tarifario");

        // Grupos de C7 (Atender una PQR: iguales a los camunda:candidateGroups de C7_AtenderPQR y C7_NotificarDecision)
        crearGrupo("orientadores-pqr", "Orientador y enrutador de usuarios (C7)");
        crearGrupo("analistas-pqr", "Analista de PQR (C7)");
        crearGrupo("juridica-pqr", "Apoyo jurídico de PQR (C7)");

        // Usuarios de prueba (usuario, contraseña, nombre, apellido, grupoId)
        crearUsuario("operador1", "1234", "Carlos", "Operador", "operador-control");
        crearUsuario("cuadrilla1", "1234", "Luis", "Técnico", "cuadrilla");
        crearUsuario("coordinador1", "1234", "Ana", "Coordinadora", "coordinador-zona");
        crearUsuario("analista1", "1234", "Laura", "Analista", "analistas-facturacion");
        crearUsuario("calidad1", "1234", "Marta", "Calidad", "calidad-facturacion");
        crearUsuario("liquidador1", "1234", "Pedro", "Tarifas", "liquidadores-tarifarios");
        // C7: un usuario por carril (analista1 ya es de C4, por eso el analista de PQR es analistapqr1)
        crearUsuario("orientador1", "1234", "Olga", "Orientadora", "orientadores-pqr");
        crearUsuario("analistapqr1", "1234", "Andrés", "Analista", "analistas-pqr");
        crearUsuario("juridica1", "1234", "Julia", "Jurídica", "juridica-pqr");
        // pqr1 = coordinación de la Oficina de PQR: trabaja en los tres carriles de C7
        crearUsuario("pqr1", "1234", "Sofia", "PQR", "orientadores-pqr");
        crearUsuario("pqr1", "1234", "Sofia", "PQR", "analistas-pqr");
        crearUsuario("pqr1", "1234", "Sofia", "PQR", "juridica-pqr");

        // Filtros de Tasklist para cualquier usuario (genéricos: sirven para C2, C4 y C7)
        crearFiltro("Tareas de mis grupos", taskService.createTaskQuery().taskCandidateGroupInExpression("${currentUserGroups()}"),
                -10, "Tareas sin reclamar de los grupos (carriles) del usuario");
        crearFiltro("Mis tareas", taskService.createTaskQuery().taskAssigneeExpression("${currentUser()}"),
                -5, "Tareas reclamadas por el usuario o asignadas a él");
        completarFiltroTodas();
    }

    private void crearFiltro(String nombre, TaskQuery consulta, int prioridad, String descripcion) {
        if (filterService.createFilterQuery().filterName(nombre).count() == 0) {
            Filter filtro = filterService.newTaskFilter(nombre)
                    .setQuery(consulta)
                    .setProperties(propiedadesFiltro(prioridad, descripcion));
            filterService.saveFilter(filtro);
        }
    }

    /**
     * El starter crea el filtro de todas las tareas sin propiedades. Se le da prioridad y descripción para que
     * el Tasklist lo ordene siempre después de los dos anteriores y abra por defecto "Tareas de mis grupos".
     */
    private void completarFiltroTodas() {
        if (filtroTodas == null || filtroTodas.isBlank()) {
            return;
        }
        for (Filter filtro : filterService.createFilterQuery().filterName(filtroTodas).list()) {
            if (filtro.getProperties() == null || !filtro.getProperties().containsKey("priority")) {
                filtro.setProperties(propiedadesFiltro(0, "Todas las tareas abiertas de todos los procesos"));
                filterService.saveFilter(filtro);
            }
        }
    }

    private static Map<String, Object> propiedadesFiltro(int prioridad, String descripcion) {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("description", descripcion);
        propiedades.put("priority", prioridad);
        propiedades.put("refresh", true);
        return propiedades;
    }

    private void crearGrupo(String id, String nombre) {
        if (identityService.createGroupQuery().groupId(id).singleResult() == null) {
            Group grupo = identityService.newGroup(id);
            grupo.setName(nombre);
            grupo.setType("WORKFLOW");
            identityService.saveGroup(grupo);
        }
    }

    private void crearUsuario(String id, String password, String nombre, String apellido, String grupoId) {
        if (identityService.createUserQuery().userId(id).singleResult() == null) {
            User usuario = identityService.newUser(id);
            usuario.setFirstName(nombre);
            usuario.setLastName(apellido);
            usuario.setPassword(password);
            identityService.saveUser(usuario);
        }
        if (identityService.createUserQuery().userId(id).memberOfGroup(grupoId).count() == 0) {
            identityService.createMembership(id, grupoId);
        }
    }
}