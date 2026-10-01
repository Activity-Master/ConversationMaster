module com.guicedee.activitymaster.conversations {
    requires transitive com.guicedee.activitymaster.fsdm;
    requires com.guicedee.rest;
    requires com.guicedee.vertx.graphql;
    exports com.guicedee.activitymaster.conversations;
    exports com.guicedee.activitymaster.conversations.rest;
    exports com.guicedee.activitymaster.conversations.graphql;
    opens com.guicedee.activitymaster.conversations.graphql to com.google.guice;
    provides com.guicedee.vertx.graphql.services.IGraphQLSchemaProvider
        with com.guicedee.activitymaster.conversations.graphql.ConversationGraphQLSchemaProvider;
    opens com.guicedee.activitymaster.conversations to com.google.guice, tools.jackson.databind;
    opens com.guicedee.activitymaster.conversations.rest to com.google.guice, com.guicedee.rest, tools.jackson.databind;
    provides com.guicedee.client.services.lifecycle.IGuiceModule with com.guicedee.activitymaster.conversations.ConversationModule;
    provides com.guicedee.client.services.config.IGuiceScanModuleInclusions with com.guicedee.activitymaster.conversations.ConversationInclusionModule;
    provides com.guicedee.activitymaster.fsdm.client.services.systems.IMasterSystem with com.guicedee.activitymaster.conversations.ConversationSystem;
    provides com.guicedee.activitymaster.fsdm.client.services.systems.ISystemUpdate with com.guicedee.activitymaster.conversations.ConversationInstall;
}
