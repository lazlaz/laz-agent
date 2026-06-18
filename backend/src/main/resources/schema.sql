CREATE TABLE IF NOT EXISTS conversation (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    title VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_conv_session ON conversation(session_id);

CREATE TABLE IF NOT EXISTS message (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_msg_session ON message(session_id, created_at);

-- LangChain4j ChatMemoryStore table (replaces old message table for agent conversations)
CREATE TABLE IF NOT EXISTS agent_message (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    msg_type VARCHAR(20) NOT NULL,
    content TEXT,
    tool_requests_json TEXT,
    tool_name VARCHAR(100),
    tool_request_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_amsg_session ON agent_message(session_id, created_at);

CREATE TABLE IF NOT EXISTS product (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50),
    price DECIMAL(10, 2),
    stock INT DEFAULT 0,
    specs TEXT,
    description TEXT
);

CREATE TABLE IF NOT EXISTS customer_order (
    id VARCHAR(36) PRIMARY KEY,
    order_no VARCHAR(20) UNIQUE NOT NULL,
    customer_name VARCHAR(100),
    status VARCHAR(20),
    total_amount DECIMAL(10, 2),
    items TEXT,
    logistics VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
