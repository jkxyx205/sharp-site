package com.rick.site.tenant.filter;

import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.tenant.service.TenantDomainService;
import com.rick.site.tenant.service.TenantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-0105 验收测试:TenantFilter 每请求 Host → TenantContext,finally 清理。
 * 事务自动回滚。
 */
@SpringBootTest
@Transactional
class TenantFilterTest {

    @Autowired
    private TenantFilter tenantFilter;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantDomainService domainService;

    private Tenant tenantA;

    @BeforeEach
    void seed() {
        tenantA = tenantService.save(Tenant.builder()
                .code("filter-a").name("Filter A").themeId("modern").build());
        // 域名写入需上下文(tenant_id 由 TenantContext 注入);解析阶段无上下文
        TenantContext.set(tenantA);
        domainService.add("a.example.com", true);
        TenantContext.clear();
    }

    @AfterEach
    void ensureClean() {
        TenantContext.clear();
    }

    @Test
    void setsContextDuringChainAndClearsAfter() throws ServletException, IOException {
        AtomicReference<Optional<Tenant>> seenInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenInChain.set(TenantContext.get());

        tenantFilter.doFilter(request("a.example.com"), new MockHttpServletResponse(), chain);

        assertThat(seenInChain.get()).isPresent()
                .get().extracting(Tenant::getId).isEqualTo(tenantA.getId());
        // 请求结束后必须清理
        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    void unknownHostProceedsWithEmptyContext() throws ServletException, IOException {
        AtomicReference<Optional<Tenant>> seenInChain = new AtomicReference<>();
        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);
        FilterChain chain = (req, res) -> {
            seenInChain.set(TenantContext.get());
            chainInvoked.set(true);
        };

        tenantFilter.doFilter(request("unknown.example.com"), new MockHttpServletResponse(), chain);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(seenInChain.get()).isEmpty();
        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    void contextClearedEvenWhenChainThrows() {
        FilterChain throwingChain = (req, res) -> {
            assertThat(TenantContext.get()).isPresent();
            throw new ServletException("downstream failure");
        };

        assertThatThrownBy(() -> tenantFilter.doFilter(
                request("a.example.com"), new MockHttpServletResponse(), throwingChain))
                .isInstanceOf(ServletException.class);

        assertThat(TenantContext.get()).isEmpty();
    }

    private MockHttpServletRequest request(String host) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setServerName(host);
        return request;
    }
}
