package com.minibank;

import java.math.BigDecimal;

public class Main {

    public static void main(String[] args) throws Exception {
        // 1. Запускаем миграции (создаем БД и таблицы, если их нет)
        System.out.println("=== 1. Инициализация базы данных ===");
        DbMigrationRunner.main(new String[]{});

        // 2. Создаем сервис (работает через JDBC)
        BankService bank = new BankService();

        // 3. Демонстрация
        System.out.println("\n=== 2. Регистрация клиента и открытие счетов ===");
        Client client = bank.registerClient("Ivan Petrov", "ivan@example.com");
        Account account1 = bank.openAccount(client.getId(), "ACC-001");
        Account account2 = bank.openAccount(client.getId(), "ACC-002");

        // 4. Пополняем первый счет
        bank.deposit(account1.getId(), new BigDecimal("1000.00"));

        System.out.println("Баланс account1 до перевода: " + bank.getAccount(account1.getId()).getBalance());
        System.out.println("Баланс account2 до перевода: " + bank.getAccount(account2.getId()).getBalance());

        System.out.println("\n=== 3. Обычный перевод ===");
        bank.transfer(account1.getId(), account2.getId(), new BigDecimal("100.00"));
        System.out.println("Баланс account1 после перевода: " + bank.getAccount(account1.getId()).getBalance());
        System.out.println("Баланс account2 после перевода: " + bank.getAccount(account2.getId()).getBalance());

        System.out.println("\n=== 4. Перевод на несуществующий счёт (баг) ===");
        BigDecimal balanceBefore = bank.getAccount(account1.getId()).getBalance();
        try {
            bank.transfer(account1.getId(), 999L, new BigDecimal("50.00"));
        } catch (IllegalArgumentException e) {
            System.out.println("Ошибка (как и ожидалось): " + e.getMessage());
        }
        BigDecimal balanceAfter = bank.getAccount(account1.getId()).getBalance();

        System.out.println("Баланс до попытки перевода: " + balanceBefore);
        System.out.println("Баланс после попытки перевода: " + balanceAfter);
        System.out.println("Деньги пропали: " + (balanceAfter.compareTo(balanceBefore) < 0));
    }
}
