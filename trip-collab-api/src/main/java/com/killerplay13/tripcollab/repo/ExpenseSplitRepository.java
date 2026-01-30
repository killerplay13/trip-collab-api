package com.killerplay13.tripcollab.repo;

import com.killerplay13.tripcollab.domain.ExpenseSplitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplitEntity, UUID> {
    List<ExpenseSplitEntity> findByExpenseId(UUID expenseId);
    void deleteByExpenseId(UUID expenseId);

    @Query("""
    select s.memberId, coalesce(sum(s.shareAmount), 0)
    from ExpenseSplitEntity s, ExpenseEntity e
    where s.expenseId = e.id
      and e.tripId = :tripId
    group by s.memberId
""")
    List<Object[]> sumOwedByMember(@Param("tripId") UUID tripId);

}
