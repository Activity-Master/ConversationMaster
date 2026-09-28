package com.guicedee.activitymaster.conversations;

import com.google.inject.ImplementedBy;
import io.smallrye.mutiny.Uni;

/** The consuming host binds this to its authenticated, call-scoped identity. */
@ImplementedBy(ConversationIdentityProvider.Deny.class)
public interface ConversationIdentityProvider {
    Uni<ConversationIdentity> current();

    final class Deny implements ConversationIdentityProvider {
        @Override public Uni<ConversationIdentity> current() {
            return Uni.createFrom().failure(new SecurityException("Authenticated conversation identity required"));
        }
    }
}
