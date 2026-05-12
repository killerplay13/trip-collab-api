package com.killerplay13.tripcollab.web;

import com.killerplay13.tripcollab.domain.NoteEntity;
import com.killerplay13.tripcollab.security.AuthGuard;
import com.killerplay13.tripcollab.security.MemberTokenFilter;
import com.killerplay13.tripcollab.service.NoteService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.killerplay13.tripcollab.repo.TripMemberRepository;

@RestController
@RequestMapping("/api/trips/{tripId}/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final TripMemberRepository tripMemberRepository;

    public record NoteResponse(
            UUID id,
            UUID tripId,
            UUID authorId,
            String title,
            String content,
            Instant createdAt,
            Instant updatedAt,
            UUID creatorMemberId,
            String creatorNickname
    ) {
        public static NoteResponse from(NoteEntity n, String creatorNickname) {
            return new NoteResponse(n.getId(), n.getTripId(), n.getAuthorId(), n.getTitle(), n.getContent(), n.getCreatedAt(), n.getUpdatedAt(), n.getAuthorId(), creatorNickname);
        }
    }

    public record CreateNoteRequest(String title, String content) {}
    public record UpdateNoteRequest(String title, String content) {}

    @GetMapping
    public ResponseEntity<List<NoteResponse>> list(@PathVariable UUID tripId, HttpServletRequest request) {
        ResponseEntity<String> guard = AuthGuard.requireMember(request);
        if (guard != null) return ResponseEntity.status(401).body(null); // Simple guard check

        var notes = noteService.listByTrip(tripId);
        
        var authorIds = notes.stream().map(NoteEntity::getAuthorId).filter(java.util.Objects::nonNull).distinct().toList();
        var authors = tripMemberRepository.findAllById(authorIds)
                .stream().collect(java.util.stream.Collectors.toMap(com.killerplay13.tripcollab.domain.TripMemberEntity::getId, com.killerplay13.tripcollab.domain.TripMemberEntity::getNickname));

        return ResponseEntity.ok(notes.stream().map(n -> NoteResponse.from(n, authors.get(n.getAuthorId()))).toList());
    }

    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable UUID tripId,
            @RequestBody CreateNoteRequest req,
            HttpServletRequest request
    ) {
        ResponseEntity<String> guard = AuthGuard.requireMember(request);
        if (guard != null) return guard;

        UUID authorId = (UUID) request.getAttribute(MemberTokenFilter.ATTR_MEMBER_ID);
        var note = noteService.create(tripId, authorId, req.title(), req.content());
        var nickname = tripMemberRepository.findById(note.getAuthorId())
                .map(com.killerplay13.tripcollab.domain.TripMemberEntity::getNickname).orElse(null);
        return ResponseEntity.status(201).body(NoteResponse.from(note, nickname));
    }

    @GetMapping("/{noteId}")
    public ResponseEntity<?> get(@PathVariable UUID tripId, @PathVariable UUID noteId, HttpServletRequest request) {
        ResponseEntity<String> guard = AuthGuard.requireMember(request);
        if (guard != null) return guard;

        var note = noteService.get(noteId);
        var nickname = tripMemberRepository.findById(note.getAuthorId())
                .map(com.killerplay13.tripcollab.domain.TripMemberEntity::getNickname).orElse(null);
        return ResponseEntity.ok(NoteResponse.from(note, nickname));
    }

    @PatchMapping("/{noteId}")
    public ResponseEntity<?> update(
            @PathVariable UUID tripId,
            @PathVariable UUID noteId,
            @RequestBody UpdateNoteRequest req,
            HttpServletRequest request
    ) {
        ResponseEntity<String> guard = AuthGuard.requireMember(request);
        if (guard != null) return guard;

        var note = noteService.update(noteId, req.title(), req.content());
        var nickname = tripMemberRepository.findById(note.getAuthorId())
                .map(com.killerplay13.tripcollab.domain.TripMemberEntity::getNickname).orElse(null);
        return ResponseEntity.ok(NoteResponse.from(note, nickname));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<?> delete(@PathVariable UUID tripId, @PathVariable UUID noteId, HttpServletRequest request) {
        ResponseEntity<String> guard = AuthGuard.requireMember(request);
        if (guard != null) return guard;

        noteService.delete(noteId);
        return ResponseEntity.noContent().build();
    }
}
