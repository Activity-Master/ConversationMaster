package com.guicedee.activitymaster.conversations.test;

import com.google.inject.Key;
import com.google.inject.name.Names;
import com.guicedee.activitymaster.conversations.*;
import com.guicedee.activitymaster.conversations.ConversationModels.*;
import com.guicedee.activitymaster.fsdm.client.services.*;
import com.guicedee.activitymaster.fsdm.client.services.administration.ActivityMasterConfiguration;
import com.guicedee.activitymaster.fsdm.transactions.ActivityScope;
import com.guicedee.client.IGuiceContext;
import com.guicedee.client.utils.Pair;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple4;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConversationStorageTest {
    private static final String ENTERPRISE = "ConversationTest";
    private IConversationService service;
    private UUID enterpriseId;
    private UUID actorId;
    private UUID recipientId;
    private UUID outsiderId;
    private UUID token;
    @Test void pluginAdmissionCannotBeReplacedByConversationMembershipOrPluginCredential() {
        assertFalse(com.guicedee.activitymaster.fsdm.client.services.systems.IMasterSystem.class
                .isAssignableFrom(ConversationSystem.class));
        var user = run(c -> PluginTestFixture.user(c.getItem1(), c.getItem2()));
        var identity = new ConversationIdentity(user.partyId(), user.enterpriseId(),
                new ActivityScope.Context(ActivityScope.Realm.PERSONAL, user.partyId()), user.identityToken());
        assertThrows(SecurityException.class, () -> run(c -> service.list(c.getItem1(), c.getItem3(), identity, 0, 10)));
        run(c -> PluginTestFixture.enable(c.getItem1(), c.getItem3(), user));
        Conversation conversation = run(c -> service.create(c.getItem1(), c.getItem3(), identity,
                new Create(ActivityScope.Realm.PERSONAL, user.partyId(), List.of())));
        run(c -> service.send(c.getItem1(), c.getItem3(), identity, conversation.id(), new Send("Retained content")));
        var forged = new ConversationIdentity(user.partyId(), user.enterpriseId(), identity.context(),
                run(c -> Uni.createFrom().item(c.getItem4()[0])));
        assertThrows(SecurityException.class, () -> run(c -> service.messages(c.getItem1(), c.getItem3(), forged, conversation.id(), 0, 10)));
        run(c -> IGuiceContext.get(com.guicedee.activitymaster.fsdm.plugins.PluginService.class)
                .remove(c.getItem1(), c.getItem3(), user, c.getItem3().getId(), user.partyId()));
        assertThrows(SecurityException.class, () -> run(c -> service.messages(c.getItem1(), c.getItem3(), identity, conversation.id(), 0, 10)));
        run(c -> IGuiceContext.get(com.guicedee.activitymaster.fsdm.plugins.PluginService.class)
                .install(c.getItem1(), c.getItem3(), user, c.getItem3().getId(), user.partyId()));
        assertThrows(SecurityException.class, () -> run(c -> service.find(c.getItem1(), c.getItem3(), identity, conversation.id())));
        run(c -> PluginTestFixture.enable(c.getItem1(), c.getItem3(), user));
        assertEquals("Retained content", run(c -> service.messages(c.getItem1(), c.getItem3(), identity, conversation.id(), 0, 10)).items().getFirst().text());
    }
    private final java.util.Map<UUID, UUID> tokens = new java.util.HashMap<>();

    @BeforeAll void setup() {
        var database = PostgreSQLTestDBModule.DATABASE;
        System.setProperty("ENVIRONMENT", "test");
        System.setProperty("FSDM_DBSERVER", "127.0.0.1");
        System.setProperty("FSDM_DBPORT", database.getFirstMappedPort().toString());
        System.setProperty("FSDM_DBNAME", database.getDatabaseName());
        System.setProperty("FSDM_USER", database.getUsername());
        System.setProperty("FSDM_PASSWORD", database.getPassword());
        System.setProperty("FSDM_SSL_MODE", "disable");
        ActivityMasterConfiguration.get().setApplicationEnterpriseName(ENTERPRISE);
        IGuiceContext.instance();
        Mutiny.SessionFactory factory = IGuiceContext.get(Key.get(Mutiny.SessionFactory.class, Names.named("ActivityMaster-Test")));
        service = IGuiceContext.get(IConversationService.class);
        IEnterpriseService<?> enterprises = IGuiceContext.get(IEnterpriseService.class);
        factory.withStatelessSession(session -> enterprises.startNewEnterprise(session, ENTERPRISE, "admin", "adminadmin!@"))
                .await().atMost(Duration.ofMinutes(5));
        factory.withStatelessTransaction(session -> enterprises.getEnterprise(session, ENTERPRISE)
                .chain(e -> enterprises.loadUpdates(session, e))).await().atMost(Duration.ofMinutes(5));
        run(c -> {
            enterpriseId = c.getItem2().getId();
            return PluginTestFixture.user(c.getItem1(), c.getItem2()).chain(actor -> {
                actorId = actor.partyId(); token = actor.identityToken(); tokens.put(actor.partyId(), actor.identityToken());
                return PluginTestFixture.enable(c.getItem1(), c.getItem3(), actor);
            }).chain(() -> PluginTestFixture.user(c.getItem1(), c.getItem2())).chain(recipient -> {
                recipientId = recipient.partyId(); tokens.put(recipient.partyId(), recipient.identityToken());
                return PluginTestFixture.enable(c.getItem1(), c.getItem3(), recipient);
            }).chain(() -> PluginTestFixture.user(c.getItem1(), c.getItem2())).chain(outsider -> {
                outsiderId = outsider.partyId(); tokens.put(outsider.partyId(), outsider.identityToken());
                return PluginTestFixture.enable(c.getItem1(), c.getItem3(), outsider);
            });
        });
    }

    private <T> T run(Function<Tuple4<Mutiny.StatelessSession,
            com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.enterprise.IEnterprise<?, ?>,
            com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems<?, ?>, UUID[]>, Uni<T>> work) {
        return SessionUtils.withActivityMaster(ENTERPRISE, ConversationSystem.NAME, work)
                .await().atMost(Duration.ofMinutes(2));
    }

    private ConversationIdentity identity(UUID party) {
        return new ConversationIdentity(party, enterpriseId,
                new ActivityScope.Context(ActivityScope.Realm.WORK, enterpriseId), tokens.get(party));
    }

    private ConversationIdentity social(UUID party) {
        return new ConversationIdentity(party, enterpriseId,
                new ActivityScope.Context(ActivityScope.Realm.SOCIAL, party), tokens.get(party));
    }

    @Test void storesFsdmConversationAndRestrictsMessagesToMembers() {
        Conversation created = run(c -> service.create(c.getItem1(), c.getItem3(), identity(actorId),
                new Create(ActivityScope.Realm.WORK, enterpriseId, List.of(recipientId))));
        assertEquals(List.of(recipientId, actorId).size(), created.participants().size());
        Message sent = run(c -> service.send(c.getItem1(), c.getItem3(), identity(actorId), created.id(), new Send("Private message")));
        assertEquals(actorId, sent.senderId());
        assertEquals(created.id(), run(c -> service.list(c.getItem1(), c.getItem3(), identity(recipientId), 0, 10))
                .items().getFirst().id());
        assertTrue(run(c -> service.list(c.getItem1(), c.getItem3(), identity(outsiderId), 0, 10)).items().isEmpty());
        assertEquals("Private message", run(c -> service.messages(c.getItem1(), c.getItem3(), identity(recipientId), created.id(), 0, 10))
                .items().getFirst().text());
        assertEquals(sent.createdAt(), run(c -> service.messages(c.getItem1(), c.getItem3(), identity(actorId), created.id(), 0, 10))
                .items().getFirst().createdAt());
        assertThrows(BadRequestException.class, () -> run(c -> service.send(c.getItem1(), c.getItem3(),
                identity(actorId), created.id(), new Send("bad\u0000text"))));
        assertThrows(BadRequestException.class, () -> run(c -> service.send(c.getItem1(), c.getItem3(),
                identity(actorId), created.id(), new Send("\uD800"))));
        assertThrows(SecurityException.class, () -> run(c -> service.messages(c.getItem1(), c.getItem3(),
                new ConversationIdentity(actorId, enterpriseId,
                        new ActivityScope.Context(ActivityScope.Realm.WORK, enterpriseId), UUID.randomUUID()), created.id(), 0, 10)));
        assertThrows(NotFoundException.class, () -> run(c -> service.messages(c.getItem1(), c.getItem3(),
                identity(outsiderId), created.id(), 0, 10)));
        assertThrows(NotFoundException.class, () -> run(c -> service.send(c.getItem1(), c.getItem3(),
                identity(outsiderId), created.id(), new Send("intrusion"))));
        run(c -> c.getItem1().createNativeQuery("update arrangement.arrangementxinvolvedparty set effectivetodate=statement_timestamp() "
                        + "where arrangementid=:id and involvedpartyid=:party")
                .setParameter("id", created.id()).setParameter("party", recipientId).executeUpdate());
        assertThrows(NotFoundException.class, () -> run(c -> service.messages(c.getItem1(), c.getItem3(),
                identity(recipientId), created.id(), 0, 10)));
        assertTrue(run(c -> service.list(c.getItem1(), c.getItem3(), identity(recipientId), 0, 10)).items().isEmpty());
    }

    @Test void socialMembersKeepTheirOwnVerifiedPartyContext() {
        Conversation created = run(c -> service.create(c.getItem1(), c.getItem3(), social(actorId),
                new Create(ActivityScope.Realm.SOCIAL, actorId, List.of(recipientId))));
        assertEquals(actorId, run(c -> service.find(c.getItem1(), c.getItem3(), social(recipientId), created.id())).ownerId());
        Message reply = run(c -> service.send(c.getItem1(), c.getItem3(), social(recipientId), created.id(), new Send("Social reply")));
        assertEquals(recipientId, reply.senderId());
        assertThrows(NotFoundException.class, () -> run(c -> service.find(c.getItem1(), c.getItem3(), social(outsiderId), created.id())));
        assertThrows(SecurityException.class, () -> new ConversationIdentity(recipientId, enterpriseId,
                new ActivityScope.Context(ActivityScope.Realm.SOCIAL, actorId), token));
        run(c -> service.leave(c.getItem1(), c.getItem3(), social(recipientId), created.id()));
        assertThrows(NotFoundException.class, () -> run(c -> service.messages(c.getItem1(), c.getItem3(),
                social(recipientId), created.id(), 0, 10)));
        assertEquals(reply.id(), run(c -> service.messages(c.getItem1(), c.getItem3(), social(actorId), created.id(), 0, 10))
                .items().getFirst().id());
        assertThrows(BadRequestException.class, () -> run(c -> service.create(c.getItem1(), c.getItem3(),
                new ConversationIdentity(actorId, enterpriseId,
                        new ActivityScope.Context(ActivityScope.Realm.PERSONAL, actorId), token),
                new Create(ActivityScope.Realm.PERSONAL, actorId, List.of(recipientId)))));
    }
}
