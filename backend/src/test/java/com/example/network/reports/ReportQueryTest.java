package com.example.network.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportQueryTest {

	private static final String REPORT = """
			{
			  "reportId": "ff78e898-f80a-4df4-8f6a-785ffa01b6f0",
			  "host": "demo-linux",
			  "timestamp": "2026-11-23T13:40:33Z",
			  "interfaceName": "eth0",
			  "receiveErrors": 0,
			  "sendErrors": 2,
			  "mtu": 1500,
			  "operationalState": "up",
			  "kernelLogs": {"available": true, "tail": "example log", "error": null}
			}
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void returnsEmptyListWhenNoReportsExist() throws Exception {
		mvc.perform(get("/api/reports"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void savesPostedReportAndReturnsItFromGet() throws Exception {
		mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(REPORT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reportId").value("ff78e898-f80a-4df4-8f6a-785ffa01b6f0"))
				.andExpect(jsonPath("$.message").value("Report received and stored; not analyzed."));

		mvc.perform(get("/api/reports"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].reportId").value("ff78e898-f80a-4df4-8f6a-785ffa01b6f0"))
				.andExpect(jsonPath("$[0].host").value("demo-linux"))
				.andExpect(jsonPath("$[0].timestamp").value("2026-11-23T13:40:33Z"))
				.andExpect(jsonPath("$[0].receivedAt").isNotEmpty())
				.andExpect(jsonPath("$[0].interfaceName").value("eth0"))
				.andExpect(jsonPath("$[0].receiveErrors").value(0))
				.andExpect(jsonPath("$[0].sendErrors").value(2))
				.andExpect(jsonPath("$[0].mtu").value(1500))
				.andExpect(jsonPath("$[0].operationalState").value("up"))
				.andExpect(jsonPath("$[0].kernelLogs.available").value(true))
				.andExpect(jsonPath("$[0].kernelLogs.tail").value("example log"))
				.andExpect(jsonPath("$[0].kernelLogs.error").isEmpty());
	}

	@Test
	void rejectsDuplicateReportIdWithoutOverwriting() throws Exception {
		mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(REPORT))
				.andExpect(status().isOk());

		mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
				.content(REPORT.replace("demo-linux", "different-host")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("A report with this reportId already exists."));

		mvc.perform(get("/api/reports"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].host").value("demo-linux"));
	}

	@Test
	void returnsStoredReportFields() throws Exception {
		UUID id = UUID.fromString("ff78e898-f80a-4df4-8f6a-785ffa01b6f0");
		insertReport(id, OffsetDateTime.parse("2026-11-25T10:00:00Z"));

		mvc.perform(get("/api/reports"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].reportId").value(id.toString()))
				.andExpect(jsonPath("$[0].host").value("demo-linux"))
				.andExpect(jsonPath("$[0].timestamp").value("2026-11-23T13:40:33Z"))
				.andExpect(jsonPath("$[0].receivedAt").value("2026-11-25T10:00:00Z"))
				.andExpect(jsonPath("$[0].interfaceName").value("eth0"))
				.andExpect(jsonPath("$[0].receiveErrors").value(0))
				.andExpect(jsonPath("$[0].sendErrors").value(2))
				.andExpect(jsonPath("$[0].mtu").value(1500))
				.andExpect(jsonPath("$[0].operationalState").value("up"))
				.andExpect(jsonPath("$[0].kernelLogs.available").value(false))
				.andExpect(jsonPath("$[0].kernelLogs.tail").value(""))
				.andExpect(jsonPath("$[0].kernelLogs.error").value("Permission denied"));
	}

	@Test
	void returnsAtMost100NewestReports() throws Exception {
		OffsetDateTime firstReceived = OffsetDateTime.parse("2026-11-25T10:00:00Z");
		for (int i = 0; i <= 100; i++) {
			insertReport(new UUID(0, i), firstReceived.plusSeconds(i));
		}

		mvc.perform(get("/api/reports"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(100))
				.andExpect(jsonPath("$[0].reportId").value(new UUID(0, 100).toString()))
				.andExpect(jsonPath("$[99].reportId").value(new UUID(0, 1).toString()));
	}

	private void insertReport(UUID id, OffsetDateTime receivedAt) {
		jdbc.update("""
				INSERT INTO telemetry_reports
				    (report_id, host, observed_at, received_at, interface_name, receive_errors,
				     send_errors, mtu, operational_state, kernel_logs_available, kernel_logs_tail,
				     kernel_logs_error)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				id, "demo-linux", OffsetDateTime.parse("2026-11-23T13:40:33Z"),
				receivedAt, "eth0", 0, 2, 1500, "up", false, "", "Permission denied");
	}
}
