package com.guicedee.activitymaster.conversations;

import com.guicedee.activitymaster.fsdm.client.services.*;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.fsdm.client.services.classifications.EnterpriseClassificationDataConcepts;
import com.guicedee.activitymaster.fsdm.client.services.systems.ISystemUpdate;
import com.guicedee.activitymaster.fsdm.client.services.systems.SortedUpdate;
import com.guicedee.client.IGuiceContext;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import java.util.UUID;
import static com.guicedee.activitymaster.fsdm.client.services.IActivityMasterService.*;

/** Installs only FSDM taxonomy; conversation content is never imported at startup. */
@SortedUpdate(sortOrder = 1175, taskCount = 1)
public final class ConversationInstall implements ISystemUpdate {
    @Override public Uni<Boolean> update(Mutiny.StatelessSession session, IEnterprise<?, ?> enterprise) {
        ISystemsService<?> systems = IGuiceContext.get(ISystemsService.class);
        var extension = IGuiceContext.get(ConversationSystem.class);
        return systems.getActivityMaster(session, enterprise)
                .chain(core -> systems.getSecurityIdentityToken(session, core)
                        .chain(bootstrapToken -> IGuiceContext.get(com.guicedee.activitymaster.fsdm.plugins.PluginService.class)
                                .registerBuiltIn(session, core, bootstrapToken, extension)
                                .chain(() -> extension.getSystem(session, enterprise))
                                .chain(system -> install(session, system, bootstrapToken).replaceWith(true))));
    }

    private Uni<Void> install(Mutiny.StatelessSession session, ISystems<?, ?> system, UUID token) {
        IArrangementsService<?> arrangements = IGuiceContext.get(IArrangementsService.class);
        IEventService<?> events = IGuiceContext.get(IEventService.class);
        IResourceItemService<?> resources = IGuiceContext.get(IResourceItemService.class);
        IClassificationService<?> classes = IGuiceContext.get(IClassificationService.class);
        return arrangements.createArrangementType(session, "Conversation", system, token)
                .chain(() -> events.createEventType(session, "Conversation Message", system, token))
                .chain(() -> resources.createType(session, "Conversation Message Body", "Private conversation text", system, token))
                .chain(() -> role(session, classes, system, token, "ConversationType", EnterpriseClassificationDataConcepts.ArrangementXArrangementType))
                .chain(() -> role(session, classes, system, token, "ConversationContext", EnterpriseClassificationDataConcepts.ArrangementXClassification))
                .chain(() -> role(session, classes, system, token, "ConversationParticipant", EnterpriseClassificationDataConcepts.ArrangementXInvolvedParty))
                .chain(() -> role(session, classes, system, token, "ConversationMessageType", EnterpriseClassificationDataConcepts.EventXEventType))
                .chain(() -> role(session, classes, system, token, "ConversationMessage", EnterpriseClassificationDataConcepts.EventXArrangement))
                .chain(() -> role(session, classes, system, token, "ConversationSender", EnterpriseClassificationDataConcepts.EventXInvolvedParty))
                .chain(() -> role(session, classes, system, token, "ConversationBodyType", EnterpriseClassificationDataConcepts.ResourceItemXResourceItemType))
                .chain(() -> role(session, classes, system, token, "ConversationMessageBody", EnterpriseClassificationDataConcepts.EventXResourceItem))
                .replaceWithVoid();
    }

    private Uni<?> role(Mutiny.StatelessSession session, IClassificationService<?> classes, ISystems<?, ?> system,
                        UUID token, String name, EnterpriseClassificationDataConcepts concept) {
        return classes.create(session, name, name, concept, system, token);
    }
}
