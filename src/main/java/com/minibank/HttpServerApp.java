package com.minibank;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HttpServerApp {
    private static final BankService bank = new BankService();
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws IOException {
        try {
            DbMigrationRunner.main(new String[]{});
        } catch (Exception e) {
            System.err.println("Ошибка миграции: " + e.getMessage());
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/transfers", HttpServerApp::handleTransfer);
        server.createContext("/clients", HttpServerApp::handleClient);
        server.createContext("/accounts", HttpServerApp::handleAccount);

        server.setExecutor(null);
        server.start();
        System.out.println("Сервер запущен на порту 8080. Ожидание запросов...");
    }

    // POST /transfers
    private static void handleTransfer(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendResponse(exchange, 405, "Method Not Allowed. Use POST.");
            return;
        }

        try {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(body, JsonObject.class);

            if (json == null || !json.has("fromAccountId") ||
                    !json.has("toAccountId") ||
                    !json.has("amount")
            ) {
                sendResponse(exchange, 400, "Bad Request: Missing fields");
                return;
            }

            Long fromId = json.get("fromAccountId").getAsLong();
            Long toId = json.get("toAccountId").getAsLong();
            BigDecimal amount = json.get("amount").getAsBigDecimal();

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                sendResponse(exchange, 400, "Bad Request: Amount must be positive");
                return;
            }

            bank.transfer(fromId, toId, amount);
            sendResponse(exchange, 200, "Transfer successful");

        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("not found")) {
                sendResponse(exchange, 404, "Not Found: " + e.getMessage());
            } else {
                sendResponse(exchange, 400, "Bad Request: " + e.getMessage());
            }
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("Insufficient funds")) {
                sendResponse(exchange, 409, "Conflict: " + e.getMessage());
            } else {
                sendResponse(exchange, 500, "Internal Server Error");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    // POST /clients
    private static void handleClient(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendResponse(exchange, 405, "Method Not Allowed. Use POST.");
            return;
        }
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(body, JsonObject.class);

            if (json == null || !json.has("name") || !json.has("email")) {
                sendResponse(exchange, 400, "Bad Request: Missing fields");
                return;
            }

            Client client = bank.registerClient(json.get("name").getAsString(), json.get("email").getAsString());
            exchange.getResponseHeaders().set("Location", "/clients/" + client.getId());
            sendResponse(exchange, 201, "Client created with ID: " + client.getId());

        } catch (Exception e) {
            sendResponse(exchange, 400, "Bad Request: " + e.getMessage());
        }
    }

    // /accounts (POST, GET, GET /transactions)
    private static void handleAccount(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            // POST /accounts (создание счета)
            if (path.equals("/accounts") && method.equalsIgnoreCase("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = gson.fromJson(body, JsonObject.class);

                if (json == null || !json.has("clientId") || !json.has("accountNumber")) {
                    sendResponse(exchange, 400, "Bad Request: Missing fields");
                    return;
                }

                Account account = bank.openAccount(
                        json.get("clientId").getAsLong(),
                        json.get("accountNumber").getAsString()
                );

                exchange.getResponseHeaders().set("Location", "/accounts/" + account.getId());
                sendResponse(exchange, 201, "Account created with ID: " + account.getId());
                return;
            }

            // GET /accounts/{id} (получение баланса)
            if (path.matches("^/accounts/\\d+$") && method.equalsIgnoreCase("GET")) {
                Long id = Long.parseLong(path.substring("/accounts/".length()));
                Account account = bank.getAccount(id);
                sendResponse(
                        exchange,
                        200,
                        "{\"id\":" + account.getId() + ", \"balance\":" + account.getBalance() + "}"
                );
                return;
            }

            // GET /accounts/{id}/transactions (история операций)
            if (path.matches("^/accounts/\\d+/transactions$") && method.equalsIgnoreCase("GET")) {
                String idStr = path.split("/")[2];
                Long id = Long.parseLong(idStr);

                List<Transaction> transactions = bank.getTransactionsByAccountId(id);

                sendResponse(exchange, 200, gson.toJson(transactions));
                return;
            }
            sendResponse(exchange, 404, "Not Found");

        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 404, "Not Found: " + e.getMessage());
        } catch (Exception e) {
            sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] responseBytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
