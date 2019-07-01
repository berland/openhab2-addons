/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.habpanelfilter.internal.web;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.google.gson.Gson;
import io.swagger.annotations.*;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.auth.Role;
import org.eclipse.smarthome.core.events.Event;
import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.eclipse.smarthome.core.items.events.GroupItemStateChangedEvent;
import org.eclipse.smarthome.core.items.events.ItemStateChangedEvent;
import org.eclipse.smarthome.core.items.events.ItemStateEvent;
import org.eclipse.smarthome.core.items.events.ItemUpdatedEvent;
import org.eclipse.smarthome.io.rest.LocaleService;
import org.eclipse.smarthome.io.rest.RESTResource;
import org.eclipse.smarthome.io.rest.core.item.EnrichedItemDTO;
import org.eclipse.smarthome.io.rest.core.item.EnrichedItemDTOMapper;
import org.eclipse.smarthome.io.rest.sse.beans.EventBean;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.media.sse.SseBroadcaster;
import org.glassfish.jersey.media.sse.SseFeature;
import org.openhab.binding.habpanelfilter.internal.HabPanelFilterConfig;
import org.osgi.service.component.annotations.Component;

import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *  @author Pavel Cuchriajev
 */
@Component(immediate = true, service = FilteredEventsResource.class)
@Path(FilteredEventsResource.PATH_EVENTS_FILTERED)
@RolesAllowed({ Role.USER })
@Singleton
@Api(value = FilteredEventsResource.PATH_EVENTS_FILTERED, hidden = true)
public class FilteredEventsResource implements RESTResource {
    private final Logger logger = LoggerFactory.getLogger(FilteredEventsResource.class);

    public static final String PATH_EVENTS_FILTERED = "events-filtered";
    private static final String X_ACCEL_BUFFERING_HEADER = "X-Accel-Buffering";

    @NonNullByDefault({})
    @Context
    UriInfo uriInfo;

    @NonNullByDefault({})
    private ItemRegistry itemRegistry;

    @NonNullByDefault({})
    private LocaleService localeService;

    private final SseBroadcaster broadcaster;

    private final ExecutorService executorService;

    @Context
    private HttpServletResponse response;

    @Context
    private HttpServletRequest request;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    protected void unsetItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = null;
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setLocaleService(LocaleService localeService) {
        this.localeService = localeService;
    }

    protected void unsetLocaleService(LocaleService localeService) {
        this.localeService = null;
    }

    public FilteredEventsResource() {
        logger.info("FilteredEventsResource created ...");
        this.executorService = Executors.newSingleThreadExecutor();
        this.broadcaster = new SseBroadcaster();
    }

    /**
     * Subscribes the connecting client to the stream of events filtered by the
     * given eventFilter.
     *
     * @return {@link EventOutput} object associated with the incoming
     *         connection.
     * @throws IOException
     * @throws InterruptedException
     */
    @GET
    @Produces(SseFeature.SERVER_SENT_EVENTS)
    @ApiOperation(value = "Get all events.", response = EventOutput.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 400, message = "Topic is empty or contains invalid characters") })
    public Object getEvents()
            throws IOException, InterruptedException {

        final EventOutput eventOutput = new EventOutput();
        broadcaster.add(eventOutput);

        // Disables proxy buffering when using an nginx http server proxy for this response.
        // This allows you to not disable proxy buffering in nginx and still have working sse
        response.addHeader(X_ACCEL_BUFFERING_HEADER, "no");

        // We want to make sure that the response is not compressed and buffered so that the client receives server sent
        // events at the moment of sending them.
        response.addHeader(HttpHeaders.CONTENT_ENCODING, "identity");

        return eventOutput;
    }

    public void broadcastEvent(final Event event) {
        executorService.execute(new Runnable() {
            private @Nullable Item getItem(String itemname) {
                return itemRegistry.get(itemname);
            }

            private OutboundEvent buildEvent(Event event) {
                String type = event.getType();
                if (type.equals(ItemStateChangedEvent.TYPE) || type.equals(ItemStateEvent.TYPE)
                    || type.equals(ItemUpdatedEvent.TYPE) || type.equals(GroupItemStateChangedEvent.TYPE)) {

                    EventBean eventBean = new EventBean();
                    eventBean.type = event.getType();
                    eventBean.topic = event.getTopic();
                    eventBean.payload = event.getPayload();

                    String[] topicSplits = eventBean.topic.split("/");
                    if (topicSplits.length < 3) {
                        return null;
                    }
                    String itemName = topicSplits[2];
                    Item item = getItem(itemName);
                    if (item == null) {
                        return null;
                    }
                    if (!item.getGroupNames().contains(HabPanelFilterConfig.FILTER_GROUP_NAME)) {
                        return null;
                    }

                    EnrichedItemDTO dto = EnrichedItemDTOMapper.map(item, true, null, null, Locale.US);
                    eventBean.payload = (new Gson()).toJson(dto);

                    OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder();
                    OutboundEvent outboundEvent = eventBuilder.name("message").mediaType(MediaType.APPLICATION_JSON_TYPE)
                            .data(eventBean).build();

                    return outboundEvent;
                } else {
                    return null;
                }
            }

            @Override
            public void run() {
                OutboundEvent outboundEvent = buildEvent(event);
                if (outboundEvent == null)
                    return;
                broadcaster.broadcast(outboundEvent);
            }
        });
    }

    @Override
    public boolean isSatisfied() {
        return itemRegistry != null
                && localeService != null;
    }
}
