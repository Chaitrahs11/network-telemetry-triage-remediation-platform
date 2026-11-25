package com.example.network.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class AgentApplication {
    public static void main(String[] args) {
        if (args.length < 3 || args.length > 4
                || !"--url".equals(args[1])
                || (args.length == 4 && !"--watch".equals(args[3]))) {
            System.err.println("Usage: java -jar agent-0.0.1-SNAPSHOT.jar <interface-name> "
                    + "--url <http://backend-host:8080/api/reports> [--watch]");
            System.exit(2);
            return;
        }
        boolean watch = args.length == 4;
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")) {
            System.err.println("Live collection requires Linux. On Windows, run 'mvn test' to test using sample files.");
            System.exit(2);
            return;
        }
        try {
            var networkCollector = new NetworkErrorCollector(Path.of("/sys/class/net"));
            var kernelLogCollector = new KernelLogCollector();
            var sender = new TelemetryReportSender(args[2]);
            String host = InetAddress.getLocalHost().getHostName();
            do {
                var counters = networkCollector.collect(args[0]);
                var kernelLogs = kernelLogCollector.collect();
                if (!kernelLogs.available()) {
                    System.err.println("Kernel logs unavailable: " + kernelLogs.error());
                }
                Report report = new Report(UUID.randomUUID().toString(),
                        host, Instant.now().toString(), args[0],
                        counters.receiveErrors(), counters.sendErrors(),
                        counters.mtu(), counters.operationalState(), kernelLogs);
                String receipt = sender.send(report);
                System.out.println("Backend acknowledged report " + report.reportId() + ": " + receipt);
                if (watch) {
                    Thread.sleep(5_000);
                }
            } while (watch);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Collection interrupted.");
            System.exit(1);
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Collection failed: " + exception.getMessage());
            System.exit(1);
        }
    }

    public record Report(String reportId, String host, String timestamp,
                         String interfaceName, long receiveErrors, long sendErrors,
                         int mtu, String operationalState, KernelLogCollector.Result kernelLogs) {}
}
