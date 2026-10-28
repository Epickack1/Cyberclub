-- =====================================================================
--  Общая база с десктопным приложением КР2.
--
--  На рабочей базе cyberclub схему создало приложение КР2, и истории Flyway
--  в ней нет: spring.flyway.baseline-on-migrate помечает V1 как базовую, а этот
--  скрипт доводит таблицы до схемы КР3. Все операции идемпотентны, поэтому на
--  чистой базе (сразу после V1) скрипт ничего не меняет.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------- clients
-- КР2 хранил created_at без часового пояса; Hibernate ждёт Instant (TIMESTAMPTZ).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'clients' AND column_name = 'created_at'
                 AND data_type = 'timestamp without time zone') THEN
        ALTER TABLE clients ALTER COLUMN created_at TYPE TIMESTAMPTZ
            USING created_at AT TIME ZONE current_setting('TimeZone');
    END IF;
END $$;

ALTER TABLE clients ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE clients ADD COLUMN IF NOT EXISTS version    BIGINT      NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_clients_full_name ON clients (lower(full_name));

-- --------------------------------------------------------------- stations
ALTER TABLE stations ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE stations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE stations ADD COLUMN IF NOT EXISTS version    BIGINT      NOT NULL DEFAULT 0;

ALTER TABLE stations DROP CONSTRAINT IF EXISTS chk_stations_rate;
ALTER TABLE stations ADD CONSTRAINT chk_stations_rate
    CHECK (hourly_rate > 0 AND hourly_rate <= 100000);

-- --------------------------------------------------------------- bookings
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'bookings' AND column_name = 'created_at'
                 AND data_type = 'timestamp without time zone') THEN
        ALTER TABLE bookings ALTER COLUMN created_at TYPE TIMESTAMPTZ
            USING created_at AT TIME ZONE current_setting('TimeZone');
    END IF;
END $$;

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS version     BIGINT      NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS paid        BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS archived    BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

-- Номер брони для людей: в КР2 его не было — выдаём существующим строкам по порядку,
-- а дальше значение берёт sequence, как и в схеме V1.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'bookings' AND column_name = 'number') THEN
        ALTER TABLE bookings ADD COLUMN number BIGINT;
        CREATE SEQUENCE bookings_number_seq;
        UPDATE bookings SET number = nextval('bookings_number_seq');
        ALTER TABLE bookings ALTER COLUMN number SET DEFAULT nextval('bookings_number_seq');
        ALTER TABLE bookings ALTER COLUMN number SET NOT NULL;
        ALTER SEQUENCE bookings_number_seq OWNED BY bookings.number;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'bookings_number_key') THEN
        ALTER TABLE bookings ADD CONSTRAINT bookings_number_key UNIQUE (number);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_bookings_archive') THEN
        ALTER TABLE bookings ADD CONSTRAINT chk_bookings_archive
            CHECK (archived = (archived_at IS NOT NULL));
    END IF;
END $$;

-- Архивные брони место не занимают, поэтому ограничение КР2 пересоздаём
-- с дополнительным условием NOT archived.
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS excl_bookings_overlap;
ALTER TABLE bookings ADD CONSTRAINT excl_bookings_overlap EXCLUDE USING gist (
    station_id WITH =,
    tsrange(start_time, end_time) WITH &&
) WHERE (status <> 'CANCELLED' AND NOT archived);

CREATE INDEX IF NOT EXISTS idx_bookings_archived ON bookings (archived);
CREATE INDEX IF NOT EXISTS idx_bookings_comment  ON bookings (lower(comment));

COMMENT ON TABLE clients  IS 'Клиенты киберспортивного клуба (общая база КР2 и КР3)';
COMMENT ON TABLE stations IS 'Игровые места клуба (справочник)';
COMMENT ON TABLE bookings IS 'Бронирования игровых мест (основная сущность)';
