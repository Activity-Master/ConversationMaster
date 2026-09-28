package com.guicedee.activitymaster.conversations;

import com.guicedee.client.services.config.IGuiceScanModuleInclusions;
import java.util.Set;

public final class ConversationInclusionModule implements IGuiceScanModuleInclusions<ConversationInclusionModule> {
    @Override public Set<String> includeModules() { return Set.of("com.guicedee.activitymaster.conversations"); }
}
