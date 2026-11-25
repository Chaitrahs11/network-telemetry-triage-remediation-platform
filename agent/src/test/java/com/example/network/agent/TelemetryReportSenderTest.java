package com.example.network.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelemetryReportSenderTest {
    private HttpServer server;
    private String receivedBody;
    private int responseStatus;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/reports", exchange -> {
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] response = "{\"message\":\"received\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        responseStatus = 200;
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsReportAsJsonAndReturnsBackendReceipt() throws Exception {
        var sender = new TelemetryReportSender(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/reports"));
        var report = new AgentApplication.Report(
                "ff78e898-f80a-4df4-8f6a-785ffa01b6f0", "demo-linux",
                "2026-11-23T13:40:33.610090721Z", "eth0", 0, 2, 1500, "up",
                new KernelLogCollector.Result(true, "", null));

        assertEquals("{\"message\":\"received\"}", sender.send(report));
        var json = new ObjectMapper().readTree(receivedBody);
        assertEquals("demo-linux", json.get("host").asText());
        assertEquals(0, json.get("receiveErrors").asLong());
        assertEquals(2, json.get("sendErrors").asLong());
    }

    @Test
    void rejectsBackendErrorResponses() {
        responseStatus = 400;
        var sender = new TelemetryReportSender(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/reports"));
        var report = new AgentApplication.Report(
                "ff78e898-f80a-4df4-8f6a-785ffa01b6f0", "demo-linux",
                "2026-11-23T13:40:33.610090721Z", "eth0", 0, 2, 1500, "up",
                new KernelLogCollector.Result(true, "", null));

        var exception = assertThrows(IOException.class, () -> sender.send(report));
        assertTrue(exception.getMessage().contains("HTTP 400"));
    }
}
