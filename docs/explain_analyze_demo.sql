-- ДЕМОНСТРАЦИЯ РАБОТЫ ИНДЕКСА
-- Запускать в базе minibank после того, как DbMigrationRunner создаст таблицы через Liquibase

-- 0. Очистка (чтобы можно было запускать несколько раз без ошибок)
TRUNCATE TABLE transactions, accounts, clients RESTART IDENTITY CASCADE;

-- 1. Базовые данные (клиенты и счета)
INSERT INTO clients (name, email) VALUES ('Test Client 1', 'test1@mail.com');
INSERT INTO clients (name, email) VALUES ('Test Client 2', 'test2@mail.com');

INSERT INTO accounts (client_id, account_number, balance)
VALUES (1, 'ACC-001', 1000), (2, 'ACC-002', 2000);

-- 2. Генерация 55 000 транзакций за последний год
INSERT INTO transactions (account_id, amount, type, transaction_date)
SELECT
    (random() * 1 + 1)::int,
    (random() * 1000)::numeric(19,4),
    'TRANSFER',
    NOW() - (random() * interval '365 days')
FROM generate_series(1, 55000);

-- Обновляем статистику, чтобы планировщик "увидел" данные
ANALYZE transactions;

-- ПЛАН БЕЗ ИНДЕКСА
DROP INDEX IF EXISTS idx_transactions_account_date;

EXPLAIN ANALYZE
SELECT * FROM transactions
WHERE account_id = 1
  AND transaction_date BETWEEN '2025-01-01' AND '2025-12-31';

-- ПЛАН С ИНДЕКСОМ

CREATE INDEX idx_transactions_account_date ON transactions (account_id, transaction_date);

ANALYZE transactions;

EXPLAIN ANALYZE
SELECT * FROM transactions
WHERE account_id = 1
  AND transaction_date BETWEEN '2025-01-01' AND '2025-12-31';
