package com.rmbreeding.api.notifications;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // P15-02 - device tokens

    @PostMapping("/tokens")
    public ResponseEntity<NotificationDtos.TokenResponse> registerToken(
            @Valid @RequestBody NotificationDtos.TokenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.registerToken(request));
    }

    @DeleteMapping("/tokens")
    public ResponseEntity<Void> revokeToken(@RequestParam String token) {
        notificationService.revokeToken(token);
        return ResponseEntity.noContent().build();
    }

    // P15-05 - preferences

    @GetMapping("/preferences")
    public List<NotificationDtos.PreferenceResponse> preferences() {
        return notificationService.preferences();
    }

    @PutMapping("/preferences")
    public List<NotificationDtos.PreferenceResponse> updatePreferences(
            @Valid @RequestBody NotificationDtos.PreferencesUpdateRequest request) {
        return notificationService.updatePreferences(request);
    }

    // P15-04 - delivery log

    @GetMapping("/log")
    public List<NotificationDtos.LogResponse> log() {
        return notificationService.recentLogs();
    }
}
