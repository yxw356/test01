package com.yuki.enterprise_private_rag_qa.model;

/**
 * 文件索引状态（与 upload status 分离：上传完成 ≠ 已入库可检索）
 */
public final class FileIndexStatus {

    /** 已合并，等待 Kafka 消费 */
    public static final int PENDING = 0;
    /** 正在解析 / 向量化 */
    public static final int INDEXING = 1;
    /** 已写入 ES，可检索 */
    public static final int INDEXED = 2;
    /** 索引失败 */
    public static final int FAILED = 3;

    private FileIndexStatus() {
    }
}
