package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.FileIndexStatus;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeSpace;
import com.yuki.enterprise_private_rag_qa.model.OrganizationTag;
import com.yuki.enterprise_private_rag_qa.repository.KnowledgeSpaceRepository;
import com.yuki.enterprise_private_rag_qa.repository.OrganizationTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeSpaceServiceTest {

    private OrganizationTagRepository organizationTagRepository;
    private KnowledgeSpaceRepository knowledgeSpaceRepository;
    private KnowledgeSpaceService service;

    @BeforeEach
    void setUp() {
        organizationTagRepository = mock(OrganizationTagRepository.class);
        knowledgeSpaceRepository = mock(KnowledgeSpaceRepository.class);
        service = new KnowledgeSpaceService(organizationTagRepository, knowledgeSpaceRepository);
    }

    @Test
    void ensureSpaceForDocumentCreatesDepartmentSpaceWhenMissing() {
        when(organizationTagRepository.findByTagId("HR")).thenReturn(Optional.of(orgTag("HR", "人事部")));
        when(knowledgeSpaceRepository.existsBySpaceId("DEPARTMENT:HR")).thenReturn(false);

        String spaceId = service.ensureSpaceForDocument(document("h1", FileUpload.KnowledgeScope.DEPARTMENT, "HR", false,
                1, FileIndexStatus.INDEXED, FileUpload.CleaningQualityStatus.OK));

        assertEquals("DEPARTMENT:HR", spaceId);
        verify(knowledgeSpaceRepository).save(org.mockito.ArgumentMatchers.argThat(space ->
                "DEPARTMENT:HR".equals(space.getSpaceId())
                        && space.getType() == KnowledgeSpace.SpaceType.DEPARTMENT
                        && "人事部知识库".equals(space.getName())
                        && "HR".equals(space.getDepartmentId())
        ));
    }

    @Test
    void summarizesAccessibleDocumentsIntoPublicAndDepartmentSpaces() {
        when(organizationTagRepository.findByTagId("HR")).thenReturn(Optional.of(orgTag("HR", "人事部")));

        List<KnowledgeSpaceService.KnowledgeSpaceSummary> summaries = service.summarize(List.of(
                document("p1", FileUpload.KnowledgeScope.PUBLIC, null, true, 1, FileIndexStatus.INDEXED, FileUpload.CleaningQualityStatus.OK),
                document("h1", FileUpload.KnowledgeScope.DEPARTMENT, "HR", false, 1, FileIndexStatus.INDEXED, FileUpload.CleaningQualityStatus.WARNING),
                document("h2", FileUpload.KnowledgeScope.DEPARTMENT, "HR", false, 0, FileIndexStatus.PENDING, FileUpload.CleaningQualityStatus.OK)
        ));

        assertEquals(2, summaries.size());
        assertEquals("PUBLIC", summaries.get(0).id());
        assertEquals("公共知识库", summaries.get(0).title());
        assertEquals(1, summaries.get(0).fileCount());
        assertEquals(1, summaries.get(0).indexedCount());

        KnowledgeSpaceService.KnowledgeSpaceSummary hr = summaries.get(1);
        assertEquals("DEPARTMENT:HR", hr.id());
        assertEquals("人事部知识库", hr.title());
        assertEquals("HR", hr.departmentId());
        assertEquals(2, hr.fileCount());
        assertEquals(1, hr.indexedCount());
        assertEquals(1, hr.processingCount());
        assertEquals(1, hr.interruptedCount());
        assertEquals(1, hr.cleaningIssueCount());
    }

    private FileUpload document(String fileMd5, FileUpload.KnowledgeScope scope, String departmentId, boolean isPublic,
                                int status, int indexStatus, FileUpload.CleaningQualityStatus qualityStatus) {
        FileUpload document = new FileUpload();
        document.setFileMd5(fileMd5);
        document.setFileName(fileMd5 + ".md");
        document.setKnowledgeScope(scope);
        document.setDepartmentId(departmentId);
        document.setOrgTag(departmentId);
        document.setPublic(isPublic);
        document.setStatus(status);
        document.setIndexStatus(indexStatus);
        document.setCleaningStatus(FileUpload.CleaningStatus.CLEANED);
        document.setCleaningQualityStatus(qualityStatus);
        document.setCreatedAt(LocalDateTime.of(2026, 6, 4, 10, 0));
        document.setMergedAt(LocalDateTime.of(2026, 6, 4, 11, 0));
        return document;
    }

    private OrganizationTag orgTag(String tagId, String name) {
        OrganizationTag tag = new OrganizationTag();
        tag.setTagId(tagId);
        tag.setName(name);
        return tag;
    }
}
