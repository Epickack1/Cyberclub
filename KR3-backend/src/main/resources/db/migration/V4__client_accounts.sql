-- =====================================================================
--  V4: учётные записи клиентов клуба.
--  Роль CLIENT — посетитель, который входит на сайт и видит только свои
--  данные. Его учётная запись привязана к карточке в таблице clients.
-- =====================================================================

ALTER TABLE app_user_roles DROP CONSTRAINT chk_app_user_roles_role;
ALTER TABLE app_user_roles ADD CONSTRAINT chk_app_user_roles_role
    CHECK (role IN ('VIEWER', 'OPERATOR', 'ADMIN', 'CLIENT'));

-- У клиента не больше одной учётной записи. При удалении клиента удаляется и его учётная запись.
ALTER TABLE app_users ADD COLUMN client_id BIGINT
    REFERENCES clients (id) ON DELETE CASCADE;
CREATE UNIQUE INDEX ux_app_users_client ON app_users (client_id) WHERE client_id IS NOT NULL;

COMMENT ON COLUMN app_users.client_id IS 'Карточка клиента для учётной записи с ролью CLIENT; у сотрудников NULL';
