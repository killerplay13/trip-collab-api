package com.killerplay13.tripcollab.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class TripTokenUtil {
  private static final SecureRandom RND = new SecureRandom();

  // 32 bytes -> 64 hex chars (token)
  public static String generateToken() {
    byte[] bytes = new byte[32];
    RND.nextBytes(bytes);
    return toHex(bytes);
  }

  public static String sha256Hex(String token) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
      return toHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private TripTokenUtil() {}
}
