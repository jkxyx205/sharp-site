package com.rick.site.admin.config;

import com.rick.site.admin.security.AdminContextFilter;
import com.rick.site.tenant.service.TenantService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

/**
 * Spring Security 配置(TASK-0902)。
 *
 * <p>后台 {@code /admin/**}(除登录页)需认证;前台路由与静态资源公开。
 * 按租户域名表单登录({@code /admin/login}),BCrypt 校验密码。
 * AdminContextFilter 注册进 security 链,认证后用管理员 tenantId 覆盖 TenantContext。
 *
 * @author Rick.Xu
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TenantService tenantService) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/login").permitAll()
                .requestMatchers("/admin/**").authenticated()
                .requestMatchers("/preview/**").authenticated()
                .anyRequest().permitAll())
            // alwaysUse=false(默认):登录成功后优先回到登录前被拦截的受保护页面
            // (SavedRequestAwareAuthenticationSuccessHandler 从 RequestCache 取回原 URL);
            // 直接访问登录页则回退到 /admin/。
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .defaultSuccessUrl("/admin/", true)
                .failureUrl("/admin/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login?logout")
                .permitAll())
            .addFilterBefore(new AdminContextFilter(tenantService), UsernamePasswordAuthenticationFilter.class)
            // 联系表单 POST(/contact 与 /{locale}/contact)豁免 CSRF:
            // 正式站为静态 HTML(Nginx),无 session → Thymeleaf 无法注入 CSRF token;
            // 该端点为公开未认证表单,改由服务端校验 + 限频/Honeypot 防刷(TODO §1/§10)。
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                    new RegexRequestMatcher("^/(?:[a-z]{2}-[a-z]{2}/)?contact$", "POST")));
        return http.build();
    }
}
