package com.example.network.reports;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

	private final TelemetryReportRepository reports;

	public ReportController(TelemetryReportRepository reports) {
		this.reports = reports;
	}

	@GetMapping
	public List<TelemetryReportRepository.StoredReport> getReports() {
		return reports.findLatest();
	}

	@PostMapping
	public Receipt receive(@Valid @RequestBody TelemetryReport report) {
		Instant receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		reports.save(report, receivedAt);
		return new Receipt(report.reportId(), report.host(), receivedAt,
				"Report received and stored; not analyzed.");
	}

	@ExceptionHandler(DuplicateKeyException.class)
	public ResponseEntity<Map<String, String>> duplicateReport(DuplicateKeyException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("error", "A report with this reportId already exists."));
	}

	public record Receipt(UUID reportId, String host, Instant receivedAt, String message) {
	}
}
