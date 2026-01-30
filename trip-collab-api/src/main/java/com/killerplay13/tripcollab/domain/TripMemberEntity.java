package com.killerplay13.tripcollab.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "trip_members",
        indexes = {
                @Index(name = "idx_trip_members_trip_id", columnList = "trip_id")
        }
)
public class TripMemberEntity {

    public enum Role { owner, member }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    /**
     * DB constraint: ck_trip_members_role (owner/member)
     * 用 String 最不會踩 enum mapping 雷；你想更嚴謹再改 EnumType.STRING
     */
    @Column(name = "role", nullable = false, length = 20)
    private String role = "member";

    @Column(name = "member_token_hash", nullable = false, length = 64, unique = true)
    private String memberTokenHash;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        if (joinedAt == null) joinedAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (role == null || role.isBlank()) role = "member";
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
