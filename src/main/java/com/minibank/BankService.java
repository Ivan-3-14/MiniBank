package com.minibank;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class BankService {

    private static final String URL = "jdbc:postgresql://localhost:5432/minibank";
    private static final String USER = "postgres";
    private static final String PASSWORD = "1234";

    public Client registerClient(String name, String email) {
        String sql = "INSERT INTO clients (name, email) VALUES (?, ?) RETURNING id";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Client(rs.getLong("id"), name, email);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error registering client", e);
        }
        throw new RuntimeException("Failed to register client");
    }

    public Account openAccount(Long clientId, String accountNumber) {
        String sql = "INSERT INTO accounts (client_id, account_number, balance) VALUES (?, ?, 0) RETURNING id";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            ps.setString(2, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Account(rs.getLong("id"), clientId, accountNumber, BigDecimal.ZERO);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error opening account", e);
        }
        throw new RuntimeException("Failed to open account");
    }

    public Account getAccount(Long accountId) {
        String sql = "SELECT id, client_id, account_number, balance FROM accounts WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Account(
                            rs.getLong("id"),
                            rs.getLong("client_id"),
                            rs.getString("account_number"),
                            rs.getBigDecimal("balance")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error getting account", e);
        }
        throw new IllegalArgumentException("Account not found: " + accountId);
    }

    public Client getClient(Long clientId) {
        String sql = "SELECT id, name, email FROM clients WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Client(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("email")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error getting client", e);
        }
        throw new IllegalArgumentException("Client not found: " + clientId);
    }

    public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            Account from = getAccount(fromAccountId);
            if (from.getBalance().compareTo(amount) < 0) {
                throw new IllegalStateException("Insufficient funds on account " + fromAccountId);
            }

            String withdrawSql = "UPDATE accounts SET balance = balance - ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(withdrawSql)) {
                ps.setBigDecimal(1, amount);
                ps.setLong(2, fromAccountId);
                ps.executeUpdate();
            }
            recordTransaction(conn, fromAccountId, amount.negate(), "TRANSFER_OUT");

            conn.commit();
            System.out.println("Деньги списаны и зафиксированы (баг сохранен).");

            Account to = getAccount(toAccountId);

            String depositSql = "UPDATE accounts SET balance = balance + ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(depositSql)) {
                ps.setBigDecimal(1, amount);
                ps.setLong(2, toAccountId);
                ps.executeUpdate();
            }
            recordTransaction(conn, toAccountId, amount, "TRANSFER_IN");

            conn.commit();

        } catch (SQLException e) {
            throw new RuntimeException("Error during transfer", e);
        }
    }

    public void deposit(Long accountId, BigDecimal amount) {
        String sql = "UPDATE accounts SET balance = balance + ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, accountId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Account not found: " + accountId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error depositing money", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private void recordTransaction(Connection conn, Long accountId, BigDecimal amount, String type) throws SQLException {
        String sql = "INSERT INTO transactions (account_id, amount, type, transaction_date) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.setBigDecimal(2, amount);
            ps.setString(3, type);
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }
}
