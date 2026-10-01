package com.guicedee.activitymaster.conversations;

import com.guicedee.activitymaster.fsdm.client.services.administration.MasterDefaultPlugin;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

public final class ConversationSystem extends MasterDefaultPlugin<ConversationSystem> {
    public static final String NAME = "Conversation Master";

    @Override public Uni<Void> createDefaults(Mutiny.StatelessSession session, IEnterprise<?, ?> enterprise) {
        return Uni.createFrom().voidItem();
    }
    @Override public Uni<Void> postStartup(Mutiny.StatelessSession session, IEnterprise<?, ?> enterprise) {
        return Uni.createFrom().voidItem();
    }
    @Override public String getSystemName() { return NAME; }
    @Override public String getSystemDescription() { return "FSDM conversations and messages between involved parties"; }
    @Override public Integer sortOrder() { return 1175; }
    @Override public int totalTasks() { return 1; }
}
