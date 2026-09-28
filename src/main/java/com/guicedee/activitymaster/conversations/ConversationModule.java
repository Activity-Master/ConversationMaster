package com.guicedee.activitymaster.conversations;

import com.google.inject.PrivateModule;
import com.google.inject.Singleton;
import com.guicedee.client.services.lifecycle.IGuiceModule;

public final class ConversationModule extends PrivateModule implements IGuiceModule<ConversationModule> {
    @Override protected void configure() {
        bind(IConversationService.class).to(ConversationService.class).in(Singleton.class);
        expose(IConversationService.class);
        bind(ConversationApi.class).in(Singleton.class);
        expose(ConversationApi.class);
    }
}
