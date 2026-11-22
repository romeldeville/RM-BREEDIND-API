package com.rmbreeding.api.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<IdentityDtos.RegisterResponse> register(
            @Valid @RequestBody IdentityDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody IdentityDtos.VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/otp/request")
    public ResponseEntity<Void> requestOtp(@Valid @RequestBody IdentityDtos.OtpRequest request) {
        authService.requestOtp(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/otp/verify")
    public IdentityDtos.TokenPair verifyOtp(@Valid @RequestBody IdentityDtos.OtpVerifyRequest request) {
        return authService.verifyOtp(request);
    }

    @PostMapping("/login")
    public IdentityDtos.TokenPair login(@Valid @RequestBody IdentityDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/mfa/verify")
    public IdentityDtos.TokenPair verifyMfa(@Valid @RequestBody IdentityDtos.MfaVerifyRequest request) {
        return authService.verifyMfa(request);
    }

    @PostMapping("/refresh")
    public IdentityDtos.TokenPair refresh(@Valid @RequestBody IdentityDtos.RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logout(principal.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public IdentityDtos.MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.me(principal.userId());
    }

    @PutMapping("/me")
    public IdentityDtos.MeResponse updateProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody IdentityDtos.UpdateProfileRequest request) {
        return authService.updateProfile(principal.userId(), request);
    }

    @PostMapping("/password/change")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody IdentityDtos.ChangePasswordRequest request) {
        authService.changePassword(principal.userId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody IdentityDtos.PasswordForgotRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody IdentityDtos.PasswordResetRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/setup")
    public IdentityDtos.MfaSetupResponse setupMfa(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.setupMfa(principal.userId());
    }

    @PostMapping("/mfa/enable")
    public ResponseEntity<Void> enableMfa(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody IdentityDtos.MfaEnableRequest request) {
        authService.enableMfa(principal.userId(), request.code());
        return ResponseEntity.noContent().build();
    }
}
