package com.killerplay13.tripcollab.security;

import com.killerplay13.tripcollab.repo.TripRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TripTokenFilter extends OncePerRequestFilter {

  private final TripRepository tripRepository;

  public TripTokenFilter(TripRepository tripRepository) {
    this.tripRepository = tripRepository;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();

    // allow creating trip without token
    if (path.equals("/api/trips") && method.equalsIgnoreCase("POST")) return true;

    return !path.startsWith("/api/trips/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {

    String token = request.getHeader("X-Trip-Token");
    if (token == null || token.isBlank()) {
      response.sendError(401, "Missing X-Trip-Token");
      return;
    }

    UUID tripId = extractTripId(request.getRequestURI());
    if (tripId == null) {
      response.sendError(400, "Invalid trip id");
      return;
    }

    String tokenHash = TripTokenUtil.sha256Hex(token.trim());
    boolean ok = tripRepository.existsByIdAndInviteTokenHashAndInviteEnabledTrue(tripId, tokenHash);
    if (!ok) {
      response.sendError(401, "Invalid trip token");
      return;
    }

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
