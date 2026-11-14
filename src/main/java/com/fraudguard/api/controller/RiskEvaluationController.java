package com.fraudguard.api.controller;

import com.fraudguard.api.dto.EvaluateRiskRequest;
import com.fraudguard.api.dto.EvaluateRiskResponse;
import com.fraudguard.api.service.RiskEvaluationService;
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

    private final RiskEvaluationService riskEvaluationService;

    public RiskEvaluationController(RiskEvaluationService riskEvaluationService) {
        this.riskEvaluationService = riskEvaluationService;
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
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Event identifier was reused with a different transaction payload",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<EvaluateRiskResponse> evaluate(
            @Valid @RequestBody EvaluateRiskRequest request
    ) {
        return ResponseEntity.ok(riskEvaluationService.evaluate(request));
    }
}