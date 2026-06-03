package com.yuki.enterprise_private_rag_qa.config;

import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import com.yuki.enterprise_private_rag_qa.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrgTagAuthorizationFilterTest {

    private OrgTagAuthorizationFilter filter;
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        filter = new OrgTagAuthorizationFilter();
        jwtUtils = mock(JwtUtils.class);
        ReflectionTestUtils.setField(filter, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(filter, "fileUploadRepository", mock(FileUploadRepository.class));
    }

    @Test
    void categoryApisReceiveUserContextFromToken() throws Exception {
        when(jwtUtils.extractUserIdFromToken("token")).thenReturn("1");
        when(jwtUtils.extractRoleFromToken("token")).thenReturn("SUPER_ADMIN");
        when(jwtUtils.extractOrgTagsFromToken("token")).thenReturn("DEFAULT");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/knowledge-categories");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals("1", request.getAttribute("userId"));
        assertEquals("SUPER_ADMIN", request.getAttribute("role"));
        assertEquals("DEFAULT", request.getAttribute("orgTags"));
        verify(chain).doFilter(any(), any());
    }

    @Test
    void recleanApiReceivesUserContextFromToken() throws Exception {
        when(jwtUtils.extractUserIdFromToken("token")).thenReturn("1");
        when(jwtUtils.extractRoleFromToken("token")).thenReturn("DEPT_LEAD");
        when(jwtUtils.extractOrgTagsFromToken("token")).thenReturn("FIN");

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/documents/abc123abc123abc123abc123abc123ab/reclean"
        );
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals("1", request.getAttribute("userId"));
        assertEquals("DEPT_LEAD", request.getAttribute("role"));
        assertEquals("FIN", request.getAttribute("orgTags"));
        verify(chain).doFilter(any(), any());
    }

    @Test
    void dataCleaningApisReceiveUserContextFromToken() throws Exception {
        when(jwtUtils.extractUserIdFromToken("token")).thenReturn("1");
        when(jwtUtils.extractRoleFromToken("token")).thenReturn("SUPER_ADMIN");
        when(jwtUtils.extractOrgTagsFromToken("token")).thenReturn("default,admin");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/data-cleaning/preview");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals("1", request.getAttribute("userId"));
        assertEquals("SUPER_ADMIN", request.getAttribute("role"));
        assertEquals("default,admin", request.getAttribute("orgTags"));
        verify(chain).doFilter(any(), any());
    }
}
