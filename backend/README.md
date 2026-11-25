# Telemetry receiving endpoint

This backend validates and stores JSON reports in PostgreSQL. Flyway creates the
reports table at startup. The backend does not diagnose problems or execute
fixes. The Java agent can send reports using HTTP; see the agent README for its
`--url` option.

`GET /api/reports` reads up to the 100 most recently received rows from
`telemetry_reports`, newest first, including kernel logs. It returns an empty
array when the table has no rows. Reports and kernel logs may contain sensitive
information; this API is for local use.

## Run and test

Use the JDK 25 configured for this generated Spring Boot project. Run tests
from this directory in Windows PowerShell:

```powershell
.\mvnw.cmd test
```

The context test uses an in-memory H2 database and executes the migration.
It does not prove that the migration works on PostgreSQL; verify that separately
with the actual PostgreSQL server.

## Database setup

### Docker Compose

The Compose setup starts a PostgreSQL container and the backend together. It
creates a **separate database** from an existing PostgreSQL server; it does not
copy or delete that server's data. PostgreSQL data stays in a Docker volume
when the containers stop or are removed.

Enter the database username and password in `.env` in this directory before
starting. The file is Git-ignored and contains plaintext credentials; do not
share or commit it. The PostgreSQL container creates the user and
`telemetry_triage` database on its first initialization. For values containing
`$`, use single quotes in `.env` to prevent Compose interpolation. Credentials
are also visible to users who can inspect the containers. Changing `.env` later
does not change credentials already initialized in the database volume.

From this directory, build the JAR and start both containers:

```powershell
.\mvnw.cmd clean package
docker compose up --build -d
```

The backend is available at `http://127.0.0.1:18080`. PostgreSQL is available
to pgAdmin running on Windows at `127.0.0.1:15432`, with the username and
password from `.env`. Port 15432 avoids the usual PostgreSQL port 5432 on the
host. These ports are bound to the local machine only. The first run may need
to download the PostgreSQL image.

To stop the containers while keeping the database volume:

```powershell
docker compose down
```

Do not use `docker compose down -v` if you want to keep the database data.

### Running the backend directly on Windows

Create the `telemetry_triage` database in PostgreSQL first. The default JDBC
URL is `jdbc:postgresql://localhost:5432/telemetry_triage` when the backend
runs directly on Windows. Put your PostgreSQL username and password in
`src/main/resources/application.local.properties`. The file is Git-ignored,
excluded from the built JAR, and loaded when the backend is started from this
directory. Do not share or commit it. It is plain text, so anyone with access to
the file can read it.
Java properties syntax applies: backslashes in values must be escaped as `\\`.

Then start the backend from this directory:

```powershell
.\mvnw.cmd spring-boot:run
```

Environment variables `TELEMETRY_DB_USERNAME` and `TELEMETRY_DB_PASSWORD`
remain an alternative when the local file is absent. Docker does not copy the
local properties file into the image; the Compose setup above supplies its own
database URL and credentials.

On startup Flyway applies `V1__create_telemetry_reports.sql` and records its
version in `flyway_schema_history`. The configured PostgreSQL user needs
permission to create tables in the target schema. Check the URL before
starting: migrations run against whichever database `TELEMETRY_DB_URL` names.
Connection or migration failures prevent the backend from starting.

In another PowerShell terminal, send an illustrative report:

```powershell
$report = @{
    reportId = [guid]::NewGuid().ToString()
    host = "demo-linux"
    timestamp = [DateTime]::UtcNow.ToString("o")
    interfaceName = "eth0"
    receiveErrors = 0
    sendErrors = 2
    mtu = 1500
    operationalState = "up"
    kernelLogs = @{ available = $true; tail = ""; error = $null }
} | ConvertTo-Json -Depth 3

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/reports" `
    -ContentType "application/json" -Body $report
```

Expect HTTP 200 with the matching report ID and host, a server receipt timestamp,
and a message confirming the report was stored. Reusing the same report ID
returns HTTP 409 without overwriting the existing report. If saving fails, the
backend does not return a successful acknowledgement.
Read the saved report with:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/reports"
```

Missing required fields, negative counters, or malformed timestamps produce
HTTP 400 using Spring MVC's standard validation/error handling.
The HTTP tests do not require Linux, Kafka, or PostgreSQL.
There is no authentication in this first local demo; do not expose it publicly.

## Build a local backend image

From this directory, build the executable JAR first:

```powershell
.\mvnw.cmd clean package
docker build -t telemetry-backend:local .
```

The Dockerfile copies `target/network-0.0.1-SNAPSHOT.jar` into a Java 25
runtime image. The first build may download that base image and use space in
Docker Desktop's configured disk location. The image stays local; these
commands do not push it to a registry. `EXPOSE 8080` documents the container
port; it does not publish the port to Windows.

Docker containers share Docker's Linux kernel and are not independent VMs.
