package com.killerplay13.tripcollab.web;

import com.killerplay13.tripcollab.service.AiSettlementService;
import com.killerplay13.tripcollab.web.dto.ai.AiSettlementExplainResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/expenses")
public class AiSettlementController {
  private final AiSettlementService service;

  public AiSettlementController(AiSettlementService service) {
    this.service = service;
  }

  @PostMapping("/ai/explain")
  public ResponseEntity<AiSettlementExplainResponse> explain(@PathVariable UUID tripId) {
    return ResponseEntity.ok(service.explain(tripId));
  }
}
