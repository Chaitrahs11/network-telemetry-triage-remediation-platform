package com.example.network.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class TelemetryReportSender {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;

    public TelemetryReportSender(String endpoint) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper(), URI.create(endpoint));
    }

    TelemetryReportSender(HttpClient client, ObjectMapper mapper, URI endpoint) {
        if (!"http".equalsIgnoreCase(endpoint.getScheme())
                && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("Report endpoint must use HTTP or HTTPS.");
        }
        this.client = client;
        this.mapper = mapper;
        this.endpoint = endpoint;
    }

    public String send(AgentApplication.Report report) throws IOException, InterruptedException {
        String json = mapper.writeValueAsString(report);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Backend returned HTTP " + response.statusCode()
                    + (response.body().isBlank() ? "." : ": " + response.body()));
        }
        return response.body();
    }
}
