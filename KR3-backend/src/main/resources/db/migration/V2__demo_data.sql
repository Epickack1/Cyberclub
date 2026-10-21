-- =====================================================================
--  Тестовые данные (те же, что в КР1).
--  Даты броней задаются относительно текущего дня, чтобы демонстрация
--  "прошлые / сегодняшние / будущие" всегда была актуальной.
-- =====================================================================

INSERT INTO clients (nickname, full_name, phone, email, birth_date) VALUES
    ('Shadow',    'Иванов Иван Иванович',       '+79161234567', 'shadow@mail.ru',   DATE '2001-03-15'),
    ('Nova_Q',    'Петрова Анна Сергеевна',     '+79161234568', 'nova.q@yandex.ru', DATE '1999-07-22'),
    ('Kirya2010', 'Смирнов Кирилл Алексеевич',  '+79161234569', 'kirya@gmail.com',  DATE '2010-11-05'),
    ('ProGamer',  'Кузнецов Дмитрий Олегович',  '+79161234570', 'progamer@mail.ru', DATE '1995-01-30'),
    ('Lisa_K',    'Козлова Елизавета Павловна', '+79161234571', 'lisa.k@yandex.ru', DATE '2003-09-12'),
    ('Frost',     'Морозов Артём Викторович',   '+79161234572', 'frost@gmail.com',  DATE '2007-05-20');

INSERT INTO stations (name, type, hourly_rate, specs, active) VALUES
    ('PC-01',   'STANDARD', 150.00, 'RTX 4060, Ryzen 5 7600, 165 Гц',        TRUE),
    ('PC-02',   'STANDARD', 150.00, 'RTX 4060, Ryzen 5 7600, 165 Гц',        TRUE),
    ('PC-03',   'STANDARD', 150.00, 'RTX 4060, Ryzen 5 7600, 165 Гц',        TRUE),
    ('PC-04',   'STANDARD', 150.00, 'RTX 4060, Ryzen 5 7600, 165 Гц',        FALSE),
    ('VIP-01',  'VIP',      300.00, 'RTX 4080, Ryzen 7 7800X3D, 240 Гц',     TRUE),
    ('VIP-02',  'VIP',      300.00, 'RTX 4080, Ryzen 7 7800X3D, 240 Гц',     TRUE),
    ('BOOT-01', 'BOOTCAMP', 250.00, 'Комната на 5 ПК, RTX 4070 Ti, 360 Гц',  TRUE),
    ('PS5-01',  'CONSOLE',  200.00, 'PlayStation 5, 4K-телевизор 65 дюймов', TRUE);

-- Клиент и место ищутся по нику и названию, а не по номеру строки.
INSERT INTO bookings (id, client_id, station_id, start_time, end_time, status, total_price, paid, comment)
SELECT gen_random_uuid(), c.id, s.id, b.start_time, b.end_time, b.status, b.total_price, b.paid, b.comment
FROM (VALUES
    -- прошлые
    (1,  'Shadow',    'PC-01',   CURRENT_DATE - 5 + TIME '18:00', CURRENT_DATE - 5 + TIME '21:00', 'COMPLETED',  450.00, TRUE,  'Вечерняя катка в CS2'),
    (2,  'Nova_Q',    'VIP-01',  CURRENT_DATE - 5 + TIME '19:00', CURRENT_DATE - 5 + TIME '23:00', 'COMPLETED', 1200.00, TRUE,  'Стрим на Twitch'),
    (3,  'Kirya2010', 'PC-02',   CURRENT_DATE - 4 + TIME '15:00', CURRENT_DATE - 4 + TIME '17:00', 'COMPLETED',  300.00, TRUE,  'Dota 2 после школы'),
    (4,  'ProGamer',  'BOOT-01', CURRENT_DATE - 3 + TIME '10:00', CURRENT_DATE - 3 + TIME '22:00', 'COMPLETED', 3000.00, TRUE,  'Тренировка команды перед турниром'),
    (5,  'Lisa_K',    'PS5-01',  CURRENT_DATE - 3 + TIME '16:00', CURRENT_DATE - 3 + TIME '18:30', 'CANCELLED',  500.00, FALSE, 'FIFA с друзьями'),
    (6,  'Frost',     'PC-03',   CURRENT_DATE - 2 + TIME '20:00', CURRENT_DATE - 2 + TIME '23:00', 'COMPLETED',  450.00, TRUE,  NULL),
    (7,  'Shadow',    'VIP-02',  CURRENT_DATE - 1 + TIME '21:00', CURRENT_DATE     + TIME '01:00', 'COMPLETED', 1200.00, TRUE,  'Ночной марафон Valorant'),
    -- сегодня
    (8,  'Nova_Q',    'PC-01',   CURRENT_DATE     + TIME '12:00', CURRENT_DATE     + TIME '15:00', 'ACTIVE',     450.00, TRUE,  'Квалификация турнира клуба'),
    (9,  'Kirya2010', 'PC-02',   CURRENT_DATE     + TIME '14:00', CURRENT_DATE     + TIME '16:00', 'CONFIRMED',  300.00, FALSE, 'День рождения'),
    (10, 'ProGamer',  'VIP-01',  CURRENT_DATE     + TIME '22:00', CURRENT_DATE + 1 + TIME '02:00', 'CONFIRMED', 1200.00, FALSE, 'Ночная сессия'),
    -- будущие
    (11, 'Lisa_K',    'PC-03',   CURRENT_DATE + 1 + TIME '11:00', CURRENT_DATE + 1 + TIME '13:00', 'CREATED',    300.00, FALSE, 'Первое посещение'),
    (12, 'Frost',     'BOOT-01', CURRENT_DATE + 1 + TIME '18:00', CURRENT_DATE + 1 + TIME '22:00', 'CREATED',   1000.00, FALSE, 'Турнир по CS2, буткемп'),
    (13, 'Shadow',    'PC-01',   CURRENT_DATE + 2 + TIME '10:00', CURRENT_DATE + 2 + TIME '11:30', 'CREATED',    225.00, FALSE, NULL),
    (14, 'Nova_Q',    'PS5-01',  CURRENT_DATE + 2 + TIME '15:00', CURRENT_DATE + 2 + TIME '17:00', 'CANCELLED',  400.00, FALSE, 'Передумала'),
    (15, 'ProGamer',  'VIP-02',  CURRENT_DATE + 3 + TIME '19:00', CURRENT_DATE + 3 + TIME '22:00', 'CONFIRMED',  900.00, TRUE,  'Финал турнира клуба')
) AS b(seq, nickname, station_name, start_time, end_time, status, total_price, paid, comment)
JOIN clients  c ON c.nickname = b.nickname
JOIN stations s ON s.name = b.station_name
ORDER BY b.seq;
