# Conversation Master

Conversation Master is an independently discovered ActivityMaster plugin, never an
IMasterSystem. Runtime calls require a verified organic user's credential, a
current installation on the authorized party, individual dependency consent and
administrator policy, in addition to the existing domain/row permissions. The
identity's optional installationPartyId defaults to the verified party; hosts
must verify membership before selecting an organization installation. Legacy
System credentials are retired by forward updates while registration IDs and
domain data are retained. Provisioning grants no user installation or consent.


Conversation Master stores a conversation as a typed FSDM Arrangement. Its participants
are InvolvedParty records linked through ArrangementXInvolvedParty. Each sent message
is an Event linked to the Arrangement and its sender. The message body is a private
ResourceItem linked to that Event. The module adds no table or parallel conversation
store. Enterprise provisioning registers the plugin, catalogue/dependency and
arrangement/event/resource types and relationship classifications.
`ConversationPluginInstall` (1174) provisions the plugin independently of the
previously recorded domain update; `ConversationInstall` (1175) provisions taxonomy.

The consuming host supplies a verified ConversationIdentity through a binding of
ConversationIdentityProvider. The identity contains an involved party, enterprise,
FSDM context, ActivityMaster identifying token and installation party. Its
four-argument constructor defaults installation to the verified party. It must
never come from browser request data. The service checks current plugin
installation, dependency consent, administrator policy, the actor's live party
grant and membership on every read, send and leave. The conversation context (realm:ownerId) is stored on the
Arrangement. Personal and Social contexts are owned by the verified organic party;
Work context is owned by the authorized enterprise. A Social conversation stores
the creator as owner, while invited parties enter with their own verified Social
contexts and live membership. Personal conversations are private notes for one
party. All participants must be live involved parties in that enterprise. A
conversation supports at most 50 participants.

Conversation and message rows have no independent public grants. They are private
implementation records accessed through IConversationService. Hosts must not
expose generic, privileged FSDM row reads to untrusted callers. The caller owns
the stateless transaction; create and send await all related writes. Message text
is plain Unicode and limited to 65,536 UTF-16 code units.

REST base: /{enterprise}/conversations

* POST / creates Create(realm, ownerId, participants).
* GET / lists conversations in the current context.
* GET /{id} reads one conversation.
* POST /{id}/messages sends Send(text).
* GET /{id}/messages lists messages in creation order.
* DELETE /{id}/membership ends the caller's membership using FSDM effective dates.

Reads are paged with offset 0..10,000 and limit 1..100. The supplied owner must
equal the trusted host context. Participant IDs in a request are invite targets,
never proof of the caller's identity or authority.

## GraphQL

`ConversationGraphQLSchemaProvider` is registered through JPMS and
`META-INF/services`. It contributes typed operations to the host's GuicedEE
GraphQL endpoint, normally `/graphql`, using the same `ConversationApi` as REST.
Bind `ConversationIdentityProvider` to the verified HTTP caller. The adapter
subscribes within the server's call scope so the API captures that identity
before starting its stateless FSDM transaction.

Queries: `conversation`, `conversations`, `conversationMessages`.
Mutations: `conversationCreate`, `conversationSend`, `conversationLeave`.
All operations require `enterprise`; targets use `conversationId`. Pages default
to offset 0 and limit 50, with the same 0..10,000 offset and 1..100 limit bounds.
Successful leave returns `true` and ends only the caller's current membership.

```graphql
mutation SendMessage($input: ConversationSendInput!) {
  conversationSend(enterprise: "Example", conversationId: "<uuid>", input: $input) {
    id conversationId senderId text createdAt
  }
}
```

`ConversationCreateInput` contains `realm`, `ownerId` and optional `participants`
(default `[]`). `ConversationSendInput` contains `text`. The realm/owner still
must match the verified host context, and participant IDs remain invitation
targets. GraphQL accepts no caller identity or security token argument.

Typed pages expose `items`, `offset`, `limit` and `hasMore`. IDs are GraphQL `ID`
values and timestamps are ISO offset-date-time strings. Current membership gates
both reads and sends; unavailable conversations return `NOT_FOUND`. Other error
codes are `FORBIDDEN`, `BAD_USER_INPUT` and `INTERNAL_SERVER_ERROR`, with sanitized
messages in all cases.

The combined HTTP/PostgreSQL GraphQL regression is in
`../forums/src/test/java/com/guicedee/activitymaster/forums/test/CommunicationsGraphQLTest.java`.
