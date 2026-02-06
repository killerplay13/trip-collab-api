package com.killerplay13.tripcollab.security;

import com.killerplay13.tripcollab.domain.TripMemberEntity;
import com.killerplay13.tripcollab.repo.TripMemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

public class MemberTokenFilter extends OncePerRequestFilter {

  public static final String ATTR_MEMBER_ID = "memberId";
  public static final String ATTR_TRIP_ID = "tripId";
  public static final String ATTR_ROLE = "role";

  private final TripMemberRepository tripMemberRepository;

  public MemberTokenFilter(TripMemberRepository tripMemberRepository) {
    this.tripMemberRepository = tripMemberRepository;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();

    if (method.equalsIgnoreCase("OPTIONS")) return true;

    if (path.equals("/api/trips") && method.equalsIgnoreCase("POST")) return true;

    if (path.matches("^/api/trips/[^/]+/members$") && method.equalsIgnoreCase("POST")) return true;

    return !path.startsWith("/api/trips/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {

    String token = request.getHeader("X-Member-Token");
    if (token == null || token.isBlank()) {
      FilterErrorUtil.writeJsonError(request, response, 401, "Missing X-Member-Token");
      return;
    }

    UUID tripId = extractTripId(request.getRequestURI());
    if (tripId == null) {
      FilterErrorUtil.writeJsonError(request, response, 400, "Invalid trip id");
      return;
    }

    String tokenHash = TripTokenUtil.sha256Hex(token.trim());
    TripMemberEntity member = tripMemberRepository
        .findByMemberTokenHashAndIsActiveTrue(tokenHash)
        .orElse(null);
    if (member == null) {
      FilterErrorUtil.writeJsonError(request, response, 401, "Invalid member token");
      return;
    }

    if (!tripId.equals(member.getTripId())) {
      FilterErrorUtil.writeJsonError(request, response, 403, "Member token does not belong to this trip");
      return;
    }

    request.setAttribute(ATTR_MEMBER_ID, member.getId());
    request.setAttribute(ATTR_TRIP_ID, member.getTripId());
    request.setAttribute(ATTR_ROLE, member.getRole());

    filterChain.doFilter(request, response);
  }

  private UUID extractTripId(String uri) {
    // /api/trips/{tripId} or /api/trips/{tripId}/xxx
    String[] parts = uri.split("/");
    if (parts.length < 4) return null;
    try {
      return UUID.fromString(parts[3]);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
