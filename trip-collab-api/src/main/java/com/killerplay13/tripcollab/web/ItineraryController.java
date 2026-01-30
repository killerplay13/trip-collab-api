package com.killerplay13.tripcollab.web;

import com.killerplay13.tripcollab.domain.ItineraryItem;
import com.killerplay13.tripcollab.service.ItineraryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
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
  public List<ItineraryItemResponse> list(
      @PathVariable UUID tripId,
      @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
  ) {
    return service.list(tripId, date).stream().map(ItineraryController::toResponse).toList();
  }

  @PostMapping
  public ItineraryItemResponse create(
      @PathVariable UUID tripId,
      @RequestBody CreateItineraryItemRequest req
  ) {
    ItineraryItem item = service.create(tripId, new ItineraryService.CreateItineraryItemCommand(
        req.dayDate(), req.title(), req.startTime(), req.endTime(),
        req.locationName(), req.mapUrl(), req.note(), req.sortOrder()
    ));
    return toResponse(item);
  }

  @PatchMapping("/{itemId}")
  public ItineraryItemResponse patch(
      @PathVariable UUID tripId,
      @PathVariable UUID itemId,
      @RequestBody PatchItineraryItemRequest req
  ) {
    ItineraryItem item = service.patch(tripId, itemId, new ItineraryService.PatchItineraryItemCommand(
        req.dayDate(), req.title(), req.startTime(), req.endTime(),
        req.locationName(), req.mapUrl(), req.note(), req.sortOrder()
    ));
    return toResponse(item);
  }

  @DeleteMapping("/{itemId}")
  public void delete(@PathVariable UUID tripId, @PathVariable UUID itemId) {
    service.delete(tripId, itemId);
  }

  @PutMapping("/reorder")
  public void reorder(
    @PathVariable UUID tripId,
    @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
    @RequestBody List<ReorderIdOnly> items
) {
  service.reorder(tripId, date, items.stream()
      .map(i -> new ItineraryService.ReorderItem(i.id(), 0)) // sortOrder 不用，service 會壓縮
      .toList());
}

@GetMapping("/all")
public List<ItineraryDayGroupResponse> listAll(
    @PathVariable UUID tripId,
    @RequestParam(value = "from", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate from,
    @RequestParam(value = "to", required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate to
) {
  return service.listAllGrouped(tripId, from, to).stream()
      .map(g -> new ItineraryDayGroupResponse(
          g.dayDate(),
          g.items().stream().map(ItineraryController::toResponse).toList()
      ))
      .toList();
}

public record ItineraryDayGroupResponse(
    LocalDate dayDate,
    List<ItineraryItemResponse> items
) {}


public record ReorderIdOnly(@NotNull UUID id) {}


  @PostMapping("/{itemId}/move")
    public ItineraryItemResponse move(
        @PathVariable UUID tripId,
        @PathVariable UUID itemId,
        @RequestBody MoveRequest req
    ) {
    ItineraryItem item = service.moveToDate(tripId, itemId, req.toDate());
    return toResponse(item);
    }

    @GetMapping("/search")
    public List<ItineraryItemResponse> search(
        @PathVariable UUID tripId,
        @RequestParam("q") String q,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
      return service.search(tripId, q, limit).stream()
          .map(ItineraryController::toResponse)
          .toList();
    }

    @PostMapping("/bulk")
    public List<ItineraryItemResponse> bulkCreate(
            @PathVariable UUID tripId,
            @RequestBody BulkCreateRequest req
    ) {
      var created = service.bulkCreate(tripId, req.dayDate(), req.items());
      return created.stream().map(ItineraryController::toResponse).toList();
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
  public List<ItineraryItemResponse> paste(
          @PathVariable UUID tripId,
          @RequestBody PasteRequest req
  ) {
    var created = service.pasteToBulk(tripId, req.dayDate(), req.text());
    return created.stream().map(ItineraryController::toResponse).toList();
  }

  public record PasteRequest(
          @NotNull LocalDate dayDate,
          @NotBlank String text
  ) {}

  @PostMapping("/paste/preview")
  public ItineraryService.PastePreviewResult pastePreview(
          @PathVariable UUID tripId,
          @RequestBody PastePreviewRequest req
  ) {
    return service.previewPaste(req.text());
  }

  public record PastePreviewRequest(String text) {}


  @PutMapping("/{itemId}")
  public ItineraryItemResponse update(
          @PathVariable UUID tripId,
          @PathVariable UUID itemId,
          @RequestBody UpdateItineraryRequest req
  ) {
    var cmd = new ItineraryService.UpdateCmd();
    cmd.dayDate = req.dayDate();
    cmd.startTime = req.startTime();
    cmd.endTime = req.endTime();
    cmd.title = req.title();
    cmd.locationName = req.locationName();
    cmd.mapUrl = req.mapUrl();
    cmd.note = req.note();

    var updated = service.updateItem(tripId, itemId, cmd);
    return toResponse(updated);
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
