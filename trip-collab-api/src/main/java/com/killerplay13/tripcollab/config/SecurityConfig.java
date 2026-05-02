package com.killerplay13.tripcollab.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.killerplay13.tripcollab.repo.TripMemberRepository;
import com.killerplay13.tripcollab.repo.TripRepository;
import com.killerplay13.tripcollab.security.MemberTokenFilter;
import com.killerplay13.tripcollab.security.TripTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
  private static final List<String> DEFAULT_ALLOWED_ORIGIN_PATTERNS =
      List.of(
          "http://localhost:5173",
          "http://localhost:5174",
          "https://trip-collab-web.vercel.app",
          "https://*.vercel.app"
      );

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      TripTokenFilter tripTokenFilter,
      MemberTokenFilter memberTokenFilter,
      CorsConfigurationSource corsConfigurationSource
  ) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .cors(cors -> cors.configurationSource(corsConfigurationSource))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .anyRequest().permitAll()
      )
      .addFilterBefore(tripTokenFilter, UsernamePasswordAuthenticationFilter.class)
      .addFilterAfter(memberTokenFilter, TripTokenFilter.class)
      .formLogin(form -> form.disable())
      .httpBasic(basic -> basic.disable());

    return http.build();
  }

  @Bean
  public TripTokenFilter tripTokenFilter(TripRepository tripRepository) {
    return new TripTokenFilter(tripRepository);
  }

  @Bean
  public MemberTokenFilter memberTokenFilter(TripMemberRepository tripMemberRepository) {
    return new MemberTokenFilter(tripMemberRepository);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Content-Type", "X-Trip-Token", "X-Member-Token", "Authorization"));
    config.setExposedHeaders(List.of("X-Trip-Token", "X-Member-Token"));
    config.setAllowCredentials(false);

    String rawOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
    List<String> originPatterns = parseAllowedOriginPatterns(rawOrigins);
    config.setAllowedOriginPatterns(originPatterns);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  private List<String> parseAllowedOriginPatterns(String rawOrigins) {
    Set<String> origins = new LinkedHashSet<>(DEFAULT_ALLOWED_ORIGIN_PATTERNS);
    if (rawOrigins == null || rawOrigins.isBlank()) {
      return List.copyOf(origins);
    }
    origins.addAll(Arrays.stream(rawOrigins.split(","))
      .map(String::trim)
      .filter(s -> !s.isBlank())
      .collect(Collectors.toList()));
    return List.copyOf(origins);
  }
}
