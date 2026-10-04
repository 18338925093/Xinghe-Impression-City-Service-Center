CREATE TABLE IF NOT EXISTS customer_service_session (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_customer_session_user (user_id, updated_at)
);

CREATE TABLE IF NOT EXISTS customer_service_message (
    message_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    message_type VARCHAR(16) NOT NULL,
    intent VARCHAR(64),
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_customer_message_session (session_id, created_at)
);

CREATE TABLE IF NOT EXISTS product (
    id BIGINT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(12,2) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ON_SALE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_product_store (store_id),
    KEY idx_product_status_name (status, name)
);

CREATE TABLE IF NOT EXISTS logistics_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    company VARCHAR(64),
    tracking_no VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_logistics_order (order_no)
);
