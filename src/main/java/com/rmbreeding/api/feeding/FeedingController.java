package com.rmbreeding.api.feeding;

import com.rmbreeding.api.referential.ReferentialDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FeedingController {

    private final FeedingService feedingService;

    public FeedingController(FeedingService feedingService) {
        this.feedingService = feedingService;
    }

    // P8-01 Feeds

    @GetMapping("/feeds")
    public List<FeedingDtos.FeedResponse> listFeeds(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
        return feedingService.listFeeds(search, type, includeArchived);
    }

    @PostMapping("/feeds")
    public ResponseEntity<FeedingDtos.FeedResponse> createFeed(
            @Valid @RequestBody FeedingDtos.FeedCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedingService.createFeed(request));
    }

    @GetMapping("/feeds/{feedId}")
    public FeedingDtos.FeedResponse getFeed(@PathVariable UUID feedId) {
        return feedingService.getFeed(feedId);
    }

    @PatchMapping("/feeds/{feedId}")
    public FeedingDtos.FeedResponse patchFeed(@PathVariable UUID feedId,
            @Valid @RequestBody FeedingDtos.FeedPatchRequest request) {
        return feedingService.patchFeed(feedId, request);
    }

    @DeleteMapping("/feeds/{feedId}")
    public ResponseEntity<Void> archiveFeed(@PathVariable UUID feedId) {
        feedingService.archiveFeed(feedId);
        return ResponseEntity.noContent().build();
    }

    // P8-02 Receipts

    @GetMapping("/feeds/{feedId}/receipts")
    public List<FeedingDtos.FeedReceiptResponse> listReceipts(@PathVariable UUID feedId) {
        return feedingService.listReceipts(feedId);
    }

    @PostMapping("/feeds/{feedId}/receipts")
    public ResponseEntity<FeedingDtos.FeedReceiptResponse> receiveFeed(@PathVariable UUID feedId,
            @Valid @RequestBody FeedingDtos.FeedReceiptCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedingService.receiveFeed(feedId, request));
    }

    // P8-03 Distributions

    @GetMapping("/feed-distributions")
    public List<FeedingDtos.FeedDistributionResponse> listDistributions(
            @RequestParam(required = false) UUID feedId) {
        return feedingService.listDistributions(feedId);
    }

    @PostMapping("/feed-distributions")
    public ResponseEntity<FeedingDtos.FeedDistributionResponse> distribute(
            @Valid @RequestBody FeedingDtos.FeedDistributionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedingService.distribute(request));
    }

    // P8-04 Feeding plans

    @GetMapping("/feeding-plans")
    public List<ReferentialDtos.FeedingPlanResponse> listFeedingPlans(
            @RequestParam(required = false) UUID speciesId) {
        return feedingService.listFeedingPlans(speciesId);
    }

    // P8-05 Stock projection

    @GetMapping("/feed-stocks")
    public List<FeedingDtos.FeedStockResponse> listStocks() {
        return feedingService.listStocks();
    }

    @GetMapping("/feed-stocks/low")
    public List<FeedingDtos.LowStockAlertResponse> lowStock(
            @RequestParam(required = false) BigDecimal threshold) {
        return feedingService.lowStock(threshold);
    }

    // P8-06 Feed costs

    @GetMapping("/feed-costs")
    public List<FeedingDtos.FeedCostResponse> listCosts() {
        return feedingService.listCosts();
    }

    @PostMapping("/feed-costs")
    public ResponseEntity<FeedingDtos.FeedCostResponse> computeCost(
            @Valid @RequestBody FeedingDtos.FeedCostComputeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedingService.computeCost(request));
    }
}
