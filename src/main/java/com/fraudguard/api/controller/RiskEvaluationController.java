package com.fraudguard.api.controller;

import com.fraudguard.api.dto.EvaluateRiskRequest;
import com.fraudguard.api.dto.EvaluateRiskResponse;
import com.fraudguard.api.validator.TransactionTimestampValidator;
import com.fraudguard.domain.engine.RuleEngine;
import com.fraudguard.domain.model.EvaluationResult;
import com.fraudguard.domain.model.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/risk")
@Tag(name = "Risk Evaluation", description = "Real-time transaction fraud risk scoring")
public class RiskEvaluationController {

    private final RuleEngine ruleEngine;
    private final TransactionTimestampValidator timestampValidator;

    public RiskEvaluationController(RuleEngine ruleEngine, TransactionTimestampValidator timestampValidator) {
        this.ruleEngine = ruleEngine;
        this.timestampValidator = timestampValidator;
    }

    @PostMapping("/evaluate")
    @Operation(
            summary = "Evaluate transaction fraud risk",
            description = "Evaluates an incoming transaction against configured rules and returns an aggregated score with ALLOW, REVIEW, or BLOCK decision."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Risk evaluation completed successfully",
                    content = @Content(schema = @Schema(implementation = EvaluateRiskResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failure or timestamp out of bounds",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<EvaluateRiskResponse> evaluate(@Valid @RequestBody EvaluateRiskRequest request) {
        timestampValidator.validate(request.timestamp());

        Transaction transaction = new Transaction(
                request.eventId(),
                request.sourceWalletId(),
                request.destinationWalletId(),
                request.amountPaise(),
                request.type(),
                request.timestamp()
        );

        EvaluationResult result = ruleEngine.evaluate(transaction);

        EvaluateRiskResponse response = new EvaluateRiskResponse(
                request.eventId(),
                result.decision(),
                result.score(),
                result.reasonCodes()
        );

        return ResponseEntity.ok(response);
    }
}
