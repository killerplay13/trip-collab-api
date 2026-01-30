package com.killerplay13.tripcollab.repo;

import com.killerplay13.tripcollab.domain.ExpenseEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.*;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, UUID> {

    List<ExpenseEntity> findByTripIdAndExpenseDateOrderByCreatedAtAsc(UUID tripId, LocalDate expenseDate);

    List<ExpenseEntity> findByTripIdOrderByExpenseDateAscCreatedAtAsc(UUID tripId);

    @Query("""
        select e from ExpenseEntity e
        where e.tripId = :tripId
          and (:from is null or e.expenseDate >= :from)
          and (:to is null or e.expenseDate <= :to)
          and (:q is null or :q = '' or lower(e.title) like lower(concat('%', :q, '%')))
        order by e.expenseDate asc, e.createdAt asc
    """)
    List<ExpenseEntity> search(
            @Param("tripId") UUID tripId,
            @Param("q") String q,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    Optional<ExpenseEntity> findByIdAndTripId(UUID id, UUID tripId);

    void deleteByIdAndTripId(UUID id, UUID tripId);

    @Query("""
    select e.paidByMemberId, coalesce(sum(e.amount), 0)
    from ExpenseEntity e
    where e.tripId = :tripId
    group by e.paidByMemberId
    """)
    List<Object[]> sumPaidByMember(@Param("tripId") UUID tripId);

}
