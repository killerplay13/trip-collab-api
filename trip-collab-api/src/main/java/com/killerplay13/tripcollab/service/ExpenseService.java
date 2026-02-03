package com.killerplay13.tripcollab.service;

import com.killerplay13.tripcollab.domain.ExpenseEntity;
import com.killerplay13.tripcollab.domain.ExpenseSplitEntity;
import com.killerplay13.tripcollab.domain.TripMemberEntity;
import com.killerplay13.tripcollab.repo.ExpenseRepository;
import com.killerplay13.tripcollab.repo.ExpenseSplitRepository;
import com.killerplay13.tripcollab.repo.TripMemberRepository;
import com.killerplay13.tripcollab.repo.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    public enum SplitMethod { EQUAL, CUSTOM_AMOUNT }

    private static final String PAYMENT_SOURCE_PERSONAL = "PERSONAL";
    private static final String PAYMENT_SOURCE_SHARED_WALLET = "SHARED_WALLET";

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository splitRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripRepository tripRepository;
    private final WalletCommandService walletCommandService;

    // ---------- Queries ----------
    public List<ExpenseEntity> listDay(UUID tripId, LocalDate day) {
        return expenseRepository.findByTripIdAndExpenseDateOrderByCreatedAtAsc(tripId, day);
    }

    public List<ExpenseEntity> listAll(UUID tripId) {
        return expenseRepository.findByTripIdOrderByExpenseDateAscCreatedAtAsc(tripId);
    }

    public List<ExpenseEntity> search(UUID tripId, String q, LocalDate from, LocalDate to) {
        return expenseRepository.search(tripId, q, from, to);
    }

    public ExpenseEntity get(UUID tripId, UUID expenseId) {
        return expenseRepository.findByIdAndTripId(expenseId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }

    public List<ExpenseSplitEntity> getSplits(UUID expenseId) {
        return splitRepository.findByExpenseId(expenseId);
    }

    public List<ExpenseSplitEntity> getSplitsByExpense(UUID tripId, UUID expenseId) {
        get(tripId, expenseId);
        return splitRepository.findByExpenseId(expenseId);
    }

    // ---------- Commands ----------
    @Transactional
    public ExpenseEntity create(
            UUID tripId,
            String title,
            BigDecimal amount,
            String currency,
            String paymentSource,
            UUID paidByMemberId,
            LocalDate expenseDate,
            String note,
            UUID createdByMemberId,
            SplitMethod splitMethod,
            List<UUID> participantMemberIds,
            List<MemberAmount> customSplits,
            BigDecimal originalAmount,
            String originalCurrency,
            BigDecimal fxRate,
            String fxSource
    ) {
        String normalizedPaymentSource = normalizePaymentSource(paymentSource);
        boolean isSharedWallet = PAYMENT_SOURCE_SHARED_WALLET.equals(normalizedPaymentSource);

        if (isSharedWallet) {
            if (originalCurrency == null || originalCurrency.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "originalCurrency is required for shared wallet payments");
            }
            if (originalAmount == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "originalAmount is required for shared wallet payments");
            }
            if (fxRate == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fxRate is required for shared wallet payments");
            }
        }

        validateMembers(tripId, paidByMemberId, participantMemberIds, customSplits, !isSharedWallet);

        String tripCurrency = getTripCurrency(tripId);

        FxResolved fx = resolveAmountInTripCurrency(tripCurrency, amount, currency, originalAmount, originalCurrency, fxRate);
        String normalizedOriginalCurrency = normalizeCurrencyNullable(originalCurrency);
        UUID paidByForStorage = isSharedWallet ? null : paidByMemberId;

        var expense = ExpenseEntity.builder()
                .tripId(tripId)
                .title(requireNonBlank(title, "title"))
                .amount(fx.finalAmount())
                .currency(tripCurrency)
                .paidByMemberId(paidByForStorage)
                .expenseDate(expenseDate != null ? expenseDate : LocalDate.now())
                .note(note)
                .createdByMemberId(createdByMemberId)
                .originalAmount(normalizeMoneyNullable(originalAmount))
                .originalCurrency(normalizedOriginalCurrency)
                .fxRate(fxRate)
                .fxSource(fxSource)
                .amountOverridden(fx.overridden())
                .paymentSource(normalizedPaymentSource)
                .build();

        expense = expenseRepository.save(expense);

        var splits = buildSplits(expense.getId(), fx.finalAmount(), splitMethod, participantMemberIds, customSplits);
        splitRepository.saveAll(splits);

        if (isSharedWallet) {
            var walletCurrency = normalizedOriginalCurrency != null ? normalizedOriginalCurrency : tripCurrency;
            var walletAmount = normalizedOriginalCurrency != null ? normalizeMoneyRequired(originalAmount, "original.amount") : fx.finalAmount();
            var walletFxRate = normalizedOriginalCurrency != null ? requirePositiveNumber(fxRate, "original.fxRate") : BigDecimal.ONE;

            walletCommandService.recordExpense(
                    tripId,
                    expense.getId(),
                    null,
                    walletAmount,
                    walletCurrency,
                    walletFxRate,
                    fxSource,
                    note
            );
        }

        return expense;
    }

    @Transactional
    public ExpenseEntity update(
            UUID tripId,
            UUID expenseId,
            String title,
            BigDecimal amount,
            String currency,
            UUID paidByMemberId,
            LocalDate expenseDate,
            String note,
            SplitMethod splitMethod,
            List<UUID> participantMemberIds,
            List<MemberAmount> customSplits,
            BigDecimal originalAmount,
            String originalCurrency,
            BigDecimal fxRate,
            String fxSource
    ) {
        var expense = get(tripId, expenseId);
        if (PAYMENT_SOURCE_SHARED_WALLET.equals(expense.getPaymentSource())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Editing shared-wallet-paid expenses is not supported in v0.1"
            );
        }

        validateMembers(tripId, paidByMemberId, participantMemberIds, customSplits, true);

        String tripCurrency = getTripCurrency(tripId);

        FxResolved fx = resolveAmountInTripCurrency(tripCurrency, amount, currency, originalAmount, originalCurrency, fxRate);

        expense.setTitle(requireNonBlank(title, "title"));
        expense.setAmount(fx.finalAmount());
        expense.setCurrency(tripCurrency);
        expense.setPaidByMemberId(paidByMemberId);
        expense.setExpenseDate(expenseDate != null ? expenseDate : expense.getExpenseDate());
        expense.setNote(note);
        expense.setOriginalAmount(normalizeMoneyNullable(originalAmount));
        expense.setOriginalCurrency(normalizeCurrencyNullable(originalCurrency));
        expense.setFxRate(fxRate);
        expense.setFxSource(fxSource);
        expense.setAmountOverridden(fx.overridden());

        expenseRepository.save(expense);

        // Replace splits
        splitRepository.deleteByExpenseId(expenseId);
        splitRepository.flush();
        var splits = buildSplits(expenseId, fx.finalAmount(), splitMethod, participantMemberIds, customSplits);
        splitRepository.saveAll(splits);

        return expense;
    }

    @Transactional
    public void delete(UUID tripId, UUID expenseId) {
        // ensure exists and belongs to trip
        var expense = get(tripId, expenseId);
        if (PAYMENT_SOURCE_SHARED_WALLET.equals(expense.getPaymentSource())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Deleting shared-wallet-paid expenses is not supported in v0.1"
            );
        }
        // splits cascade by FK, but we delete explicitly to be safe/clear
        splitRepository.deleteByExpenseId(expenseId);
        expenseRepository.deleteByIdAndTripId(expenseId, tripId);
    }

    @Transactional
    public ExpenseEntity move(UUID tripId, UUID expenseId, LocalDate newDate) {
        if (newDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newDate is required");
        }
        var expense = get(tripId, expenseId);
        expense.setExpenseDate(newDate);
        return expenseRepository.save(expense);
    }

    // ---------- Split building ----------
    public record MemberAmount(UUID memberId, BigDecimal amount) {}

    private record FxResolved(BigDecimal finalAmount, BigDecimal computedAmount, boolean overridden) {}

    private List<ExpenseSplitEntity> buildSplits(
            UUID expenseId,
            BigDecimal total,
            SplitMethod method,
            List<UUID> participantMemberIds,
            List<MemberAmount> customSplits
    ) {
        if (method == null) method = SplitMethod.EQUAL;

        return switch (method) {
            case EQUAL -> buildEqualSplits(expenseId, total, participantMemberIds);
            case CUSTOM_AMOUNT -> buildCustomAmountSplits(expenseId, total, customSplits);
        };
    }

    public record MemberSummary(
            UUID memberId,
            String nickname,
            java.math.BigDecimal paidTotal,
            java.math.BigDecimal owedTotal,
            java.math.BigDecimal net,
            String currency
    ) {}

    public List<MemberSummary> summary(UUID tripId) {
        String tripCurrency = getTripCurrency(tripId);
        // 1) members（用你已經有的 listActive）
        List<TripMemberEntity> members = tripMemberRepository.findByTripIdAndIsActiveTrueOrderByJoinedAtAsc(tripId);

        // 2) paid map
        Map<UUID, BigDecimal> paidMap = expenseRepository.sumPaidByMember(tripId).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((BigDecimal) row[1]).setScale(2)
                ));

        // 3) owed map
        Map<UUID, BigDecimal> owedMap = splitRepository.sumOwedByMember(tripId).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((BigDecimal) row[1]).setScale(2)
                ));

        // 4) merge
        return members.stream().map(m -> {
            BigDecimal paid = paidMap.getOrDefault(m.getId(), BigDecimal.ZERO).setScale(2);
            BigDecimal owed = owedMap.getOrDefault(m.getId(), BigDecimal.ZERO).setScale(2);
            BigDecimal net = paid.subtract(owed).setScale(2);
            return new MemberSummary(m.getId(), m.getNickname(), paid, owed, net, tripCurrency);
        }).toList();
    }

    public record SettlementTransfer(
            UUID fromMemberId,
            String fromNickname,
            UUID toMemberId,
            String toNickname,
            BigDecimal amount,
            String currency
    ) {}

    public List<SettlementTransfer> settlements(UUID tripId) {
        String tripCurrency = getTripCurrency(tripId);

        var summaries = summary(tripId);

        // creditors: net > 0 (should receive)
        // debtors: net < 0 (should pay)
        record Node(UUID id, String name, BigDecimal remaining) {}

        List<Node> creditors = new ArrayList<>();
        List<Node> debtors = new ArrayList<>();

        for (var s : summaries) {
            var net = s.net().setScale(2);
            int cmp = net.compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                creditors.add(new Node(s.memberId(), s.nickname(), net));
            } else if (cmp < 0) {
                debtors.add(new Node(s.memberId(), s.nickname(), net.abs())); // use positive remaining to pay
            }
        }

        // stable deterministic order (optional but recommended)
        creditors.sort(Comparator.comparing(Node::id));
        debtors.sort(Comparator.comparing(Node::id));

        List<SettlementTransfer> transfers = new ArrayList<>();
        int i = 0, j = 0;

        while (i < debtors.size() && j < creditors.size()) {
            var d = debtors.get(i);
            var c = creditors.get(j);

            BigDecimal pay = d.remaining().min(c.remaining()).setScale(2);
            if (pay.compareTo(BigDecimal.ZERO) > 0) {
                transfers.add(new SettlementTransfer(
                        d.id(), d.name(),
                        c.id(), c.name(),
                        pay,
                        tripCurrency
                ));
            }

            BigDecimal dLeft = d.remaining().subtract(pay).setScale(2);
            BigDecimal cLeft = c.remaining().subtract(pay).setScale(2);

            if (dLeft.compareTo(BigDecimal.ZERO) == 0) i++;
            else debtors.set(i, new Node(d.id(), d.name(), dLeft));

            if (cLeft.compareTo(BigDecimal.ZERO) == 0) j++;
            else creditors.set(j, new Node(c.id(), c.name(), cLeft));
        }

        return transfers;
    }


    private List<ExpenseSplitEntity> buildEqualSplits(UUID expenseId, BigDecimal total, List<UUID> participants) {
        if (participants == null || participants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "participantMemberIds is required for EQUAL split");
        }

        var seen = new HashSet<UUID>();
        for (var mid : participants) {
            if (mid == null) continue;
            if (!seen.add(mid)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate memberId in participantMemberIds");
            }
        }

        // stable order for deterministic remainder allocation
        var sorted = new ArrayList<>(participants);
        sorted.sort(Comparator.naturalOrder());

        int n = sorted.size();
        BigDecimal base = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(base.multiply(BigDecimal.valueOf(n))); // 0.00 ~ 0.(n-1)*0.01
        int extraCents = remainder.movePointRight(2).intValueExact();

        var result = new ArrayList<ExpenseSplitEntity>(n);
        for (int i = 0; i < n; i++) {
            BigDecimal add = (i < extraCents) ? new BigDecimal("0.01") : BigDecimal.ZERO;
            result.add(ExpenseSplitEntity.builder()
                    .expenseId(expenseId)
                    .memberId(sorted.get(i))
                    .shareAmount(base.add(add))
                    .build());
        }
        // guarantee sum == total
        return result;
    }

    private List<ExpenseSplitEntity> buildCustomAmountSplits(UUID expenseId, BigDecimal total, List<MemberAmount> customSplits) {
        if (customSplits == null || customSplits.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customSplits is required for CUSTOM_AMOUNT split");
        }

        BigDecimal sum = BigDecimal.ZERO;
        var seen = new HashSet<UUID>();
        var result = new ArrayList<ExpenseSplitEntity>(customSplits.size());

        for (var s : customSplits) {
            if (s == null || s.memberId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customSplits.memberId is required");
            }
            if (!seen.add(s.memberId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate memberId in customSplits");
            }
            BigDecimal amt = normalizeMoney(s.amount());
            if (amt.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customSplits.amount must be >= 0");
            }
            sum = sum.add(amt);
            result.add(ExpenseSplitEntity.builder()
                    .expenseId(expenseId)
                    .memberId(s.memberId())
                    .shareAmount(amt)
                    .build());
        }

        if (sum.compareTo(total) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sum(customSplits.amount) must equal total amount");
        }
        return result;
    }

    private String getTripCurrency(UUID tripId) {
        var trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        String currency = trip.getCurrency();
        if (currency == null || currency.isBlank()) {
            return "TWD";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private FxResolved resolveAmountInTripCurrency(
            String tripCurrency,
            BigDecimal reqAmount,
            String reqCurrency,
            BigDecimal originalAmount,
            String originalCurrency,
            BigDecimal fxRate
    ) {
        String normalizedReqCurrency = normalizeCurrencyNullable(reqCurrency);
        if (normalizedReqCurrency == null || !normalizedReqCurrency.equals(tripCurrency)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "currency must equal trip base currency (" + tripCurrency + "). Use originalAmount/originalCurrency + fxRate for foreign expenses."
            );
        }

        String normalizedOriginalCurrency = normalizeCurrencyNullable(originalCurrency);
        if (normalizedOriginalCurrency == null) {
            BigDecimal finalAmount = normalizeMoneyRequired(reqAmount, "amount");
            if (finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
            }
            return new FxResolved(finalAmount, null, false);
        }

        BigDecimal checkedOriginalAmount = normalizeMoneyRequired(originalAmount, "original.amount");
        if (checkedOriginalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "original.amount must be > 0");
        }
        BigDecimal checkedFxRate = requirePositiveNumber(fxRate, "original.fxRate");
        BigDecimal computed = checkedOriginalAmount.multiply(checkedFxRate).setScale(2, RoundingMode.HALF_UP);

        if (reqAmount == null) {
            return new FxResolved(computed, computed, false);
        }

        BigDecimal finalAmount = reqAmount.setScale(2, RoundingMode.HALF_UP);
        boolean overridden = finalAmount.compareTo(computed) != 0;
        return new FxResolved(finalAmount, computed, overridden);
    }

    // ---------- Validation helpers ----------
    private void validateMembers(
            UUID tripId,
            UUID paidBy,
            List<UUID> participants,
            List<MemberAmount> customSplits,
            boolean requirePaidBy
    ) {
        if (requirePaidBy) {
            if (paidBy == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paidByMemberId is required");
            if (!tripMemberRepository.existsByIdAndTripIdAndIsActiveTrue(paidBy, tripId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paidByMemberId is not an active member of this trip");
            }
        }

        // for EQUAL participants
        if (participants != null) {
            for (var mid : participants) {
                if (mid == null) continue;
                if (!tripMemberRepository.existsByIdAndTripIdAndIsActiveTrue(mid, tripId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "participant is not an active member of this trip: " + mid);
                }
            }
        }

        // for CUSTOM_AMOUNT members
        if (customSplits != null) {
            for (var s : customSplits) {
                if (s == null || s.memberId() == null) continue;
                if (!tripMemberRepository.existsByIdAndTripIdAndIsActiveTrue(s.memberId(), tripId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customSplits member is not an active member of this trip: " + s.memberId());
                }
            }
        }
    }

    private static String requireNonBlank(String v, String field) {
        if (v == null || v.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        return v.trim();
    }

    private static BigDecimal normalizeMoney(BigDecimal v) {
        if (v == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeMoneyRequired(BigDecimal v, String field) {
        if (v == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeMoneyNullable(BigDecimal v) {
        if (v == null) return null;
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal requirePositiveNumber(BigDecimal v, String field) {
        if (v == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        if (v.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be > 0");
        }
        return v;
    }

    private static String normalizeCurrency(String ccy) {
        if (ccy == null || ccy.isBlank()) return "TWD";
        var v = ccy.trim().toUpperCase(Locale.ROOT);
        if (v.length() != 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency must be 3 letters");
        return v;
    }

    private static String normalizeCurrencyNullable(String ccy) {
        if (ccy == null || ccy.isBlank()) return null;
        var v = ccy.trim().toUpperCase(Locale.ROOT);
        if (v.length() != 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency must be 3 letters");
        return v;
    }

    private static String normalizePaymentSource(String paymentSource) {
        if (paymentSource == null || paymentSource.isBlank()) {
            return PAYMENT_SOURCE_PERSONAL;
        }
        String v = paymentSource.trim().toUpperCase(Locale.ROOT);
        if (!PAYMENT_SOURCE_PERSONAL.equals(v) && !PAYMENT_SOURCE_SHARED_WALLET.equals(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentSource is invalid");
        }
        return v;
    }

}
