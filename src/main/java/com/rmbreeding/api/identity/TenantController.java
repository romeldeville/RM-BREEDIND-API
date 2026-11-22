package com.rmbreeding.api.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final AuthService authService;
    private final TenantService tenantService;
    private final MemberService memberService;
    private final InvitationService invitationService;
    private final QuotaService quotaService;

    public TenantController(AuthService authService, TenantService tenantService, MemberService memberService,
            InvitationService invitationService, QuotaService quotaService) {
        this.authService = authService;
        this.tenantService = tenantService;
        this.memberService = memberService;
        this.invitationService = invitationService;
        this.quotaService = quotaService;
    }

    @GetMapping
    public List<IdentityDtos.TenantResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return tenantService.listForUser(principal.userId());
    }

    @PostMapping
    public ResponseEntity<IdentityDtos.TenantResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody IdentityDtos.CreateTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantService.createForUser(principal.userId(), request));
    }

    @PostMapping("/{tenantId}/switch")
    public IdentityDtos.TokenPair switchTenant(@AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID tenantId) {
        return authService.changeTenant(principal.userId(), tenantId);
    }

    @GetMapping("/{tenantId}/quotas")
    public List<IdentityDtos.QuotaResponse> quotas(@PathVariable UUID tenantId) {
        FarmService.requireTenantAccess(tenantId);
        return quotaService.quotas(tenantId);
    }

    @GetMapping("/{tenantId}/members")
    public List<IdentityDtos.MemberResponse> members(@PathVariable UUID tenantId) {
        return memberService.listByTenant(tenantId);
    }

    @PatchMapping("/{tenantId}/members/{membershipId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','PLATFORM_ADMIN')")
    public IdentityDtos.MemberResponse updateMember(@PathVariable UUID tenantId,
            @PathVariable UUID membershipId, @Valid @RequestBody IdentityDtos.UpdateMemberRequest request) {
        return memberService.update(tenantId, membershipId, request);
    }

    @DeleteMapping("/{tenantId}/members/{membershipId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','PLATFORM_ADMIN')")
    public ResponseEntity<Void> removeMember(@PathVariable UUID tenantId, @PathVariable UUID membershipId) {
        memberService.remove(tenantId, membershipId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tenantId}/invitations")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','PLATFORM_ADMIN')")
    public ResponseEntity<IdentityDtos.InvitationResponse> invite(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID tenantId,
            @Valid @RequestBody IdentityDtos.InviteMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invitationService.invite(tenantId, request, principal.userId()));
    }

    @GetMapping("/{tenantId}/invitations")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','PLATFORM_ADMIN')")
    public List<IdentityDtos.InvitationResponse> invitations(@PathVariable UUID tenantId) {
        return invitationService.list(tenantId);
    }

    @GetMapping("/{tenantId}/export")
    @PreAuthorize("hasAnyRole('OWNER','PLATFORM_ADMIN')")
    public java.util.Map<String, Object> export(@PathVariable UUID tenantId) {
        FarmService.requireTenantAccess(tenantId);
        return tenantService.exportTenant(tenantId);
    }

    @PostMapping("/{tenantId}/deletion-request")
    @PreAuthorize("hasAnyRole('OWNER','PLATFORM_ADMIN')")
    public IdentityDtos.TenantResponse requestDeletion(@PathVariable UUID tenantId) {
        FarmService.requireTenantAccess(tenantId);
        return tenantService.requestDeletion(tenantId);
    }

    @PostMapping("/{tenantId}/restore")
    @PreAuthorize("hasAnyRole('OWNER','PLATFORM_ADMIN')")
    public IdentityDtos.TenantResponse restoreDeletion(@PathVariable UUID tenantId) {
        FarmService.requireTenantAccess(tenantId);
        return tenantService.restoreDeletion(tenantId);
    }
}
