package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeCategory;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.KnowledgeCategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeCategoryService {

    private final KnowledgeCategoryRepository categoryRepository;
    private final DocumentPermissionService permissionService;

    public KnowledgeCategoryService(KnowledgeCategoryRepository categoryRepository,
                                    DocumentPermissionService permissionService) {
        this.categoryRepository = categoryRepository;
        this.permissionService = permissionService;
    }

    public record CategoryCreateRequest(
            String name,
            Long parentId,
            FileUpload.KnowledgeScope knowledgeScope,
            String departmentId,
            String description,
            Integer sortOrder
    ) {
    }

    public KnowledgeCategory createCategory(String userId, CategoryCreateRequest request) {
        User user = permissionService.requireUser(userId);
        FileUpload.KnowledgeScope scope = request.knowledgeScope() == null
                ? FileUpload.KnowledgeScope.DEPARTMENT
                : request.knowledgeScope();
        String departmentId = request.departmentId();

        if (scope == FileUpload.KnowledgeScope.PUBLIC) {
            if (!permissionService.canUploadPublic(user)) {
                throw new CustomException("没有创建公共分类的权限", HttpStatus.FORBIDDEN);
            }
            departmentId = null;
        } else if (scope == FileUpload.KnowledgeScope.DEPARTMENT) {
            if (isBlank(departmentId)) {
                departmentId = user.getPrimaryOrg() != null ? user.getPrimaryOrg() : firstOrgTag(user);
            }
            if (!permissionService.canUploadDepartment(user, departmentId)) {
                throw new CustomException("没有创建该部门分类的权限", HttpStatus.FORBIDDEN);
            }
        } else {
            throw new CustomException("暂不支持创建私人知识分类", HttpStatus.BAD_REQUEST);
        }

        if (isBlank(request.name())) {
            throw new CustomException("分类名称不能为空", HttpStatus.BAD_REQUEST);
        }

        KnowledgeCategory category = new KnowledgeCategory();
        category.setName(request.name().trim());
        category.setParentId(request.parentId());
        category.setKnowledgeScope(scope);
        category.setDepartmentId(departmentId);
        category.setDescription(request.description());
        category.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        category.setEnabled(true);
        category.setCreatedBy(userId);
        return categoryRepository.save(category);
    }

    public List<KnowledgeCategory> listVisibleCategories(String userId) {
        User user = permissionService.requireUser(userId);
        return categoryRepository.findByEnabledTrueOrderBySortOrderAscNameAsc().stream()
                .filter(category -> canViewCategory(user, category))
                .toList();
    }

    public KnowledgeCategory resolveUploadCategory(Long categoryId, FileUpload.KnowledgeScope scope,
                                                   String departmentId, User user) {
        if (categoryId == null) {
            return null;
        }
        KnowledgeCategory category = categoryRepository.findByIdAndEnabledTrue(categoryId)
                .orElseThrow(() -> new CustomException("知识分类不存在或已停用", HttpStatus.BAD_REQUEST));
        if (!canViewCategory(user, category)) {
            throw new CustomException("没有使用该知识分类的权限", HttpStatus.FORBIDDEN);
        }
        if (category.getKnowledgeScope() != scope) {
            throw new CustomException("知识分类与知识类型不匹配", HttpStatus.BAD_REQUEST);
        }
        if (scope == FileUpload.KnowledgeScope.DEPARTMENT
                && !same(category.getDepartmentId(), departmentId)) {
            throw new CustomException("知识分类与所属部门不匹配", HttpStatus.BAD_REQUEST);
        }
        return category;
    }

    private boolean canViewCategory(User user, KnowledgeCategory category) {
        if (!category.isEnabled()) {
            return false;
        }
        if (category.getKnowledgeScope() == FileUpload.KnowledgeScope.PUBLIC) {
            return true;
        }
        if (category.getKnowledgeScope() == FileUpload.KnowledgeScope.DEPARTMENT) {
            FileUpload file = new FileUpload();
            file.setKnowledgeScope(FileUpload.KnowledgeScope.DEPARTMENT);
            file.setDepartmentId(category.getDepartmentId());
            file.setOrgTag(category.getDepartmentId());
            return permissionService.canView(user, file);
        }
        return false;
    }

    private static boolean same(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstOrgTag(User user) {
        String orgTags = user.getOrgTags();
        if (orgTags == null || orgTags.isBlank()) {
            return null;
        }
        return orgTags.split(",")[0].trim();
    }
}
