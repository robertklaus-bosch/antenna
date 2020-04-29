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
package org.eclipse.sw360.antenna.sw360.client.adapter;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import org.eclipse.sw360.antenna.sw360.client.rest.SW360ReleaseClient;
import org.eclipse.sw360.antenna.sw360.client.utils.SW360ClientException;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.LinkObjects;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.Self;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.attachments.SW360AttachmentType;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.attachments.SW360SparseAttachment;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.components.SW360Component;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.components.SW360ComponentEmbedded;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.releases.SW360Release;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.releases.SW360ReleaseEmbedded;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.releases.SW360ReleaseLinkObjects;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.releases.SW360SparseRelease;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.sw360.antenna.sw360.client.utils.FutureUtils.block;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SW360ReleaseClientAdapterAsyncImplTest {
    private static final String RELEASE_DOWNLOAD_URL = "https://organisation-test.org/";
    private static final String RELEASE_CLEARING_STATE = "PROJECT_APPROVED";
    private static final String RELEASE_DECLEARED_LICENSE = "The-Test-License";
    private static final String RELEASE_OBSERVED_LICENSE = "A-Test-License";
    private static final String RELEASE_RELEASE_TAG_URL = "https://gitTool.com/project/repository";
    private static final String RELEASE_SOFTWAREHERITGAE_ID = "swh:1:rel:1234512345123451234512345123451234512345";
    private static final String RELEASE_HASH1= "b2a4d4ae21c789b689dd162deb819665567f481c";
    private static final String RELEASE_CHANGESTATUS = "AS_IS";
    private static final String RELEASE_COPYRIGHT = "Copyright xxxx Some Copyright Enterprise";
    private static final String ID = "12345";
    private static final String RELEASE_VERSION1 = "1.0.0";

    private SW360ReleaseClientAdapterAsync releaseClientAdapter;

    private SW360ReleaseClient releaseClient;
    private SW360ComponentClientAdapterAsync componentClientAdapter;
    private SW360Release release;

    @Before
    public void setUp() throws MalformedPackageURLException {
        releaseClient = mock(SW360ReleaseClient.class);
        componentClientAdapter = mock(SW360ComponentClientAdapterAsync.class);
        release = mkSW360Release("releaseName");
        releaseClientAdapter = new SW360ReleaseClientAdapterAsyncImpl(releaseClient, componentClientAdapter);
    }

    @Test
    public void testGetOrCreateReleaseWithPatchRelease() {
        SW360ReleaseLinkObjects links = getSw360ReleaseLinkObjects();
        SW360SparseRelease sparseRelease = new SW360SparseRelease();
        sparseRelease.set_Links(links);

        when(releaseClient.getReleasesByExternalIds(release.getExternalIds()))
                .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(sparseRelease)));
        when(releaseClient.getRelease(sparseRelease.getReleaseId()))
                .thenReturn(CompletableFuture.completedFuture(release));

        when(releaseClient.patchRelease(release))
                .thenReturn(CompletableFuture.completedFuture(release));

        SW360Release patchedRelease = block(releaseClientAdapter.getOrCreateRelease(release, true));

        assertThat(patchedRelease).isEqualTo(release);
        verify(releaseClient).getRelease(ID);
        verify(releaseClient).patchRelease(release);
    }

    private static SW360ReleaseLinkObjects getSw360ReleaseLinkObjects() {
        String releaseHref = "url/" + ID;
        Self releaseSelf = new Self().setHref(releaseHref);
        SW360ReleaseLinkObjects links = new SW360ReleaseLinkObjects();
        links.setSelf(releaseSelf);
        return links;
    }

    @Test
    public void testCreateRelease() {
        SW360SparseRelease sparseRelease = new SW360SparseRelease()
                .setVersion(release.getVersion() + "-noMatch");
        String componentHref = "url/" + ID;
        Self componentSelf = new Self().setHref(componentHref);
        LinkObjects links = new LinkObjects()
                .setSelf(componentSelf);
        SW360Component component = getSw360Component(sparseRelease, "componentName");
        component.set_Links(links);

        when(componentClientAdapter.getOrCreateComponent(any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(component)));
        when(releaseClient.createRelease(release))
                .thenReturn(CompletableFuture.completedFuture(release));

        SW360Release createdRelease = block(releaseClientAdapter.createRelease(this.release));

        assertThat(createdRelease).isEqualTo(release);
        verify(releaseClient).createRelease(release);
        verify(componentClientAdapter).getOrCreateComponent(any());
    }

    @Test
    public void testCreateReleaseInvalid() {
        release.setName("");

        try {
            block(releaseClientAdapter.createRelease(release));
            fail("Invalid release not detected");
        } catch (SW360ClientException e) {
            assertThat(e.getMessage()).contains("invalid release");
        }
    }

    @Test
    public void testCreateReleaseWithID() {
        release.setReleaseId("R11");

        try {
            block(releaseClientAdapter.createRelease(release));
            fail("Release ID not detected");
        } catch (SW360ClientException e) {
            assertThat(e.getMessage()).contains("already has the id");
        }
    }

    @Test
    public void testCreateReleaseAlreadyPresent() {
        SW360SparseRelease sparseRelease = new SW360SparseRelease()
                .setVersion(release.getVersion());
        String componentHref = "url/" + ID;
        Self componentSelf = new Self().setHref(componentHref);
        LinkObjects links = new LinkObjects()
                .setSelf(componentSelf);
        SW360Component component = getSw360Component(sparseRelease, "componentName");
        component.set_Links(links);
        when(componentClientAdapter.getOrCreateComponent(any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(component)));

        try {
            block(releaseClientAdapter.createRelease(release));
            fail("Existing release not detected");
        } catch (SW360ClientException e) {
            assertThat(e.getMessage()).contains("release already exists");
        }
    }

    @Test
    public void testUploadAttachments() {
        SW360ReleaseEmbedded sw360ReleaseEmbedded = new SW360ReleaseEmbedded();
        sw360ReleaseEmbedded.setAttachments(Collections.emptySet());
        release.set_Embedded(sw360ReleaseEmbedded);

        SW360AttachmentType attachmentType = SW360AttachmentType.SOURCE;
        Path path = Paths.get("");

        when(releaseClient.uploadAndAttachAttachment(release, path, attachmentType))
                .thenReturn(CompletableFuture.completedFuture(release));

        Map<Path, SW360AttachmentType> attachmentMap = new HashMap<>();
        attachmentMap.put(path, attachmentType);

        SW360Release releaseWithAttachment = block(releaseClientAdapter.uploadAttachments(this.release, attachmentMap));

        assertThat(releaseWithAttachment).isEqualTo(release);
        verify(releaseClient).uploadAndAttachAttachment(release, path, attachmentType);
    }

    @Test
    public void testGetReleaseByExternalIds() {
        SW360SparseRelease sparseRelease = new SW360SparseRelease();
        Map<String, String> externalIds = new HashMap<>();

        when(releaseClient.getReleasesByExternalIds(externalIds))
                .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(sparseRelease)));

        Optional<SW360SparseRelease> releaseByExternalIds =
                block(releaseClientAdapter.getReleaseByExternalIds(externalIds));

        assertThat(releaseByExternalIds).isPresent();
        assertThat(releaseByExternalIds).hasValue(sparseRelease);
        verify(releaseClient).getReleasesByExternalIds(externalIds);
    }

    @Test
    public void testGetReleaseByExternalIdsNotFound() {
        Map<String, String> externalIds = new HashMap<>();
        when(releaseClient.getReleasesByExternalIds(externalIds))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

        Optional<SW360SparseRelease> releaseByExternalIds =
                block(releaseClientAdapter.getReleaseByExternalIds(externalIds));
        assertThat(releaseByExternalIds).isEmpty();
    }

    @Test
    public void testGetReleaseByExternalIdsMultiple() {
        Map<String, String> externalIds = new HashMap<>();
        when(releaseClient.getReleasesByExternalIds(externalIds))
                .thenReturn(CompletableFuture.completedFuture(Arrays.asList(
                        new SW360SparseRelease(), new SW360SparseRelease())));

        try {
            block(releaseClientAdapter.getReleaseByExternalIds(externalIds));
            fail("Multiple results not detected");
        } catch (SW360ClientException e) {
            assertThat(e.getMessage()).contains("Multiple releases");
        }
    }

    @Test
    public void testGetReleaseByNameAndVersion() {
        SW360SparseRelease sparseRelease = new SW360SparseRelease()
                .setVersion(RELEASE_VERSION1);
        String componentName = "componentName";
        SW360Component component = getSw360Component(sparseRelease, componentName);
        when(componentClientAdapter.getComponentByName(release.getName()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(component)));

        Optional<SW360SparseRelease> releaseByNameAndVersion =
                block(releaseClientAdapter.getReleaseByNameAndVersion(release));

        assertThat(releaseByNameAndVersion).isPresent();
        assertThat(releaseByNameAndVersion).hasValue(sparseRelease);
    }

    private static SW360Component getSw360Component(SW360SparseRelease sparseRelease, String componentName) {
        SW360ComponentEmbedded embedded = new SW360ComponentEmbedded();
        embedded.setReleases(Collections.singletonList(sparseRelease));
        SW360Component component = new SW360Component()
                .setName(componentName);
        component.set_Embedded(embedded);
        return component;
    }

    @Test
    public void testGetReleaseByVersion() {
        SW360SparseRelease sparseRelease = new SW360SparseRelease()
                .setVersion(RELEASE_VERSION1);
        String releaseHref = "url/" + ID;
        Self releaseSelf = new Self().setHref(releaseHref);
        LinkObjects links = new LinkObjects();
        links.setSelf(releaseSelf);
        sparseRelease.set_Links(links);
        String componentName = "componentName";
        SW360Component component = getSw360Component(sparseRelease, componentName);
        when(releaseClient.getRelease(ID))
                .thenReturn(CompletableFuture.completedFuture(release));

        final Optional<SW360Release> releaseByVersion =
                block(releaseClientAdapter.getReleaseByVersion(component, release.getVersion()));

        assertThat(releaseByVersion).isPresent();
        assertThat(releaseByVersion).hasValue(release);
        verify(releaseClient, times(1)).getRelease(ID);
    }

    @Test
    public void testGetReleaseByVersionNoComponent() {
        Optional<SW360Release> optRelease = block(releaseClientAdapter.getReleaseByVersion(null, RELEASE_VERSION1));

        assertThat(optRelease).isEmpty();
    }

    @Test
    public void testDownloadAttachment() {
        String releaseHref = "url/" + ID;
        Self releaseSelf = new Self().setHref(releaseHref);
        SW360ReleaseLinkObjects links = new SW360ReleaseLinkObjects();
        links.setSelf(releaseSelf);
        release.set_Links(links);

        SW360SparseAttachment sparseAttachment = new SW360SparseAttachment();

        Path path = Paths.get("");

        when(releaseClient.downloadAttachment(releaseHref, sparseAttachment, path))
                .thenReturn(CompletableFuture.completedFuture(path));

        Optional<Path> downloadPath = block(releaseClientAdapter.downloadAttachment(release, sparseAttachment, path));

        assertThat(downloadPath).isPresent();
        assertThat(downloadPath).hasValue(path);
    }

    public static SW360Release mkSW360Release(String name) throws MalformedPackageURLException {
        SW360Release sw360Release = new SW360Release();

        sw360Release.setVersion(RELEASE_VERSION1);

        sw360Release.setDownloadurl(RELEASE_DOWNLOAD_URL);
        sw360Release.setClearingState(RELEASE_CLEARING_STATE);

        sw360Release.setDeclaredLicense(RELEASE_DECLEARED_LICENSE);
        sw360Release.setObservedLicense(RELEASE_OBSERVED_LICENSE);
        PackageURL packageURL = new PackageURL(PackageURL.StandardTypes.MAVEN, "org.group.id", name, RELEASE_VERSION1, null, null);
        sw360Release.setCoordinates(Collections.singletonMap(PackageURL.StandardTypes.MAVEN,
                packageURL.toString()));
        sw360Release.setReleaseTagUrl(RELEASE_RELEASE_TAG_URL);
        sw360Release.setSoftwareHeritageId(RELEASE_SOFTWAREHERITGAE_ID);
        sw360Release.setHashes(Collections.singleton(RELEASE_HASH1));
        sw360Release.setChangeStatus(RELEASE_CHANGESTATUS);
        sw360Release.setCopyrights(RELEASE_COPYRIGHT);
        sw360Release.setName(String.join("/", packageURL.getNamespace(), packageURL.getName()));

        return sw360Release;
    }
}