package com.example.network.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class KernelLogCollectorTest {
    @Test
    void keepsOnlyLastTwentyLinesFromCommandOutput() throws Exception {
        var result = collector("lines", Duration.ofSeconds(10)).collect();
        assertTrue(result.available());
        assertNull(result.error());
        assertEquals(20, result.tail().lines().count());
        assertTrue(result.tail().startsWith("line-6\n"));
        assertTrue(result.tail().endsWith("line-25"));
    }

    @Test
    void emptySuccessfulOutputIsAvailable() throws Exception {
        var result = collector("empty", Duration.ofSeconds(10)).collect();
        assertTrue(result.available());
        assertEquals("", result.tail());
        assertNull(result.error());
    }

    @Test
    void permissionFailureIsExplicitAndDoesNotBecomeLogContent() throws Exception {
        var result = collector("denied", Duration.ofSeconds(10)).collect();
        assertFalse(result.available());
        assertEquals("", result.tail());
        assertTrue(result.error().contains("Permission denied"));
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(result));
        assertFalse(json.get("available").asBoolean());
        assertTrue(json.get("error").asText().contains("code 1"));
    }

    @Test
    void commandTimeoutIsReported() throws Exception {
        var result = collector("sleep", Duration.ofMillis(100)).collect();
        assertFalse(result.available());
        assertTrue(result.error().contains("timeout"));
    }

    @Test
    void missingCommandIsReported() throws Exception {
        var result = new KernelLogCollector(List.of("nonexistent-agent-test-command"),
                Duration.ofSeconds(1)).collect();
        assertFalse(result.available());
        assertTrue(result.error().contains("Could not start"));
    }

    private KernelLogCollector collector(String mode, Duration timeout) {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        return new KernelLogCollector(List.of(java, "-cp", System.getProperty("java.class.path"),
                CommandFixture.class.getName(), mode), timeout);
    }

    public static class CommandFixture {
        public static void main(String[] args) throws InterruptedException {
            switch (args[0]) {
                case "lines" -> {
                    for (int i = 1; i <= 25; i++) {
                        System.out.println("line-" + i);
                    }
                }
                case "denied" -> {
                    System.err.println("Permission denied");
                    System.exit(1);
                }
                case "sleep" -> Thread.sleep(30_000);
                case "empty" -> {}
                default -> throw new IllegalArgumentException("Unknown test mode");
            }
        }
    }
}
