package com.yuki.enterprise_private_rag_qa.service;

import java.util.List;

/**
 * 文档访问权限策略（与 ES 检索 filter 保持一致，便于单测与二次校验）
 */
public final class DocumentAccessPolicy {

    private DocumentAccessPolicy() {
    }

    public static boolean canAccess(String docUserId, String docOrgTag, boolean docIsPublic,
                                    String requestUserDbId, List<String> userEffectiveTags) {
        if (requestUserDbId != null && requestUserDbId.equals(docUserId)) {
            return true;
        }
        if (docIsPublic) {
            return true;
        }
        if (docOrgTag == null || userEffectiveTags == null || userEffectiveTags.isEmpty()) {
            return false;
        }
        for (String tag : userEffectiveTags) {
            if (tag == null) {
                continue;
            }
            if (tag.equals(docOrgTag)) {
                return true;
            }
            if ("DEFAULT".equalsIgnoreCase(tag) && "default".equalsIgnoreCase(docOrgTag)) {
                return true;
            }
            if ("default".equalsIgnoreCase(tag) && "DEFAULT".equalsIgnoreCase(docOrgTag)) {
                return true;
            }
        }
        return false;
    }
}
