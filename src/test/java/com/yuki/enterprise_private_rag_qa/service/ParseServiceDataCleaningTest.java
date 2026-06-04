package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.DocumentVector;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.repository.DocumentVectorRepository;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParseServiceDataCleaningTest {

    private ParseService parseService;
    private DocumentVectorRepository documentVectorRepository;
    private FileUploadRepository fileUploadRepository;

    @BeforeEach
    void setUp() {
        parseService = new ParseService();
        documentVectorRepository = mock(DocumentVectorRepository.class);
        fileUploadRepository = mock(FileUploadRepository.class);

        ReflectionTestUtils.setField(parseService, "documentVectorRepository", documentVectorRepository);
        ReflectionTestUtils.setField(parseService, "fileUploadRepository", fileUploadRepository);
        ReflectionTestUtils.setField(parseService, "dataCleaningService", new DataCleaningService());
        ReflectionTestUtils.setField(parseService, "chunkSize", 1000);
        ReflectionTestUtils.setField(parseService, "parentChunkSize", 3000);
        ReflectionTestUtils.setField(parseService, "childMinChunkSize", 1);
        ReflectionTestUtils.setField(parseService, "semanticSimilarityThreshold", 0.72);
        ReflectionTestUtils.setField(parseService, "bufferSize", 8192);
        ReflectionTestUtils.setField(parseService, "maxMemoryThreshold", 0.8);
    }

    @Test
    void parsePlainTextCleansTextBeforeSavingChunksAndUpdatesFileStats() {
        FileUpload upload = new FileUpload();
        upload.setFileMd5("clean-md5");
        upload.setStatus(0);
        when(fileUploadRepository.findByFileMd5("clean-md5")).thenReturn(Optional.of(upload));

        String rawText = """
                第一条   公司制度说明。
                第一条   公司制度说明。
                第二条\t员工应遵守流程。
                """;

        parseService.parsePlainTextAndSave("clean-md5", rawText, "1", "DEFAULT", true,
                FileUpload.KnowledgeScope.PUBLIC.name(), "DEFAULT", 3L, "制度规范");

        ArgumentCaptor<DocumentVector> vectorCaptor = ArgumentCaptor.forClass(DocumentVector.class);
        verify(documentVectorRepository).save(vectorCaptor.capture());
        String savedText = vectorCaptor.getValue().getTextContent();
        assertTrue(savedText.contains("第一条 公司制度说明。"));
        assertTrue(savedText.contains("第二条 员工应遵守流程。"));
        assertFalse(savedText.contains("第一条   公司制度说明。"));
        assertEquals(savedText.indexOf("第一条 公司制度说明。"), savedText.lastIndexOf("第一条 公司制度说明。"));

        ArgumentCaptor<FileUpload> uploadCaptor = ArgumentCaptor.forClass(FileUpload.class);
        verify(fileUploadRepository, times(2)).save(uploadCaptor.capture());
        FileUpload savedUpload = uploadCaptor.getAllValues().get(1);
        assertEquals(1, savedUpload.getStatus());
        assertEquals(FileUpload.CleaningStatus.CLEANED, savedUpload.getCleaningStatus());
        assertEquals(rawText.length(), savedUpload.getOriginalChars());
        assertTrue(savedUpload.getCleanedChars() > 0);
        assertTrue(savedUpload.getRemovedChars() > 0);
        assertEquals(1, savedUpload.getDuplicateLinesRemoved());
    }

    @Test
    void parsePlainTextUsesSelectedCleaningRuleSetBeforeSavingChunks() {
        CleaningRuleSetService ruleSetService = mock(CleaningRuleSetService.class);
        ReflectionTestUtils.setField(parseService, "cleaningRuleSetService", ruleSetService);

        FileUpload upload = new FileUpload();
        upload.setFileMd5("custom-rule-md5");
        upload.setCleaningRuleSetId(7L);
        when(fileUploadRepository.findByFileMd5("custom-rule-md5")).thenReturn(Optional.of(upload));
        when(ruleSetService.resolveRuleConfig(7L, "1")).thenReturn(new DataCleaningService.CleaningRuleConfig(
                true,
                true,
                true,
                true,
                true,
                false,
                8,
                java.util.List.of("^第\\s*\\d+\\s*页\\s*/\\s*共\\s*\\d+\\s*页$")
        ));

        parseService.parsePlainTextAndSave("custom-rule-md5",
                "制度正文\n第 1 页 / 共 3 页\n第一条 公司制度说明。",
                "1", "DEFAULT", true, FileUpload.KnowledgeScope.PUBLIC.name(), "DEFAULT", null, null, 7L);

        ArgumentCaptor<DocumentVector> vectorCaptor = ArgumentCaptor.forClass(DocumentVector.class);
        verify(documentVectorRepository).save(vectorCaptor.capture());
        String savedText = vectorCaptor.getValue().getTextContent();
        assertTrue(savedText.contains("制度正文"));
        assertTrue(savedText.contains("第一条 公司制度说明。"));
        assertFalse(savedText.contains("第 1 页 / 共 3 页"));
    }
}
