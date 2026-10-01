package com.guicedee.activitymaster.conversations;

import com.guicedee.activitymaster.conversations.ConversationModels.*;
import com.guicedee.activitymaster.fsdm.client.services.IActiveFlagService;
import com.guicedee.activitymaster.fsdm.client.services.ISecurityTokenService;
import com.guicedee.activitymaster.fsdm.client.services.builders.warehouse.systems.ISystems;
import com.google.inject.Inject;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Private FSDM rows. Access is derived from live party membership and the trusted caller. */
public final class ConversationService implements IConversationService {
    @Inject private IActiveFlagService<?> flags;
    @Inject private ISecurityTokenService<?> security;
    private static final int MAX_PARTICIPANTS = 50;
    private static final int MAX_TEXT = 65_536;
    private static final OffsetDateTime END = OffsetDateTime.parse("2999-12-31T23:59:59Z");

    private record Scope(UUID enterprise, UUID system, UUID actor, String context) { }

    private Scope scope(ISystems<?, ?> system, ConversationIdentity identity) {
        if (system == null || identity == null || system.getEnterprise() == null
                || !ConversationSystem.NAME.equals(system.getName())
                || !identity.enterpriseId().equals(system.getEnterprise().getId()))
            throw new SecurityException("Conversation system scope mismatch");
        return new Scope(identity.enterpriseId(), system.getId(), identity.partyId(),
                identity.context().realm().name() + ":" + identity.context().ownerId());
    }

    private Uni<Scope> actor(Mutiny.StatelessSession session, ISystems<?, ?> system, ConversationIdentity identity) {
        Scope scope = scope(system, identity);
        if (session == null) return Uni.createFrom().failure(new BadRequestException("Session required"));
        return com.guicedee.client.IGuiceContext.get(com.guicedee.activitymaster.fsdm.plugins.PluginService.class)
                .checkBuiltIn(session, system, identity.user(), identity.installationPartyId())
                .chain(() -> session.createNativeQuery("select 1 from security.securitytoken k where k.securitytoken=:token "
                        + "and " + live("k"), Integer.class)
                .setParameter("token", identity.identityToken().toString())
                .setParameter("enterprise", scope.enterprise()).getResultList())
                .chain(identities -> {
                    if (identities.isEmpty()) return Uni.createFrom().failure(new SecurityException("Caller token unavailable"));
                    return session.createNativeQuery("select 1 from party.involvedparty p where p.involvedpartyid=:actor "
                                    + "and " + live("p") + " and (:work=true or exists (select 1 from party.involvedpartyorganic o "
                                    + "where o.involvedpartyorganicid=p.involvedpartyid and " + live("o") + "))", Integer.class)
                            .setParameter("actor", scope.actor()).setParameter("enterprise", scope.enterprise())
                            .setParameter("work", identity.context().realm() == com.guicedee.activitymaster.fsdm.transactions.ActivityScope.Realm.WORK)
                            .getResultList();
                }).chain(rows -> {
                    if (rows.isEmpty()) return Uni.createFrom().failure(new SecurityException("Actor unavailable"));
                    return security.getApplicableSecurityTokenIds(session, system, identity.tokens())
                            .chain(tokens -> tokens == null || tokens.isEmpty()
                                    ? Uni.createFrom().failure(new SecurityException("Actor token denied"))
                                    : session.createNativeQuery("select 1 from dbo.systemssecuritytoken g "
                                                    + "where g.systemid=:system and g.enterpriseid=:enterprise "
                                                    + "and g.securitytokenid in (:tokens) and g.readallowed=1 "
                                                    + "and " + live("g"), Integer.class)
                                            .setParameter("system", scope.system()).setParameter("enterprise", scope.enterprise())
                                            .setParameter("tokens", tokens).getResultList()
                                            .chain(systemGrants -> systemGrants.isEmpty()
                                                    ? Uni.createFrom().failure(new SecurityException("System token denied"))
                                                    : session.createNativeQuery("select 1 from party.involvedpartysecuritytoken g "
                                                    + "where g.involvedpartyid=:actor and g.enterpriseid=:enterprise "
                                                    + "and g.securitytokenid in (:tokens) and g.readallowed=1 "
                                                    + "and " + live("g"), Integer.class)
                                            .setParameter("actor", scope.actor()).setParameter("enterprise", scope.enterprise())
                                            .setParameter("tokens", tokens).getResultList()
                                            .chain(grants -> grants.isEmpty()
                                                    ? Uni.createFrom().failure(new SecurityException("Actor token denied"))
                                                    : Uni.createFrom().item(scope))));
                });
    }

