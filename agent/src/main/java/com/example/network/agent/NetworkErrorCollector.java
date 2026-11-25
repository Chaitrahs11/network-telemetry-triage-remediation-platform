package com.example.network.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NetworkErrorCollector {
    private final Path networkDirectory;

    public NetworkErrorCollector(Path networkDirectory) {
        this.networkDirectory = networkDirectory;
    }

    public Counters collect(String interfaceName) throws IOException {
        if (interfaceName == null || !interfaceName.matches("[a-zA-Z0-9_-][a-zA-Z0-9_.-]{0,14}")) {
            throw new IllegalArgumentException("Provide a Linux interface name, for example et  h0 or ens33.");
        }
        Path directory = networkDirectory.resolve(interfaceName);
        Path statistics = directory.resolve("statistics");
        long receiveErrors = readCounter(statistics.resolve("rx_errors"));
        long sendErrors = readCounter(statistics.resolve("tx_errors"));
        long mtu = readCounter(directory.resolve("mtu"));
        if (mtu == 0 || mtu > Integer.MAX_VALUE) {
            throw new IOException("Invalid positive MTU in " + directory.resolve("mtu"));
        }
        Path stateFile = directory.resolve("operstate");
        String state = Files.readString(stateFile).strip();
        if (!state.matches("unknown|notpresent|down|lowerlayerdown|testing|dormant|up")) {
            throw new IOException("Invalid interface operational state in " + stateFile);
        }
        return new Counters(receiveErrors, sendErrors, (int) mtu, state);
    }

    private long readCounter(Path counter) throws IOException {
        String value = Files.readString(counter).strip();
        try {
            long errors = Long.parseLong(value);
            if (errors < 0) {
                throw new NumberFormatException("Negative counter");
            }
            return errors;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid non-negative integer in " + counter, exception);
        }
    }

    public record Counters(long receiveErrors, long sendErrors, int mtu, String operationalState) {}
}
