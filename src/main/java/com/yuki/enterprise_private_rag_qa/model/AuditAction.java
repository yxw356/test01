package com.yuki.enterprise_private_rag_qa.model;

/**
 * 审计操作类型
 */
public enum AuditAction {
    UPLOAD,
    DELETE,
    SEARCH,
    PREVIEW,
    DOWNLOAD,
    CHAT,
    INDEX_SUCCESS,
    INDEX_FAILURE,
    LOGIN
}
