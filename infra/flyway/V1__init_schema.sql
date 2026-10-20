-- RailFlow Initial Relational Schema

CREATE TABLE IF NOT EXISTS station (
    station_code VARCHAR(10) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    state VARCHAR(64) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    is_reservation_point BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS train (
    train_number VARCHAR(10) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    train_type VARCHAR(32) NOT NULL,
    operating_zone VARCHAR(16) NOT NULL,
    has_pantry BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS coach_layout (
    layout_id VARCHAR(32) PRIMARY KEY,
    class_code VARCHAR(4) NOT NULL,
    berth_count INT NOT NULL,
    berth_map JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS booking (
    booking_id UUID NOT NULL,
    pnr CHAR(10) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    train_number VARCHAR(10) NOT NULL,
    journey_date DATE NOT NULL,
    from_station VARCHAR(10) NOT NULL,
    to_station VARCHAR(10) NOT NULL,
    class_code VARCHAR(4) NOT NULL,
    quota_code VARCHAR(4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_fare NUMERIC(10,2) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    partition_offset BIGINT NOT NULL,
    PRIMARY KEY (journey_date, train_number, booking_id),
    UNIQUE (pnr, journey_date, train_number),
    UNIQUE (idempotency_key, journey_date, train_number)
) PARTITION BY RANGE (journey_date);

CREATE TABLE IF NOT EXISTS passenger (
    passenger_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    name VARCHAR(128) NOT NULL,
    age INT NOT NULL,
    gender CHAR(1) NOT NULL,
    berth_preference VARCHAR(8),
    allocated_coach VARCHAR(8),
    allocated_berth INT,
    allocated_berth_type VARCHAR(8),
    current_status VARCHAR(16) NOT NULL,
    booking_status VARCHAR(16) NOT NULL,
    id_proof_hash VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS payment (
    payment_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    psp_ref VARCHAR(128),
    amount NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE NOT NULL,
    captured_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    refund_amount NUMERIC(10,2)
);

CREATE TABLE IF NOT EXISTS outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed ON outbox(id) WHERE published_at IS NULL;

CREATE TABLE IF NOT EXISTS pnr_block (
    shard_id INT PRIMARY KEY,
    block_start BIGINT NOT NULL,
    block_end BIGINT NOT NULL,
    next_value BIGINT NOT NULL
);

INSERT INTO pnr_block (shard_id, block_start, block_end, next_value)
VALUES (1, 10000000, 20000000, 10000000)
ON CONFLICT DO NOTHING;
