package com.killerplay13.tripcollab.repo;

import com.killerplay13.tripcollab.domain.ItineraryItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {

  List<ItineraryItem> findByTripIdAndDayDateOrderBySortOrderAscStartTimeAscCreatedAtAsc(
      UUID tripId,
      LocalDate dayDate
  );

  Optional<ItineraryItem> findByIdAndTripId(UUID id, UUID tripId);

  @Query("select max(i.sortOrder) from ItineraryItem i where i.tripId = :tripId and i.dayDate = :dayDate")
  Integer findMaxSortOrder(UUID tripId, LocalDate dayDate);

  @Query("select i.id from ItineraryItem i where i.tripId = :tripId and i.dayDate = :dayDate")
List<UUID> findIdsByTripIdAndDayDate(@Param("tripId") UUID tripId, @Param("dayDate") LocalDate dayDate);


@Modifying
@Query("update ItineraryItem i set i.sortOrder = :sortOrder, i.updatedAt = CURRENT_TIMESTAMP where i.id = :id and i.tripId = :tripId")
int updateSortOrder(@Param("tripId") UUID tripId, @Param("id") UUID id, @Param("sortOrder") int sortOrder);

@Query("""
    select i from ItineraryItem i
    where i.tripId = :tripId
    order by i.dayDate asc, i.sortOrder asc, i.startTime asc, i.createdAt asc
  """)
  List<ItineraryItem> findAllByTrip(@Param("tripId") UUID tripId);

  @Query("""
    select i from ItineraryItem i
    where i.tripId = :tripId
      and i.dayDate >= :from
    order by i.dayDate asc, i.sortOrder asc, i.startTime asc, i.createdAt asc
  """)
  List<ItineraryItem> findAllByTripFrom(
      @Param("tripId") UUID tripId,
      @Param("from") LocalDate from
  );

  @Query("""
    select i from ItineraryItem i
    where i.tripId = :tripId
      and i.dayDate between :from and :to
    order by i.dayDate asc, i.sortOrder asc, i.startTime asc, i.createdAt asc
  """)
  List<ItineraryItem> findAllByTripInRange(
      @Param("tripId") UUID tripId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to
  );

  @Query(
  value = """
    select *
    from itinerary_items
    where trip_id = :tripId
      and (
        title ilike '%' || :q || '%'
        or coalesce(location_name,'') ilike '%' || :q || '%'
        or coalesce(note,'') ilike '%' || :q || '%'
      )
    order by day_date asc, sort_order asc, start_time asc nulls last, created_at asc , id asc                                                                                             
    limit :limit
  """,
  nativeQuery = true
)

List<ItineraryItem> searchInTrip(
    @Param("tripId") UUID tripId,
    @Param("q") String q,
    @Param("limit") int limit
);

  long countByTripIdAndDayDate(UUID tripId, LocalDate dayDate);

}
