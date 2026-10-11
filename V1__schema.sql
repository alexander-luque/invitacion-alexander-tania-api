CREATE TABLE invitado (
 codigo VARCHAR(32) PRIMARY KEY,
 nombre VARCHAR(200) NOT NULL,
 cupos INTEGER NOT NULL CHECK(cupos >= 0),
 estado VARCHAR(32) NOT NULL DEFAULT 'PENDIENTE',
 mensaje VARCHAR(1500),
 pase_token UUID NOT NULL UNIQUE,
 ingreso_cupos INTEGER NOT NULL DEFAULT 0 CHECK(ingreso_cupos >= 0),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT ck_ingreso_cupos CHECK(ingreso_cupos <= cupos),
 CONSTRAINT ck_estado CHECK(estado IN ('PENDIENTE','SI','NO'))
);
CREATE TABLE ingreso (
 id BIGSERIAL PRIMARY KEY,
 codigo VARCHAR(32) NOT NULL REFERENCES invitado(codigo),
 cantidad INTEGER NOT NULL CHECK(cantidad > 0),
 registrado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE aporte (
 id BIGSERIAL PRIMARY KEY,
 codigo VARCHAR(32) NOT NULL REFERENCES invitado(codigo),
 monto NUMERIC(12,2) NOT NULL CHECK(monto > 0),
 fecha_deposito DATE NOT NULL,
 estado VARCHAR(24) NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN ('PENDIENTE','VERIFICADO','RECHAZADO')),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ingreso_codigo ON ingreso(codigo);
CREATE INDEX idx_aporte_codigo ON aporte(codigo);
