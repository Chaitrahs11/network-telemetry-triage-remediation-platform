package com.example.network.reports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private TelemetryReportRepository reports;

	private static final String REPORT = """
			{
			  "reportId": "ff78e898-f80a-4df4-8f6a-785ffa01b6f0",
			  "host": "demo-linux",
			  "timestamp": "2026-11-23T13:40:33.610090721Z",
			  "interfaceName": "eth0",
			  "receiveErrors": 0,
			  "sendErrors": 2,
			  "mtu": 1500,
			  "operationalState": "up",
			  "kernelLogs": {"available": true, "tail": "", "error": null}
			}
			""";

	@Test
	void acknowledgesAgentShapedReport() throws Exception {
		mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(REPORT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reportId").value("ff78e898-f80a-4df4-8f6a-785ffa01b6f0"))
				.andExpect(jsonPath("$.host").value("demo-linux"))
				.andExpect(jsonPath("$.receivedAt").isNotEmpty())
				.andExpect(jsonPath("$.message").value("Report received and stored; not analyzed."));
		verify(reports).save(any(TelemetryReport.class), any());
	}

	@Test
	void acceptsExplicitlyUnavailableKernelLogs() throws Exception {
		String report = REPORT.replace(
				"\"available\": true, \"tail\": \"\", \"error\": null",
				"\"available\": false, \"tail\": \"\", \"error\": \"Permission denied\"");
		mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(report))
				.andExpect(status().isOk());
	}

	@Test
	void doesNotAcknowledgeWhenSavingFails() {
		doThrow(new DataAccessResourceFailureException("Database unavailable"))
				.when(reports).save(any(TelemetryReport.class), any());

		ServletException exception = assertThrows(ServletException.class,
				() -> mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(REPORT)));
		assertInstanceOf(DataAccessResourceFailureException.class, exception.getCause());
	}

	@Test
	void rejectsMissingCounterRatherThanDefaultingToZero() throws Exception {
		mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
				.content(REPORT.replace("\"receiveErrors\": 0,", "")))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(reports);
	}

	@Test
	void rejectsNegativeCounter() throws Exception {
		mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
				.content(REPORT.replace("\"sendErrors\": 2", "\"sendErrors\": -1")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void validatesNestedKernelLogFields() throws Exception {
		mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
				.content(REPORT.replace("\"available\": true", "\"available\": null")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsInvalidTimestamp() throws Exception {
		mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
				.content(REPORT.replace("2026-11-23T13:40:33.610090721Z", "not-a-time")))
				.andExpect(status().isBadRequest());
	}
}
