package com.guicedee.activitymaster.conversations.rest;

import com.google.inject.Inject;
import com.guicedee.activitymaster.conversations.ConversationApi;
import com.guicedee.activitymaster.conversations.ConversationModels.*;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("{enterprise}/conversations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class ConversationRestService {
    @Inject private ConversationApi api;

    @POST public Uni<Conversation> create(@PathParam("enterprise") String enterprise, Create request) {
        return api.create(enterprise, request);
    }
    @GET @Path("{id}") public Uni<Conversation> find(@PathParam("enterprise") String enterprise,
                                                        @PathParam("id") UUID id) {
        return api.find(enterprise, id);
    }
    @GET public Uni<Page<Conversation>> list(@PathParam("enterprise") String enterprise,
                                               @QueryParam("offset") @DefaultValue("0") int offset,
                                               @QueryParam("limit") @DefaultValue("50") int limit) {
        return api.list(enterprise, offset, limit);
    }
    @POST @Path("{id}/messages") public Uni<Message> send(@PathParam("enterprise") String enterprise,
                                                             @PathParam("id") UUID id, Send request) {
        return api.send(enterprise, id, request);
    }
    @GET @Path("{id}/messages") public Uni<Page<Message>> messages(@PathParam("enterprise") String enterprise,
                                                                      @PathParam("id") UUID id,
                                                                      @QueryParam("offset") @DefaultValue("0") int offset,
                                                                      @QueryParam("limit") @DefaultValue("50") int limit) {
        return api.messages(enterprise, id, offset, limit);
    }
    @DELETE @Path("{id}/membership") public Uni<Void> leave(@PathParam("enterprise") String enterprise,
                                                               @PathParam("id") UUID id) {
        return api.leave(enterprise, id);
    }
}
