package com.jieb.community.config;

import com.jieb.community.util.CommunityConstant;
import com.jieb.community.util.CommunityUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter implements CommunityConstant {

    // 忽略对静态资源的访问
    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/resources/**");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // 授权，分为管理员、版主和普通用户
        http.authorizeRequests()
                .antMatchers(
                        "/user/setting",
                        "/user/upload",
                        "/discuss/add",
                        "/comment/add/**",
                        "/letter/**",
                        "/notice/**",
                        "/like",
                        "/follow",
                        "/unfollow"
                )
                .hasAnyAuthority(
                        AUTHORITY_ADMIN,
                        AUTHORITY_MODERATOR,
                        AUTHORITY_USER
                )
                .antMatchers(
                        "/discuss/top",
                        "/discuss/wonderful"
                )
                .hasAnyAuthority(
                        AUTHORITY_MODERATOR
                )
                .antMatchers(
                        "/discuss/delete",
                        "/data/**",
                        "/actuator/**"
                )
                .hasAnyAuthority(
                        AUTHORITY_ADMIN
                )
                .anyRequest().permitAll()
                // 异步请求需要配置 html 和 js，这里不配置 csrf
                .and().csrf().disable();

        // 权限不够时的处理
        http.exceptionHandling().authenticationEntryPoint(new AuthenticationEntryPoint() {
            // 没有登录
            @Override
            public void commence(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AuthenticationException e) throws IOException, ServletException {
                String xRequestedWith = httpServletRequest.getHeader("x-requested-with");
                // 异步
                if ("XMLHttpRequest".equals(xRequestedWith)) {
                    httpServletResponse.setContentType("application/plain;charset=utf-8");
                    PrintWriter writer = httpServletResponse.getWriter();
                    writer.write(CommunityUtil.getJSONString(403, "你还未登录！"));
                } else {
                    httpServletResponse.sendRedirect(httpServletRequest.getContextPath()+"/login");
                }
            }
        }).accessDeniedHandler(new AccessDeniedHandler() {
            // 权限不足
            @Override
            public void handle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AccessDeniedException e) throws IOException, ServletException {
                String xRequestedWith = httpServletRequest.getHeader("x-requested-with");
                // 异步
                if ("XMLHttpRequest".equals(xRequestedWith)) {
                    httpServletResponse.setContentType("application/plain;charset=utf-8");
                    PrintWriter writer = httpServletResponse.getWriter();
                    writer.write(CommunityUtil.getJSONString(403, "你没有访问此功能的权限！"));
                } else {
                    httpServletResponse.sendRedirect(httpServletRequest.getContextPath()+"/denied");
                }
            }
        });

        // Security 底层默认会拦截 /logout 请求，并退出。
        // 覆盖它默认的处理，执行自己的退出代码。
        http.logout().logoutUrl("/securitylogout");
    }
}
