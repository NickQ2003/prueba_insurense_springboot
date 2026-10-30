CREATE TABLE parties (
    id UUID PRIMARY KEY,
    party_type VARCHAR(20) NOT NULL CHECK (party_type IN ('PERSON', 'ORGANIZATION')),
    display_name VARCHAR(200) NOT NULL CHECK (length(btrim(display_name)) > 0),
    document_reference VARCHAR(100) NOT NULL UNIQUE CHECK (length(btrim(document_reference)) > 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE policies (
    id UUID PRIMARY KEY,
    policy_number VARCHAR(64) NOT NULL UNIQUE CHECK (length(btrim(policy_number)) > 0),
    policy_type VARCHAR(16) NOT NULL CHECK (policy_type IN ('INDIVIDUAL', 'COLLECTIVE')),
    holder_party_id UUID NOT NULL REFERENCES parties (id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'RENOVATED', 'CANCELLED')),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX policies_holder_party_id_idx ON policies (holder_party_id);
CREATE INDEX policies_status_policy_number_idx ON policies (status, policy_number);

CREATE TABLE risks (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES policies (id),
    risk_number VARCHAR(64) NOT NULL CHECK (length(btrim(risk_number)) > 0),
    property_address VARCHAR(500) NOT NULL CHECK (length(btrim(property_address)) > 0),
    tenant_party_id UUID NOT NULL REFERENCES parties (id),
    landlord_party_id UUID NOT NULL REFERENCES parties (id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT risks_policy_risk_number_unique UNIQUE (policy_id, risk_number)
);

CREATE INDEX risks_policy_status_idx ON risks (policy_id, status);
CREATE INDEX risks_tenant_party_id_idx ON risks (tenant_party_id);
CREATE INDEX risks_landlord_party_id_idx ON risks (landlord_party_id);

CREATE TABLE ipc_rates (
    id UUID PRIMARY KEY,
    reference_period VARCHAR(32) NOT NULL UNIQUE CHECK (length(btrim(reference_period)) > 0),
    effective_on DATE NOT NULL,
    rate NUMERIC(8, 5) NOT NULL CHECK (rate >= 0),
    source VARCHAR(200) NOT NULL CHECK (length(btrim(source)) > 0),
    is_fictional BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ipc_rates_effective_on_idx ON ipc_rates (effective_on DESC, id);

CREATE TABLE policy_periods (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES policies (id),
    sequence INTEGER NOT NULL CHECK (sequence > 0),
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL CHECK (ends_on > starts_on),
    duration_months SMALLINT NOT NULL CHECK (duration_months > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('CURRENT', 'SCHEDULED', 'EXPIRED')),
    origin_period_id UUID REFERENCES policy_periods (id),
    ipc_rate_id UUID REFERENCES ipc_rates (id),
    ipc_rate_snapshot NUMERIC(8, 5) CHECK (ipc_rate_snapshot >= 0),
    calculation_version VARCHAR(40) NOT NULL CHECK (length(btrim(calculation_version)) > 0),
    core_reference VARCHAR(100),
    CONSTRAINT policy_periods_policy_sequence_unique UNIQUE (policy_id, sequence),
    CONSTRAINT policy_periods_policy_start_unique UNIQUE (policy_id, starts_on)
);

CREATE INDEX policy_periods_policy_status_idx ON policy_periods (policy_id, status);

CREATE TABLE period_risk_values (
    id UUID PRIMARY KEY,
    period_id UUID NOT NULL REFERENCES policy_periods (id),
    risk_id UUID NOT NULL REFERENCES risks (id),
    monthly_rent NUMERIC(14, 2) NOT NULL CHECK (monthly_rent >= 0),
    premium NUMERIC(14, 2) NOT NULL CHECK (premium >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'COP' CHECK (currency = 'COP'),
    CONSTRAINT period_risk_values_period_risk_unique UNIQUE (period_id, risk_id)
);

CREATE INDEX period_risk_values_risk_id_idx ON period_risk_values (risk_id);

CREATE TABLE operations (
    id UUID PRIMARY KEY,
    consumer_scope VARCHAR(100) NOT NULL CHECK (length(btrim(consumer_scope)) > 0),
    operation_type VARCHAR(40) NOT NULL CHECK (length(btrim(operation_type)) > 0),
    idempotency_key VARCHAR(128) NOT NULL CHECK (length(btrim(idempotency_key)) > 0),
    request_hash CHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    policy_id UUID REFERENCES policies (id),
    target_period_id UUID REFERENCES policy_periods (id),
    status VARCHAR(24) NOT NULL CHECK (
        status IN (
            'PENDING',
            'WAITING_INPUT',
            'PROCESSING',
            'RECONCILING',
            'SUCCEEDED',
            'REJECTED',
            'NEEDS_REVIEW'
        )
    ),
    requested_payload JSONB NOT NULL,
    result_payload JSONB,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    lease_owner VARCHAR(100),
    lease_until TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT operations_idempotency_unique UNIQUE (consumer_scope, operation_type, idempotency_key)
);

CREATE INDEX operations_policy_created_idx ON operations (policy_id, created_at DESC);
CREATE INDEX operations_ready_idx ON operations (status, next_attempt_at)
    WHERE status IN ('PENDING', 'PROCESSING', 'RECONCILING');

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL REFERENCES operations (id),
    event_type VARCHAR(64) NOT NULL CHECK (length(btrim(event_type)) > 0),
    payload JSONB NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(100),
    lease_until TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT outbox_events_operation_type_unique UNIQUE (operation_id, event_type)
);

CREATE INDEX outbox_events_ready_idx ON outbox_events (available_at, created_at)
    WHERE published_at IS NULL;

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor VARCHAR(128) NOT NULL CHECK (length(btrim(actor)) > 0),
    action VARCHAR(64) NOT NULL CHECK (length(btrim(action)) > 0),
    entity_type VARCHAR(64) NOT NULL CHECK (length(btrim(entity_type)) > 0),
    entity_id UUID NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    correlation_id UUID,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX audit_events_entity_idx ON audit_events (entity_type, entity_id, created_at DESC);

CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY,
    outbox_event_id UUID NOT NULL REFERENCES outbox_events (id),
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('EMAIL', 'SMS')),
    recipient VARCHAR(254) NOT NULL CHECK (length(btrim(recipient)) > 0),
    delivered_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT notification_deliveries_dedup_unique UNIQUE (outbox_event_id, channel, recipient)
);
