package com.guicedee.activitymaster.conversations;

import com.google.inject.Inject;
import com.guicedee.activitymaster.conversations.ConversationModels.*;
import com.guicedee.activitymaster.fsdm.client.services.SessionUtils;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import java.util.UUID;

/** Captures trusted host identity and keeps it bound to the entire FSDM operation. */
public final class ConversationApi {
    private final ConversationIdentityProvider identities;
    private final IConversationService service;

    @Inject public ConversationApi(ConversationIdentityProvider identities, IConversationService service) {
        this.identities = identities;
        this.service = service;
    }

    @FunctionalInterface private interface Work<T> {
        Uni<T> run(Mutiny.StatelessSession session, ISystems<?, ?> system, ConversationIdentity identity);
    }

    private <T> Uni<T> execute(String enterprise, Work<T> work) {
        if (enterprise == null || enterprise.isBlank())
            return Uni.createFrom().failure(new IllegalArgumentException("Enterprise required"));
        return Uni.createFrom().deferred(identities::current)
                .onItem().ifNull().failWith(() -> new SecurityException("Authenticated conversation identity required"))
                .chain(identity -> SessionUtils.withActivityMaster(enterprise, ConversationSystem.NAME, tuple -> {
                    if (!identity.enterpriseId().equals(tuple.getItem2().getId()))
                        return Uni.createFrom().failure(new SecurityException("Conversation enterprise scope mismatch"));
                    return work.run(tuple.getItem1(), tuple.getItem3(), identity);
                }));
    }

    public Uni<Conversation> create(String enterprise, Create request) {
        return execute(enterprise, (session, system, identity) -> service.create(session, system, identity, request));
    }
    public Uni<Conversation> find(String enterprise, UUID id) {
        return execute(enterprise, (session, system, identity) -> service.find(session, system, identity, id));
    }
    public Uni<Page<Conversation>> list(String enterprise, int offset, int limit) {
        return execute(enterprise, (session, system, identity) -> service.list(session, system, identity, offset, limit));
    }
    public Uni<Message> send(String enterprise, UUID id, Send request) {
        return execute(enterprise, (session, system, identity) -> service.send(session, system, identity, id, request));
    }
    public Uni<Page<Message>> messages(String enterprise, UUID id, int offset, int limit) {
        return execute(enterprise, (session, system, identity) -> service.messages(session, system, identity, id, offset, limit));
    }
    public Uni<Void> leave(String enterprise, UUID id) {
        return execute(enterprise, (session, system, identity) -> service.leave(session, system, identity, id));
    }
}
