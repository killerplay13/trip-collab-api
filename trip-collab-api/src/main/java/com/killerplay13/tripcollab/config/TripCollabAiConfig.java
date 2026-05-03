package com.killerplay13.tripcollab.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TripCollabAiConfig {

  @Bean
  @Qualifier("tripCollabAiRestClient")
  public RestClient tripCollabAiRestClient(TripCollabAiProperties properties, RestClient.Builder builder) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
    requestFactory.setConnectTimeout(timeout);
    requestFactory.setReadTimeout(timeout);

    return builder
        .baseUrl(normalizeBaseUrl(properties))
        .requestFactory(requestFactory)
        .build();
  }

  private static String normalizeBaseUrl(TripCollabAiProperties properties) {
    String baseUrl = properties.getBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      if (properties.isEnabled()) {
        throw new IllegalStateException("TRIP_COLLAB_AI_BASE_URL is required when TRIP_COLLAB_AI_ENABLED=true");
      }
      return "http://localhost:8000";
    }
    String trimmed = baseUrl.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
