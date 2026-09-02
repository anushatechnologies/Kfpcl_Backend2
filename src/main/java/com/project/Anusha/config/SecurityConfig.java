package com.project.Anusha.config;

import com.project.Anusha.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:}")
    private String extraAllowedOrigins;

    @Bean
    public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowUrlEncodedPercent(true);
        firewall.setAllowUrlEncodedPeriod(true);
        firewall.setAllowSemicolon(true);
        firewall.setAllowBackSlash(true);
        firewall.setAllowUrlEncodedDoubleSlash(true); // This fixes the // issue
        return firewall;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter authenticationJwtTokenFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Explicitly list all allowed origins (admin panel, customer app, local dev),
        // then append deployment-specific origins from CORS_ALLOWED_ORIGINS.
        List<String> allowedOrigins = new ArrayList<>(Arrays.asList(
            "https://admin.anushatechnologies.com",
            "http://admin.anushatechnologies.com",
            "https://app.anushatechnologies.com",
            "http://app.anushatechnologies.com",
            "https://anushatechnologies.com",
            "http://anushatechnologies.com",
            "https://www.anushatechnologies.com",
            "http://www.anushatechnologies.com",
            "https://kfpclexports.com",
            "http://kfpclexports.com",
            "https://www.kfpclexports.com",
            "http://www.kfpclexports.com",
            "https://anushabazaar.com",
            "http://anushabazaar.com",
            "https://www.anushabazaar.com",
            "http://www.anushabazaar.com",
            "https://app.anushabazaar.com",
            "http://app.anushabazaar.com",
            "https://kfpclexports.com",
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:5174",
            "http://localhost:8080",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:5174"
        ));
        if (extraAllowedOrigins != null && !extraAllowedOrigins.isBlank()) {
            Arrays.stream(extraAllowedOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isBlank())
                    .forEach(allowedOrigins::add);
        }
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "http://127.0.0.1:*"
        ));

        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
        ));
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Requested-With",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
        ));
        // Allow Authorization header to be read by the browser
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Disposition"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // Standalone CorsFilter bean — runs before Spring Security filters,
    // so even 401/403 error responses get the correct CORS headers.
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        FilterRegistrationBean<CorsFilter> bean =
                new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource()));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            System.err.println("❌ Auth Error: " + authException.getMessage() + " | Path: " + request.getRequestURI());
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Error: Unauthorized");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            System.err.println("❌ Access Denied: " + accessDeniedException.getMessage() + " | Path: " + request.getRequestURI());
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Error: Forbidden");
                        })
                )
                .authorizeHttpRequests(auth -> auth

                        // ── Always allow CORS pre-flight requests ────────────────────────
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()

                        // ── Public Auth endpoints (Customer App & Admin) ────────────────
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/webhooks/**").permitAll()

                        // ── Common public endpoints ──────────────────────────────────────
                        .requestMatchers("/api/save-token").permitAll()
                        .requestMatchers("/api/health", "/api/health/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/settings", "/api/settings/").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/app/version").permitAll()

                        // ── DeliveryApp public endpoints ──────────────────────────────────
                        // /api/delivery/auth/** covers: check-phone, upload-profile-photo,
                        // signup, login, fare-rules (GET), fare/calculate (POST)
                        .requestMatchers("/api/delivery/auth/**").permitAll()

                        // ── Public Browsing (GET only) ────────────────────────────────────
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/products/**", "/api/products", "/api/products/", "/api/products/*/images").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/stores/**", "/api/stores", "/api/stores/").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/stores1/**", "/api/stores1").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/categories/**", "/api/categories", "/api/categories/").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/subcategories/**", "/api/subcategories", "/api/subcategories/").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/customer/banners", "/api/customer/banners/").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/customer/products/*/ratings", "/api/customer/products/*/ratings/").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/website/banners", "/api/website/banners/").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/checkout-settings", "/api/checkout-settings/").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/marquee", "/api/marquee/").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/coupons/active", "/api/coupons/active/").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/free-item-offers/active", "/api/free-item-offers/active/").permitAll()

                        // ── Public Marketing & Reference ─────────────────────────────────
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/policies/**", "/api/policies").permitAll()
                        .requestMatchers("/api/documents/validation-rules").permitAll()
                        .requestMatchers("/error").permitAll()

                        // ── Super admin only – admin user management ─────────────────────
                        .requestMatchers("/api/super-admin/**").hasRole("SUPER_ADMIN")

                        // ── Admin panel – ADMIN or SUPER_ADMIN ───────────────────────────
                        .requestMatchers("/api/delivery-admin/**", "/api/admin-panel/**", "/api/admin/**", "/api/user-logs/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // ── Customer profile – any authenticated customer or admin ─────────
                        .requestMatchers("/api/customer/**", "/api/coupons/**").authenticated()

                        // ── Delivery person secured endpoints ─────────────────────────────
                        .requestMatchers("/api/delivery-app/**", "/api/delivery-person/**", "/api/documents/**", "/api/delivery-orders/**", "/api/payouts/**").authenticated()

                        // ── Orders & Tracking ─────────────────────────────────────────────
                        .requestMatchers("/api/orders/**", "/api/tracking/**", "/api/cart/**", "/api/addresses/**", "/api/wallet/**").authenticated()
                        .requestMatchers("/api/customer/products/wishlist/**", "/api/customer/products/rating/**").authenticated()

                        // ── Payment (Cashfree Payments) ──────────────────────────────────
                        .requestMatchers("/api/payment/webhook").permitAll()   // Cashfree server webhook — no JWT
                        .requestMatchers("/api/payment/initiate").authenticated()
                        .requestMatchers("/api/payment/verify").authenticated()
                        .requestMatchers("/api/payment/refund/**", "/api/payment/refund-status/**").authenticated()

                        // ── Any other request must be authenticated ────────────────────────


                        .anyRequest().authenticated())
                .addFilterBefore(authenticationJwtTokenFilter(),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
