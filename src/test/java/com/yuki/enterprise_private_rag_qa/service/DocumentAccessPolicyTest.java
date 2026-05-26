package com.yuki.enterprise_private_rag_qa.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentAccessPolicyTest {

    @Test
    void ownerCanAccessPrivateDocument() {
        assertTrue(DocumentAccessPolicy.canAccess("1", "ORG_A", false, "1", List.of("ORG_B")));
    }

    @Test
    void publicDocumentAccessibleByAnyone() {
        assertTrue(DocumentAccessPolicy.canAccess("1", "ORG_A", true, "2", List.of("ORG_B")));
    }

    @Test
    void sameOrgCanAccessPrivateDocument() {
        assertTrue(DocumentAccessPolicy.canAccess("1", "ORG_A", false, "2", List.of("ORG_A")));
    }

    @Test
    void differentOrgCannotAccessPrivateDocument() {
        assertFalse(DocumentAccessPolicy.canAccess("1", "ORG_A", false, "2", List.of("ORG_B")));
    }

    @Test
    void defaultOrgTagCaseInsensitive() {
        assertTrue(DocumentAccessPolicy.canAccess("1", "default", false, "2", List.of("DEFAULT")));
        assertTrue(DocumentAccessPolicy.canAccess("1", "DEFAULT", false, "2", List.of("default")));
    }

    @Test
    void noOrgTagsCannotAccessOthersPrivateDocument() {
        assertFalse(DocumentAccessPolicy.canAccess("1", "ORG_A", false, "2", List.of()));
    }
}
