package com.killerplay13.tripcollab.web;

import com.killerplay13.tripcollab.service.AiItineraryService;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryGenerateRequest;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryGenerateResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/itinerary")
public class AiItineraryController {
  private final AiItineraryService service;

  public AiItineraryController(AiItineraryService service) {
    this.service = service;
  }

  @PostMapping("/ai/generate")
  public ResponseEntity<AiItineraryGenerateResponse> generate(
      @PathVariable UUID tripId,
      @RequestBody AiItineraryGenerateRequest request
  ) {
    return ResponseEntity.ok(service.generate(tripId, request));
  }
}
