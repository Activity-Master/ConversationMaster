package com.guicedee.activitymaster.conversations;

import com.guicedee.activitymaster.fsdm.transactions.ActivityScope;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Immutable service and transport values. Identity and context are supplied by the host. */
public final class ConversationModels {
    private ConversationModels() { }

    public record Create(ActivityScope.Realm realm, UUID ownerId, List<UUID> participants) {
        public Create {
            participants = participants == null ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(participants));
        }
    }
    public record Conversation(UUID id, ActivityScope.Realm realm, UUID ownerId, List<UUID> participants) {
        public Conversation { participants = List.copyOf(participants); }
    }
    public record Send(String text) { }
    public record Message(UUID id, UUID conversationId, UUID senderId, String text, OffsetDateTime createdAt) { }
    public record Page<T>(List<T> items, int offset, int limit, boolean hasMore) {
        public Page { items = List.copyOf(items); }
    }
}
