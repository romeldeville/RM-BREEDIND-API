package com.rmbreeding.api.identity;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/accept")
    public ResponseEntity<Void> accept(@Valid @RequestBody IdentityDtos.AcceptInvitationRequest request) {
        invitationService.accept(request);
        return ResponseEntity.ok().build();
    }
}
