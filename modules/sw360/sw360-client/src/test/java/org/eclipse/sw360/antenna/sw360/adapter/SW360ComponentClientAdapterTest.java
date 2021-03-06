/*
 * Copyright (c) Bosch.IO GmbH 2020.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.antenna.sw360.adapter;

import org.eclipse.sw360.antenna.http.utils.FailedRequestException;
import org.eclipse.sw360.antenna.http.utils.HttpConstants;
import org.eclipse.sw360.antenna.sw360.client.rest.SW360ComponentClient;
import org.eclipse.sw360.antenna.sw360.client.utils.SW360ClientException;
import org.eclipse.sw360.antenna.sw360.rest.resource.LinkObjects;
import org.eclipse.sw360.antenna.sw360.rest.resource.Self;
import org.eclipse.sw360.antenna.sw360.rest.resource.components.SW360Component;
import org.eclipse.sw360.antenna.sw360.rest.resource.components.SW360SparseComponent;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SW360ComponentClientAdapterTest {
    private final static String COMPONENT_ID = "12345";
    private final static String COMPONENT_NAME = "componentName";

    private SW360ComponentClientAdapter componentClientAdapter;

    private SW360ComponentClient componentClient = mock(SW360ComponentClient.class);

    private SW360SparseComponent sparseComponent;
    private SW360Component component;

    @Before
    public void setUp() {
        componentClientAdapter = new SW360ComponentClientAdapter(componentClient);
        sparseComponent = new SW360SparseComponent();
        component = new SW360Component();
    }

    @Test
    public void testGetOrCreateComponentByID() {
        SW360Component componentFromRelease = mock(SW360Component.class);
        when(componentFromRelease.getComponentId()).thenReturn(COMPONENT_ID);
        when(componentClient.getComponent(COMPONENT_ID))
                .thenReturn(CompletableFuture.completedFuture(component));

        Optional<SW360Component> optResult = componentClientAdapter.getOrCreateComponent(componentFromRelease);
        assertThat(optResult).contains(component);
    }

    @Test
    public void testGetOrCreateComponentByName() {
        SW360Component componentFromRelease = mock(SW360Component.class);
        when(componentFromRelease.getComponentId()).thenReturn(null);
        when(componentFromRelease.getName()).thenReturn(COMPONENT_NAME);
        LinkObjects linkObjects = makeLinkObjects();
        sparseComponent.setName(COMPONENT_NAME)
                .set_Links(linkObjects);
        component.setName(COMPONENT_NAME);

        when(componentClient.getComponent(COMPONENT_ID))
                .thenReturn(CompletableFuture.completedFuture(component));
        when(componentClient.searchByName(COMPONENT_NAME))
                .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(sparseComponent)));

        Optional<SW360Component> optResult = componentClientAdapter.getOrCreateComponent(componentFromRelease);
        assertThat(optResult).contains(component);
    }

    @Test
    public void testGetOrCreateComponentCreateNew() {
        SW360Component componentFromRelease = mock(SW360Component.class);
        when(componentFromRelease.getComponentId()).thenReturn(null);
        when(componentFromRelease.getName()).thenReturn(COMPONENT_NAME);
        when(componentClient.searchByName(COMPONENT_NAME))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        when(componentClient.createComponent(componentFromRelease))
                .thenReturn(CompletableFuture.completedFuture(component));

        Optional<SW360Component> optResult = componentClientAdapter.getOrCreateComponent(componentFromRelease);
        assertThat(optResult).contains(component);
    }

    @Test
    public void testCreateComponent() {
        component.setName(COMPONENT_NAME);
        when(componentClient.createComponent(component))
                .thenReturn(CompletableFuture.completedFuture(component));

        SW360Component createdComponent = componentClientAdapter.createComponent(this.component);

        assertThat(createdComponent).isEqualTo(component);
        verify(componentClient).createComponent(component);
    }

    @Test (expected = SW360ClientException.class)
    public void testCreateComponentNull() {
        componentClientAdapter.createComponent(this.component);
    }

    @Test
    public void testGetComponentById() {
        when(componentClient.getComponent(COMPONENT_ID))
                .thenReturn(CompletableFuture.completedFuture(component));

        Optional<SW360Component> componentById = componentClientAdapter.getComponentById(COMPONENT_ID);

        assertThat(componentById).isPresent();
        assertThat(componentById).hasValue(component);
        verify(componentClient).getComponent(COMPONENT_ID);
    }

    @Test
    public void testGetComponentByIdNotFound() {
        CompletableFuture<SW360Component> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new FailedRequestException("tag", HttpConstants.STATUS_ERR_NOT_FOUND));
        when(componentClient.getComponent(COMPONENT_ID)).thenReturn(failedFuture);

        Optional<SW360Component> componentById = componentClientAdapter.getComponentById(COMPONENT_ID);
        assertThat(componentById).isNotPresent();
    }

    @Test
    public void testGetComponentByName() {
        LinkObjects linkObjects = makeLinkObjects();
        sparseComponent.setName(COMPONENT_NAME)
                .set_Links(linkObjects);

        component.setName(COMPONENT_NAME);

        when(componentClient.getComponent(COMPONENT_ID))
                .thenReturn(CompletableFuture.completedFuture(component));
        when(componentClient.searchByName(COMPONENT_NAME))
                .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(sparseComponent)));

        Optional<SW360Component> componentByName = componentClientAdapter.getComponentByName(COMPONENT_NAME);

        assertThat(componentByName).isPresent();
        assertThat(componentByName).hasValue(component);
        verify(componentClient).getComponent(COMPONENT_ID);
        verify(componentClient).searchByName(COMPONENT_NAME);
    }

    @Test
    public void testGetComponents() {
        when(componentClient.getComponents())
                .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(sparseComponent)));

        List<SW360SparseComponent> components = componentClientAdapter.getComponents();

        assertThat(components).hasSize(1);
        assertThat(components).containsExactly(sparseComponent);
        verify(componentClient).getComponents();
    }

    private static LinkObjects makeLinkObjects() {
        String componentHref = "url/" + COMPONENT_ID;
        Self componentSelf = new Self().setHref(componentHref);
        return new LinkObjects()
                .setSelf(componentSelf);
    }
}