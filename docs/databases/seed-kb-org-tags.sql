-- 业务知识库组织标签（试点）
-- 用法: docker exec -i mysql mysql -uroot -pPaiSmart2025 PaiSmart < docs/databases/seed-kb-org-tags.sql
-- 说明: 重启后端时 OrgTagInitializer 也会自动创建同名标签（幂等）

SET NAMES utf8mb4;

INSERT INTO organization_tags (tag_id, name, description, parent_tag, created_by)
SELECT 'KB_POLICY', '制度流程库', '请假、报销、采购、合同审批等制度与流程', NULL, u.id
FROM users u WHERE u.username = 'admin' LIMIT 1
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

INSERT INTO organization_tags (tag_id, name, description, parent_tag, created_by)
SELECT 'KB_PROJECT', '项目知识库', '项目资料、会议纪要、交付文档', NULL, u.id
FROM users u WHERE u.username = 'admin' LIMIT 1
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

INSERT INTO organization_tags (tag_id, name, description, parent_tag, created_by)
SELECT 'KB_PRESALES', '产品售前库', '方案、报价、案例、FAQ', NULL, u.id
FROM users u WHERE u.username = 'admin' LIMIT 1
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

INSERT INTO organization_tags (tag_id, name, description, parent_tag, created_by)
SELECT 'KB_OPS', '客服运维库', '故障处理、标准话术、操作手册', NULL, u.id
FROM users u WHERE u.username = 'admin' LIMIT 1
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- 试点账号示例（按需执行）：仅制度库权限
-- UPDATE users SET org_tags = 'KB_POLICY' WHERE username = 'test';
