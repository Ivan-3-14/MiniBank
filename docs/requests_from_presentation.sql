
CREATE TABLE products (
                          id UUID PRIMARY KEY,
                          name CHAR(255),
                          price BIGINT,
                          category CHAR(50),
                          in_stock BOOLEAN,
                          date_of_incomming DATE
);

-- 1. Вывести названия и цены товаров категории 'Электроника' в наличии
SELECT name, price
FROM products
WHERE category = 'Электроника' AND in_stock = true;

-- 2. Увеличить цену всех товаров категории 'Книги' на 10%
UPDATE products
SET price = price * 1.1
WHERE category = 'Книги';

-- 3. Добавить новый товар: «Моя первая PostgreSQL книга», 500, 'Книги', true
INSERT INTO products (id, name, price, category, in_stock)
VALUES (gen_random_uuid(), 'Моя первая PostgreSQL книга', 500, 'Книги', true);

-- 4. Удалить все товары, которых нет в наличии
DELETE FROM products
WHERE in_stock = false;

-- 5. Снизить цену книг, доставленных от полугода до 2 лет назад, на 20% и добавить «(Скидочная позиция)» в название
UPDATE products
SET
    price = price * 0.8,
    name = TRIM(name) || ' (Скидочная позиция)'
WHERE
    category = 'Книги'
    AND date_of_incomming BETWEEN (CURRENT_DATE - INTERVAL '2 years')
                              AND (CURRENT_DATE - INTERVAL '6 months');
