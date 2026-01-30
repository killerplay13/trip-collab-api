package com.killerplay13.tripcollab.service;

import com.killerplay13.tripcollab.domain.ItineraryItem;
import com.killerplay13.tripcollab.repo.ItineraryItemRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.killerplay13.tripcollab.web.ItineraryController;
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

    long count = repo.countByTripIdAndDayDate(tripId, cmd.dayDate());
    int next = (int) count;
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

  @Transactional
  public List<ItineraryItem> bulkCreate(UUID tripId, LocalDate dayDate, List<ItineraryController.BulkItem> items) {
    if (dayDate == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dayDate is required");
    }
    if (items == null || items.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items is empty");
    }

    // 1) base sortOrder = 當天目前筆數（append 到最後）
    int base = (int) repo.countByTripIdAndDayDate(tripId, dayDate);

    // 2) 建立 entities
    var toSave = new ArrayList<ItineraryItem>(items.size());
    for (int i = 0; i < items.size(); i++) {
      var it = items.get(i);

      if (it.title() == null || it.title().trim().isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required at index " + i);
      }

      ItineraryItem e = new ItineraryItem();
      e.setTripId(tripId);
      e.setDayDate(dayDate);
      e.setTitle(it.title().trim());
      e.setLocationName(blankToNull(it.locationName()));
      e.setMapUrl(blankToNull(it.mapUrl()));
      e.setNote(blankToNull(it.note()));
      e.setStartTime(parseTimeOrNull(it.startTime(), "startTime", i));
      e.setEndTime(parseTimeOrNull(it.endTime(), "endTime", i));
      e.setSortOrder(base + i);

      toSave.add(e);
    }

    // 3) 一次存（同一個 transaction）
    return repo.saveAll(toSave);
  }

  // helpers
  private static String blankToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static LocalTime parseTimeOrNull(String s, String field, int idx) {
    String t = blankToNull(s);
    if (t == null) return null;
    try {
      // 支援 "HH:mm" 或 "HH:mm:ss"
      if (t.length() == 5) return LocalTime.parse(t + ":00");
      return LocalTime.parse(t);
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
              "invalid " + field + " at index " + idx + ": " + s);
    }
  }

  @Transactional
  public List<ItineraryItem> pasteToBulk(UUID tripId, LocalDate dayDate, String text) {
    if (dayDate == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dayDate is required");
    }

    var preview = previewPaste(text);

    if (!preview.errors().isEmpty()) {
      // v0.1：有錯就整包拒絕（避免部分寫入造成使用者困惑）
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paste parse failed");
    }

    var bulkItems = preview.items().stream()
            .map(it -> new ItineraryController.BulkItem(
                    it.startTime(),
                    null,
                    it.title(),
                    it.locationName(),
                    null,
                    it.note()
            ))
            .toList();

    return bulkCreate(tripId, dayDate, bulkItems);
  }


  @Transactional
  public ItineraryItem updateItem(UUID tripId, UUID itemId, UpdateCmd cmd) {
    var item = repo.findByIdAndTripId(itemId, tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "itinerary item not found"));

    if (cmd.dayDate != null && !cmd.dayDate.equals(item.getDayDate())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dayDate cannot be changed here; use move endpoint");
    }

    if (cmd.title != null) {
      String t = cmd.title.trim();
      if (t.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title cannot be blank");
      }
      item.setTitle(t);
    }

    if (cmd.startTime != null) item.setStartTime(cmd.startTime);
    if (cmd.endTime != null) item.setEndTime(cmd.endTime);

    if (cmd.locationName != null) item.setLocationName(blankToNull(cmd.locationName));
    if (cmd.mapUrl != null) item.setMapUrl(blankToNull(cmd.mapUrl));
    if (cmd.note != null) item.setNote(blankToNull(cmd.note));

    return repo.save(item);
  }

  private static class ParsedLine {
    String startTime;     // "HH:mm" or null
    String title;         // required
    String locationName;  // optional
    String note;          // optional
  }

  private ParsedLine parseLine(String line, int idx) {
    ParsedLine out = new ParsedLine();

    String rest = line;
    if (rest.length() >= 5 && rest.charAt(2) == ':' &&
            Character.isDigit(rest.charAt(0)) && Character.isDigit(rest.charAt(1)) &&
            Character.isDigit(rest.charAt(3)) && Character.isDigit(rest.charAt(4))) {

      String maybeTime = rest.substring(0, 5);
      parseTimeOrNull(maybeTime, "startTime", idx);
      out.startTime = maybeTime;

      rest = rest.substring(5).trim();
    }

    String note = null;
    int hashIdx = rest.indexOf('#');
    if (hashIdx >= 0) {
      note = rest.substring(hashIdx + 1).trim();
      rest = rest.substring(0, hashIdx).trim();
    }

    String location = null;
    int atIdx = rest.indexOf('@');
    if (atIdx >= 0) {
      location = rest.substring(atIdx + 1).trim();
      rest = rest.substring(0, atIdx).trim();
    }

    String title = rest.trim();
    if (title.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
              "title is empty at line " + (idx + 1));
    }

    out.title = title;
    out.locationName = blankToNull(location);
    out.note = blankToNull(note);
    return out;
  }

  public static class UpdateCmd {
    public java.time.LocalDate dayDate;
    public java.time.LocalTime startTime;
    public java.time.LocalTime endTime;
    public String title;
    public String locationName;
    public String mapUrl;
    public String note;
  }
  @Transactional(readOnly = true)
  public PastePreviewResult previewPaste(String text) {
    if (text == null || text.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is empty");
    }

    var lines = text.replace("\r\n", "\n").split("\n");
    var items = new ArrayList<PastePreviewItem>();
    var errors = new ArrayList<PastePreviewError>();

    for (int i = 0; i < lines.length; i++) {
      int lineNo = i + 1;
      String line = lines[i].trim();
      if (line.isEmpty()) continue;

      try {
        ParsedLine p = parseLine(line, i); // 你之前那支 parseLine 可直接沿用
        items.add(new PastePreviewItem(
                lineNo,
                p.startTime,                 // "HH:mm" or null
                p.title,
                p.locationName,
                p.note
        ));
      } catch (ResponseStatusException ex) {
        // 我們把解析錯誤收集起來，不直接 throw
        errors.add(new PastePreviewError(lineNo, ex.getReason() == null ? "invalid line" : ex.getReason()));
      } catch (Exception ex) {
        errors.add(new PastePreviewError(lineNo, "invalid line"));
      }
    }

    return new PastePreviewResult(items, errors);
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

  public record PastePreviewItem(
          int lineNo,
          String startTime,
          String title,
          String locationName,
          String note
  ) {}

  public record PastePreviewError(
          int lineNo,
          String message
  ) {}

  public record PastePreviewResult(
          List<PastePreviewItem> items,
          List<PastePreviewError> errors
  ) {}


  public record ReorderItem(UUID id, int sortOrder) {}

  @Transactional(readOnly = true)
  public List<ItineraryItem> search(UUID tripId, String q, Integer limit) {
  String keyword = (q == null) ? "" : q.trim();
  if (keyword.isEmpty()) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q is required");
  }

  int lim = (limit == null) ? 50 : Math.min(Math.max(limit, 1), 200);
  return repo.searchInTrip(tripId, keyword, lim);
}

}
