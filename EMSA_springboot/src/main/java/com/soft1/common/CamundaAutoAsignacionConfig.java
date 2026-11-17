package com.soft1.common;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.task.TaskDefinition;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.camunda.bpm.model.bpmn.instance.Lane;
import org.camunda.bpm.model.bpmn.instance.LaneSet;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Locale;

@Configuration
public class CamundaAutoAsignacionConfig {

    @Bean
    public static ProcessEnginePlugin autoAsignacionTareasPlugin() {
        return new AbstractProcessEnginePlugin() {
            @Override
            public void preInit(ProcessEngineConfigurationImpl config) {
                if (config.getCustomPostBPMNParseListeners() == null) {
                    config.setCustomPostBPMNParseListeners(new ArrayList<>());
                }
                config.getCustomPostBPMNParseListeners().add(new AbstractBpmnParseListener() {
                    @Override
                    public void parseUserTask(Element userTaskElement, ScopeImpl scope, ActivityImpl activity) {
                        if (activity.getActivityBehavior() instanceof UserTaskActivityBehavior behavior) {
                            TaskDefinition taskDefinition = behavior.getTaskDefinition();
                            taskDefinition.addTaskListener(TaskListener.EVENTNAME_CREATE, crearListenerAsignacion());
                        }
                    }
                });
            }
        };
    }

    private static TaskListener crearListenerAsignacion() {
        return delegateTask -> {
            if (!delegateTask.getCandidates().isEmpty() || delegateTask.getAssignee() != null) {
                return;
            }
            String grupoDestino = resolverGrupoPorCarrilOTarea(delegateTask);
            if (grupoDestino != null) {
                delegateTask.addCandidateGroup(grupoDestino);
            }
        };
    }

    private static String resolverGrupoPorCarrilOTarea(DelegateTask task) {
        String nombreCarril = obtenerNombreCarril(task).toLowerCase(Locale.ROOT);
        String key = task.getTaskDefinitionKey().toLowerCase(Locale.ROOT);

        if (nombreCarril.contains("cuadrilla") || key.contains("visita") || key.contains("reparacion") || key.contains("inspeccion")) {
            return "cuadrilla";
        }
        if (nombreCarril.contains("operador") || key.contains("alarma") || key.contains("agendar")) {
            return "operador-control";
        }
        if (nombreCarril.contains("coordinador") || key.contains("escalar") || key.contains("demora")) {
            return "coordinador-zona";
        }
        if (nombreCarril.contains("calidad") || key.contains("calidad")) {
            return "calidad-facturacion";
        }
        if (nombreCarril.contains("liquidador") || key.contains("liquidacion")) {
            return "liquidadores-tarifarios";
        }
        if (nombreCarril.contains("analista") || key.contains("excepcion") || key.contains("entrega")) {
            return "analistas-facturacion";
        }
        if (nombreCarril.contains("pqr") || key.contains("pqr") || key.contains("respuesta") || key.contains("notificar")) {
            return "atencion-pqr";
        }
        return null;
    }

    private static String obtenerNombreCarril(DelegateTask task) {
        UserTask userTask = task.getBpmnModelElementInstance();
        if (userTask != null && userTask.getParentElement() instanceof Process process) {
            for (LaneSet laneSet : process.getLaneSets()) {
                for (Lane lane : laneSet.getLanes()) {
                    if (lane.getFlowNodeRefs().contains(userTask) && lane.getName() != null) {
                        return lane.getName();
                    }
                }
            }
        }
        return "";
    }
}