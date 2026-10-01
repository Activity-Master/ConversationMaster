package com.guicedee.activitymaster.conversations.graphql;

import com.guicedee.activitymaster.conversations.ConversationApi;
import com.guicedee.activitymaster.conversations.ConversationModels.*;
import com.guicedee.activitymaster.fsdm.transactions.ActivityScope;
import com.guicedee.client.IGuiceContext;
import com.guicedee.vertx.graphql.services.IGraphQLSchemaProvider;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.guicedee.activitymaster.fsdm.graphql.ActivityMasterGraphQLAdapter.*;

/** GraphQL operations use the same verified-identity API as Conversation REST. */
public final class ConversationGraphQLSchemaProvider implements IGraphQLSchemaProvider<ConversationGraphQLSchemaProvider> {
    private final ConversationApi suppliedApi;
    public ConversationGraphQLSchemaProvider() { suppliedApi = null; }
    public ConversationGraphQLSchemaProvider(ConversationApi api) { suppliedApi = api; }
    private ConversationApi api() { return suppliedApi == null ? IGuiceContext.get(ConversationApi.class) : suppliedApi; }

    private static final String SDL = """
        enum ConversationRealm { PERSONAL SOCIAL WORK }
        type Conversation { id: ID!, realm: ConversationRealm!, ownerId: ID!, participants: [ID!]! }
        type ConversationMessage { id: ID!, conversationId: ID!, senderId: ID!, text: String!, createdAt: String! }
        type ConversationPage { items: [Conversation!]!, offset: Int!, limit: Int!, hasMore: Boolean! }
        type ConversationMessagePage { items: [ConversationMessage!]!, offset: Int!, limit: Int!, hasMore: Boolean! }
        input ConversationCreateInput { realm: ConversationRealm!, ownerId: ID!, participants: [ID!]! = [] }
        input ConversationSendInput { text: String! }
        extend type Query {
            conversation(enterprise: String!, conversationId: ID!): Conversation!
            conversations(enterprise: String!, offset: Int! = 0, limit: Int! = 50): ConversationPage!
            conversationMessages(enterprise: String!, conversationId: ID!, offset: Int! = 0, limit: Int! = 50): ConversationMessagePage!
        }
        extend type Mutation {
            conversationCreate(enterprise: String!, input: ConversationCreateInput!): Conversation!
            conversationSend(enterprise: String!, conversationId: ID!, input: ConversationSendInput!): ConversationMessage!
            conversationLeave(enterprise: String!, conversationId: ID!): Boolean!
        }
        """;

    @Override public TypeDefinitionRegistry getTypeDefinitions() { return new SchemaParser().parse(SDL); }
    @Override public RuntimeWiring.Builder configureWiring(RuntimeWiring.Builder builder) {
        return builder.type("Query", q -> q
                .dataFetcher("conversation", fetch("Conversation", e -> api().find(e.getArgument("enterprise"), id(e, "conversationId"))))
                .dataFetcher("conversations", fetch("Conversation", e -> api().list(e.getArgument("enterprise"), e.getArgument("offset"), e.getArgument("limit"))))
                .dataFetcher("conversationMessages", fetch("Conversation", e -> api().messages(e.getArgument("enterprise"), id(e, "conversationId"), e.getArgument("offset"), e.getArgument("limit")))))
            .type("Mutation", m -> m
                .dataFetcher("conversationCreate", fetch("Conversation", e -> {
                    Map<String, Object> input = e.getArgument("input");
                    return api().create(e.getArgument("enterprise"), new Create(ActivityScope.Realm.valueOf((String) input.get("realm")),
                            UUID.fromString((String) input.get("ownerId")), ids(strings(input, "participants"))));
                }))
                .dataFetcher("conversationSend", fetch("Conversation", e -> {
                    Map<String, Object> input = e.getArgument("input");
                    return api().send(e.getArgument("enterprise"), id(e, "conversationId"), new Send((String) input.get("text")));
                }))
                .dataFetcher("conversationLeave", fetch("Conversation", e -> api().leave(e.getArgument("enterprise"), id(e, "conversationId")).replaceWith(true))));
    }
    @SuppressWarnings("unchecked")
    private static List<String> strings(Map<String, Object> input, String key) { return (List<String>) input.get(key); }
}
