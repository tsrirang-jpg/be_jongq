package com.example.jongq.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import com.example.jongq.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager manager;
    private final LoginAttempts attempts;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;
    public AuthController(AuthenticationManager manager, HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf, LoginAttempts attempts) {
        this.manager = manager; this.contexts = contexts; this.csrf = csrf; this.attempts = attempts;
    }
    public record LoginRequest(@NotBlank @Size(max = 80) String username, @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "LoginRequest[username=" + username + ", password=[REDACTED]]"; }
    }
    public record AdminSession(String username, String role, long expiresAt) {}
    @GetMapping("/csrf") Map<String, String> csrf(@io.swagger.v3.oas.annotations.Parameter(hidden = true) CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
    @PostMapping("/login") AdminSession login(@Valid @RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(HttpStatus.BAD_REQUEST, "รหัสผ่านยาวเกินกำหนด");
        var address = request.getRemoteAddr();
        attempts.check(address);
        Authentication authentication;
        try { authentication = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.username().trim(), input.password())); }
        catch (AuthenticationException error) { attempts.failure(address); throw error; }
        attempts.success(address);
        request.getSession(true);
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        csrf.saveToken(null, request, response);
        return session(authentication, request);
    }
    @GetMapping("/me") AdminSession me(Authentication authentication, HttpServletRequest request) { return session(authentication, request); }
    private AdminSession session(Authentication authentication, HttpServletRequest request) {
        return new AdminSession(authentication.getName(), "admin", System.currentTimeMillis() + request.getSession().getMaxInactiveInterval() * 1000L);
    }
}
