package com.killerplay13.tripcollab.service;

import com.killerplay13.tripcollab.domain.WalletTransactionEntity;
import com.killerplay13.tripcollab.repo.SharedWalletRepository;
import com.killerplay13.tripcollab.repo.WalletBalanceRepository;
import com.killerplay13.tripcollab.repo.WalletTransactionRepository;
import com.killerplay13.tripcollab.wallet.dto.WalletDepositRequest;
import com.killerplay13.tripcollab.wallet.dto.WalletExchangeRequest;
import com.killerplay13.tripcollab.wallet.dto.WalletExchangeResponse;
import com.killerplay13.tripcollab.wallet.dto.WalletTransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletCommandService {

    private final SharedWalletRepository sharedWalletRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public WalletTransactionResponse deposit(UUID tripId, UUID actorMemberId, WalletDepositRequest req) {
        var wallet = sharedWalletRepository.findByTripId(tripId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shared wallet not found for trip " + tripId
                ));

        BigDecimal originalAmount = requirePositiveAmount(req.originalAmount(), "originalAmount")
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal fxRate = requirePositiveAmount(req.fxRate(), "fxRate");
        String currency = normalizeCurrency(req.originalCurrency());

        BigDecimal computedBaseAmount = originalAmount.multiply(fxRate);

        var txn = WalletTransactionEntity.builder()
                .walletId(wallet.getId())
                .txnType("DEPOSIT")
                .direction("IN")
                .originalAmount(originalAmount)
                .originalCurrency(currency)
                .fxRate(fxRate)
                .computedBaseAmount(computedBaseAmount)
                .memberId(actorMemberId)
                .fxSource(req.fxSource())
                .note(req.note())
                .build();

        txn = walletTransactionRepository.save(txn);

        walletBalanceRepository.upsertBalance(wallet.getId(), currency, originalAmount);

        wallet.setUpdatedAt(Instant.now());
        sharedWalletRepository.save(wallet);

        return toResponse(txn);
    }

    @Transactional
    public WalletTransactionResponse recordExpense(
            UUID tripId,
            UUID expenseId,
            UUID memberId,
            BigDecimal originalAmount,
            String originalCurrency,
            BigDecimal fxRate,
            String fxSource,
            String note
    ) {
        var wallet = sharedWalletRepository.findByTripId(tripId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shared wallet not found for trip " + tripId
                ));

        if (expenseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expenseId is required");
        }

        if (walletTransactionRepository.existsByExpenseIdAndTxnType(expenseId, "EXPENSE")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Wallet expense transaction already exists"
            );
        }

        String currency = normalizeCurrency(originalCurrency);
        BigDecimal amount = requirePositiveAmount(originalAmount, "originalAmount")
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = requirePositiveAmount(fxRate, "fxRate");
        BigDecimal computedBase = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        int updated = walletBalanceRepository.debitIfSufficient(wallet.getId(), currency, amount);
        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient wallet balance in " + currency
            );
        }

        var txn = WalletTransactionEntity.builder()
                .walletId(wallet.getId())
                .txnType("EXPENSE")
                .direction("OUT")
                .originalAmount(amount)
                .originalCurrency(currency)
                .fxRate(rate)
                .computedBaseAmount(computedBase)
                .memberId(memberId)
                .expenseId(expenseId)
                .fxSource(fxSource)
                .note(note)
                .build();

        txn = walletTransactionRepository.save(txn);

        wallet.setUpdatedAt(Instant.now());
        sharedWalletRepository.save(wallet);

        return toResponse(txn);
    }

    @Transactional
    public WalletExchangeResponse exchange(UUID tripId, UUID actorMemberId, WalletExchangeRequest req) {
        var wallet = sharedWalletRepository.findByTripId(tripId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shared wallet not found for trip " + tripId
                ));

        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (req.from() == null || req.to() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to are required");
        }

        String fromCurrency = normalizeCurrency(req.from().currency(), "from.currency");
        String toCurrency = normalizeCurrency(req.to().currency(), "to.currency");
        if (fromCurrency.equals(toCurrency)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from.currency and to.currency must be different");
        }

        BigDecimal fromAmount = requirePositiveAmount(req.from().amount(), "from.amount")
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal toAmount = requirePositiveAmount(req.to().amount(), "to.amount")
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal fromFxRate = requirePositiveAmount(req.from().fxRateToBase(), "from.fxRateToBase");
        BigDecimal toFxRate = requirePositiveAmount(req.to().fxRateToBase(), "to.fxRateToBase");

        BigDecimal outBase = fromAmount.multiply(fromFxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal inBase = toAmount.multiply(toFxRate).setScale(2, RoundingMode.HALF_UP);

        int updated = walletBalanceRepository.debitIfSufficient(wallet.getId(), fromCurrency, fromAmount);
        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient wallet balance in " + fromCurrency
            );
        }

        UUID exchangeGroupId = UUID.randomUUID();

        var outTxn = WalletTransactionEntity.builder()
                .walletId(wallet.getId())
                .txnType("EXCHANGE")
                .direction("OUT")
                .originalAmount(fromAmount)
                .originalCurrency(fromCurrency)
                .fxRate(fromFxRate)
                .computedBaseAmount(outBase)
                .memberId(actorMemberId)
                .fxSource(req.fxSource())
                .note(req.note())
                .exchangeGroupId(exchangeGroupId)
                .build();

        var inTxn = WalletTransactionEntity.builder()
                .walletId(wallet.getId())
                .txnType("EXCHANGE")
                .direction("IN")
                .originalAmount(toAmount)
                .originalCurrency(toCurrency)
                .fxRate(toFxRate)
                .computedBaseAmount(inBase)
                .memberId(actorMemberId)
                .fxSource(req.fxSource())
                .note(req.note())
                .exchangeGroupId(exchangeGroupId)
                .build();

        outTxn = walletTransactionRepository.save(outTxn);
        inTxn = walletTransactionRepository.save(inTxn);

        walletBalanceRepository.upsertBalance(wallet.getId(), toCurrency, toAmount);

        wallet.setUpdatedAt(Instant.now());
        sharedWalletRepository.save(wallet);

        return new WalletExchangeResponse(
                exchangeGroupId,
                wallet.getId(),
                List.of(toResponse(outTxn), toResponse(inTxn))
        );
    }

    private static BigDecimal requirePositiveAmount(BigDecimal v, String field) {
        if (v == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        if (v.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be > 0");
        }
        return v;
    }

    private static String normalizeCurrency(String ccy) {
        return normalizeCurrency(ccy, "originalCurrency");
    }

    private static String normalizeCurrency(String ccy, String field) {
        if (ccy == null || ccy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        String v = ccy.trim().toUpperCase(Locale.ROOT);
        if (v.length() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be 3 letters");
        }
        return v;
    }

    private static WalletTransactionResponse toResponse(WalletTransactionEntity txn) {
        return new WalletTransactionResponse(
                txn.getId(),
                txn.getWalletId(),
                txn.getTxnType(),
                txn.getDirection(),
                txn.getOriginalAmount(),
                txn.getOriginalCurrency(),
                txn.getFxRate(),
                txn.getComputedBaseAmount(),
                txn.getMemberId(),
                txn.getExpenseId(),
                txn.getExchangeGroupId(),
                txn.getFxSource(),
                txn.getNote(),
                txn.getCreatedAt()
        );
    }
}
