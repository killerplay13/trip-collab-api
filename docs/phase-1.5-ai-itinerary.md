# Phase 1.5: Spring Boot Call trip-collab-ai for Itinerary Draft

## 1. Purpose

Phase 1.5 introduces an AI draft generation flow where `trip-collab-api` calls the external `trip-collab-ai` FastAPI service and returns an itinerary draft to the frontend.

This phase does not write any AI-generated result into `itinerary_items`. The goal is to keep AI generation separate from persistence so the existing itinerary CRUD flow remains unchanged.

## 2. Architecture Overview

Request flow:

`Frontend -> Spring Boot trip-collab-api -> FastAPI trip-collab-ai -> OpenRouter / LLM`

Responsibility split:

- Frontend sends structured itinerary generation preferences.
- Spring Boot validates member access, loads trip context, applies business rules, and calls FastAPI.
- FastAPI generates itinerary draft, explanation, and warnings.
- LLM provider is called only behind FastAPI.
- Spring Boot returns the draft to frontend without writing DB data in this phase.

## 3. Spring Boot API Design

Recommended endpoint:

`POST /api/trips/{tripId}/itinerary/ai/generate`

Rationale:

- The endpoint belongs to itinerary domain rather than generic trip AI utilities.
- It is explicit that the result is itinerary-related and AI-generated.
- It does not conflict with existing itinerary CRUD endpoints.

## 4. Permission Strategy

Authentication should use `X-Member-Token`.

Access policy:

- Allow all trip members.
- Do not restrict this endpoint to owner only in Phase 1.5.

Rationale:

- AI generation does not write DB data.
- Existing itinerary create, bulk create, and paste flows already allow general members.
- If AI usage cost becomes a concern later, owner-only access or rate limiting can be added in a later phase.

## 5. Spring Boot Request DTO Draft

```java
public record AiItineraryGenerateRequest(
    LocalDate from,
    LocalDate to,
    List<String> interests,
    List<String> mustVisitPlaces,
    List<String> avoidPlaces,
    String travelStyle,
    String budgetLevel,
    String notes,
    String language
) {}
```

Design note:

- Frontend should not send a raw prompt string directly.
- Spring Boot should accept structured fields and compose downstream AI request data in a controlled way.
- This keeps the contract stable and reduces prompt-shape drift between frontend and backend.

## 6. FastAPI Request Mapping

Spring Boot should enrich the AI request with trip context loaded from DB instead of fully trusting frontend input.

Expected server-side context sources:

- `trip.title`
- trip destination or inferred destination if available
- `trip.startDate`
- `trip.endDate`
- `trip.timezone`
- member/trip metadata when needed

FastAPI request JSON example:

```json
{
  "trip_title": "Hong Kong family trip",
  "destination": "Hong Kong",
  "start_date": "2026-05-01",
  "end_date": "2026-05-07",
  "timezone": "Asia/Taipei",
  "travelers_count": 4,
  "travel_style": "food",
  "budget_level": "medium",
  "interests": ["food", "theme park", "shopping"],
  "must_visit_places": ["Hong Kong Disneyland"],
  "avoid_places": ["pub"],
  "notes": "Prefer public transportation and family-friendly restaurants.",
  "language": "zh-TW"
}
```

Mapping principles:

- Spring Boot owns the outbound contract to FastAPI.
- Frontend provides preferences, not authoritative trip facts.
- FastAPI should receive normalized and backend-owned context.

## 7. FastAPI Response Contract

Success response example:

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "day_date": "2026-05-01",
        "title": "抵達香港與中環美食散步",
        "start_time": "10:00:00",
        "end_time": "12:00:00",
        "location_name": "Central, Hong Kong",
        "map_url": null,
        "note": "以輕鬆步調安排，適合抵達日。",
        "sort_order": 1
      }
    ],
    "explanation": "此行程依照美食、家庭友善與低壓步調安排。",
    "warnings": [],
    "source": "openrouter",
    "fallback": false,
    "fallback_reason": null
  },
  "error": null
}
```

Contract expectations:

- `success` indicates whether FastAPI completed the request contract successfully.
- `data.items` is a flat list keyed by `day_date`.
- `explanation` and `warnings` are user-visible metadata.
- `fallback` and `fallback_reason` must always be interpreted by Spring Boot.

## 8. Spring Boot Response DTO Draft

```java
public record AiItineraryGenerateResponse(
    UUID tripId,
    boolean fallback,
    String fallbackReason,
    String explanation,
    List<String> warnings,
    List<AiItineraryDraftDay> days
) {}

