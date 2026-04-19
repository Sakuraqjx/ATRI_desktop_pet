package com.example.desktoppet.ui;

import com.example.desktoppet.core.PetContext;
import com.example.desktoppet.plugin.MenuCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class PetBackendServer {
    private static final Map<String, String> CONTENT_TYPES = createContentTypes();

    private final Path assetRoot;
    private final PetContext context;
    private final PetView petView;
    private final Runnable shutdownHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;

    public PetBackendServer(Path assetRoot, PetContext context, PetView petView, Runnable shutdownHandler) {
        this.assetRoot = assetRoot;
        this.context = context;
        this.petView = petView;
        this.shutdownHandler = shutdownHandler;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/state", this::handleState);
        server.createContext("/api/model", this::handleModel);
        server.createContext("/api/interaction", this::handleInteraction);
        server.createContext("/api/menu", this::handleMenu);
        server.createContext("/api/menu/execute", this::handleMenuExecute);
        server.createContext("/api/log", this::handleLog);
        server.createContext("/api/exit", this::handleExit);
        server.createContext("/", this::handleAssetRequest);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "desktop-pet-http");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handleState(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        PetShellSnapshot snapshot = petView.snapshot(context.getPetState());
        sendJson(exchange, 200, snapshot);
    }

    private void handleModel(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        sendJson(exchange, 200, petView.getLive2dModel());
    }

    private void handleInteraction(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String type = query.getOrDefault("type", "");
        String payload = query.getOrDefault("payload", "");

        switch (type) {
            case "pet.tap" -> context.runAction("greet");
            case "pet.doubleTap" -> context.runAction("play");
            case "pet.contextMenu" -> {
                // The native menu is rendered by Electron, so this event is informational only.
            }
            default -> System.err.println("[INTERACTION] Unknown type: " + type + " payload=" + payload);
        }

        sendJson(exchange, 200, Map.of("ok", true));
    }

    private void handleMenu(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        List<Map<String, String>> commands = context.getMenuCommands().stream()
                .map(command -> Map.of("id", command.id(), "label", command.label()))
                .toList();
        sendJson(exchange, 200, commands);
    }

    private void handleMenuExecute(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String id = query.get("id");
        if (id == null || id.isBlank()) {
            sendText(exchange, 400, "Missing id");
            return;
        }

        context.invokeMenuCommand(id);
        sendJson(exchange, 200, Map.of("ok", true));
    }

    private void handleLog(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String level = query.getOrDefault("level", "info");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        System.err.println("[SHELL][" + level.toUpperCase() + "] " + body);
        sendJson(exchange, 200, Map.of("ok", true));
    }

    private void handleExit(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        sendJson(exchange, 200, Map.of("ok", true));
        shutdownHandler.run();
    }

    private void handleAssetRequest(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        String decodedPath = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        String relativePath = decodedPath.startsWith("/") ? decodedPath.substring(1) : decodedPath;
        if (relativePath.isBlank()) {
            relativePath = "index.html";
        }

        Path requestedPath = assetRoot.resolve(relativePath).normalize();
        if (!requestedPath.startsWith(assetRoot) || !Files.exists(requestedPath) || Files.isDirectory(requestedPath)) {
            sendText(exchange, 404, "Not Found");
            return;
        }

        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Cache-Control", "no-cache");
        headers.add("Content-Type", contentTypeFor(requestedPath));

        byte[] bytes = Files.readAllBytes(requestedPath);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private String contentTypeFor(Path path) {
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "application/octet-stream";
        }
        return CONTENT_TYPES.getOrDefault(filename.substring(dot + 1).toLowerCase(), "application/octet-stream");
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            query.put(key, value);
        }
        return query;
    }

    private static Map<String, String> createContentTypes() {
        Map<String, String> map = new HashMap<>();
        map.put("html", "text/html; charset=utf-8");
        map.put("css", "text/css; charset=utf-8");
        map.put("js", "application/javascript; charset=utf-8");
        map.put("json", "application/json; charset=utf-8");
        map.put("png", "image/png");
        map.put("jpg", "image/jpeg");
        map.put("jpeg", "image/jpeg");
        map.put("moc3", "application/octet-stream");
        return map;
    }
}
