package com.killerplay13.tripcollab.service;

import com.killerplay13.tripcollab.repo.SharedWalletRepository;
import com.killerplay13.tripcollab.repo.WalletBalanceRepository;
import com.killerplay13.tripcollab.repo.WalletTransactionRepository;
import com.killerplay13.tripcollab.wallet.dto.TotalsInBaseDto;
import com.killerplay13.tripcollab.wallet.dto.WalletBalanceDto;
import com.killerplay13.tripcollab.wallet.dto.WalletSummaryResponse;
import com.killerplay13.tripcollab.wallet.dto.WalletTransactionListResponse;
import com.killerplay13.tripcollab.wallet.dto.WalletTransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletQueryService {

    private final SharedWalletRepository sharedWalletRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private static final Set<String> ALLOWED_TXN_TYPES = Set.of(
            "DEPOSIT", "EXCHANGE", "EXPENSE", "WITHDRAW", "ADJUSTMENT"
    );

    @Transactional(readOnly = true)
    public WalletSummaryResponse getSummary(UUID tripId) {
        var wallet = sharedWalletRepository.findByTripId(tripId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shared wallet not found for trip " + tripId
                ));

        var balances = walletBalanceRepository.findAllByWalletIdOrderByCurrencyAsc(wallet.getId()).stream()
                .map(b -> new WalletBalanceDto(b.getCurrency(), b.getBalance()))
                .toList();

        TotalsInBaseDto totals = walletTransactionRepository.aggregateTotalsInBase(wallet.getId());
        if (totals == null) {
            totals = new TotalsInBaseDto(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        return new WalletSummaryResponse(
                wallet.getId(),
                wallet.getTripId(),
                wallet.getBaseCurrency(),
                balances,
                totals,
                wallet.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public WalletTransactionListResponse listTransactions(
            UUID tripId,
            String currency,
            String txnType,
            UUID exchangeGroupId,
            int page,
            int size
    ) {
        var wallet = sharedWalletRepository.findByTripId(tripId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shared wallet not found for trip " + tripId
                ));

        String normalizedCurrency = normalizeCurrencyOrNull(currency);
        String normalizedTxnType = normalizeTxnTypeOrNull(txnType);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        var result = walletTransactionRepository.search(
                wallet.getId(),
                normalizedCurrency,
                normalizedTxnType,
                exchangeGroupId,
                PageRequest.of(safePage, safeSize)
        );

        var items = result.getContent().stream()
                .map(WalletQueryService::toResponse)
                .toList();

        return new WalletTransactionListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public WalletTransactionResponse getTransaction(UUID tripId, Long transactionId) {
        var wallet = sharedWalletRepository.findByTripId(tripId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shared wallet not found for trip " + tripId
                ));

        var txn = walletTransactionRepository.findByIdAndWalletId(transactionId, wallet.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transaction not found"
                ));

        return toResponse(txn);
    }

    private static String normalizeCurrencyOrNull(String currency) {
        if (currency == null) return null;
        if (currency.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency is required");
        }
        String v = currency.trim().toUpperCase(Locale.ROOT);
        if (v.length() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency must be 3 letters");
        }
        return v;
    }

    private static String normalizeTxnTypeOrNull(String txnType) {
        if (txnType == null) return null;
        if (txnType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "txnType is required");
        }
        String v = txnType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_TXN_TYPES.contains(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "txnType is invalid");
        }
        return v;
    }

    private static WalletTransactionResponse toResponse(com.killerplay13.tripcollab.domain.WalletTransactionEntity txn) {
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
