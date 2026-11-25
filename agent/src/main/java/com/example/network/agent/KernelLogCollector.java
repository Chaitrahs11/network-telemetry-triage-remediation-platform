package com.example.network.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class KernelLogCollector {
    private final List<String> command;
    private final Duration timeout;

    public KernelLogCollector() {
        this(List.of("dmesg"), Duration.ofSeconds(5));
    }

    KernelLogCollector(List<String> command, Duration timeout) {
        this.command = List.copyOf(command);
        this.timeout = timeout;
    }

    public Result collect() throws IOException, InterruptedException {
        Path output = Files.createTempFile("agent-kernel-output-", ".txt");
        try {
            Path error = Files.createTempFile("agent-kernel-error-", ".txt");
            Process process = null;
            try {
                try {
                    process = new ProcessBuilder(command)
                            .redirectOutput(output.toFile())
                            .redirectError(error.toFile())
                            .start();
                } catch (IOException exception) {
                    return new Result(false, "", "Could not start dmesg: " + exception.getMessage());
                }
                process.getOutputStream().close();
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    return new Result(false, "", "dmesg exceeded its collection timeout.");
                }
                if (process.exitValue() != 0) {
                    String detail;
                    try (var stream = Files.newInputStream(error)) {
                        detail = new String(stream.readNBytes(4096), StandardCharsets.UTF_8).strip();
                    }
                    return new Result(false, "", "dmesg exited with code " + process.exitValue()
                            + (detail.isEmpty() ? "." : ": " + detail));
                }
                return new Result(true, lastTwentyLines(output), null);
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor();
                }
                Files.deleteIfExists(error);
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private String lastTwentyLines(Path output) throws IOException {
        var lines = new ArrayDeque<String>();
        try (BufferedReader reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() == 20) {
                    lines.removeFirst();
                }
                lines.addLast(line);
            }
        }
        return String.join("\n", lines);
    }

    public record Result(boolean available, String tail, String error) {}
}
