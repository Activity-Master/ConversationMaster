package com.guicedee.activitymaster.conversations;

import com.guicedee.activitymaster.conversations.ConversationModels.*;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import java.util.UUID;

/** Caller owns the stateless session and transaction; actor identity comes from the host. */
public interface IConversationService {
    Uni<Conversation> create(Mutiny.StatelessSession session, ISystems<?, ?> system, ConversationIdentity identity, Create request);
    Uni<Conversation> find(Mutiny.StatelessSession session, ISystems<?, ?> system, ConversationIdentity identity, UUID id);
    Uni<Page<Conversation>> list(Mutiny.StatelessSession session, ISystems<?, ?> system, ConversationIdentity identity, int offset, int limit);
    Uni<Message> send(Mutiny.StatelessSession session, ISystems<?, ?> system, ConversationIdentity identity, UUID id, Send request);
    Uni<Page<Message>> messages(Mutiny.StatelessSession session, ISystems<?, ?> system, ConversationIdentity identity, UUID id, int offset, int limit);
    Uni<Void> leave(Mutiny.StatelessSession session, ISystems<?, ?> system, ConversationIdentity identity, UUID id);
}
