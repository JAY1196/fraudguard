package com.fraudguard.api.controller;

import com.fraudguard.api.dto.DecisionPageResponse;
import com.fraudguard.api.service.DecisionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets/{wallet_id}/decisions")
@Tag(name = "Risk Decisions", description = "Query stored fraud risk decisions by wallet")
public class DecisionController {

    private final DecisionQueryService decisionQueryService;

    public DecisionController(DecisionQueryService decisionQueryService) {
        this.decisionQueryService = decisionQueryService;
    }

    @GetMapping
    @Operation(
            summary = "List risk decisions for a wallet",
            description = "Returns decisions where the wallet is either the source or destination, in descending ID order using opaque cursor-based pagination."
    )
    public ResponseEntity<DecisionPageResponse> getWalletDecisions(
            @Parameter(description = "Wallet UUID")
            @PathVariable("wallet_id") UUID walletId,

            @RequestParam(required = false) Integer limit,

            @RequestParam(required = false) String cursor
    ) {
        return ResponseEntity.ok(
                decisionQueryService.getWalletDecisions(walletId, limit, cursor)
        );
    }
}