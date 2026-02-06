package com.killerplay13.tripcollab.web;

import com.killerplay13.tripcollab.domain.ExpenseEntity;
import com.killerplay13.tripcollab.domain.ExpenseSplitEntity;
import com.killerplay13.tripcollab.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/trips/{tripId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    // ---------- DTOs ----------
    public record MemberAmount(UUID memberId, BigDecimal amount) {}

    public record OriginalMoney(
            BigDecimal amount,
            String currency,
            BigDecimal fxRate,
            String fxSource
    ) {}

    /**
     * amount/currency: always in trip base currency (used for settlements).
     * original.amount/original.currency/fxRate: for foreign currency expenses; system converts to base.
     * If paymentSource = SHARED_WALLET, original fields are required.
     * For SHARED_WALLET, amount override is not allowed; amount must equal original converted amount.
     */
    public record CreateOrUpdateExpenseRequest(
            String title,
            BigDecimal amount,
            String currency,
            String paymentSource,
            UUID paidByMemberId,
            LocalDate expenseDate,
            String note,
            UUID createdByMemberId,

            ExpenseService.SplitMethod splitMethod,
            List<UUID> participantMemberIds,
            List<MemberAmount> customSplits,
            OriginalMoney original
    ) {}

    public record ExpenseResponse(
            UUID id,
            UUID tripId,
            String title,
            BigDecimal amount,
            String currency,
            UUID paidByMemberId,
            LocalDate expenseDate,
            String note,
            UUID createdByMemberId,
            BigDecimal originalAmount,
            String originalCurrency,
            BigDecimal fxRate,
            String fxSource,
            BigDecimal computedAmount,
            Boolean amountOverridden
    ) {
        static ExpenseResponse from(ExpenseEntity e) {
            BigDecimal computed = null;
            if (e.getOriginalAmount() != null && e.getFxRate() != null) {
                computed = e.getOriginalAmount()
                        .multiply(e.getFxRate())
                        .setScale(2, java.math.RoundingMode.HALF_UP);
            }

            return new ExpenseResponse(
                    e.getId(), e.getTripId(), e.getTitle(), e.getAmount(), e.getCurrency(),
                    e.getPaidByMemberId(), e.getExpenseDate(), e.getNote(), e.getCreatedByMemberId(),

                    e.getOriginalAmount(),
                    e.getOriginalCurrency(),
                    e.getFxRate(),
                    e.getFxSource(),
                    computed,
                    e.getAmountOverridden()
            );
        }
    }

    public record SplitResponse(UUID memberId, BigDecimal shareAmount) {
        static SplitResponse from(ExpenseSplitEntity s) {
            return new SplitResponse(s.getMemberId(), s.getShareAmount());
        }
    }

    public record ExpenseSplitResponse(UUID memberId, BigDecimal shareAmount) {
        static ExpenseSplitResponse from(ExpenseSplitEntity s) {
            return new ExpenseSplitResponse(s.getMemberId(), s.getShareAmount());
        }
    }

    public record ExpenseDetailResponse(ExpenseResponse expense, List<SplitResponse> splits) {}

    public record MoveExpenseRequest(LocalDate newDate) {}

    // ---------- Endpoints ----------
    @GetMapping
    public List<ExpenseResponse> listDay(@PathVariable UUID tripId, @RequestParam LocalDate day) {
        return expenseService.listDay(tripId, day).stream().map(ExpenseResponse::from).toList();
    }

    @GetMapping("/all")
    public Map<LocalDate, List<ExpenseResponse>> listAllGrouped(@PathVariable UUID tripId) {
        var all = expenseService.listAll(tripId);
        var map = new LinkedHashMap<LocalDate, List<ExpenseResponse>>();
        for (var e : all) {
            map.computeIfAbsent(e.getExpenseDate(), k -> new ArrayList<>()).add(ExpenseResponse.from(e));
        }
        return map;
    }

    @GetMapping("/search")
    public List<ExpenseResponse> search(
            @PathVariable UUID tripId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return expenseService.search(tripId, q, from, to).stream().map(ExpenseResponse::from).toList();
    }

    @GetMapping("/{expenseId}")
    public ExpenseDetailResponse get(@PathVariable UUID tripId, @PathVariable UUID expenseId) {
        var e = expenseService.get(tripId, expenseId);
        var splits = expenseService.getSplits(expenseId).stream().map(SplitResponse::from).toList();
        return new ExpenseDetailResponse(ExpenseResponse.from(e), splits);
    }

    @GetMapping("/{expenseId}/splits")
    public List<ExpenseSplitResponse> listSplits(@PathVariable UUID tripId, @PathVariable UUID expenseId) {
        return expenseService.getSplitsByExpense(tripId, expenseId).stream()
                .map(ExpenseSplitResponse::from)
                .toList();
    }

    @PostMapping
    /**
     * amount/currency must be in trip base currency.
     * For foreign expenses, provide original fields with fxRate.
     */
    public ExpenseDetailResponse create(@PathVariable UUID tripId, @RequestBody CreateOrUpdateExpenseRequest req) {
        var custom = req.customSplits() == null ? null :
                req.customSplits().stream().map(x -> new ExpenseService.MemberAmount(x.memberId(), x.amount())).toList();
        var original = req.original();
        BigDecimal originalAmount = original != null ? original.amount() : null;
        String originalCurrency = original != null ? original.currency() : null;
        BigDecimal fxRate = original != null ? original.fxRate() : null;
        String fxSource = original != null ? original.fxSource() : null;

        var e = expenseService.create(
                tripId,
                req.title(),
                req.amount(),
                req.currency(),
                req.paymentSource(),
                req.paidByMemberId(),
                req.expenseDate(),
                req.note(),
                req.createdByMemberId(),
                req.splitMethod(),
                req.participantMemberIds(),
                custom,
                originalAmount,
                originalCurrency,
                fxRate,
                fxSource
        );

        var splits = expenseService.getSplits(e.getId()).stream().map(SplitResponse::from).toList();
        return new ExpenseDetailResponse(ExpenseResponse.from(e), splits);
    }

    @PutMapping("/{expenseId}")
    public ExpenseDetailResponse update(@PathVariable UUID tripId, @PathVariable UUID expenseId, @RequestBody CreateOrUpdateExpenseRequest req) {
        var custom = req.customSplits() == null ? null :
                req.customSplits().stream().map(x -> new ExpenseService.MemberAmount(x.memberId(), x.amount())).toList();
        var original = req.original();
        BigDecimal originalAmount = original != null ? original.amount() : null;
        String originalCurrency = original != null ? original.currency() : null;
        BigDecimal fxRate = original != null ? original.fxRate() : null;
        String fxSource = original != null ? original.fxSource() : null;

        var e = expenseService.update(
                tripId,
                expenseId,
                req.title(),
                req.amount(),
                req.currency(),
                req.paidByMemberId(),
                req.expenseDate(),
                req.note(),
                req.splitMethod(),
                req.participantMemberIds(),
                custom,
                originalAmount,
                originalCurrency,
                fxRate,
                fxSource
        );

        var splits = expenseService.getSplits(e.getId()).stream().map(SplitResponse::from).toList();
        return new ExpenseDetailResponse(ExpenseResponse.from(e), splits);
    }

    @DeleteMapping("/{expenseId}")
    public void delete(@PathVariable UUID tripId, @PathVariable UUID expenseId) {
        expenseService.delete(tripId, expenseId);
    }

    @PostMapping("/{expenseId}/move")
    public ExpenseResponse move(@PathVariable UUID tripId, @PathVariable UUID expenseId, @RequestBody MoveExpenseRequest req) {
        var e = expenseService.move(tripId, expenseId, req.newDate());
        return ExpenseResponse.from(e);
    }

    @GetMapping("/summary")
    public List<ExpenseService.MemberSummary> summary(@PathVariable UUID tripId) {
        return expenseService.summary(tripId);
    }

    @GetMapping("/settlements")
    public List<ExpenseService.SettlementTransfer> settlements(@PathVariable UUID tripId) {
        return expenseService.settlements(tripId);
    }

}
