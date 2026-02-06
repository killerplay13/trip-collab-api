package com.killerplay13.tripcollab.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;

final class FilterErrorUtil {

  static void writeJsonError(HttpServletRequest req, HttpServletResponse res, int status, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json; charset=UTF-8");
    res.getWriter().write(buildJsonError(req, status, message));
  }

  static String buildJsonError(HttpServletRequest req, int status, String message) {
    String error = switch (status) {
      case 401 -> "Unauthorized";
      case 403 -> "Forbidden";
      default -> "Bad Request";
    };
    String path = req.getRequestURI();
    String timestamp = Instant.now().toString();

    return "{"
        + "\"status\":" + status + ","
        + "\"error\":\"" + escapeJson(error) + "\","
        + "\"message\":\"" + escapeJson(message) + "\","
        + "\"path\":\"" + escapeJson(path) + "\","
        + "\"timestamp\":\"" + escapeJson(timestamp) + "\""
        + "}";
  }

  private static String escapeJson(String value) {
    if (value == null) return "";
    StringBuilder sb = new StringBuilder(value.length() + 16);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\\' -> sb.append("\\\\");
        case '"' -> sb.append("\\\"");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  private FilterErrorUtil() {}
}
