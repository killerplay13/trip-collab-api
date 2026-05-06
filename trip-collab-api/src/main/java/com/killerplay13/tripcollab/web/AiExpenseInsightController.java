package com.killerplay13.tripcollab.web;

import com.killerplay13.tripcollab.service.AiExpenseInsightService;
import com.killerplay13.tripcollab.web.dto.ai.AiExpenseInsightRequest;
import com.killerplay13.tripcollab.web.dto.ai.AiExpenseInsightResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/expenses")
public class AiExpenseInsightController {
  private final AiExpenseInsightService service;

  public AiExpenseInsightController(AiExpenseInsightService service) {
    this.service = service;
  }

  @PostMapping("/ai/insight")
  public ResponseEntity<AiExpenseInsightResponse> insight(
      @PathVariable UUID tripId,
      @RequestBody(required = false) AiExpenseInsightRequest request
  ) {
    return ResponseEntity.ok(service.insight(tripId, request));
  }
}
