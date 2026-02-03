package com.killerplay13.tripcollab.web;

import com.killerplay13.tripcollab.service.WalletCommandService;
import com.killerplay13.tripcollab.service.WalletQueryService;
import com.killerplay13.tripcollab.wallet.dto.WalletDepositRequest;
import com.killerplay13.tripcollab.wallet.dto.WalletExchangeRequest;
import com.killerplay13.tripcollab.wallet.dto.WalletExchangeResponse;
import com.killerplay13.tripcollab.wallet.dto.WalletSummaryResponse;
import com.killerplay13.tripcollab.wallet.dto.WalletTransactionListResponse;
import com.killerplay13.tripcollab.wallet.dto.WalletTransactionResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/trips/{tripId}/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletQueryService walletQueryService;
    private final WalletCommandService walletCommandService;

    @PostConstruct
    void init() {
        System.out.println("[WalletController] loaded");
    }


    @GetMapping
    public WalletSummaryResponse getSummary(@PathVariable UUID tripId) {
        return walletQueryService.getSummary(tripId);
    }

    @GetMapping("/transactions")
    public WalletTransactionListResponse listTransactions(
            @PathVariable UUID tripId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String txnType,
            @RequestParam(required = false) UUID exchangeGroupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return walletQueryService.listTransactions(tripId, currency, txnType, exchangeGroupId, page, size);
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<WalletTransactionResponse> getTransaction(
            @PathVariable UUID tripId,
            @PathVariable Long transactionId
    ) {
        var result = walletQueryService.getTransaction(tripId, transactionId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/deposits")
    public ResponseEntity<WalletTransactionResponse> deposit(
            @PathVariable UUID tripId,
            @RequestBody WalletDepositRequest req
    ) {
        var result = walletCommandService.deposit(tripId, req);
        return ResponseEntity.status(201).body(result);
    }

    @PostMapping("/exchanges")
    public ResponseEntity<WalletExchangeResponse> exchange(
            @PathVariable UUID tripId,
            @RequestBody WalletExchangeRequest req
    ) {
        var result = walletCommandService.exchange(tripId, req);
        return ResponseEntity.status(201).body(result);
    }
}
