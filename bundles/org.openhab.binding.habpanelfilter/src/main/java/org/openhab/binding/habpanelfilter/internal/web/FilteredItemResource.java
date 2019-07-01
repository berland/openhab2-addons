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

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.auth.Role;
import org.eclipse.smarthome.core.events.EventPublisher;
import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.items.ItemBuilderFactory;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.eclipse.smarthome.core.items.ManagedItemProvider;
import org.eclipse.smarthome.core.items.MetadataRegistry;
import org.eclipse.smarthome.io.rest.DTOMapper;
import org.eclipse.smarthome.io.rest.JSONResponse;
import org.eclipse.smarthome.io.rest.LocaleService;
import org.eclipse.smarthome.io.rest.RESTResource;
import org.eclipse.smarthome.io.rest.Stream2JSONInputStream;
import org.eclipse.smarthome.io.rest.core.item.EnrichedItemDTO;
import org.eclipse.smarthome.io.rest.core.item.EnrichedItemDTOMapper;
import org.openhab.binding.habpanelfilter.internal.HabPanelFilterConfig;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Pavel Cuchriajev
 */
@NonNullByDefault
@Path(FilteredItemResource.PATH_FILTERED_ITEMS)
@Api(value = FilteredItemResource.PATH_FILTERED_ITEMS)
@Component
public class FilteredItemResource implements RESTResource {

    private final Logger logger = LoggerFactory.getLogger(FilteredItemResource.class);

    public static final String PATH_FILTERED_ITEMS = "items-filtered";

    @NonNullByDefault({})
    @Context
    UriInfo uriInfo;

    @NonNullByDefault({})
    private ItemRegistry itemRegistry;
    @NonNullByDefault({})
    private MetadataRegistry metadataRegistry;
    @NonNullByDefault({})
    private EventPublisher eventPublisher;
    @NonNullByDefault({})
    private ManagedItemProvider managedItemProvider;
    @NonNullByDefault({})
    private DTOMapper dtoMapper;
    @NonNullByDefault({})
    private ItemBuilderFactory itemBuilderFactory;

    @NonNullByDefault({})
    private LocaleService localeService;

