package com.example.network.reports;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TelemetryReportRepository {

	private final JdbcTemplate jdbc;

	public TelemetryReportRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void save(TelemetryReport report, Instant receivedAt) {
		jdbc.update("""
				INSERT INTO telemetry_reports
				    (report_id, host, observed_at, received_at, interface_name, receive_errors,
				     send_errors, mtu, operational_state, kernel_logs_available, kernel_logs_tail,
				     kernel_logs_error)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				report.reportId(), report.host(), report.timestamp().atOffset(ZoneOffset.UTC),
				receivedAt.atOffset(ZoneOffset.UTC), report.interfaceName(), report.receiveErrors(),
				report.sendErrors(), report.mtu(), report.operationalState(),
				report.kernelLogs().available(), report.kernelLogs().tail(), report.kernelLogs().error());
	}

	public List<StoredReport> findLatest() {
		return jdbc.query("""
				SELECT report_id, host, observed_at, received_at, interface_name,
				       receive_errors, send_errors, mtu, operational_state,
				       kernel_logs_available, kernel_logs_tail, kernel_logs_error
				FROM telemetry_reports
				ORDER BY received_at DESC, report_id DESC
				LIMIT 100
				""", (row, rowNumber) -> new StoredReport(
				row.getObject("report_id", UUID.class),
				row.getString("host"),
				row.getObject("observed_at", OffsetDateTime.class).toInstant(),
				row.getObject("received_at", OffsetDateTime.class).toInstant(),
				row.getString("interface_name"),
				row.getLong("receive_errors"),
				row.getLong("send_errors"),
				row.getInt("mtu"),
				row.getString("operational_state"),
				new TelemetryReport.KernelLogs(
						row.getBoolean("kernel_logs_available"),
						row.getString("kernel_logs_tail"),
						row.getString("kernel_logs_error"))));
	}

	public record StoredReport(
			UUID reportId,
			String host,
			Instant timestamp,
			Instant receivedAt,
			String interfaceName,
			long receiveErrors,
			long sendErrors,
			int mtu,
			String operationalState,
			TelemetryReport.KernelLogs kernelLogs) {
	}
}
