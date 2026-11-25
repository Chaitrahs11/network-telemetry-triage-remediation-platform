# Java agent: first collection step

This is a plain Java application, separate from the Spring Boot backend.
It reads one Linux network interface's cumulative receive- and send-error counters,
MTU setting, operational state, and the last 20 kernel log lines, and
collects a report and sends it to the Spring Boot backend using HTTP POST.
By default, it sends one report and exits. Optional `--watch` mode repeats
collection after a five-second pause following each report. It does not use
Kafka, diagnose faults, or apply fixes yet.

## Build and test on Windows

From this directory, with Maven and a JDK 17 or newer:

```powershell
mvn verify
```

Tests create temporary sample files with the same layout as Linux counter
files. They verify the reader and report serialization, not a real VM or NIC.
Windows cannot provide the Linux counter file; live collection on Windows
fails explicitly instead of printing fake telemetry.

## Collect on your own Linux machine

Copy `target/agent-0.0.1-SNAPSHOT.jar` to a Linux machine with Java 17 or newer.
Find its interface name using `ip link show` (it may not be `eth0`).
Start the backend first and make it reachable from Linux. Then send one report:

```bash
java -jar agent-0.0.1-SNAPSHOT.jar eth0 --url http://<backend-host>:8080/api/reports
```

For continuous collection until Ctrl+C:

```bash
java -jar agent-0.0.1-SNAPSHOT.jar eth0 --url http://<backend-host>:8080/api/reports --watch
```

Replace `<backend-host>` with the backend machine's hostname or IP address.
For WSL2 NAT networking, the Windows host address is commonly the default
gateway shown by `ip route show default`; verify connectivity and Windows
firewall access rather than assuming `localhost` reaches Windows.
The backend returns an acknowledgement for each accepted report. A failed
connection or non-2xx response is reported as an error and stops the agent;
there is no retry or local buffering in this milestone.

Each iteration reads fresh values and creates a new report ID and timestamp.
The interval is collection time plus a five-second pause, not an exact
five-second schedule. Kernel log lines can repeat between reports; they are
the latest available lines, not necessarily new messages.
An interface read failure stops the program with an explicit error and nonzero
exit code, rather than producing a misleading report.

Example report sent to the backend (these are illustrative values, not collected data):

```json
{"reportId":"unique-id","host":"vm-1","timestamp":"2026-11-23T10:00:00Z","interfaceName":"eth0","receiveErrors":9,"sendErrors":2,"mtu":1500,"operationalState":"up","kernelLogs":{"available":true,"tail":"[1203.4] example kernel message","error":null}}
```

`receiveErrors` comes from `statistics/rx_errors` (errin).
`sendErrors` comes from `statistics/tx_errors` (errout).
Both are cumulative totals, not counts during the last interval.
`mtu` comes from the interface's `mtu` file; it is a packet-size setting,
not a packet capture. Different MTUs alone do not prove a network fault.
`operationalState` comes from `operstate` and preserves Linux values such as
`up`, `down`, and `unknown`. An `up` state does not prove another host is reachable.
Zero only means this counter currently reports zero; it does not prove the
network is healthy. A failed or invalid read prints an error and exits nonzero.
Do not install this into Docker Desktop's internal Linux distribution.

`KernelLogCollector` runs `dmesg` using `ProcessBuilder`, with a five-second
timeout, and retains the last 20 output lines. It does not run a shell or
request elevated privileges. Some Linux systems restrict access to kernel logs.
Command failure, missing `dmesg`, or timeout produces `kernelLogs.available: false`,
an empty `tail`, and an explicit `error`; the agent also warns on standard error.
The interface measurements remain in the report. Successful empty output has
`available: true`, distinguishing no log lines from unavailable logs.
These are kernel-wide logs, not necessarily messages about the selected interface.
Tests use a small Java subprocess to simulate output and failures, without
accessing the host's real kernel logs.

The collection idea is inspired by the existing Python agent's Linux-file
fallback, but this implementation is written separately in Java. Missing data
is an error rather than a zero fallback. The backend and frontend are unchanged.

## Build a local agent image

From this directory, build the JAR and then the image:

```powershell
mvn clean package
docker build -t telemetry-agent:local .
```

The Dockerfile uses the same Java 25 base image as the backend, so Docker can
reuse a cached copy. It requires the already-built agent JAR and does not
publish any ports or push anything to a registry.

When run in Docker, the agent observes its container's network interface and
Docker's shared Linux kernel, not an independent VM. Kernel logs may be
unavailable because container permissions restrict `dmesg`; do not grant
privileged access just to make the demo work. The Docker network connection
to the backend is configured separately when running the containers.
