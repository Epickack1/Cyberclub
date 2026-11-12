-- =====================================================================
--  V5: остальные требования КР3 к основной сущности и администрированию.
--  * вычисляемый показатель брони — риск неоплаты (risk_level);
--  * теги броней, заметки к броням;
--  * обязательная смена временного пароля;
--  * журнал аудита.
-- =====================================================================

-- ---------------------------------------------------------------------
--  Риск неоплаты. Считает сервер (BookingRules.paymentRisk) при каждом
--  сохранении брони; хранится в колонке, чтобы по нему фильтровать и
--  группировать в аналитике. Существующие брони пересчитываются здесь
--  по тем же правилам.
-- ---------------------------------------------------------------------
ALTER TABLE bookings ADD COLUMN risk_level VARCHAR(10) NOT NULL DEFAULT 'NONE';

UPDATE bookings SET risk_level = CASE
    WHEN paid OR status = 'CANCELLED'                       THEN 'NONE'
    WHEN status IN ('ACTIVE', 'COMPLETED') OR total_price >= 2000 THEN 'HIGH'
    WHEN total_price >= 800                                 THEN 'MEDIUM'
    ELSE 'LOW'
END;

ALTER TABLE bookings ADD CONSTRAINT chk_bookings_risk
    CHECK (risk_level IN ('NONE', 'LOW', 'MEDIUM', 'HIGH'));

CREATE INDEX idx_bookings_risk ON bookings (risk_level);
CREATE INDEX idx_bookings_paid ON bookings (paid);

COMMENT ON COLUMN bookings.risk_level IS 'Риск неоплаты: вычисляется сервером из статуса, оплаты и стоимости';

-- ---------------------------------------------------------------------
--  Теги броней: строчные буквы, цифры, "_" и "-", от 2 до 30 символов.
-- ---------------------------------------------------------------------
CREATE TABLE booking_tags (
    booking_id UUID        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    tag        VARCHAR(30) NOT NULL,
    PRIMARY KEY (booking_id, tag),
    CONSTRAINT chk_booking_tags_tag CHECK (tag ~ '^[a-zа-яё0-9_-]{2,30}$')
);

CREATE INDEX idx_booking_tags_tag ON booking_tags (tag);

-- ---------------------------------------------------------------------
--  Заметки к броням: автор, время, закрепление.
--  Автор хранится и ссылкой, и именем: после удаления учётной записи
--  история заметок остаётся читаемой.
-- ---------------------------------------------------------------------
CREATE TABLE booking_notes (
    id          UUID          PRIMARY KEY,
    booking_id  UUID          NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    author_id   UUID          REFERENCES app_users (id) ON DELETE SET NULL,
    author_name VARCHAR(100)  NOT NULL,
    body        VARCHAR(2000) NOT NULL,
    pinned      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version     BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT chk_booking_notes_body CHECK (length(trim(body)) > 0)
);

CREATE INDEX idx_booking_notes_booking ON booking_notes (booking_id, pinned DESC, created_at DESC);

-- ---------------------------------------------------------------------
--  Временный пароль: учётная запись, созданная администратором или
--  после сброса пароля, работает только до смены пароля.
-- ---------------------------------------------------------------------
ALTER TABLE app_users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------
--  Журнал аудита: входы и важные изменения данных.
--  Внешнего ключа на app_users нет намеренно: записи журнала не должны
--  исчезать или меняться вместе с учётной записью.
-- ---------------------------------------------------------------------
CREATE TABLE audit_events (
    id             UUID         PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_id       UUID,
    actor_email    VARCHAR(100),
    event_type     VARCHAR(40)  NOT NULL,
    entity_type    VARCHAR(30),
    entity_id      VARCHAR(64),
    summary        VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(64),
    ip_address     VARCHAR(64)
);

CREATE INDEX idx_audit_occurred ON audit_events (occurred_at DESC);
CREATE INDEX idx_audit_type     ON audit_events (event_type);
CREATE INDEX idx_audit_actor    ON audit_events (lower(actor_email));
CREATE INDEX idx_audit_entity   ON audit_events (entity_type, entity_id);

COMMENT ON TABLE audit_events IS 'Журнал аудита: входы и изменения данных; читает только администратор';
