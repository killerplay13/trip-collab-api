package com.killerplay13.tripcollab.web;

import com.killerplay13.tripcollab.domain.ItineraryItem;
import com.killerplay13.tripcollab.security.AuthGuard;
import com.killerplay13.tripcollab.security.MemberTokenFilter;
import com.killerplay13.tripcollab.service.ItineraryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}/itinerary")
public class ItineraryController {

  private final ItineraryService service;

  public ItineraryController(ItineraryService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<List<ItineraryItemResponse>> list(
      @PathVariable UUID tripId,
      @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
  ) {
    return ResponseEntity.ok(service.list(tripId, date).stream().map(ItineraryController::toResponse).toList());
  }

  @PostMapping
  public ResponseEntity<ItineraryItemResponse> create(
      @PathVariable UUID tripId,
      @RequestBody CreateItineraryItemRequest req,
      HttpServletRequest request
  ) {
    UUID actorMemberId = (UUID) request.getAttribute(MemberTokenFilter.ATTR_MEMBER_ID);
    LocalDate dayDate = req.dayDate();
    String title = req.title();
    LocalTime startTime = req.startTime();
    LocalTime endTime = req.endTime();
    String locationName = req.locationName();
    String mapUrl = req.mapUrl();
    String note = req.note();
    Integer sortOrder = req.sortOrder();

    ItineraryItem item = service.create(tripId, actorMemberId, new ItineraryService.CreateItineraryItemCommand(
        dayDate,
        title,
        startTime,
        endTime,
        locationName,
        mapUrl,
        note,
        sortOrder
    ));
    return ResponseEntity.status(201).body(toResponse(item));
  }

  @PatchMapping("/{itemId}")
  public ResponseEntity<ItineraryItemResponse> patch(
      @PathVariable UUID tripId,
      @PathVariable UUID itemId,
      @RequestBody PatchItineraryItemRequest req,
      HttpServletRequest request
  ) {
    UUID actorMemberId = (UUID) request.getAttribute(MemberTokenFilter.ATTR_MEMBER_ID);
    ItineraryItem item = service.patch(tripId, itemId, actorMemberId, new ItineraryService.PatchItineraryItemCommand(
        req.dayDate(), req.title(), req.startTime(), req.endTime(),
        req.locationName(), req.mapUrl(), req.note(), req.sortOrder()
    ));
    return ResponseEntity.ok(toResponse(item));
  }

  @DeleteMapping("/{itemId}")
  public ResponseEntity<?> delete(
      @PathVariable UUID tripId,
      @PathVariable UUID itemId,
      HttpServletRequest request
  ) {
    ResponseEntity<String> guard = AuthGuard.requireOwner(request);
    if (guard != null) return guard;
    UUID actorMemberId = (UUID) request.getAttribute(MemberTokenFilter.ATTR_MEMBER_ID);
    service.delete(tripId, itemId, actorMemberId);
    return ResponseEntity.ok().build();
  }

  @PutMapping("/reorder")
  public ResponseEntity<?> reorder(
    @PathVariable UUID tripId,
    @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
    @RequestBody List<ReorderIdOnly> items,
    HttpServletRequest request
  ) {
  ResponseEntity<String> guard = AuthGuard.requireOwner(request);
  if (guard != null) return guard;
  UUID actorMemberId = (UUID) request.getAttribute(MemberTokenFilter.ATTR_MEMBER_ID);
  
  // sortOrder is ignored; service will normalize
  List<ItineraryService.ReorderItem> reorderItems = items.stream()
      .map((ReorderIdOnly i) -> new ItineraryService.ReorderItem(i.id(), 0))
      .toList();
  service.reorder(tripId, date, actorMemberId, reorderItems);
  return ResponseEntity.ok().build();
  }

  @GetMapping("/all")
  public ResponseEntity<List<ItineraryDayGroupResponse>> listAll(
      @PathVariable UUID tripId,
      @RequestParam(value = "from", required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate from,
      @RequestParam(value = "to", required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate to
  ) {
    return ResponseEntity.ok(service.listAllGrouped(tripId, from, to).stream()
        .map(g -> new ItineraryDayGroupResponse(
            g.dayDate(),
            g.items().stream().map(ItineraryController::toResponse).toList()
        ))
        .toList());
  }

  public record ItineraryDayGroupResponse(
      LocalDate dayDate,
      List<ItineraryItemResponse> items
  ) {}


  public record ReorderIdOnly(@NotNull UUID id) {}


  @PostMapping("/{itemId}/move")
  public ResponseEntity<?> move(
      @PathVariable UUID tripId,
      @PathVariable UUID itemId,
      @RequestBody MoveRequest req,
      HttpServletRequest request
  ) {
    ResponseEntity<String> guard = AuthGuard.requireOwner(request);
    if (guard != null) return guard;
    UUID actorMemberId = (UUID) request.getAttribute(MemberTokenFilter.ATTR_MEMBER_ID);
    ItineraryItem item = service.moveToDate(tripId, itemId, actorMemberId, req.toDate());
    return ResponseEntity.ok(toResponse(item));
  }

  @GetMapping("/search")
  public ResponseEntity<List<ItineraryItemResponse>> search(
      @PathVariable UUID tripId,
      @RequestParam("q") String q,
      @RequestParam(value = "limit", required = false) Integer limit
  ) {
    return ResponseEntity.ok(service.search(tripId, q, limit).stream()
        .map(ItineraryController::toResponse)
        .toList());
  }

  @PostMapping("/bulk")
  public ResponseEntity<List<ItineraryItemResponse>> bulkCreate(
          @PathVariable UUID tripId,
          @RequestBody BulkCreateRequest req,
          HttpServletRequest request
  ) {
    UUID actorMemberId = (UUID) request.getAttribute(MemberTokenFilter.ATTR_MEMBER_ID);
    var created = service.bulkCreate(tripId, actorMemberId, req.dayDate(), req.items());
    return ResponseEntity.status(201).body(created.stream().map(ItineraryController::toResponse).toList());
  }

  public record BulkCreateRequest(
          @NotNull LocalDate dayDate,
          @NotNull List<BulkItem> items
  ) {}

  public record BulkItem(
          String startTime,
          String endTime,
          @NotBlank String title,
          String locationName,
          String mapUrl,
          String note
  ) {}

  @PostMapping("/paste")
  public ResponseEntity<List<ItineraryItemResponse>> paste(
          @PathVariable UUID tripId,
          @RequestBody PasteRequest req,
          HttpServletRequest request
  ) {
    UUID actorMemberId = (UUID) request.getAttribute(MemberTokenFilter.ATTR_MEMBER_ID);
    var created = service.pasteToBulk(tripId, actorMemberId, req.dayDate(), req.text());
    return ResponseEntity.status(201).body(created.stream().map(ItineraryController::toResponse).toList());
  }

  public record PasteRequest(
          @NotNull LocalDate dayDate,
          @NotBlank String text
  ) {}

  @PostMapping("/paste/preview")
  public ResponseEntity<ItineraryService.PastePreviewResult> pastePreview(
          @PathVariable UUID tripId,
          @RequestBody PastePreviewRequest req
  ) {
    return ResponseEntity.ok(service.previewPaste(req.text()));
  }

  public record PastePreviewRequest(String text) {}


  @PutMapping("/{itemId}")
  public ResponseEntity<ItineraryItemResponse> update(
          @PathVariable UUID tripId,
          @PathVariable UUID itemId,
          @RequestBody UpdateItineraryRequest req,
          HttpServletRequest request
  ) {
    UUID actorMemberId = (UUID) request.getAttribute(MemberTokenFilter.ATTR_MEMBER_ID);
    var cmd = new ItineraryService.UpdateCmd();
    cmd.dayDate = req.dayDate();
    cmd.startTime = req.startTime();
    cmd.endTime = req.endTime();
    cmd.title = req.title();
    cmd.locationName = req.locationName();
    cmd.mapUrl = req.mapUrl();
    cmd.note = req.note();

    var updated = service.updateItem(tripId, itemId, actorMemberId, cmd);
    return ResponseEntity.ok(toResponse(updated));
  }

  public record UpdateItineraryRequest(
          java.time.LocalDate dayDate,
          java.time.LocalTime startTime,
          java.time.LocalTime endTime,
          String title,
          String locationName,
          String mapUrl,
          String note
  ) {}



  public record MoveRequest(@NotNull LocalDate toDate) {}


  private static ItineraryItemResponse toResponse(ItineraryItem i) {
    return new ItineraryItemResponse(
        i.getId(),
        i.getTripId(),
        i.getDayDate(),
        i.getStartTime(),
        i.getEndTime(),
        i.getTitle(),
        i.getLocationName(),
        i.getMapUrl(),
        i.getNote(),
        i.getSortOrder(),
        i.getCreatedAt(),
        i.getUpdatedAt()
    );
  }

  public record CreateItineraryItemRequest(
      @NotNull LocalDate dayDate,
      @NotBlank String title,
      LocalTime startTime,
      LocalTime endTime,
      String locationName,
      String mapUrl,
      String note,
      Integer sortOrder
  ) {}

  public record PatchItineraryItemRequest(
      LocalDate dayDate,
      String title,
      LocalTime startTime,
      LocalTime endTime,
      String locationName,
      String mapUrl,
      String note,
      Integer sortOrder
  ) {}

  public record ReorderRequestItem(@NotNull UUID id, int sortOrder) {}

  public record ItineraryItemResponse(
      UUID id,
      UUID tripId,
      LocalDate dayDate,
      LocalTime startTime,
      LocalTime endTime,
      String title,
      String locationName,
      String mapUrl,
      String note,
      int sortOrder,
      java.time.Instant createdAt,
      java.time.Instant updatedAt
  ) {}
}
