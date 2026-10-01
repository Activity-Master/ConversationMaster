package com.guicedee.activitymaster.conversations;

import com.guicedee.activitymaster.fsdm.client.services.ISystemsService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.systems.ISystemUpdate;
import com.guicedee.activitymaster.fsdm.client.services.systems.SortedUpdate;
import com.guicedee.activitymaster.fsdm.plugins.PluginService;
import com.guicedee.client.IGuiceContext;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

/** Forward conversion, including enterprises whose original domain update is already recorded. */
@SortedUpdate(sortOrder = 1174, taskCount = 1)
public final class ConversationPluginInstall implements ISystemUpdate {
    @Override public Uni<Boolean> update(Mutiny.StatelessSession session, IEnterprise<?, ?> enterprise) {
        ISystemsService<?> systems = IGuiceContext.get(ISystemsService.class);
        return systems.getActivityMaster(session, enterprise)
                .chain(core -> systems.getSecurityIdentityToken(session, core)
                        .chain(token -> IGuiceContext.get(PluginService.class).registerBuiltIn(session, core, token,
                                IGuiceContext.get(ConversationSystem.class))))
                .replaceWith(true);
    }
}
