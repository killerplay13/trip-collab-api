package com.killerplay13.tripcollab.web;

import com.killerplay13.tripcollab.domain.TripMemberEntity;
import com.killerplay13.tripcollab.repo.TripMemberRepository;
import com.killerplay13.tripcollab.service.TripMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips/{tripId}/members")
@RequiredArgsConstructor
public class TripMemberController {

    private final TripMemberService tripMemberService;
    private final TripMemberRepository tripMemberRepository;

    // ---------- DTOs ----------
    public record MemberResponse(
            UUID id,
            String nickname,
            String role,
            Boolean isActive,
            Instant joinedAt
    ) {
        static MemberResponse from(TripMemberEntity m) {
            return new MemberResponse(m.getId(), m.getNickname(), m.getRole(), m.getIsActive(), m.getJoinedAt());
        }
    }

    public record CreateMemberRequest(String nickname, String role) {}

    public record CreateMemberResponse(MemberResponse member, String memberToken) {}

    public record PatchMemberRequest(String nickname, Boolean isActive) {}

    // ---------- Endpoints ----------
    @GetMapping
    public List<MemberResponse> list(@PathVariable UUID tripId) {
        return tripMemberRepository.findByTripIdAndIsActiveTrueOrderByJoinedAtAsc(tripId)
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    @PostMapping
    public CreateMemberResponse create(@PathVariable UUID tripId, @RequestBody CreateMemberRequest req) {
        var created = tripMemberService.create(tripId, req.nickname(), req.role());
        return new CreateMemberResponse(MemberResponse.from(created.member()), created.memberToken());
    }

    @GetMapping("/{memberId}")
    public MemberResponse get(@PathVariable UUID tripId, @PathVariable UUID memberId) {
        return MemberResponse.from(tripMemberService.get(tripId, memberId));
    }

    @PatchMapping("/{memberId}")
    public MemberResponse patch(@PathVariable UUID tripId, @PathVariable UUID memberId, @RequestBody PatchMemberRequest req) {
        var updated = tripMemberService.update(tripId, memberId, req.nickname(), req.isActive());
        return MemberResponse.from(updated);
    }
}
