package com.soft1.common;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class CamundaUsuariosConfig implements ApplicationRunner {

    private final IdentityService identityService;

    public CamundaUsuariosConfig(IdentityService identityService) {
        this.identityService = identityService;
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

        // Grupo de C7 (Atención PQR)
        crearGrupo("atencion-pqr", "Agente de Atención PQR");

        // Usuarios de prueba (usuario, contraseña, nombre, apellido, grupoId)
        crearUsuario("operador1", "1234", "Carlos", "Operador", "operador-control");
        crearUsuario("cuadrilla1", "1234", "Luis", "Técnico", "cuadrilla");
        crearUsuario("coordinador1", "1234", "Ana", "Coordinadora", "coordinador-zona");
        crearUsuario("analista1", "1234", "Laura", "Analista", "analistas-facturacion");
        crearUsuario("calidad1", "1234", "Marta", "Calidad", "calidad-facturacion");
        crearUsuario("liquidador1", "1234", "Pedro", "Tarifas", "liquidadores-tarifarios");
        crearUsuario("pqr1", "1234", "Sofia", "PQR", "atencion-pqr");
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