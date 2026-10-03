package com.medislot.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** Wires the real security stack (filter chain, JWT, handlers) into @WebMvcTest slices. */
@TestConfiguration
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class, ProblemDetailWriter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
public class SecurityTestConfig {
}
