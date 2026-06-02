package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class DocumentPermissionService {

    private final UserRepository userRepository;
    private final OrgTagCacheService orgTagCacheService;

    public DocumentPermissionService(UserRepository userRepository, OrgTagCacheService orgTagCacheService) {
        this.userRepository = userRepository;
        this.orgTagCacheService = orgTagCacheService;
    }

    public User requireUser(String userIdOrUsername) {
        if (userIdOrUsername == null || userIdOrUsername.isBlank()) {
            throw new CustomException("User not found", HttpStatus.UNAUTHORIZED);
        }
        if (userIdOrUsername.chars().allMatch(Character::isDigit)) {
            return userRepository.findById(Long.parseLong(userIdOrUsername))
                    .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        }
        return userRepository.findByUsername(userIdOrUsername)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
    }

    public FileUpload.KnowledgeScope resolveScope(String knowledgeScope, boolean isPublic) {
        if (knowledgeScope != null && !knowledgeScope.isBlank()) {
            return FileUpload.KnowledgeScope.valueOf(knowledgeScope.trim().toUpperCase(Locale.ROOT));
        }
        return isPublic ? FileUpload.KnowledgeScope.PUBLIC : FileUpload.KnowledgeScope.DEPARTMENT;
    }

    public void assertCanUpload(User user, FileUpload.KnowledgeScope scope, String departmentId) {
        if (scope == FileUpload.KnowledgeScope.PUBLIC) {
            if (!canUploadPublic(user)) {
                throw new CustomException("Only super administrators can upload public knowledge", HttpStatus.FORBIDDEN);
            }
            return;
        }
        if (scope == FileUpload.KnowledgeScope.DEPARTMENT && !canUploadDepartment(user, departmentId)) {
            throw new CustomException("Only department leads can upload department knowledge", HttpStatus.FORBIDDEN);
        }
    }

    public boolean canUploadPublic(User user) {
        return user != null && (user.isSuperAdmin() || user.getRole() == User.Role.KNOWLEDGE_ADMIN);
    }

    public boolean canUploadDepartment(User user, String departmentId) {
        if (user == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        return user.isDepartmentLead() && userHasDepartment(user, departmentId);
    }

    public boolean canView(User user, FileUpload document) {
        if (user == null || document == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        if (Objects.equals(String.valueOf(user.getId()), document.getUserId())) {
            return true;
        }
        FileUpload.KnowledgeScope scope = effectiveScope(document);
        if (scope == FileUpload.KnowledgeScope.PUBLIC || document.isPublic()) {
            return true;
        }
        if (scope == FileUpload.KnowledgeScope.PRIVATE) {
            return false;
        }
        return userHasDepartment(user, effectiveDepartmentId(document));
    }

    public boolean canManage(User user, FileUpload document) {
        if (user == null || document == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        if (Objects.equals(String.valueOf(user.getId()), document.getUserId())) {
            return true;
        }
        return effectiveScope(document) == FileUpload.KnowledgeScope.DEPARTMENT
                && user.isDepartmentLead()
                && userHasDepartment(user, effectiveDepartmentId(document));
    }

    public List<String> effectiveDepartmentIds(User user) {
        if (user == null) {
            return Collections.emptyList();
        }
        Set<String> departments = new LinkedHashSet<>();
        try {
            List<String> cached = orgTagCacheService.getUserEffectiveOrgTags(user.getUsername());
            if (cached != null && !cached.isEmpty()) {
                cached.stream()
                        .filter(tag -> tag != null && !tag.isBlank())
                        .forEach(departments::add);
            }
        } catch (Exception ignored) {
            // Fallback to user.orgTags below.
        }
        if (user.getOrgTags() != null && !user.getOrgTags().isBlank()) {
            Arrays.stream(user.getOrgTags().split(","))
                    .map(String::trim)
                    .filter(tag -> !tag.isEmpty())
                    .forEach(departments::add);
        }
        return List.copyOf(departments);
    }

    public FileUpload.KnowledgeScope effectiveScope(FileUpload document) {
        if (document.getKnowledgeScope() != null) {
            return document.getKnowledgeScope();
        }
        return document.isPublic() ? FileUpload.KnowledgeScope.PUBLIC : FileUpload.KnowledgeScope.DEPARTMENT;
    }

    public String effectiveDepartmentId(FileUpload document) {
        if (document.getDepartmentId() != null && !document.getDepartmentId().isBlank()) {
            return document.getDepartmentId();
        }
        return document.getOrgTag();
    }

    private boolean userHasDepartment(User user, String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            return false;
        }
        return effectiveDepartmentIds(user).stream().anyMatch(tag -> sameDepartment(tag, departmentId));
    }

    private boolean sameDepartment(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right) || left.equalsIgnoreCase(right);
    }
}
