-- Esquema inicial de la BD de la institución (enunciado 3.2). Ver docs/07-base-de-datos.md.

CREATE TABLE empresa (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuit         VARCHAR(13)  NOT NULL UNIQUE,
    razon_social VARCHAR(200) NOT NULL
);

CREATE TABLE cuenta_empresa (
    empresa_id    BIGINT PRIMARY KEY REFERENCES empresa (id),
    usuario       VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL, -- BCrypt; la comunicación de la clave se simula (§4.1.2)
    creada_en     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE curso (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empresa_id              BIGINT       NOT NULL REFERENCES empresa (id),
    process_instance_id     VARCHAR(64) UNIQUE,
    nombre                  VARCHAR(200) NOT NULL, -- temática solicitada en el formulario (§4.1.1)
    descripcion             TEXT         NOT NULL,
    participantes_estimados INT          NOT NULL CHECK (participantes_estimados > 0),
    fechas_preferidas       TEXT         NOT NULL,
    contacto_nombre         VARCHAR(200) NOT NULL,
    contacto_email          VARCHAR(200) NOT NULL,
    contacto_telefono       VARCHAR(50),
    programa                TEXT,
    presupuesto             NUMERIC(12, 2),
    fecha_inicio            TIMESTAMP,
    informe_resultados      TEXT,
    estado                  VARCHAR(20)  NOT NULL DEFAULT 'EN_CURSO'
        CHECK (estado IN ('EN_CURSO', 'FINALIZADO', 'CANCELADO', 'RECHAZADO', 'SIN_RESPUESTA', 'NO_VIABLE')),
    creado_en               TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE propuesta (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    curso_id              BIGINT         NOT NULL REFERENCES curso (id),
    version               INT            NOT NULL CHECK (version > 0),
    programa              TEXT           NOT NULL,
    presupuesto           NUMERIC(12, 2) NOT NULL CHECK (presupuesto >= 0),
    fechas_posibles       TEXT           NOT NULL,
    observaciones_empresa TEXT,
    creada_en             TIMESTAMP      NOT NULL DEFAULT now(),
    UNIQUE (curso_id, version)
);
