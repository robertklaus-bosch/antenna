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
package org.eclipse.sw360.antenna.sw360;

import org.eclipse.sw360.antenna.model.license.License;
import org.eclipse.sw360.antenna.sw360.adapter.SW360LicenseClientAdapter;
import org.eclipse.sw360.antenna.sw360.adapter.SW360ProjectClientAdapter;
import org.eclipse.sw360.antenna.sw360.adapter.SW360ReleaseClientAdapter;
import org.eclipse.sw360.antenna.sw360.client.api.SW360Connection;
import org.eclipse.sw360.antenna.sw360.rest.resource.licenses.SW360License;
import org.eclipse.sw360.antenna.sw360.rest.resource.releases.SW360Release;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SW360MetaDataUpdaterTest {
    private SW360MetaDataUpdater metaDataUpdater;
    private SW360Connection connection = mock(SW360Connection.class);
    private SW360ProjectClientAdapter projectClientAdapter = mock(SW360ProjectClientAdapter.class);
    private SW360LicenseClientAdapter licenseClientAdapter = mock(SW360LicenseClientAdapter.class);
    private SW360ReleaseClientAdapter releaseClientAdapter = mock(SW360ReleaseClientAdapter.class);
    final private boolean uploadSources = true;
    final private boolean updateReleases = false;

    public void setUp() {
        when(connection.getReleaseAdapter())
                .thenReturn(releaseClientAdapter);
        when(connection.getProjectAdapter())
                .thenReturn(projectClientAdapter);
        when(connection.getLicenseAdapter())
                .thenReturn(licenseClientAdapter);

        metaDataUpdater = new SW360MetaDataUpdater(connection, updateReleases, uploadSources);
    }

    @Test
    public void testGetLicensesWithExistingLicense() {
        final String licenseName = "licenseName";
        final SW360License license = new SW360License()
                .setShortName(licenseName);
        License licenseAntenna = new License();
        licenseAntenna.setId(licenseName);
        when(licenseClientAdapter.isLicenseOfArtifactAvailable(licenseName))
                .thenReturn(true);
        when(licenseClientAdapter.getSW360LicenseByAntennaLicense(licenseName))
                .thenReturn(Optional.of(license));
        setUp();

        final Set<SW360License> licenses = metaDataUpdater.getLicenses(Collections.singletonList(licenseAntenna));

        assertThat(licenses).hasSize(1);

        verify(licenseClientAdapter, times(1)).isLicenseOfArtifactAvailable(licenseName);
        verify(licenseClientAdapter, times(1)).getSW360LicenseByAntennaLicense(licenseName);
    }

    @Test
    public void testGetLicensesWithNonExistingLicense() {
        final String licenseName = "licenseName";
        final SW360License license = new SW360License()
                .setShortName(licenseName);
        License licenseAntenna = new License();
        licenseAntenna.setId(licenseName);
        when(licenseClientAdapter.isLicenseOfArtifactAvailable(licenseName))
                .thenReturn(false);
        when(licenseClientAdapter.getSW360LicenseByAntennaLicense(licenseName))
                .thenReturn(Optional.of(license));
        setUp();

        final Set<SW360License> licenses = metaDataUpdater.getLicenses(Collections.singletonList(licenseAntenna));

        assertThat(licenses).hasSize(0);

        verify(licenseClientAdapter, times(1)).isLicenseOfArtifactAvailable(licenseName);
    }

    @Test
    public void testGetOrCreateRelease() {
        final SW360Release release = new SW360Release();
        when(releaseClientAdapter.getOrCreateRelease(release, updateReleases))
                .thenReturn(release);
        setUp();

        final SW360Release getOrCreatedRelease = metaDataUpdater.getOrCreateRelease(release);

        assertThat(getOrCreatedRelease).isEqualTo(release);
        verify(releaseClientAdapter, times(1)).getOrCreateRelease(release, updateReleases);
    }

    @Test
    public void testCreateProjectWithProjectPresent() {
        final String projectName = "projectName";
        final String projectVersion = "projectVersion";
        final String projectId = "12345";
        when(projectClientAdapter.getProjectIdByNameAndVersion(projectName, projectVersion))
                .thenReturn(Optional.of(projectId));
        setUp();

        metaDataUpdater.createProject(projectName, projectVersion, Collections.emptySet());

        verify(projectClientAdapter, times(1)).getProjectIdByNameAndVersion(projectName, projectVersion);
        verify(projectClientAdapter, never()).addProject(projectName, projectVersion);
        verify(projectClientAdapter, times(1)).addSW360ReleasesToSW360Project(projectId, Collections.emptySet());
    }

    @Test
    public void testCreateProjectWithProjectNotPresent() {
        final String projectName = "projectName";
        final String projectVersion = "projectVersion";
        final String projectId = "12345";
        when(projectClientAdapter.getProjectIdByNameAndVersion(projectName, projectVersion))
                .thenReturn(Optional.empty());
        when(projectClientAdapter.addProject(projectName, projectVersion))
                .thenReturn(projectId);
        setUp();

        metaDataUpdater.createProject(projectName, projectVersion, Collections.emptySet());

        verify(projectClientAdapter, times(1)).getProjectIdByNameAndVersion(projectName, projectVersion);
        verify(projectClientAdapter, times(1)).addProject(projectName, projectVersion);
        verify(projectClientAdapter, times(1)).addSW360ReleasesToSW360Project(projectId, Collections.emptySet());
    }

    @Test
    public void testIsUploadSources() {
        setUp();
        assertThat(metaDataUpdater.isUploadSources()).isEqualTo(uploadSources);
    }

    @Test
    public void testUploadAttachments() {
        final SW360Release release = new SW360Release();
        when(releaseClientAdapter.uploadAttachments(release, Collections.emptyMap()))
                .thenReturn(release);

        setUp();

        final SW360Release releaseWithAttachment = metaDataUpdater.uploadAttachments(release, Collections.emptyMap());

        assertThat(releaseWithAttachment).isEqualTo(release);
        verify(releaseClientAdapter).uploadAttachments(release, Collections.emptyMap());
    }
}