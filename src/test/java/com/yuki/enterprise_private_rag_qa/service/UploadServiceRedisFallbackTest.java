package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.ChunkInfo;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.repository.ChunkInfoRepository;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadServiceRedisFallbackTest {

    private UploadService uploadService;
    private List<ChunkInfo> chunks;
    private Optional<FileUpload> fileUpload;

    @BeforeEach
    void setUp() {
        uploadService = new UploadService();

        ReflectionTestUtils.setField(uploadService, "chunkInfoRepository", chunkInfoRepository());
        ReflectionTestUtils.setField(uploadService, "fileUploadRepository", fileUploadRepository());
        ReflectionTestUtils.setField(uploadService, "uploadRedisEnabled", false);
    }

    @Test
    void readsUploadedChunksFromDatabaseWhenRedisDisabled() {
        FileUpload file = new FileUpload();
        file.setFileMd5("file-md5");
        file.setUserId("1");
        file.setTotalSize(11L * 1024 * 1024);
        fileUpload = Optional.of(file);
        chunks = List.of(chunk(2), chunk(0), chunk(2), chunk(9));

        List<Integer> uploaded = uploadService.getUploadedChunks("file-md5", "1");

        assertEquals(List.of(0, 2), uploaded);
    }

    @Test
    void checksSingleChunkFromDatabaseWhenRedisDisabled() {
        chunks = List.of(chunk(0), chunk(2));

        assertTrue(uploadService.isChunkUploaded("file-md5", 2, "1"));
        assertFalse(uploadService.isChunkUploaded("file-md5", 1, "1"));
    }

    @Test
    void markingChunkIsNoopWhenRedisDisabled() {
        uploadService.markChunkUploaded("file-md5", 0, "1");
    }

    private ChunkInfo chunk(int index) {
        ChunkInfo chunk = new ChunkInfo();
        chunk.setFileMd5("file-md5");
        chunk.setChunkIndex(index);
        chunk.setChunkMd5("chunk-" + index);
        chunk.setStoragePath("chunks/file-md5/" + index);
        return chunk;
    }

    private ChunkInfoRepository chunkInfoRepository() {
        return (ChunkInfoRepository) Proxy.newProxyInstance(
                ChunkInfoRepository.class.getClassLoader(),
                new Class<?>[]{ChunkInfoRepository.class},
                (proxy, method, args) -> {
                    if ("findByFileMd5OrderByChunkIndexAsc".equals(method.getName())) {
                        return chunks == null ? List.of() : chunks;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private FileUploadRepository fileUploadRepository() {
        return (FileUploadRepository) Proxy.newProxyInstance(
                FileUploadRepository.class.getClassLoader(),
                new Class<?>[]{FileUploadRepository.class},
                (proxy, method, args) -> {
                    if ("findByFileMd5AndUserId".equals(method.getName())) {
                        return fileUpload == null ? Optional.empty() : fileUpload;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == long.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == double.class || type == float.class) {
            return 0.0;
        }
        if (type == Optional.class) {
            return Optional.empty();
        }
        if (type == List.class) {
            return List.of();
        }
        return null;
    }
}
