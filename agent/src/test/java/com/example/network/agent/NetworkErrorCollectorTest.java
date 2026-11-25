package com.example.network.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NetworkErrorCollectorTest {
    @TempDir
    Path networkDirectory;

    @ParameterizedTest
    @ValueSource(longs = {0, 9, 3_000_000_000L})
    void readsCumulativeCounterFromLinuxStyleFile(long value) throws IOException {
        writeCounter("rx_errors", value + "\n");
        writeCounter("tx_errors", "2\n");
        var counters = new NetworkErrorCollector(networkDirectory).collect("eth0");
        assertEquals(value, counters.receiveErrors());
        assertEquals(2, counters.sendErrors());
        assertEquals(1500, counters.mtu());
        assertEquals("up", counters.operationalState());
    }

    @Test
    void missingInterfaceFailsRatherThanReportingZero() {
        assertThrows(IOException.class,
                () -> new NetworkErrorCollector(networkDirectory).collect("eth0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-number", "-1"})
    void invalidCounterFailsExplicitly(String value) throws IOException {
        writeCounter("rx_errors", value);
        writeCounter("tx_errors", "0");
        assertThrows(IOException.class,
                () -> new NetworkErrorCollector(networkDirectory).collect("eth0"));
    }

    @Test
    void missingSendCounterFailsRatherThanReportingZero() throws IOException {
        writeCounter("rx_errors", "9");
        assertThrows(IOException.class,
                () -> new NetworkErrorCollector(networkDirectory).collect("eth0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-number", "-1"})
    void invalidSendCounterFailsExplicitly(String value) throws IOException {
        writeCounter("rx_errors", "9");
        writeCounter("tx_errors", value);
        assertThrows(IOException.class,
                () -> new NetworkErrorCollector(networkDirectory).collect("eth0"));
    }

    @Test
    void readsLargeSendCounterWithoutTruncation() throws IOException {
        writeCounter("rx_errors", "0");
        writeCounter("tx_errors", "3000000000\n");
        var counters = new NetworkErrorCollector(networkDirectory).collect("eth0");
        assertEquals(0, counters.receiveErrors());
        assertEquals(3_000_000_000L, counters.sendErrors());
    }

    @Test
    void rejectsPathsInsteadOfInterfaceNames() {
        assertThrows(IllegalArgumentException.class,
                () -> new NetworkErrorCollector(networkDirectory).collect("../eth0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "notpresent", "down", "lowerlayerdown", "testing", "dormant", "up"})
    void preservesLinuxOperationalStates(String state) throws IOException {
        writeCounter("rx_errors", "0");
        writeCounter("tx_errors", "0");
        Files.writeString(networkDirectory.resolve("eth0").resolve("operstate"), state + "\n");
        assertEquals(state, new NetworkErrorCollector(networkDirectory).collect("eth0").operationalState());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid", "2147483648"})
    void invalidMtuFailsExplicitly(String mtu) throws IOException {
        writeCounter("rx_errors", "0");
        writeCounter("tx_errors", "0");
        Files.writeString(networkDirectory.resolve("eth0").resolve("mtu"), mtu);
        assertThrows(IOException.class,
                () -> new NetworkErrorCollector(networkDirectory).collect("eth0"));
    }

    @Test
    void preservesNonDefaultMtu() throws IOException {
        writeCounter("rx_errors", "0");
        writeCounter("tx_errors", "0");
        Files.writeString(networkDirectory.resolve("eth0").resolve("mtu"), "9000\n");
        assertEquals(9000, new NetworkErrorCollector(networkDirectory).collect("eth0").mtu());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mtu", "operstate"})
    void missingSettingFailsRatherThanUsingDefault(String file) throws IOException {
        writeCounter("rx_errors", "0");
        writeCounter("tx_errors", "0");
        Files.delete(networkDirectory.resolve("eth0").resolve(file));
        assertThrows(IOException.class,
                () -> new NetworkErrorCollector(networkDirectory).collect("eth0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid"})
    void invalidOperationalStateFailsExplicitly(String state) throws IOException {
        writeCounter("rx_errors", "0");
        writeCounter("tx_errors", "0");
        Files.writeString(networkDirectory.resolve("eth0").resolve("operstate"), state);
        assertThrows(IOException.class,
                () -> new NetworkErrorCollector(networkDirectory).collect("eth0"));
    }

    @Test
    void serializesCollectedCounterAsNumberInReport() throws IOException {
        writeCounter("rx_errors", "9\n");
        writeCounter("tx_errors", "2\n");
        var counters = new NetworkErrorCollector(networkDirectory).collect("eth0");
        var report = new AgentApplication.Report("test-report", "test-host",
                "2026-11-23T10:00:00Z", "eth0", counters.receiveErrors(), counters.sendErrors(),
                counters.mtu(), counters.operationalState(), new KernelLogCollector.Result(true, "", null));
        var mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(report));
        assertEquals(9, json.size());
        assertEquals("test-host", json.get("host").asText());
        assertEquals("eth0", json.get("interfaceName").asText());
        assertTrue(json.get("receiveErrors").isIntegralNumber());
        assertEquals(9, json.get("receiveErrors").asLong());
        assertTrue(json.get("sendErrors").isIntegralNumber());
        assertEquals(2, json.get("sendErrors").asLong());
        assertTrue(json.get("mtu").isIntegralNumber());
        assertEquals(1500, json.get("mtu").asInt());
        assertEquals("up", json.get("operationalState").asText());
    }

    private void writeCounter(String name, String value) throws IOException {
        Path directory = Files.createDirectories(networkDirectory.resolve("eth0").resolve("statistics"));
        Files.writeString(directory.resolve(name), value);
        Path interfaceDirectory = directory.getParent();
        if (!Files.exists(interfaceDirectory.resolve("mtu"))) {
            Files.writeString(interfaceDirectory.resolve("mtu"), "1500\n");
        }
        if (!Files.exists(interfaceDirectory.resolve("operstate"))) {
            Files.writeString(interfaceDirectory.resolve("operstate"), "up\n");
        }
    }
}
