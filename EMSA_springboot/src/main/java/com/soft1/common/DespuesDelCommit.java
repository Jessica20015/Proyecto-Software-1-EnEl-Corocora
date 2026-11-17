package com.soft1.common;

import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ejecuta una acción con efectos fuera del engine (escribir un archivo, programar un mensaje simulado)
 * solo cuando la transacción del engine en curso se confirma.
 * <p>
 * Los delegates corren dentro de la transacción del engine: si esta se revierte (error en una DMN,
 * OptimisticLockingException en un job, reintento...), lo escrito en disco o lo programado no se deshace.
 * Al aplazar la acción hasta el COMMIT, una transacción revertida no deja registros fantasma ni mensajes huérfanos.
 * Fuera de un comando del engine (sin transacción) la acción se ejecuta de inmediato.
 */
public final class DespuesDelCommit {

    private static final Logger LOG = LoggerFactory.getLogger("emsa");

    private DespuesDelCommit() {
    }

    public static void ejecutar(Runnable accion) {
        CommandContext commandContext = Context.getCommandContext();
        if (commandContext == null) {
            accion.run();   // fuera de un comando del engine: no hay transacción que esperar
            return;
        }
        commandContext.getTransactionContext().addTransactionListener(TransactionState.COMMITTED, cc -> {
            try {
                accion.run();
            } catch (RuntimeException e) {
                // la transacción ya se confirmó: no se puede revertir; se informa sin afectar a las demás acciones
                LOG.error("Falló una acción posterior al commit de la transacción del engine", e);
            }
        });
    }
}
