package com.rapidmart.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService service;
    public PasswordResetController(PasswordResetService service) { this.service = service; }
    public record EmailRequest(@NotBlank @Email @Size(max=254) String email) {}
    public record ResetRequest(@NotBlank @Email @Size(max=254) String email,
            @NotBlank @Pattern(regexp="[0-9]{6}") String otp,
            @NotBlank @Size(min=8, max=72) String password,
            @NotBlank String confirmPassword) {
        @Override public String toString() { return "ResetRequest[redacted]"; }
    }
    @PostMapping("/request")
    public Map<String, String> request(@Valid @RequestBody EmailRequest request) {
        service.request(request.email());
        return Map.of("message", "If an eligible customer account exists, a code has been emailed. Check your inbox and spam folder.");
    }
    @PostMapping("/confirm")
    public Map<String, String> confirm(@Valid @RequestBody ResetRequest request) {
        service.confirm(request);
        return Map.of("message", "Password changed. Sign in with your new password.");
    }
}
