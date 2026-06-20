CREATE TABLE users (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户唯一标识',
                       username VARCHAR(255) NOT NULL UNIQUE COMMENT '用户名，唯一',
                       password VARCHAR(255) NOT NULL COMMENT '加密后的密码',
                       role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER' COMMENT '用户角色',
                       org_tags VARCHAR(255) DEFAULT NULL COMMENT '用户所属组织标签，多个用逗号分隔',
                       primary_org VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '用户主组织标签',
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                       INDEX idx_username (username) COMMENT '用户名索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
CREATE TABLE organization_tags (
                                   tag_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY COMMENT '标签唯一标识',
                                   name VARCHAR(100) NOT NULL COMMENT '标签名称',
                                   description TEXT COMMENT '描述',
                                   parent_tag VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '父标签ID',
                                   created_by BIGINT NOT NULL COMMENT '创建者ID',
                                   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   FOREIGN KEY (parent_tag) REFERENCES organization_tags(tag_id) ON DELETE SET NULL,
                                   FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织标签表';


CREATE TABLE file_upload (
                             id           BIGINT           NOT NULL AUTO_INCREMENT COMMENT '主键',
                             file_md5     VARCHAR(32)      NOT NULL COMMENT '文件 MD5',
                             file_name    VARCHAR(255)     NOT NULL COMMENT '文件名称',
                             total_size   BIGINT           NOT NULL COMMENT '文件大小',
                             status       TINYINT          NOT NULL DEFAULT 0 COMMENT '上传状态',
                             user_id      VARCHAR(64)      NOT NULL COMMENT '用户 ID',
                             org_tag      VARCHAR(50)      DEFAULT NULL COMMENT '组织标签',
                             is_public    BOOLEAN          NOT NULL DEFAULT FALSE COMMENT '是否公开',                             created_at   TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             merged_at    TIMESTAMP        NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '合并时间',
                             PRIMARY KEY (id),
                             UNIQUE KEY uk_md5_user (file_md5, user_id),
                             INDEX idx_user (user_id),
                             INDEX idx_org_tag (org_tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件上传记录';
CREATE TABLE chunk_info (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分块记录唯一标识',
                            file_md5 VARCHAR(32) NOT NULL COMMENT '关联的文件MD5值',
                            chunk_index INT NOT NULL COMMENT '分块序号',
                            chunk_md5 VARCHAR(32) NOT NULL COMMENT '分块的MD5值',
                            storage_path VARCHAR(255) NOT NULL COMMENT '分块在存储系统中的路径'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件分块信息表';

CREATE TABLE document_vectors (
                                  vector_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '向量记录唯一标识',
                                  file_md5 VARCHAR(32) NOT NULL COMMENT '关联的文件MD5值',
                                  chunk_id INT NOT NULL COMMENT '文本分块序号',
                                  parent_id VARCHAR(64) COMMENT '父块ID',
                                  text_content TEXT COMMENT '文本内容',
                                  parent_text_content LONGTEXT COMMENT '父块完整文本',
                                  model_version VARCHAR(32) COMMENT '向量模型版本',
                                  user_id VARCHAR(64) NOT NULL COMMENT '上传用户ID',
                                  org_tag VARCHAR(50) COMMENT '文件所属组织标签',
                                  is_public BOOLEAN NOT NULL DEFAULT FALSE COMMENT '文件是否公开'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档向量存储表';

CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审计记录ID',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    username VARCHAR(128) DEFAULT NULL COMMENT '用户名',
    action VARCHAR(32) NOT NULL COMMENT '操作类型',
    resource_type VARCHAR(64) DEFAULT NULL COMMENT '资源类型',
    resource_id VARCHAR(255) DEFAULT NULL COMMENT '资源标识',
    detail TEXT COMMENT '操作详情',
    result VARCHAR(32) DEFAULT NULL COMMENT '结果 SUCCESS/FAILURE',
    client_ip VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
    duration_ms BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

CREATE TABLE conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '对话记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    question TEXT NOT NULL COMMENT '用户提问',
    answer TEXT NOT NULL COMMENT '系统回答',
    session_id VARCHAR(64) DEFAULT NULL COMMENT 'Redis会话ID',
    retrieval_citations TEXT DEFAULT NULL COMMENT '结构化检索引用JSON',
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '对话时间',
    INDEX idx_user_id (user_id),
    INDEX idx_timestamp (timestamp),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话历史表';

ALTER TABLE file_upload
    ADD COLUMN index_status TINYINT NOT NULL DEFAULT 2 COMMENT '0待索引 1索引中 2已索引 3失败' AFTER status,
    ADD COLUMN index_error VARCHAR(512) DEFAULT NULL COMMENT '索引失败原因' AFTER index_status;

CREATE TABLE IF NOT EXISTS knowledge_space (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    space_id VARCHAR(80) NOT NULL UNIQUE,
    type VARCHAR(24) NOT NULL,
    name VARCHAR(120) NOT NULL,
    department_id VARCHAR(80) DEFAULT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识空间';

CREATE TABLE IF NOT EXISTS user_knowledge_space_layout (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL UNIQUE,
    space_order TEXT NOT NULL,
    collapsed_spaces TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户知识空间布局';

CREATE TABLE IF NOT EXISTS knowledge_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    parent_id BIGINT DEFAULT NULL,
    knowledge_scope VARCHAR(24) NOT NULL,
    department_id VARCHAR(50) DEFAULT NULL,
    description VARCHAR(255) DEFAULT NULL,
    sort_order INT NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识分类';

CREATE TABLE IF NOT EXISTS cleaning_rule_set (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    knowledge_scope VARCHAR(24) NOT NULL,
    department_id VARCHAR(50) DEFAULT NULL,
    description VARCHAR(255) DEFAULT NULL,
    normalize_line_breaks BOOLEAN NOT NULL DEFAULT TRUE,
    normalize_unicode_spaces BOOLEAN NOT NULL DEFAULT TRUE,
    normalize_whitespace BOOLEAN NOT NULL DEFAULT TRUE,
    trim_lines BOOLEAN NOT NULL DEFAULT TRUE,
    collapse_blank_lines BOOLEAN NOT NULL DEFAULT TRUE,
    remove_duplicate_lines BOOLEAN NOT NULL DEFAULT TRUE,
    min_duplicate_line_length INT NOT NULL DEFAULT 8,
    drop_line_patterns LONGTEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清洗规则集';

ALTER TABLE file_upload
    ADD COLUMN knowledge_scope VARCHAR(24) NOT NULL DEFAULT 'DEPARTMENT' COMMENT 'PUBLIC/DEPARTMENT/PRIVATE' AFTER org_tag,
    ADD COLUMN department_id VARCHAR(80) DEFAULT NULL AFTER knowledge_scope,
    ADD COLUMN space_id VARCHAR(80) DEFAULT NULL AFTER department_id,
    ADD COLUMN category_id BIGINT DEFAULT NULL AFTER space_id,
    ADD COLUMN category_name VARCHAR(120) DEFAULT NULL AFTER category_id,
    ADD COLUMN cleaning_rule_set_id BIGINT DEFAULT NULL AFTER category_name,
    ADD COLUMN cleaning_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER cleaning_rule_set_id,
    ADD COLUMN original_chars INT NOT NULL DEFAULT 0 AFTER cleaning_status,
    ADD COLUMN cleaned_chars INT NOT NULL DEFAULT 0 AFTER original_chars,
    ADD COLUMN removed_chars INT NOT NULL DEFAULT 0 AFTER cleaned_chars,
    ADD COLUMN duplicate_lines_removed INT NOT NULL DEFAULT 0 AFTER removed_chars,
    ADD COLUMN cleaning_quality_status VARCHAR(24) NOT NULL DEFAULT 'OK' AFTER duplicate_lines_removed,
    ADD COLUMN cleaning_quality_issues VARCHAR(512) DEFAULT NULL AFTER cleaning_quality_status,
    ADD COLUMN cleaning_quality_score DOUBLE NOT NULL DEFAULT 1.0 AFTER cleaning_quality_issues;
