package com.killerplay13.tripcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
    name = "itinerary_items",
    indexes = {
      @Index(name = "idx_itinerary_trip_day", columnList = "trip_id,day_date"),
      @Index(name = "idx_itinerary_trip_day_sort", columnList = "trip_id,day_date,sort_order")
    }
)
public class ItineraryItem {

  @Id
  @GeneratedValue
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "trip_id", nullable = false, columnDefinition = "uuid")
  private UUID tripId;

  @Column(name = "day_date", nullable = false)
  private LocalDate dayDate;

  @Column(name = "start_time")
  private LocalTime startTime;

  @Column(name = "end_time")
  private LocalTime endTime;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(name = "location_name", length = 120)
  private String locationName;

  @Column(name = "map_url", columnDefinition = "text")
  private String mapUrl;

  @Column(columnDefinition = "text")
  private String note;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;

  @Column(name = "created_by_member_id", columnDefinition = "uuid")
  private UUID createdByMemberId;

  @Column(name = "updated_by_member_id", columnDefinition = "uuid")
  private UUID updatedByMemberId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    this.updatedAt = Instant.now();
  }

  // getters/setters
  public UUID getId() { return id; }

  public UUID getTripId() { return tripId; }
  public void setTripId(UUID tripId) { this.tripId = tripId; }

  public LocalDate getDayDate() { return dayDate; }
  public void setDayDate(LocalDate dayDate) { this.dayDate = dayDate; }

  public LocalTime getStartTime() { return startTime; }
  public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

  public LocalTime getEndTime() { return endTime; }
  public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getLocationName() { return locationName; }
  public void setLocationName(String locationName) { this.locationName = locationName; }

  public String getMapUrl() { return mapUrl; }
  public void setMapUrl(String mapUrl) { this.mapUrl = mapUrl; }

  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }

  public int getSortOrder() { return sortOrder; }
  public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

  public UUID getCreatedByMemberId() { return createdByMemberId; }
  public void setCreatedByMemberId(UUID createdByMemberId) { this.createdByMemberId = createdByMemberId; }

  public UUID getUpdatedByMemberId() { return updatedByMemberId; }
  public void setUpdatedByMemberId(UUID updatedByMemberId) { this.updatedByMemberId = updatedByMemberId; }

  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
