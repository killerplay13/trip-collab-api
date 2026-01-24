package com.killerplay13.tripcollab.service;

import com.killerplay13.tripcollab.domain.ItineraryItem;
import com.killerplay13.tripcollab.repo.ItineraryItemRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItineraryService {

  private final ItineraryItemRepository repo;

  public ItineraryService(ItineraryItemRepository repo) {
    this.repo = repo;
  }

  @Transactional(readOnly = true)
  public List<ItineraryItem> list(UUID tripId, LocalDate dayDate) {
    return repo.findByTripIdAndDayDateOrderBySortOrderAscStartTimeAscCreatedAtAsc(tripId, dayDate);
  }

  @Transactional
  public ItineraryItem create(UUID tripId, CreateItineraryItemCommand cmd) {
    ItineraryItem item = new ItineraryItem();
    item.setTripId(tripId);
    item.setDayDate(cmd.dayDate());
    item.setTitle(cmd.title());
    item.setStartTime(cmd.startTime());
    item.setEndTime(cmd.endTime());
    item.setLocationName(cmd.locationName());
    item.setMapUrl(cmd.mapUrl());
    item.setNote(cmd.note());

    Integer max = repo.findMaxSortOrder(tripId, cmd.dayDate());
    int next = (max == null ? 0 : max + 1);
    item.setSortOrder(cmd.sortOrder() != null ? cmd.sortOrder() : next);

    return repo.save(item);
  }

  @Transactional
  public ItineraryItem patch(UUID tripId, UUID itemId, PatchItineraryItemCommand cmd) {
    ItineraryItem item = repo.findByIdAndTripId(itemId, tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (cmd.dayDate() != null) item.setDayDate(cmd.dayDate());
    if (cmd.title() != null) item.setTitle(cmd.title());
    if (cmd.startTime() != null) item.setStartTime(cmd.startTime());
    if (cmd.endTime() != null) item.setEndTime(cmd.endTime());
    if (cmd.locationName() != null) item.setLocationName(cmd.locationName());
    if (cmd.mapUrl() != null) item.setMapUrl(cmd.mapUrl());
    if (cmd.note() != null) item.setNote(cmd.note());
    if (cmd.sortOrder() != null) item.setSortOrder(cmd.sortOrder());

    return repo.save(item);
  }

  @Transactional
  public void delete(UUID tripId, UUID itemId) {
    ItineraryItem item = repo.findByIdAndTripId(itemId, tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    repo.delete(item);
  }

  @Transactional
public void reorder(UUID tripId, LocalDate dayDate, List<ReorderItem> items) {
  if (dayDate == null) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
  }
  if (items == null || items.isEmpty()) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items is empty");
  }

  // 1) 驗證：request 裡的 id 必須全部屬於該 trip + day
  List<UUID> existingIds = repo.findIdsByTripIdAndDayDate(tripId, dayDate);
  var existingSet = new java.util.HashSet<>(existingIds);

  for (ReorderItem r : items) {
    if (r.id() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
    }
    if (!existingSet.contains(r.id())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "item not in this trip/day: " + r.id());
    }
  }

  // 2) 壓縮：按照「前端送來的順序」重新編號 0..n-1
  // （前端只要給 list 順序即可，不用自己算 sortOrder）
  for (int idx = 0; idx < items.size(); idx++) {
    UUID id = items.get(idx).id();
    repo.updateSortOrder(tripId, id, idx);
  }
}


  @Transactional
  public ItineraryItem moveToDate(UUID tripId, UUID itemId, LocalDate toDate) {
    ItineraryItem item = repo.findByIdAndTripId(itemId, tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (toDate == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toDate is required");
    }

    // 如果同一天就不用 move
    if (toDate.equals(item.getDayDate())) {
      return item;
    }

    Integer max = repo.findMaxSortOrder(tripId, toDate);
    int next = (max == null ? 0 : max + 1);

    item.setDayDate(toDate);
    item.setSortOrder(next);

    return repo.save(item);
  }

  @Transactional(readOnly = true)
public List<DayGroup> listAllGrouped(UUID tripId, LocalDate from, LocalDate to) {
  List<ItineraryItem> rows;

  if (from == null && to == null) {
  rows = repo.findAllByTrip(tripId);
} else if (from != null && to == null) {
  rows = repo.findAllByTripFrom(tripId, from);
} else if (from != null) {
  rows = repo.findAllByTripInRange(tripId, from, to);
} else {
  throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "from is required when to is provided"
  );
}


  var map = new java.util.LinkedHashMap<java.time.LocalDate, java.util.List<ItineraryItem>>();
  for (var item : rows) {
    map.computeIfAbsent(item.getDayDate(), k -> new java.util.ArrayList<>()).add(item);
  }

  var result = new java.util.ArrayList<DayGroup>();
  for (var e : map.entrySet()) {
    result.add(new DayGroup(e.getKey(), e.getValue()));
  }
  return result;
}


public record DayGroup(LocalDate dayDate, List<ItineraryItem> items) {}



  public record CreateItineraryItemCommand(
      LocalDate dayDate,
      String title,
      LocalTime startTime,
      LocalTime endTime,
      String locationName,
      String mapUrl,
      String note,
      Integer sortOrder
  ) {}

  public record PatchItineraryItemCommand(
      LocalDate dayDate,
      String title,
      LocalTime startTime,
      LocalTime endTime,
      String locationName,
      String mapUrl,
      String note,
      Integer sortOrder
  ) {}

  public record ReorderItem(UUID id, int sortOrder) {}
}
