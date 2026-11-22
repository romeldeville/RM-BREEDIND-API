package com.rmbreeding.api.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/farms")
public class FarmController {

    private final FarmService farmService;
    private final MemberService memberService;

    public FarmController(FarmService farmService, MemberService memberService) {
        this.farmService = farmService;
        this.memberService = memberService;
    }

    @GetMapping
    public List<IdentityDtos.FarmResponse> list() {
        return farmService.list();
    }

    @PostMapping
    public ResponseEntity<IdentityDtos.FarmResponse> create(
            @Valid @RequestBody IdentityDtos.CreateFarmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(farmService.create(request));
    }

    @GetMapping("/{farmId}")
    public IdentityDtos.FarmResponse get(@PathVariable UUID farmId) {
        return farmService.get(farmId);
    }

    @PatchMapping("/{farmId}")
    public IdentityDtos.FarmResponse patch(@PathVariable UUID farmId,
            @Valid @RequestBody IdentityDtos.FarmPatchRequest request) {
        return farmService.patch(farmId, request);
    }

    @GetMapping("/{farmId}/members")
    public List<IdentityDtos.MemberResponse> members(@PathVariable UUID farmId) {
        return memberService.listByFarm(farmId);
    }
}