    private static void transaction(Mutiny.StatelessSession session) {
        if (session.currentTransaction() == null)
            throw new IllegalStateException("Conversation writes require a caller-owned transaction");
    }

    private static void page(int offset, int limit) {
        if (offset < 0 || offset > 10_000 || limit < 1 || limit > 100)
            throw new BadRequestException("Offset must be 0..10000 and limit 1..100");
    }

    private static String text(Send request) {
        if (request == null || request.text() == null || request.text().isBlank() || request.text().length() > MAX_TEXT)
            throw new BadRequestException("Message text must be 1..65536 characters");
        String value = request.text();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == 0) throw new BadRequestException("Message text contains a null character");
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))
                    throw new BadRequestException("Message text contains an invalid surrogate");
            } else if (Character.isLowSurrogate(current)) {
                throw new BadRequestException("Message text contains an invalid surrogate");
            }
        }
        return value;
    }

    private static String live(String alias) {
        return alias + ".enterpriseid=:enterprise and " + alias + ".effectivefromdate<=statement_timestamp()"
                + " and " + alias + ".effectivetodate>statement_timestamp()"
                + " and exists (select 1 from dbo.activeflag f where f.activeflagid=" + alias + ".activeflagid and f.allowaccess=1)";
    }

    private static String conversationWhere() {
        return " from arrangement.arrangement a "
                + "join arrangement.arrangementxarrangementtype at on at.arrangementid=a.arrangementid "
                + "join arrangement.arrangementtype t on t.arrangementtypeid=at.arrangementtypeid "
                + "join arrangement.arrangementxclassification cx on cx.arrangementid=a.arrangementid "
                + "join classification.classification contextClass on contextClass.classificationid=cx.classificationid "
                + "join arrangement.arrangementxinvolvedparty m on m.arrangementid=a.arrangementid "
                + "join classification.classification memberClass on memberClass.classificationid=m.classificationid "
                + "where " + live("a") + " and " + live("at") + " and " + live("t")
                + " and " + live("cx") + " and " + live("contextClass") + " and " + live("m")
                + " and " + live("memberClass")
                + " and a.systemid=:system and at.systemid=:system and t.systemid=:system"
                + " and cx.systemid=:system and contextClass.systemid=:system"
                + " and m.systemid=:system and memberClass.systemid=:system"
                + " and t.arrangementtypename='Conversation' and contextClass.classificationname='ConversationContext'"
                + " and (cx.value=:context or (:social=true and cx.value like 'SOCIAL:%'))"
                + " and memberClass.classificationname='ConversationParticipant'"
                + " and m.involvedpartyid=:actor";
    }

    private Uni<Void> member(Mutiny.StatelessSession session, Scope scope, UUID id, boolean write) {
        if (id == null) return Uni.createFrom().failure(new BadRequestException("Conversation ID required"));
        return session.createNativeQuery("select 1" + conversationWhere() + " and a.arrangementid=:id"
                        + (write ? " for share of a,m" : ""), Integer.class)
                .setParameter("enterprise", scope.enterprise()).setParameter("system", scope.system())
                .setParameter("context", scope.context()).setParameter("actor", scope.actor())
                .setParameter("social", scope.context().startsWith("SOCIAL:"))
                .setParameter("id", id).getResultList()
                .chain(rows -> rows.isEmpty() ? Uni.createFrom().failure(new NotFoundException()) : Uni.createFrom().voidItem());
    }

    private Uni<UUID> definition(Mutiny.StatelessSession session, Scope scope, String table, String key,
                                 String nameColumn, String name) {
        return session.createNativeQuery("select " + key + " from " + table + " where enterpriseid=:enterprise"
                        + " and systemid=:system and " + nameColumn + "=:name"
                        + " and effectivefromdate<=statement_timestamp() and effectivetodate>statement_timestamp()", UUID.class)
                .setParameter("enterprise", scope.enterprise()).setParameter("system", scope.system())
                .setParameter("name", name).getResultList()
                .chain(rows -> rows.size() == 1 ? Uni.createFrom().item(rows.getFirst())
                        : Uni.createFrom().failure(new IllegalStateException("Conversation taxonomy unavailable: " + name)));
    }

    private Uni<UUID> classification(Mutiny.StatelessSession session, Scope scope, String name, String concept) {
        return session.createNativeQuery("select c.classificationid from classification.classification c "
                        + "join classification.classificationdataconcept d on d.classificationdataconceptid=c.classificationdataconceptid "
                        + "where c.enterpriseid=:enterprise and c.systemid=:system and c.classificationname=:name "
                        + "and d.classificationdataconceptname=:concept and " + live("c") + " and " + live("d"), UUID.class)
                .setParameter("enterprise", scope.enterprise()).setParameter("system", scope.system())
                .setParameter("name", name).setParameter("concept", concept).getResultList()
                .chain(rows -> rows.size() == 1 ? Uni.createFrom().item(rows.getFirst())
                        : Uni.createFrom().failure(new IllegalStateException("Conversation role unavailable: " + name)));
    }

    private Uni<Void> insert(Mutiny.StatelessSession session, ISystems<?, ?> system, ConversationIdentity identity,
                             String table, String idColumn, UUID id, Map<String, Object> values) {
        return flags.getActiveFlag(session, system.getEnterprise(), identity.tokens()).chain(flag -> {
                    Map<String, Object> columns = new LinkedHashMap<>(values);
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    columns.put(idColumn, id);
                    columns.put("enterpriseid", identity.enterpriseId());
                    columns.put("systemid", system.getId());
                    columns.put("originalsourcesystemid", system.getId());
                    columns.put("originalsourcesystemuniqueid", new UUID(0, 0));
                    columns.put("activeflagid", flag.getId());
                    columns.put("effectivefromdate", now);
                    columns.put("effectivetodate", END);
                    columns.put("warehousecreatedtimestamp", now);
                    columns.put("warehouselastupdatedtimestamp", now);
                    columns.put("warehousefromdate", now.toLocalDate());
                    String sql = "insert into " + table + " (" + String.join(",", columns.keySet()) + ") values ("
                            + columns.keySet().stream().map(c -> ":" + c).collect(java.util.stream.Collectors.joining(",")) + ")";
                    var query = session.createNativeQuery(sql);
                    columns.forEach(query::setParameter);
                    return query.executeUpdate().replaceWithVoid();
                });
    }

    @Override public Uni<Conversation> create(Mutiny.StatelessSession session, ISystems<?, ?> system,
                                                ConversationIdentity identity, Create request) {
        if (request == null || request.realm() == null || request.ownerId() == null)
            throw new BadRequestException("Conversation context required");
        if (identity == null || request.realm() != identity.context().realm()
                || !request.ownerId().equals(identity.context().ownerId()))
            throw new SecurityException("Conversation context mismatch");
        LinkedHashSet<UUID> parties = new LinkedHashSet<>(request.participants());
        if (parties.contains(null) || parties.size() > MAX_PARTICIPANTS - 1)
            throw new BadRequestException("Invalid participants");
        parties.add(identity.partyId());
        if (request.realm() == com.guicedee.activitymaster.fsdm.transactions.ActivityScope.Realm.PERSONAL && parties.size() != 1)
            throw new BadRequestException("Personal conversations are party-owned notes");
        transaction(session);
        return actor(session, system, identity).chain(scope -> {
            return session.createNativeQuery("select p.involvedpartyid from party.involvedparty p "
                            + "join dbo.activeflag f on f.activeflagid=p.activeflagid where p.involvedpartyid in (:participants) "
                            + "and p.enterpriseid=:enterprise and f.allowaccess=1 "
                            + "and p.effectivefromdate<=statement_timestamp() and p.effectivetodate>statement_timestamp()", UUID.class)
                    .setParameter("participants", parties).setParameter("enterprise", scope.enterprise())
                    .getResultList().chain(found -> {
                        if (found.size() != parties.size()) return Uni.createFrom().failure(new BadRequestException("Participant unavailable"));
                        UUID id = UUID.randomUUID();
                        return definition(session, scope, "arrangement.arrangementtype", "arrangementtypeid", "arrangementtypename", "Conversation")
                                .chain(type -> classification(session, scope, "ConversationType", "ArrangementXArrangementType")
                                .chain(typeRole -> classification(session, scope, "ConversationContext", "ArrangementXClassification")
                                .chain(contextRole -> classification(session, scope, "ConversationParticipant", "ArrangementXInvolvedParty")
                                .chain(memberRole -> insert(session, system, identity, "arrangement.arrangement", "arrangementid", id, Map.of())
                                        .chain(() -> insert(session, system, identity, "arrangement.arrangementxarrangementtype", "arrangementxarrangementtypeid", UUID.randomUUID(),
                                                Map.of("arrangementid", id, "arrangementtypeid", type, "classificationid", typeRole, "value", "1")))
                                        .chain(() -> insert(session, system, identity, "arrangement.arrangementxclassification", "arrangementxclassificationid", UUID.randomUUID(),
                                                Map.of("arrangementid", id, "classificationid", contextRole, "value", scope.context())))
                                        .chain(() -> participants(session, system, identity, id, memberRole, parties))
                                        .replaceWith(new Conversation(id, request.realm(), request.ownerId(), List.copyOf(parties)))))));
                    });
        });
    }

    private Uni<Void> participants(Mutiny.StatelessSession session, ISystems<?, ?> system, ConversationIdentity identity,
                                   UUID id, UUID role, Collection<UUID> parties) {
        Uni<Void> writes = Uni.createFrom().voidItem();
        for (UUID party : parties) {
            writes = writes.chain(() -> insert(session, system, identity, "arrangement.arrangementxinvolvedparty", "arrangementxinvolvedpartyid", UUID.randomUUID(),
                    Map.of("arrangementid", id, "involvedpartyid", party, "classificationid", role, "value", "1")));
        }
        return writes;
    }

    @Override public Uni<Conversation> find(Mutiny.StatelessSession session, ISystems<?, ?> system,
                                              ConversationIdentity identity, UUID id) {
        return actor(session, system, identity).chain(scope -> member(session, scope, id, false)
                .chain(() -> owner(session, scope, id).chain(owner -> session.createNativeQuery("select m.involvedpartyid from arrangement.arrangementxinvolvedparty m "
                        + "join classification.classification c on c.classificationid=m.classificationid "
                                + "join party.involvedparty p on p.involvedpartyid=m.involvedpartyid "
                                + "where m.arrangementid=:id and " + live("m") + " and " + live("p")
                                + " and " + live("c") + " and m.systemid=:system and c.systemid=:system"
                                + " and c.classificationname='ConversationParticipant' "
                                + "order by m.involvedpartyid", UUID.class)
                        .setParameter("id", id).setParameter("enterprise", scope.enterprise())
                        .setParameter("system", scope.system()).getResultList()
                        .map(parties -> new Conversation(id, identity.context().realm(), owner, parties)))));
    }

    private Uni<UUID> owner(Mutiny.StatelessSession session, Scope scope, UUID id) {
        if (!scope.context().startsWith("SOCIAL:"))
            return Uni.createFrom().item(UUID.fromString(scope.context().substring(scope.context().indexOf(':') + 1)));
        return session.createNativeQuery("select x.value from arrangement.arrangementxclassification x "
                        + "join classification.classification c on c.classificationid=x.classificationid "
                        + "where x.arrangementid=:id and x.systemid=:system and c.systemid=:system "
                        + "and c.classificationname='ConversationContext' and " + live("x") + " and " + live("c"), String.class)
                .setParameter("id", id).setParameter("enterprise", scope.enterprise())
                .setParameter("system", scope.system()).getSingleResult()
                .map(value -> UUID.fromString(value.substring("SOCIAL:".length())));
    }

    @Override public Uni<Page<Conversation>> list(Mutiny.StatelessSession session, ISystems<?, ?> system,
                                                    ConversationIdentity identity, int offset, int limit) {
        page(offset, limit);
        return actor(session, system, identity).chain(scope -> session.createNativeQuery("select distinct a.arrangementid" + conversationWhere()
                        + " order by a.arrangementid", UUID.class)
                .setParameter("enterprise", scope.enterprise()).setParameter("system", scope.system())
                .setParameter("context", scope.context()).setParameter("actor", scope.actor())
                .setParameter("social", scope.context().startsWith("SOCIAL:"))
                .setFirstResult(offset).setMaxResults(limit + 1).getResultList()
                .chain(ids -> {
                    Uni<List<Conversation>> items = Uni.createFrom().item(new ArrayList<Conversation>());
                    for (UUID id : ids.stream().limit(limit).toList())
                        items = items.chain(result -> find(session, system, identity, id).map(item -> { result.add(item); return result; }));
                    return items.map(result -> new Page<>(result, offset, limit, ids.size() > limit));
                }));
    }

    @Override public Uni<Message> send(Mutiny.StatelessSession session, ISystems<?, ?> system,
                                        ConversationIdentity identity, UUID id, Send request) {
        String body = text(request);
        transaction(session);
        return actor(session, system, identity).chain(scope -> member(session, scope, id, true)
                .chain(() -> definition(session, scope, "event.eventtype", "eventtypeid", "eventtypename", "Conversation Message")
                .chain(eventType -> definition(session, scope, "resource.resourceitemtype", "resourceitemtypeid", "resourceitemtypename", "Conversation Message Body")
                .chain(resourceType -> classification(session, scope, "ConversationMessageType", "EventXEventType")
                .chain(typeRole -> classification(session, scope, "ConversationMessage", "EventXArrangement")
                .chain(arrangementRole -> classification(session, scope, "ConversationSender", "EventXInvolvedParty")
                .chain(senderRole -> classification(session, scope, "ConversationBodyType", "ResourceItemXResourceItemType")
                .chain(bodyTypeRole -> classification(session, scope, "ConversationMessageBody", "EventXResourceItem")
                .chain(resourceRole -> {
                    UUID event = UUID.randomUUID(), resource = UUID.randomUUID();
                    return insert(session, system, identity, "event.event", "eventid", event,
                                    Map.of("dayid", 0, "hourid", 0, "minuteid", 0))
                            .chain(() -> insert(session, system, identity, "event.eventxeventtype", "eventxeventtypeid", UUID.randomUUID(),
                                    Map.of("eventid", event, "eventtypeid", eventType, "classificationid", typeRole, "value", "1")))
                            .chain(() -> insert(session, system, identity, "event.eventxarrangement", "eventxarrangementsid", UUID.randomUUID(),
                                    Map.of("eventid", event, "arrangementid", id, "classificationid", arrangementRole, "value", "1")))
                            .chain(() -> insert(session, system, identity, "event.eventxinvolvedparty", "eventxinvolvedpartyid", UUID.randomUUID(),
                                    Map.of("eventid", event, "involvedpartyid", scope.actor(), "classificationid", senderRole, "value", "1")))
                            .chain(() -> insert(session, system, identity, "resource.resourceitem", "resourceitemid", resource,
                                    Map.of("resourceitemdatatype", "Conversation Message Body")))
                            .chain(() -> insert(session, system, identity, "resource.resourceitemxresourceitemtype", "resourceitemxresourceitemtypeid", UUID.randomUUID(),
                                    Map.of("resourceitemid", resource, "resourceitemtypeid", resourceType, "classificationid", bodyTypeRole, "value", "1")))
                            // A relationship value is varchar(150); message text of any length is
                            // resource item data. resourceitemdatavalue is keyed by the resource item
                            // id and carries no security columns, so messages stay private rows.
                            .chain(() -> session.createNativeQuery("insert into resource.resourceitemdatavalue"
                                            + " (resourceitemdatavalueid, resourceitemdatavalue) values (:id, :data)")
                                    .setParameter("id", resource)
                                    .setParameter("data", body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                                    .executeUpdate())
                            .chain(() -> insert(session, system, identity, "event.eventxresourceitem", "eventxresourceitemid", UUID.randomUUID(),
                                    Map.of("eventid", event, "resourceitemid", resource, "classificationid", resourceRole, "value", "1")))
                            .chain(() -> session.createNativeQuery("select warehousecreatedtimestamp from event.event where eventid=:id", OffsetDateTime.class)
                                    .setParameter("id", event).getSingleResult())
                            .map(created -> new Message(event, id, scope.actor(), body, created));
                })))))))));
    }

    @Override public Uni<Page<Message>> messages(Mutiny.StatelessSession session, ISystems<?, ?> system,
                                                   ConversationIdentity identity, UUID id, int offset, int limit) {
        page(offset, limit);
        return actor(session, system, identity).chain(scope -> member(session, scope, id, false)
                .chain(() -> session.createNativeQuery("select e.eventid, s.involvedpartyid, convert_from(dv.resourceitemdatavalue,'UTF8'), e.warehousecreatedtimestamp "
                                + "from event.event e join event.eventxarrangement ea on ea.eventid=e.eventid "
                                + "join classification.classification ear on ear.classificationid=ea.classificationid "
                                + "join event.eventxinvolvedparty s on s.eventid=e.eventid "
                                + "join classification.classification sr on sr.classificationid=s.classificationid "
                                + "join event.eventxresourceitem er on er.eventid=e.eventid "
                                + "join classification.classification err on err.classificationid=er.classificationid "
                                + "join resource.resourceitem r on r.resourceitemid=er.resourceitemid "
                                + "join resource.resourceitemdatavalue dv on dv.resourceitemdatavalueid=r.resourceitemid "
                                + "where ea.arrangementid=:id and e.systemid=:system and " + live("e") + " and " + live("ea")
                                + " and " + live("s") + " and " + live("er") + " and " + live("r")
                                + " and ea.systemid=:system and s.systemid=:system and er.systemid=:system"
                                + " and r.systemid=:system"
                                + " and ear.systemid=:system and sr.systemid=:system and err.systemid=:system"
                                + " and " + live("ear") + " and " + live("sr") + " and " + live("err")
                                + " and ear.classificationname='ConversationMessage' and sr.classificationname='ConversationSender'"
                                + " and err.classificationname='ConversationMessageBody'"
                                + " order by e.warehousecreatedtimestamp,e.eventid", Object[].class)
                        .setParameter("id", id).setParameter("system", scope.system())
                        .setParameter("enterprise", scope.enterprise()).setFirstResult(offset).setMaxResults(limit + 1)
                        .getResultList().map(rows -> new Page<>(rows.stream().limit(limit).map(row ->
                                new Message((UUID) row[0], id, (UUID) row[1], (String) row[2], (OffsetDateTime) row[3]))
                                .toList(), offset, limit, rows.size() > limit))));
    }

    @Override public Uni<Void> leave(Mutiny.StatelessSession session, ISystems<?, ?> system,
                                      ConversationIdentity identity, UUID id) {
        transaction(session);
        return actor(session, system, identity).chain(scope -> member(session, scope, id, true)
                .chain(() -> session.createNativeQuery("update arrangement.arrangementxinvolvedparty m "
                                + "set effectivetodate=statement_timestamp(), warehouselastupdatedtimestamp=statement_timestamp() "
                                + "from classification.classification c "
                                + "where m.classificationid=c.classificationid and c.classificationname='ConversationParticipant' "
                                + "and c.systemid=:system and m.arrangementid=:id and m.involvedpartyid=:actor "
                                + "and m.systemid=:system and " + live("m"))
                        .setParameter("id", id).setParameter("actor", scope.actor())
                        .setParameter("system", scope.system()).setParameter("enterprise", scope.enterprise())
                        .executeUpdate().replaceWithVoid()));
    }
}