public record AiItineraryDraftDay(
    LocalDate dayDate,
    List<AiItineraryDraftItem> items
) {}

public record AiItineraryDraftItem(
    LocalTime startTime,
    LocalTime endTime,
    String title,
    String locationName,
    String mapUrl,
    String note,
    int sortOrder
) {}
```

Mapping rule:

- Spring Boot should convert FastAPI `data.items` into grouped `days`.
- Grouping key is `day_date`.
- Spring Boot should return its own response contract rather than transparently proxying FastAPI response shape to the frontend.

## 9. Fallback Handling

Fallback response example:

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "day_date": "2026-05-01",
        "title": "Hong Kong fallback draft",
        "start_time": null,
        "end_time": null,
        "location_name": null,
        "map_url": null,
        "note": "AI provider timeout or failed. This is a safe fallback draft.",
        "sort_order": 1
      }
    ],
    "explanation": "AI service is temporarily unavailable. This fallback draft does not write to DB.",
    "warnings": ["AI provider failed or timed out."],
    "source": "fallback",
    "fallback": true,
    "fallback_reason": "timeout"
  },
  "error": null
}
```

Possible `fallback_reason` values:

- `timeout`
- `missing_provider_config`
- `rate_limited`
- `quota_exceeded`
- `invalid_response`
- `provider_http_error`
- `provider_unavailable`
- `provider_error`

Handling rule:

- `fallback=true` is not an error state.
- Spring Boot should return `200 OK` when FastAPI returns a valid fallback payload.
- Spring Boot should preserve `fallbackReason` and expose it to frontend.

## 10. Config Design

Recommended configuration:

```yaml
trip-collab-ai:
  enabled: ${TRIP_COLLAB_AI_ENABLED:false}
  base-url: ${TRIP_COLLAB_AI_BASE_URL:http://localhost:8000}
  timeout-seconds: ${TRIP_COLLAB_AI_TIMEOUT_SECONDS:10}
```

Recommended binding style for later implementation:

`@ConfigurationProperties(prefix = "trip-collab-ai")`

Design note:

- `enabled` allows feature gating without code branching in multiple places.
- `base-url` isolates external service location from application logic.
- `timeout-seconds` should be explicit and configurable per environment.

## 11. HTTP Client Design

Recommended client:

`RestClient` from Spring Boot 3 / Spring Framework 6

Rationale:

- The project already includes `spring-boot-starter-web`.
- WebFlux is not currently used and is not required for this flow.
- `RestClient` gives the smallest implementation surface for a synchronous service-to-service call.

## 12. Error Mapping

| Scenario | Spring Boot Response |
|---|---|
| AI disabled | `503 Service Unavailable` |
| timeout | `504 Gateway Timeout` |
| FastAPI 5xx / connection refused | `502 Bad Gateway` |
| FastAPI invalid JSON / missing data | `502 Bad Gateway` |
| FastAPI success with `fallback=true` | `200 OK`, `fallback=true` |
| frontend invalid request | `400 Bad Request` |

Design note:

- Spring Boot should keep its own API contract and map external integration failures into stable backend-facing semantics.

## 13. Implementation Plan

### Phase 1.5B

- Add `TripCollabAiProperties`
- Add AI itinerary DTOs
- Add `AiItineraryService`
- Add `AiItineraryController`
- No DB writes

### Phase 1.5C

- Add mock-based service tests
- Add mock-based controller tests
- No PostgreSQL dependency
- No frontend dependency

### Phase 1.5D

- Run end-to-end test on Mac environment
- Validate Spring Boot + PostgreSQL + FastAPI + frontend integration

## 14. Non-goals

- Do not write `itinerary_items`
- Do not modify Flyway
- Do not modify existing `ItineraryService` CRUD behavior
- Do not modify token filters
- Do not modify existing itinerary endpoints
- Do not implement frontend integration
- Do not implement AI draft confirm/save flow

## 15. Open Questions

- Should AI generation later become owner-only due to cost?
- Should existing itinerary items be included in FastAPI prompt context?
- Should Spring Boot implement local fallback if FastAPI is unreachable?
- What should be the final timeout value?
- Should confirm/save draft become a separate endpoint?
