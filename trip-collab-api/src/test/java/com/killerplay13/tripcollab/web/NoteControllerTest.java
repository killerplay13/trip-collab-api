package com.killerplay13.tripcollab.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.killerplay13.tripcollab.domain.NoteEntity;
import com.killerplay13.tripcollab.domain.TripMemberEntity;
import com.killerplay13.tripcollab.repo.TripMemberRepository;
import com.killerplay13.tripcollab.security.MemberTokenFilter;
import com.killerplay13.tripcollab.service.NoteService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NoteControllerTest {

  private NoteService noteService;
  private TripMemberRepository tripMemberRepository;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    noteService = mock(NoteService.class);
    tripMemberRepository = mock(TripMemberRepository.class);
    mvc = MockMvcBuilders
        .standaloneSetup(new NoteController(noteService, tripMemberRepository))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void createNoteStoresCreatorMemberId() throws Exception {
    UUID tripId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    NoteEntity note = new NoteEntity();
    note.setId(UUID.randomUUID());
    note.setTripId(tripId);
    note.setAuthorId(authorId);
    note.setTitle("My Note");
    note.setContent("Content");
    note.setCreatedAt(Instant.now());
    note.setUpdatedAt(Instant.now());

    when(noteService.create(eq(tripId), eq(authorId), eq("My Note"), eq("Content"))).thenReturn(note);
    TripMemberEntity author = new TripMemberEntity();
    author.setId(authorId);
    author.setNickname("Alice");
    when(tripMemberRepository.findById(authorId)).thenReturn(Optional.of(author));

    mvc.perform(post("/api/trips/{tripId}/notes", tripId)
            .requestAttr(MemberTokenFilter.ATTR_MEMBER_ID, authorId)
            .requestAttr(MemberTokenFilter.ATTR_ROLE, "member")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "My Note",
                  "content": "Content"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.creatorMemberId").value(authorId.toString()))
        .andExpect(jsonPath("$.creatorNickname").value("Alice"));
  }

  @Test
  void listNotesReturnsCreatorNickname() throws Exception {
    UUID tripId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    NoteEntity note = new NoteEntity();
    note.setId(UUID.randomUUID());
    note.setTripId(tripId);
    note.setAuthorId(authorId);
    note.setTitle("My Note");
    note.setContent("Content");
    note.setCreatedAt(Instant.now());
    note.setUpdatedAt(Instant.now());

    when(noteService.listByTrip(tripId)).thenReturn(List.of(note));

    TripMemberEntity author = new TripMemberEntity();
    author.setId(authorId);
    author.setNickname("Alice");
    when(tripMemberRepository.findAllById(List.of(authorId))).thenReturn(List.of(author));

    mvc.perform(get("/api/trips/{tripId}/notes", tripId)
            .requestAttr(MemberTokenFilter.ATTR_MEMBER_ID, memberId)
            .requestAttr(MemberTokenFilter.ATTR_ROLE, "member"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].creatorMemberId").value(authorId.toString()))
        .andExpect(jsonPath("$[0].creatorNickname").value("Alice"));
  }

  @Test
  void existingNullCreatorNotesDoNotCrash() throws Exception {
    UUID tripId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    NoteEntity note = new NoteEntity();
    note.setId(UUID.randomUUID());
    note.setTripId(tripId);
    // authorId is null
    note.setTitle("My Note");
    note.setContent("Content");
    note.setCreatedAt(Instant.now());
    note.setUpdatedAt(Instant.now());

    when(noteService.listByTrip(tripId)).thenReturn(List.of(note));
    when(tripMemberRepository.findAllById(List.of())).thenReturn(List.of());

    mvc.perform(get("/api/trips/{tripId}/notes", tripId)
            .requestAttr(MemberTokenFilter.ATTR_MEMBER_ID, memberId)
            .requestAttr(MemberTokenFilter.ATTR_ROLE, "member"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].creatorMemberId").isEmpty())
        .andExpect(jsonPath("$[0].creatorNickname").isEmpty());
  }
}
