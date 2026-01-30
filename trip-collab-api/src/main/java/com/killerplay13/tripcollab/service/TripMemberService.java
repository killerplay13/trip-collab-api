package com.killerplay13.tripcollab.service;

import com.killerplay13.tripcollab.domain.TripMemberEntity;
import com.killerplay13.tripcollab.repo.TripMemberRepository;
import com.killerplay13.tripcollab.security.TripTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripMemberService {

    private final TripMemberRepository tripMemberRepository;

    public List<TripMemberEntity> listActive(UUID tripId) {
        return tripMemberRepository.findByTripIdAndIsActiveTrueOrderByJoinedAtAsc(tripId);
    }

    public TripMemberEntity get(UUID tripId, UUID memberId) {
        return tripMemberRepository.findByIdAndTripId(memberId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    }

    @Transactional
    public CreatedMember create(UUID tripId, String nickname, String role) {
        var nn = requireNonBlank(nickname, "nickname");
        if (nn.length() > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nickname too long");

        var r = normalizeRole(role);

        // avoid DB unique constraint error
        if (tripMemberRepository.existsByTripIdAndNickname(tripId, nn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nickname already exists in this trip");
        }

        String rawToken = TripTokenUtil.generateToken();
        String tokenHash = TripTokenUtil.sha256Hex(rawToken);


        var now = Instant.now();
        var entity = TripMemberEntity.builder()
                .tripId(tripId)
                .nickname(nn)
                .role(r)
                .memberTokenHash(tokenHash)
                .isActive(true)
                .joinedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        entity = tripMemberRepository.save(entity);
        return new CreatedMember(entity, rawToken);
    }

    @Transactional
    public TripMemberEntity update(UUID tripId, UUID memberId, String nickname, Boolean isActive) {
        var m = get(tripId, memberId);

        if (nickname != null) {
            var nn = requireNonBlank(nickname, "nickname");
            if (!nn.equals(m.getNickname()) && tripMemberRepository.existsByTripIdAndNickname(tripId, nn)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nickname already exists in this trip");
            }
            m.setNickname(nn);
        }

        if (isActive != null) {
            m.setIsActive(isActive);
        }

        return tripMemberRepository.save(m);
    }

    // ---------- helpers ----------
    public record CreatedMember(TripMemberEntity member, String memberToken) {}

    private static String requireNonBlank(String v, String field) {
        if (v == null || v.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        return v.trim();
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "member";
        var r = role.trim().toLowerCase(Locale.ROOT);
        if (!r.equals("owner") && !r.equals("member")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be owner or member");
        }
        return r;
    }

}
