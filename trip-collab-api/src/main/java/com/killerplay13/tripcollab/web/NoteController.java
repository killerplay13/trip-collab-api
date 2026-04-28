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

@RestController
@RequestMapping("/api/trips/{tripId}/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    public record NoteResponse(
            UUID id,
            UUID tripId,
            UUID authorId,
            String title,
            String content,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static NoteResponse from(NoteEntity n) {
            return new NoteResponse(n.getId(), n.getTripId(), n.getAuthorId(), n.getTitle(), n.getContent(), n.getCreatedAt(), n.getUpdatedAt());
        }
    }

    public record CreateNoteRequest(String title, String content) {}
    public record UpdateNoteRequest(String title, String content) {}

    @GetMapping
    public ResponseEntity<List<NoteResponse>> list(@PathVariable UUID tripId, HttpServletRequest request) {
        ResponseEntity<String> guard = AuthGuard.requireMember(request);
        if (guard != null) return ResponseEntity.status(401).body(null); // Simple guard check

        var notes = noteService.listByTrip(tripId);
        return ResponseEntity.ok(notes.stream().map(NoteResponse::from).toList());
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
        return ResponseEntity.status(201).body(NoteResponse.from(note));
    }

    @GetMapping("/{noteId}")
    public ResponseEntity<?> get(@PathVariable UUID tripId, @PathVariable UUID noteId, HttpServletRequest request) {
        ResponseEntity<String> guard = AuthGuard.requireMember(request);
        if (guard != null) return guard;

        return ResponseEntity.ok(NoteResponse.from(noteService.get(noteId)));
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
        return ResponseEntity.ok(NoteResponse.from(note));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<?> delete(@PathVariable UUID tripId, @PathVariable UUID noteId, HttpServletRequest request) {
        ResponseEntity<String> guard = AuthGuard.requireMember(request);
        if (guard != null) return guard;

        noteService.delete(noteId);
        return ResponseEntity.noContent().build();
    }
}
