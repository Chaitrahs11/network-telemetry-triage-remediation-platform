package com.example.network.reports;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TelemetryReport(
		@NotNull UUID reportId,
		@NotBlank @Size(max = 255) String host,
		@NotNull Instant timestamp,
		@NotBlank @Pattern(regexp = "[a-zA-Z0-9_-][a-zA-Z0-9_.-]{0,14}") String interfaceName,
		@NotNull @PositiveOrZero Long receiveErrors,
		@NotNull @PositiveOrZero Long sendErrors,
		@NotNull @Positive Integer mtu,
		@NotNull @Pattern(regexp = "unknown|notpresent|down|lowerlayerdown|testing|dormant|up")
		String operationalState,
		@NotNull @Valid KernelLogs kernelLogs) {

	public record KernelLogs(
			@NotNull Boolean available,
			@NotNull @Size(max = 1_048_576) String tail,
			@Size(max = 8192) String error) {
	}
}
