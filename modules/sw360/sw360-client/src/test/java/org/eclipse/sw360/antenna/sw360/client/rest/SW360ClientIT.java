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
package org.eclipse.sw360.antenna.sw360.client.rest;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.sw360.antenna.http.utils.FailedRequestException;
import org.eclipse.sw360.antenna.http.utils.HttpConstants;
import org.eclipse.sw360.antenna.http.utils.HttpUtils;
import org.eclipse.sw360.antenna.sw360.client.auth.AccessToken;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.projects.SW360Project;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.projects.SW360ProjectList;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class SW360ClientIT extends AbstractMockServerTest {
    /**
     * The endpoint queried by the test client.
     */
    private static final String ENDPOINT = "/test";

    /**
     * Tag name used for test requests.
     */
    private static final String TAG = "The test request";

    /**
     * Mock for the token provider.
     */
    private AccessTokenProviderTestImpl tokenProvider;

    /**
     * The client to be tested.
     */
    private SW360Client client;

    @Before
    public void setUp() {
        tokenProvider = createMockTokenProvider();
        client = new SW360Client(createClientConfig(), tokenProvider) {
        };
    }

    /**
     * Prepares the mock token provider to answer requests for an access token
     * with the standard token.
     */
    private void givenAccessTokenAvailable() {
        prepareAccessTokens(tokenProvider, CompletableFuture.completedFuture(ACCESS_TOKEN));
    }

    /**
     * Prepares the mock token provider to answer multiple requests for access
     * tokens. The first token is considered expired, so that another token
     * needs to be fetched.
     *
     * @param token the expired access token
     */
    private void givenExpiredAccessToken(String token) {
        prepareAccessTokens(tokenProvider,
                CompletableFuture.completedFuture(new AccessToken(token)),
                CompletableFuture.completedFuture(ACCESS_TOKEN));
    }

    /**
     * Prepares the mock token provider to report a failure when asked for an
     * access token.
     *
     * @return the exception generated by the provider
     */
    private Throwable givenNoAccessTokenAvailable() {
        Throwable exception = new IllegalStateException("No token available");
        CompletableFuture<AccessToken> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(exception);
        doReturn(failedFuture).when(tokenProvider).obtainAccessToken();
        return exception;
    }

    /**
     * Invokes a standard request on the test client and returns the future
     * with the result.
     *
     * @return the future returned by the client
     */
    private CompletableFuture<SW360ProjectList> whenClientInvoked() {
        String endpointUrl = client.resourceUrl(StringUtils.stripStart(ENDPOINT, "/"));
        return client.executeJsonRequest(HttpUtils.get(endpointUrl), SW360ProjectList.class, TAG);
    }

    /**
     * Invokes a standard request on the test client and returns the result.
     * (The method blocks until the result arrives.)
     *
     * @return the converted JSON data received from the server
     * @throws IOException if an error occurs
     */
    private SW360ProjectList whenClientInvokedSuccessfully() throws IOException {
        return HttpUtils.waitFor(whenClientInvoked());
    }

    /**
     * Tests whether a request yields the expected results.
     *
     * @param projectList the project list returned by a request
     */
    private void thenCorrectResultsShouldHaveBeenRetrieved(SW360ProjectList projectList) {
        List<SW360Project> projects = projectList.get_Embedded().getProjects();
        checkTestProjects(projects);
    }

    /**
     * Checks whether the given list of projects contains the expected test
     * data.
     *
     * @param projects the list with projects to be checked
     */
    private static void checkTestProjects(List<SW360Project> projects) {
        String[] expectedProjects = {
                "Project_Foo", "Project_Bar", "Project_other", "Project_test"
        };
        List<String> actualProjectNames = projects.stream()
                .map(SW360Project::getName)
                .collect(Collectors.toList());
        assertThat(actualProjectNames).containsExactly(expectedProjects);
    }

    /**
     * Checks that the result future obtained from the test client failed with
     * an exception of the given type and returns this exception.
     *
     * @param result  the result future
     * @param exClass the expected exception class
     * @param <E>     the type of the expected exception
     * @return the exception causing the failure
     */
    private static <E extends Throwable> E thenInvocationFailed(CompletableFuture<?> result,
                                                                Class<? extends E> exClass) {
        return extractException(result, exClass);
    }

    /**
     * Checks whether the future obtained from the test client failed because
     * of a {@link FailedRequestException} with the expected status code.
     *
     * @param result the result future
     * @param status the expected status code
     */
    private static void thenFailedRequestIsReported(CompletableFuture<?> result, int status) {
        FailedRequestException exception = thenInvocationFailed(result, FailedRequestException.class);
        assertThat(exception.getStatusCode()).isEqualTo(status);
        assertThat(exception.getTag()).isEqualTo(TAG);
    }

    /**
     * Checks that the token provider was invoked to invalidate the given
     * access token.
     *
     * @param token the token to be invalidated
     */
    private void thenTokenIsInvalidated(String token) {
        verify(tokenProvider, atLeastOnce()).invalidate(new AccessToken(token));
    }

    @Test
    public void testResourceUrlWithMultipleSegments() {
        String url = client.resourceUrl("foo", "bar", "baz", "42");

        assertThat(url).isEqualTo(wireMockRule.baseUrl() + "/foo/bar/baz/42");
    }

    @Test
    public void testNoAccessTokenAvailable() {
        Throwable exception = givenNoAccessTokenAvailable();

        CompletableFuture<SW360ProjectList> result = whenClientInvoked();

        Throwable actualException = thenInvocationFailed(result, Throwable.class);
        assertThat(actualException).isEqualTo(exception);
    }

    @Test
    public void testSuccessfulRequest() throws IOException {
        wireMockRule.stubFor(authorized(get(urlPathEqualTo(ENDPOINT)))
                .willReturn(aJsonResponse(HttpConstants.STATUS_OK)
                        .withBodyFile("all_projects.json")));
        givenAccessTokenAvailable();

        SW360ProjectList projectList = whenClientInvokedSuccessfully();

        thenCorrectResultsShouldHaveBeenRetrieved(projectList);
    }

    @Test
    public void testFailedStatusCodeIsDetected() {
        wireMockRule.stubFor(authorized(get(urlPathEqualTo(ENDPOINT)))
                .willReturn(aJsonResponse(HttpConstants.STATUS_ERR_BAD_REQUEST)));
        givenAccessTokenAvailable();

        CompletableFuture<SW360ProjectList> result = whenClientInvoked();

        thenFailedRequestIsReported(result, HttpConstants.STATUS_ERR_BAD_REQUEST);
    }

    @Test
    public void testUnauthorizedRequestIsRetriedWithFreshToken() throws IOException {
        final String expiredToken = "expired_access_token:-(";
        wireMockRule.stubFor(authorized(get(urlPathEqualTo(ENDPOINT)), expiredToken)
                .willReturn(aResponse().withStatus(HttpConstants.STATUS_ERR_UNAUTHORIZED)));
        wireMockRule.stubFor(authorized(get(urlPathEqualTo(ENDPOINT)))
                .willReturn(aJsonResponse(HttpConstants.STATUS_ACCEPTED)
                        .withBodyFile("all_projects.json")));
        givenExpiredAccessToken(expiredToken);

        SW360ProjectList projectList = whenClientInvokedSuccessfully();

        thenCorrectResultsShouldHaveBeenRetrieved(projectList);
        thenTokenIsInvalidated(expiredToken);
    }

    @Test
    public void testUnauthorizedRequestIsRetriedOnlyOnce() {
        wireMockRule.stubFor(get(urlPathEqualTo(ENDPOINT))
                .willReturn(aResponse().withStatus(HttpConstants.STATUS_ERR_UNAUTHORIZED)));
        givenExpiredAccessToken(ACCESS_TOKEN.getToken());

        CompletableFuture<SW360ProjectList> result = whenClientInvoked();

        thenFailedRequestIsReported(result, HttpConstants.STATUS_ERR_UNAUTHORIZED);
        thenTokenIsInvalidated(ACCESS_TOKEN.getToken());
        assertThat(wireMockRule.getAllServeEvents()).hasSize(2);
    }
}
