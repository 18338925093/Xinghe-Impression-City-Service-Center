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
    KEY idx_customer_message_session (session_id, created_at),
    -- 阶段 A：会话删除时级联清理消息，避免客服历史产生孤儿记录。
    CONSTRAINT fk_customer_message_session FOREIGN KEY (session_id)
        REFERENCES customer_service_session(session_id) ON DELETE CASCADE
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

CREATE TABLE IF NOT EXISTS trade_order (
    order_no VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_trade_order_user_created (user_id, created_at),
    KEY idx_trade_order_status (status, created_at)
);

CREATE TABLE IF NOT EXISTS trade_order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    line_amount DECIMAL(12,2) NOT NULL,
    KEY idx_trade_order_item_order (order_no),
    -- 阶段 A：订单删除时级联清理明细，保持订单主表与明细一致。
    CONSTRAINT fk_trade_order_item_order FOREIGN KEY (order_no)
        REFERENCES trade_order(order_no) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS logistics_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    company VARCHAR(64),
    tracking_no VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_logistics_order (order_no),
    -- 阶段 A：物流记录必须关联真实订单，删除订单时同步清理物流数据。
    CONSTRAINT fk_logistics_order FOREIGN KEY (order_no)
        REFERENCES trade_order(order_no) ON DELETE CASCADE
);
