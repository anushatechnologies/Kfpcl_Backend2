package com.project.Anusha.aspect;

import com.project.Anusha.service.UserLogService;
import jakarta.servlet.http.HttpServletRequest; 
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class AdminAuditAspect {

    private final UserLogService userLogService;

    @Around("execution(* com.project.Anusha.controller..*(..))")
    public Object logMutation(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getRequest();
        String method = request != null ? request.getMethod() : "";
        String path = request != null ? request.getRequestURI() : "";
        boolean adminPath = path != null && path.startsWith("/api/admin");
        boolean mutation = "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);

        Object result = joinPoint.proceed();

        if (mutation && adminPath) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String principal = authentication != null ? authentication.getName() : "anonymous";
            String role = authentication != null && authentication.getAuthorities() != null
                    ? authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("ADMIN")
                    : "ADMIN";
            String query = request != null ? request.getQueryString() : null;
            String fullPath = query != null && !query.isBlank() ? path + "?" + query : path;
            String ipAddress = request != null ? request.getRemoteAddr() : null;
            userLogService.log(
                    null,
                    "ADMIN",
                    method + " " + fullPath,
                    "controller=" + joinPoint.getSignature().getName() + ", actor=" + principal,
                    ipAddress
            );
        }

        return result;
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
