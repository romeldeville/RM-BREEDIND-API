package com.rmbreeding.api.identity;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * P16-03/04/05 - export des données personnelles, anonymisation et consultation des consentements.
 */
@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {

    private final PrivacyService privacyService;

    public PrivacyController(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    @GetMapping("/export")
    public PrivacyDtos.PersonalDataExport export() {
        return privacyService.exportCurrentUser();
    }

    @GetMapping("/consents")
    public List<IdentityDtos.ConsentResponse> consents() {
        return privacyService.consents();
    }

    @PostMapping("/anonymize")
    public ResponseEntity<Void> anonymize(@Valid @RequestBody IdentityDtos.AnonymizeRequest request) {
        privacyService.anonymizeCurrentUser(request);
        return ResponseEntity.noContent().build();
    }
}
