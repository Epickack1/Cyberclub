-- =====================================================================
--  CyberClub, КР3, этап 2. Пользователи системы и авторизация.
--    * app_users        — учётные записи сотрудников (пароль хранится только как хеш BCrypt);
--    * app_user_roles   — роли: VIEWER (просмотр), OPERATOR (администратор зала), ADMIN;
--    * refresh_sessions — refresh-сессии; хранится SHA-256 токена, а не сам токен.
--  Первый администратор создаётся приложением из переменных окружения BOOTSTRAP_ADMIN_*.
-- =====================================================================

CREATE TABLE app_users (
    id                    UUID          PRIMARY KEY,
    email                 VARCHAR(100)  NOT NULL,
    display_name          VARCHAR(100)  NOT NULL,
    password_hash         VARCHAR(100)  NOT NULL,
    enabled               BOOLEAN       NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER       NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version               BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT chk_app_users_email    CHECK (email LIKE '%_@_%.__%'),
    CONSTRAINT chk_app_users_attempts CHECK (failed_login_attempts >= 0)
);

-- Email уникален без учёта регистра: Admin@club.ru и admin@club.ru — одна учётная запись.
CREATE UNIQUE INDEX ux_app_users_email ON app_users (lower(email));

CREATE TABLE app_user_roles (
    user_id UUID        NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT chk_app_user_roles_role CHECK (role IN ('VIEWER', 'OPERATOR', 'ADMIN'))
);

CREATE TABLE refresh_sessions (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ
);

CREATE INDEX idx_refresh_sessions_user ON refresh_sessions (user_id);
