package com.killerplay13.tripcollab.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class AuthGuard {

  public static ResponseEntity<String> requireMember(HttpServletRequest request) {
    Object memberId = request.getAttribute(MemberTokenFilter.ATTR_MEMBER_ID);
    if (!(memberId instanceof UUID)) {
      return buildErrorResponse(request, 401, "Missing member identity");
    }
    return null;
  }

  public static ResponseEntity<String> requireOwner(HttpServletRequest request) {
    ResponseEntity<String> memberGuard = requireMember(request);
    if (memberGuard != null) return memberGuard;

    Object roleObj = request.getAttribute(MemberTokenFilter.ATTR_ROLE);
    if (!(roleObj instanceof String)) {
      return buildErrorResponse(request, 401, "Missing member role");
    }

    String role = (String) roleObj;
    if (!"owner".equals(role)) {
      return buildErrorResponse(request, 403, "Owner role required");
    }

    return null;
  }

  private static ResponseEntity<String> buildErrorResponse(HttpServletRequest request, int status, String message) {
    String body = FilterErrorUtil.buildJsonError(request, status, message);
    return ResponseEntity.status(status)
        .contentType(MediaType.valueOf("application/json; charset=UTF-8"))
        .body(body);
  }

  private AuthGuard() {}
}
