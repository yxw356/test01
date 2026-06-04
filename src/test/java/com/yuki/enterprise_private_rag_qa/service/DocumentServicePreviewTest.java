package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.DocumentVector;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.repository.DocumentVectorRepository;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServicePreviewTest {

    private DocumentService documentService;
    private DocumentVectorRepository documentVectorRepository;
    private FileUploadRepository fileUploadRepository;
    private MinioClient minioClient;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService();
        documentVectorRepository = mock(DocumentVectorRepository.class);
        fileUploadRepository = mock(FileUploadRepository.class);
        minioClient = mock(MinioClient.class);

        ReflectionTestUtils.setField(documentService, "documentVectorRepository", documentVectorRepository);
        ReflectionTestUtils.setField(documentService, "fileUploadRepository", fileUploadRepository);
        ReflectionTestUtils.setField(documentService, "minioClient", minioClient);
    }

    @Test
    void previewUsesParsedTextChunksForBinaryOfficeFiles() throws Exception {
        DocumentVector first = vector(2, "第二段解析文本");
        DocumentVector second = vector(1, "第一段解析文本");
        when(documentVectorRepository.findByFileMd5("pdf-md5")).thenReturn(List.of(first, second));

        String preview = documentService.getFilePreviewContent("pdf-md5", "制度.pdf");

        assertTrue(preview.startsWith("第一段解析文本"));
        assertTrue(preview.contains("第二段解析文本"));
        assertFalse(preview.contains("%PDF"));
        verify(minioClient, never()).getObject(any(GetObjectArgs.class));
    }

    @Test
    void previewDoesNotReadBinaryOfficeFilesAsPlainTextWhenNoParsedTextExists() throws Exception {
        FileUpload upload = new FileUpload();
        upload.setFileName("制度.docx");
        upload.setTotalSize(2048L);
        upload.setCreatedAt(LocalDateTime.of(2026, 6, 3, 10, 0));
        when(documentVectorRepository.findByFileMd5("docx-md5")).thenReturn(List.of());
        when(fileUploadRepository.findByFileMd5("docx-md5")).thenReturn(Optional.of(upload));

        String preview = documentService.getFilePreviewContent("docx-md5", "制度.docx");

        assertTrue(preview.contains("此文件类型不支持预览，请下载后查看。"));
        verify(minioClient, never()).getObject(any(GetObjectArgs.class));
    }

    private DocumentVector vector(int chunkId, String content) {
        DocumentVector vector = new DocumentVector();
        vector.setChunkId(chunkId);
        vector.setTextContent(content);
        return vector;
    }
}
