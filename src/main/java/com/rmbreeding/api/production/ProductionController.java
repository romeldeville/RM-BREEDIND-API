package com.rmbreeding.api.production;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/production")
public class ProductionController {

    private final ProductionService productionService;

    public ProductionController(ProductionService productionService) {
        this.productionService = productionService;
    }

    // P9-01 Weighings

    @GetMapping("/weighings")
    public List<ProductionDtos.WeighingResponse> listWeighings(
            @RequestParam(required = false) UUID animalId,
            @RequestParam(required = false) UUID lotId) {
        return productionService.listWeighings(animalId, lotId);
    }

    @PostMapping("/weighings")
    public ResponseEntity<ProductionDtos.WeighingResponse> createWeighing(
            @Valid @RequestBody ProductionDtos.WeighingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productionService.createWeighing(request));
    }

    // P9-02 Average daily gain

    @GetMapping("/weighings/gmq")
    public ProductionDtos.GmqResponse gmq(
            @RequestParam(required = false) UUID animalId,
            @RequestParam(required = false) UUID lotId) {
        return productionService.gmq(animalId, lotId);
    }

    // P9-03 Growth curves

    @GetMapping("/growth-curve")
    public ProductionDtos.GrowthCurveResponse growthCurve(
            @RequestParam(required = false) UUID animalId,
            @RequestParam(required = false) UUID lotId) {
        return productionService.growthCurve(animalId, lotId);
    }

    // P9-07 Egg collections

    @GetMapping("/egg-collections")
    public List<ProductionDtos.EggCollectionResponse> listCollections(
            @RequestParam(required = false) UUID lotId) {
        return productionService.listCollections(lotId);
    }

    @PostMapping("/egg-collections")
    public ResponseEntity<ProductionDtos.EggCollectionResponse> createCollection(
            @Valid @RequestBody ProductionDtos.EggCollectionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productionService.createCollection(request));
    }

    // P9-08 Egg movements and stock

    @GetMapping("/egg-movements")
    public List<ProductionDtos.EggMovementResponse> listMovements(
            @RequestParam(required = false) UUID lotId) {
        return productionService.listMovements(lotId);
    }

    @PostMapping("/egg-movements")
    public ResponseEntity<ProductionDtos.EggMovementResponse> createMovement(
            @Valid @RequestBody ProductionDtos.EggMovementCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productionService.createMovement(request));
    }

    @GetMapping("/egg-stocks")
    public List<ProductionDtos.EggStockResponse> listEggStocks() {
        return productionService.listEggStocks();
    }

    // P9-09 Laying rate

    @GetMapping("/laying-rate")
    public ProductionDtos.LayingRateResponse layingRate(
            @RequestParam UUID lotId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return productionService.layingRate(lotId, from, to);
    }
}
