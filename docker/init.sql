CREATE DATABASE enel_negocio;
\connect enel_negocio

CREATE TABLE IF NOT EXISTS enel_solicitudes (
    codigo_solicitud varchar(80) PRIMARY KEY,
    proceso varchar(40) NOT NULL,
    estado varchar(30) NOT NULL,
    datos_json text NOT NULL,
    instancia_proceso varchar(80),
    creado_en timestamp with time zone NOT NULL,
    actualizado_en timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS enel_ordenes_tecnicas (
    id varchar(36) PRIMARY KEY,
    codigo_solicitud varchar(80) NOT NULL,
    codigo_suministro varchar(40),
    nivel_dano varchar(40),
    estado varchar(30) NOT NULL,
    creado_en timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_enel_ordenes_solicitud
    ON enel_ordenes_tecnicas (codigo_solicitud, creado_en);
