-- =====================================================================
--  Начальные тестовые данные.
--  Даты броней задаются относительно текущего дня (CURRENT_DATE),
--  чтобы демонстрация «прошлые / сегодняшние / будущие» всегда была актуальной.
-- =====================================================================

TRUNCATE TABLE bookings, stations, clients RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------
-- Клиенты (6 человек, в т.ч. один несовершеннолетний — Kirya2010)
-- ---------------------------------------------------------------------
INSERT INTO clients (nickname, full_name, phone, email, birth_date) VALUES
    ('Shadow',    'Иванов Иван Иванович',       '+79161234567', 'shadow@mail.ru',   DATE '2001-03-15'),
    ('Nova_Q',    'Петрова Анна Сергеевна',     '+79161234568', 'nova.q@yandex.ru', DATE '1999-07-22'),
    ('Kirya2010', 'Смирнов Кирилл Алексеевич',  '+79161234569', 'kirya@gmail.com',  DATE '2010-11-05'),
    ('ProGamer',  'Кузнецов Дмитрий Олегович',  '+79161234570', 'progamer@mail.ru', DATE '1995-01-30'),
    ('Lisa_K',    'Козлова Елизавета Павловна', '+79161234571', 'lisa.k@yandex.ru', DATE '2003-09-12'),
    ('Frost',     'Морозов Артём Викторович',   '+79161234572', 'frost@gmail.com',  DATE '2007-05-20');

-- ---------------------------------------------------------------------
-- Игровые места (8 штук, все 4 типа; PC-04 на обслуживании)
-- ---------------------------------------------------------------------
INSERT INTO stations (name, type, hourly_rate, specs, active) VALUES
    ('PC-01',   'STANDARD', 150.00, 'RTX 4060, Ryzen 5 7600, 165 Гц',       TRUE),
    ('PC-02',   'STANDARD', 150.00, 'RTX 4060, Ryzen 5 7600, 165 Гц',       TRUE),
    ('PC-03',   'STANDARD', 150.00, 'RTX 4060, Ryzen 5 7600, 165 Гц',       TRUE),
    ('PC-04',   'STANDARD', 150.00, 'RTX 4060, Ryzen 5 7600, 165 Гц',       FALSE),
    ('VIP-01',  'VIP',      300.00, 'RTX 4080, Ryzen 7 7800X3D, 240 Гц',    TRUE),
    ('VIP-02',  'VIP',      300.00, 'RTX 4080, Ryzen 7 7800X3D, 240 Гц',    TRUE),
    ('BOOT-01', 'BOOTCAMP', 250.00, 'Комната на 5 ПК, RTX 4070 Ti, 360 Гц', TRUE),
    ('PS5-01',  'CONSOLE',  200.00, 'PlayStation 5, 4K-телевизор 65 дюймов', TRUE);

-- ---------------------------------------------------------------------
-- Бронирования (15 штук, все 5 статусов, прошлые/сегодняшние/будущие)
-- Стоимость = часы (с округлением вверх до 30 мин) * тариф места.
-- ---------------------------------------------------------------------
INSERT INTO bookings (client_id, station_id, start_time, end_time, status, total_price, comment) VALUES
    -- прошлые
    (1, 1, CURRENT_DATE - 5 + TIME '18:00', CURRENT_DATE - 5 + TIME '21:00', 'COMPLETED',  450.00, 'Вечерняя катка в CS2'),
    (2, 5, CURRENT_DATE - 5 + TIME '19:00', CURRENT_DATE - 5 + TIME '23:00', 'COMPLETED', 1200.00, 'Стрим на Twitch'),
    (3, 2, CURRENT_DATE - 4 + TIME '15:00', CURRENT_DATE - 4 + TIME '17:00', 'COMPLETED',  300.00, 'Dota 2 после школы'),
    (4, 7, CURRENT_DATE - 3 + TIME '10:00', CURRENT_DATE - 3 + TIME '22:00', 'COMPLETED', 3000.00, 'Тренировка команды перед турниром'),
    (5, 8, CURRENT_DATE - 3 + TIME '16:00', CURRENT_DATE - 3 + TIME '18:30', 'CANCELLED',  500.00, 'FIFA с друзьями'),
    (6, 3, CURRENT_DATE - 2 + TIME '20:00', CURRENT_DATE - 2 + TIME '23:00', 'COMPLETED',  450.00, NULL),
    (1, 6, CURRENT_DATE - 1 + TIME '21:00', CURRENT_DATE     + TIME '01:00', 'COMPLETED', 1200.00, 'Ночной марафон Valorant'),
    -- сегодня
    (2, 1, CURRENT_DATE     + TIME '12:00', CURRENT_DATE     + TIME '15:00', 'ACTIVE',     450.00, 'Квалификация турнира клуба'),
    (3, 2, CURRENT_DATE     + TIME '14:00', CURRENT_DATE     + TIME '16:00', 'CONFIRMED',  300.00, 'День рождения'),
    (4, 5, CURRENT_DATE     + TIME '22:00', CURRENT_DATE + 1 + TIME '02:00', 'CONFIRMED', 1200.00, 'Ночная сессия'),
    -- будущие
    (5, 3, CURRENT_DATE + 1 + TIME '11:00', CURRENT_DATE + 1 + TIME '13:00', 'CREATED',    300.00, 'Первое посещение'),
    (6, 7, CURRENT_DATE + 1 + TIME '18:00', CURRENT_DATE + 1 + TIME '22:00', 'CREATED',   1000.00, 'Турнир по CS2, буткемп'),
    (1, 1, CURRENT_DATE + 2 + TIME '10:00', CURRENT_DATE + 2 + TIME '11:30', 'CREATED',    225.00, NULL),
    (2, 8, CURRENT_DATE + 2 + TIME '15:00', CURRENT_DATE + 2 + TIME '17:00', 'CANCELLED',  400.00, 'Передумала'),
    (4, 6, CURRENT_DATE + 3 + TIME '19:00', CURRENT_DATE + 3 + TIME '22:00', 'CONFIRMED',  900.00, 'Финал турнира клуба');
