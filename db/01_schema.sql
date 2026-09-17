-- =====================================================================
--  Киберспортивный клуб — схема базы данных (PostgreSQL)
--  КР1. Три связанные таблицы: clients (1) --< bookings >-- (1) stations
-- =====================================================================

-- Расширение для ограничения EXCLUDE (защита от пересекающихся броней на одном месте)
CREATE EXTENSION IF NOT EXISTS btree_gist;

DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS stations;
DROP TABLE IF EXISTS clients;

-- ---------------------------------------------------------------------
-- Клиенты клуба
-- ---------------------------------------------------------------------
CREATE TABLE clients (
    id          BIGSERIAL    PRIMARY KEY,
    nickname    VARCHAR(20)  NOT NULL UNIQUE,          -- игровой ник, уникален
    full_name   VARCHAR(100) NOT NULL,
    phone       VARCHAR(12)  NOT NULL UNIQUE,          -- формат +7XXXXXXXXXX
    email       VARCHAR(100) NOT NULL UNIQUE,
    birth_date  DATE         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_clients_nickname   CHECK (nickname ~ '^[A-Za-z0-9_]{3,20}$'),
    CONSTRAINT chk_clients_phone      CHECK (phone ~ '^\+7[0-9]{10}$'),
    CONSTRAINT chk_clients_email      CHECK (email LIKE '%_@_%.__%'),
    CONSTRAINT chk_clients_birth_date CHECK (birth_date <= CURRENT_DATE)
);

-- ---------------------------------------------------------------------
-- Игровые места (ПК, консоли, буткемп)
-- ---------------------------------------------------------------------
CREATE TABLE stations (
    id           BIGSERIAL     PRIMARY KEY,
    name         VARCHAR(20)   NOT NULL UNIQUE,        -- например PC-01, VIP-01, PS5-01
    type         VARCHAR(20)   NOT NULL,               -- значение enum StationType
    hourly_rate  NUMERIC(10,2) NOT NULL,               -- тариф, руб./час
    specs        VARCHAR(255),                         -- характеристики
    active       BOOLEAN       NOT NULL DEFAULT TRUE,  -- FALSE = на обслуживании

    CONSTRAINT chk_stations_type CHECK (type IN ('STANDARD', 'VIP', 'BOOTCAMP', 'CONSOLE')),
    CONSTRAINT chk_stations_rate CHECK (hourly_rate > 0)
);

-- ---------------------------------------------------------------------
-- Бронирования игровых мест — основная сущность системы
-- ---------------------------------------------------------------------
CREATE TABLE bookings (
    id           BIGSERIAL     PRIMARY KEY,
    client_id    BIGINT        NOT NULL,
    station_id   BIGINT        NOT NULL,
    start_time   TIMESTAMP     NOT NULL,
    end_time     TIMESTAMP     NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'CREATED',   -- значение enum BookingStatus
    total_price  NUMERIC(10,2) NOT NULL,
    comment      VARCHAR(255),
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_bookings_client  FOREIGN KEY (client_id)  REFERENCES clients(id)  ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_station FOREIGN KEY (station_id) REFERENCES stations(id) ON DELETE RESTRICT,
    CONSTRAINT chk_bookings_status CHECK (status IN ('CREATED', 'CONFIRMED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_bookings_price  CHECK (total_price >= 0),
    CONSTRAINT chk_bookings_time   CHECK (end_time > start_time),

    -- Две неотменённые брони не могут пересекаться по времени на одном месте.
    -- Интервал полуоткрытый [start, end): бронь 12:00-14:00 и 14:00-16:00 не конфликтуют.
    CONSTRAINT excl_bookings_overlap EXCLUDE USING gist (
        station_id WITH =,
        tsrange(start_time, end_time) WITH &&
    ) WHERE (status <> 'CANCELLED')
);

CREATE INDEX idx_bookings_start_time ON bookings (start_time);
CREATE INDEX idx_bookings_status     ON bookings (status);
CREATE INDEX idx_bookings_client     ON bookings (client_id);
CREATE INDEX idx_bookings_station    ON bookings (station_id);

COMMENT ON TABLE clients  IS 'Клиенты киберспортивного клуба';
COMMENT ON TABLE stations IS 'Игровые места клуба';
COMMENT ON TABLE bookings IS 'Бронирования игровых мест (основная сущность)';
