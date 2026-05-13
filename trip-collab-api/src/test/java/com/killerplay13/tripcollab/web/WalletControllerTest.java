package com.killerplay13.tripcollab.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.killerplay13.tripcollab.security.MemberTokenFilter;
import com.killerplay13.tripcollab.service.WalletCommandService;
import com.killerplay13.tripcollab.service.WalletQueryService;
import com.killerplay13.tripcollab.wallet.dto.TotalsInBaseDto;
import com.killerplay13.tripcollab.wallet.dto.WalletBalanceDto;
import com.killerplay13.tripcollab.wallet.dto.WalletDepositRequest;
import com.killerplay13.tripcollab.wallet.dto.WalletSummaryResponse;
import com.killerplay13.tripcollab.wallet.dto.WalletTransactionListResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WalletControllerTest {

  private WalletQueryService walletQueryService;
  private WalletCommandService walletCommandService;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    walletQueryService = mock(WalletQueryService.class);
    walletCommandService = mock(WalletCommandService.class);
    mvc = MockMvcBuilders
        .standaloneSetup(new WalletController(walletQueryService, walletCommandService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void ownerCanDeposit() throws Exception {
    UUID tripId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    mvc.perform(post("/api/trips/{tripId}/wallet/deposits", tripId)
            .requestAttr(MemberTokenFilter.ATTR_MEMBER_ID, memberId)
            .requestAttr(MemberTokenFilter.ATTR_ROLE, "owner")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "originalAmount": 1000,
                  "originalCurrency": "TWD",
                  "fxRate": 1.0,
                  "note": "Deposit test"
                }
                """))
        .andExpect(status().isCreated());

    verify(walletCommandService).deposit(eq(tripId), eq(memberId), any(WalletDepositRequest.class));
  }

  @Test
  void memberCannotDeposit() throws Exception {
    UUID tripId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    mvc.perform(post("/api/trips/{tripId}/wallet/deposits", tripId)
            .requestAttr(MemberTokenFilter.ATTR_MEMBER_ID, memberId)
            .requestAttr(MemberTokenFilter.ATTR_ROLE, "member")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "originalAmount": 1000,
                  "originalCurrency": "TWD",
                  "fxRate": 1.0,
                  "note": "Deposit test"
                }
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberCannotWithdraw() throws Exception {
    UUID tripId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    mvc.perform(post("/api/trips/{tripId}/wallet/withdrawals", tripId)
            .requestAttr(MemberTokenFilter.ATTR_MEMBER_ID, memberId)
            .requestAttr(MemberTokenFilter.ATTR_ROLE, "member")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "originalAmount": 500,
                  "originalCurrency": "TWD",
                  "fxRate": 1.0,
                  "note": "Withdrawal test"
                }
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberCannotExchange() throws Exception {
    UUID tripId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    mvc.perform(post("/api/trips/{tripId}/wallet/exchanges", tripId)
            .requestAttr(MemberTokenFilter.ATTR_MEMBER_ID, memberId)
            .requestAttr(MemberTokenFilter.ATTR_ROLE, "member")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "from": { "currency": "TWD", "amount": 1000, "fxRateToBase": 1.0 },
                  "to": { "currency": "JPY", "amount": 4500, "fxRateToBase": 0.22 }
                }
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberCannotAdjust() throws Exception {
    UUID tripId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    mvc.perform(post("/api/trips/{tripId}/wallet/adjustments", tripId)
            .requestAttr(MemberTokenFilter.ATTR_MEMBER_ID, memberId)
            .requestAttr(MemberTokenFilter.ATTR_ROLE, "member")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "direction": "IN",
                  "originalAmount": 10,
                  "originalCurrency": "TWD",
                  "fxRate": 1.0,
                  "note": "Adjustment test"
                }
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberCanReadWalletSummary() throws Exception {
    UUID tripId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    when(walletQueryService.getSummary(tripId))
        .thenReturn(new WalletSummaryResponse(
            1L,
            tripId,
            "TWD",
            List.of(new WalletBalanceDto("TWD", new BigDecimal("1000.00"))),
            new TotalsInBaseDto(
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            ),
            Instant.now()
        ));

    mvc.perform(get("/api/trips/{tripId}/wallet", tripId)
            .requestAttr(MemberTokenFilter.ATTR_MEMBER_ID, memberId)
            .requestAttr(MemberTokenFilter.ATTR_ROLE, "member"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseCurrency").value("TWD"));

    verify(walletQueryService).getSummary(tripId);
  }

  @Test
  void memberCanReadWalletTransactions() throws Exception {
    UUID tripId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    when(walletQueryService.listTransactions(eq(tripId), any(), any(), any(), eq(0), eq(50)))
        .thenReturn(new WalletTransactionListResponse(List.of(), 0, 0, 50, 0));

    mvc.perform(get("/api/trips/{tripId}/wallet/transactions", tripId)
            .requestAttr(MemberTokenFilter.ATTR_MEMBER_ID, memberId)
            .requestAttr(MemberTokenFilter.ATTR_ROLE, "member"))
        .andExpect(status().isOk());

    verify(walletQueryService).listTransactions(eq(tripId), any(), any(), any(), eq(0), eq(50));
  }
}
