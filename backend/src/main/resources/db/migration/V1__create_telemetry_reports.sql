CREATE TABLE telemetry_reports (
    report_id UUID PRIMARY KEY,
    host VARCHAR(255) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    interface_name VARCHAR(15) NOT NULL,
    receive_errors BIGINT NOT NULL CHECK (receive_errors >= 0),
    send_errors BIGINT NOT NULL CHECK (send_errors >= 0),
    mtu INTEGER NOT NULL CHECK (mtu > 0),
    operational_state VARCHAR(15) NOT NULL CHECK (
        operational_state IN ('unknown', 'notpresent', 'down', 'lowerlayerdown', 'testing', 'dormant', 'up')
    ),
    kernel_logs_available BOOLEAN NOT NULL,
    kernel_logs_tail TEXT NOT NULL,
    kernel_logs_error TEXT
);
