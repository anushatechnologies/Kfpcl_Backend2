package com.project.Anusha.security;

import com.project.Anusha.service.CustomUserDetailsService;
import com.project.Anusha.model.User;
import com.project.Anusha.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * JWT authentication filter.
 *
 * Intercepts every request, extracts the Bearer token from the Authorization header,
 * validates it, then loads the user from {@link CustomUserDetailsService} by the
 * subject (= phone number or e-mail) and sets it in the Security Context.
 *
 * Works for all user types – admins (email subject), customers (phone subject),
 * and delivery persons (phone subject) – because {@link CustomUserDetailsService}
 * handles all three via users_main.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);

            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                // Subject is either phone number or e-mail
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (isJwtSessionAllowed(username, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }

    private boolean isJwtSessionAllowed(String username, UserDetails userDetails) {
        if (!userDetails.isEnabled()
                || !userDetails.isAccountNonExpired()
                || !userDetails.isAccountNonLocked()
                || !userDetails.isCredentialsNonExpired()) {
            return false;
        }

        Optional<User> adminUser = userRepository.findByEmail(username);
        return adminUser.map(user -> user.isEnabled() && !user.isAdminAccessExpired()).orElse(true);
    }
}