    public FilteredItemResource() {
        logger.info("FilteredItemResource created ...");
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    protected void unsetItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = null;
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setMetadataRegistry(MetadataRegistry metadataRegistry) {
        this.metadataRegistry = metadataRegistry;
    }

    protected void unsetMetadataRegistry(MetadataRegistry metadataRegistry) {
        this.metadataRegistry = null;
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setEventPublisher(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    protected void unsetEventPublisher(EventPublisher eventPublisher) {
        this.eventPublisher = null;
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setManagedItemProvider(ManagedItemProvider managedItemProvider) {
        this.managedItemProvider = managedItemProvider;
    }

    protected void unsetManagedItemProvider(ManagedItemProvider managedItemProvider) {
        this.managedItemProvider = null;
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setDTOMapper(DTOMapper dtoMapper) {
        this.dtoMapper = dtoMapper;
    }

    protected void unsetDTOMapper(DTOMapper dtoMapper) {
        this.dtoMapper = dtoMapper;
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setLocaleService(LocaleService localeService) {
        this.localeService = localeService;
    }

    protected void unsetLocaleService(LocaleService localeService) {
        this.localeService = null;
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    public void setItemBuilderFactory(ItemBuilderFactory itemBuilderFactory) {
        this.itemBuilderFactory = itemBuilderFactory;
    }

    public void unsetItemBuilderFactory(ItemBuilderFactory itemBuilderFactory) {
        this.itemBuilderFactory = null;
    }

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get all available filtered items.", response = EnrichedItemDTO.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = EnrichedItemDTO.class, responseContainer = "List") })
    public Response getItems(
            @HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) @ApiParam(value = "language") @Nullable String language,
            @QueryParam("type") @ApiParam(value = "item type filter") @Nullable String type,
            @QueryParam("tags") @ApiParam(value = "item tag filter") @Nullable String tags,
            @DefaultValue("false") @QueryParam("recursive") @ApiParam(value = "get member items recursively") boolean recursive,
            @QueryParam("fields") @ApiParam(value = "limit output to the given fields (comma separated)") @Nullable String fields) {
        final Locale locale = localeService.getLocale(language);
        logger.info("Received HTTP GET request at '{}'", uriInfo.getPath());

        Stream<EnrichedItemDTO> itemStream = getItems(type, tags).stream() //
                .map(item -> EnrichedItemDTOMapper.map(item, recursive, null, uriInfo.getBaseUri(), locale)) //
                .peek(dto -> dto.editable = isEditable(dto.name));
        itemStream = itemStream.filter(item -> item.groupNames.contains(HabPanelFilterConfig.FILTER_GROUP_NAME));

        itemStream = dtoMapper.limitToFields(itemStream, fields);
        return Response.ok(new Stream2JSONInputStream(itemStream)).build();
    }

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/{itemname: [a-zA-Z_0-9]*}")
    @Produces({ MediaType.APPLICATION_JSON })
    @ApiOperation(value = "Gets a single item.", response = EnrichedItemDTO.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK", response = EnrichedItemDTO.class),
            @ApiResponse(code = 404, message = "Item not found") })
    public Response getItemData(@HeaderParam(HttpHeaders.ACCEPT_LANGUAGE) @ApiParam(value = "language") String language,
                                @QueryParam("metadata") @ApiParam(value = "metadata selector") @Nullable String namespaceSelector,
                                @PathParam("itemname") @ApiParam(value = "item name", required = true) String itemname) {
        final Locale locale = localeService.getLocale(language);
        logger.debug("Received HTTP GET request at '{}'", uriInfo.getPath());

        // get item
        Item item = getItem(itemname);

        // if it exists
        if (item != null) {
            logger.debug("Received HTTP GET request at '{}'.", uriInfo.getPath());
            EnrichedItemDTO dto = EnrichedItemDTOMapper.map(item, true, null, uriInfo.getBaseUri(), locale);
            dto.editable = isEditable(dto.name);
            return JSONResponse.createResponse(Status.OK, dto, null);
        } else {
            logger.info("Received HTTP GET request at '{}' for the unknown item '{}'.", uriInfo.getPath(), itemname);
            return getItemNotFoundResponse(itemname);
        }
    }

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/{itemname: [a-zA-Z_0-9]*}/state")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiOperation(value = "Gets the state of an item.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK", response = String.class),
            @ApiResponse(code = 404, message = "Item not found") })
    public Response getPlainItemState(
            @PathParam("itemname") @ApiParam(value = "item name", required = true) String itemname) {
        // get item
        Item item = getItem(itemname);

        // if it exists
        if (item != null) {
            logger.debug("Received HTTP GET request at '{}'.", uriInfo.getPath());

            // we cannot use JSONResponse.createResponse() bc. MediaType.TEXT_PLAIN
            // return JSONResponse.createResponse(Status.OK, item.getState().toString(), null);
            return Response.ok(item.getState().toFullString()).build();
        } else {
            logger.info("Received HTTP GET request at '{}' for the unknown item '{}'.", uriInfo.getPath(), itemname);
            return getItemNotFoundResponse(itemname);
        }
    }

    private static Response getItemNotFoundResponse(String itemname) {
        String message = "Item " + itemname + " does not exist!";
        return JSONResponse.createResponse(Status.NOT_FOUND, null, message);
    }

    private @Nullable Item getItem(String itemname) {
        return itemRegistry.get(itemname);
    }

    private Collection<Item> getItems(@Nullable String type, @Nullable String tags) {
        Collection<Item> items;
        if (tags == null) {
            if (type == null) {
                items = itemRegistry.getItems();
            } else {
                items = itemRegistry.getItemsOfType(type);
            }
        } else {
            String[] tagList = tags.split(",");
            if (type == null) {
                items = itemRegistry.getItemsByTag(tagList);
            } else {
                items = itemRegistry.getItemsByTagAndType(type, tagList);
            }
        }

        return items;
    }

    private boolean isEditable(String itemName) {
        return managedItemProvider.get(itemName) != null;
    }

    @Override
    public boolean isSatisfied() {
        return itemRegistry != null && managedItemProvider != null && eventPublisher != null
                && itemBuilderFactory != null && dtoMapper != null && metadataRegistry != null
                && localeService != null;
    }
}
