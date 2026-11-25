package com.example.network;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
class NetworkApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void migrationCreatesReportColumns() {
		assertDoesNotThrow(() -> jdbcTemplate.queryForList("""
				SELECT report_id, host, observed_at, received_at, interface_name,
				       receive_errors, send_errors, mtu, operational_state,
				       kernel_logs_available, kernel_logs_tail, kernel_logs_error
				FROM telemetry_reports WHERE 1 = 0
				"""));
	}

}
