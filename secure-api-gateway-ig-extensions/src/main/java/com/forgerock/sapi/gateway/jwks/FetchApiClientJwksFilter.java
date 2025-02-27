/*
 * Copyright © 2020-2022 ForgeRock AS (obst@forgerock.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.forgerock.sapi.gateway.jwks;

import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.net.MalformedURLException;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonException;
import org.forgerock.json.JsonValue;
import org.forgerock.json.jose.exceptions.FailedToLoadJWKException;
import org.forgerock.json.jose.jwk.JWKSet;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.forgerock.sapi.gateway.dcr.ApiClient;
import com.forgerock.sapi.gateway.dcr.FetchApiClientFilter;
import com.forgerock.sapi.gateway.fapi.FAPIUtils;
import com.forgerock.sapi.gateway.trusteddirectories.FetchTrustedDirectoryFilter;
import com.forgerock.sapi.gateway.trusteddirectories.TrustedDirectory;

/**
 * Filter which fetches a {@link JWKSet} containing the keys for an {@link ApiClient} that are registered with a
 * {@link TrustedDirectory}
 *
 * The ApiClient is looked up from the AttributesContext.
 *
 * To determine where to find the JWKSet for an ApiClient, the {@link TrustedDirectory} configuration is required.
 * This is also retrieved from the AttributesContext.
 *
 * Therefore, this filter must be installed after a filters which set these attributes, typically: {@link FetchApiClientFilter}
 * and {@link FetchTrustedDirectoryFilter}
 */
public class FetchApiClientJwksFilter implements Filter {

    /**
     * The key to use to get the JWKSet for the ApiClient from the AttributesContext
     */
    public static final String API_CLIENT_JWKS_ATTR_KEY = "apiClientJwkSet";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * The service to delegate to when looking up remote JWKSets by URL
     */
    private final JwkSetService jwkSetService;

    /**
     * Utility method to retrieve a {@link JWKSet} object from a Context, which belongs to the ApiClient.
     * This method can be used by other filters to retrieve the JWKSet installed into the attributes context by
     * this filter.
     *
     * @param context the context to retrieve the JWKSet from
     * @return the JWKSet or null if it is not set in the context.
     */
    public static JWKSet getApiClientJwkSetFromContext(Context context) {
        return (JWKSet) context.asContext(AttributesContext.class).getAttributes().get(API_CLIENT_JWKS_ATTR_KEY);
    }

    public FetchApiClientJwksFilter(JwkSetService jwkSetService) {
        Reject.ifNull(jwkSetService, "jwkSetService must be provided");
        this.jwkSetService = jwkSetService;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        final ApiClient apiClient = getApiClient(context);
        final TrustedDirectory trustedDirectory = getTrustedDirectory(context);

        return getJwkSet(apiClient, trustedDirectory).thenAsync(jwkSet -> {
            logger.debug("({}) added jwks to context for apiClient", FAPIUtils.getFapiInteractionIdForDisplay(context));
            context.asContext(AttributesContext.class).getAttributes().put(API_CLIENT_JWKS_ATTR_KEY, jwkSet);
            return next.handle(context, request);
        }, ex -> {
            logger.error("(" + FAPIUtils.getFapiInteractionIdForDisplay(context) + ") failed to load JWKS for apiClient: "
                    + apiClient + " and trusted directory: " + trustedDirectory + " due to exception", ex);
            return Promises.newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR));
        }, rte -> {
            logger.error("(" + FAPIUtils.getFapiInteractionIdForDisplay(context) + ") failed to load JWKS for apiClient: "
                    + apiClient + " and trusted directory: " + trustedDirectory + " due to exception", rte);
            return Promises.newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR));
        });
    }

    private TrustedDirectory getTrustedDirectory(Context context) {
        final TrustedDirectory trustedDirectory = FetchTrustedDirectoryFilter.getTrustedDirectoryFromContext(context);
        if (trustedDirectory == null) {
            logger.error("({}) trustedDirectory not found in request context", FAPIUtils.getFapiInteractionIdForDisplay(context));
            throw new IllegalStateException("trustedDirectory not found in request context");
        }
        return trustedDirectory;
    }

    private ApiClient getApiClient(Context context) {
        final ApiClient apiClient = FetchApiClientFilter.getApiClientFromContext(context);
        if (apiClient == null) {
            logger.error("({}) apiClient not found in request context", FAPIUtils.getFapiInteractionIdForDisplay(context));
            throw new IllegalStateException("apiClient not found in request context");
        }
        return apiClient;
    }

    /**
     * The JWKSet for an ApiClient can either be looked up via a URL or it is embedded into the software statement,
     * use the TrustedDirectory configuration to determine the location of the JWKSet.
     */
    private Promise<JWKSet, FailedToLoadJWKException> getJwkSet(ApiClient apiClient, TrustedDirectory trustedDirectory) {
        if (trustedDirectory.softwareStatementHoldsJwksUri()) {
            return getJwkSetUsingJwksUri(apiClient);
        } else {
            return getJwkSetFromSsaClaim(apiClient, trustedDirectory);
        }
    }

    /**
     * Use the jwkSetService to fetch the JWKSet using the ApiClient.jwksUri
     */
    private Promise<JWKSet, FailedToLoadJWKException> getJwkSetUsingJwksUri(ApiClient apiClient) {
        try {
            if (apiClient.getJwksUri() == null) {
                return Promises.newExceptionPromise(new FailedToLoadJWKException("TrustedDirectory configuration " +
                        "requires the jwksUri to be set for the apiClient"));
            }
            return jwkSetService.getJwkSet(apiClient.getJwksUri().toURL());
        } catch (MalformedURLException e) {
            return Promises.newExceptionPromise(new FailedToLoadJWKException("Malformed jwksUri", e));
        }
    }

    /**
     * Extract the JWKSet from a claim within the software statement assertion.
     */
    private Promise<JWKSet, FailedToLoadJWKException> getJwkSetFromSsaClaim(ApiClient apiClient, TrustedDirectory trustedDirectory) {
        final String jwksClaimsName = trustedDirectory.getSoftwareStatementJwksClaimName();
        if (jwksClaimsName == null) {
            return Promises.newExceptionPromise(new FailedToLoadJWKException("Trusted Directory has " +
                    "softwareStatemdntHoldsJwksUri=false but is missing softwareStatementJwksClaimName value"));
        }
        final JsonValue rawJwks = apiClient.getSoftwareStatementAssertion().getClaimsSet().get(jwksClaimsName);
        if (rawJwks.isNull()) {
            return Promises.newExceptionPromise(new FailedToLoadJWKException("SSA is missing claim: " + jwksClaimsName
                    + " which is expected to contain the JWKS"));
        }
        try {
            return Promises.newResultPromise(JWKSet.parse(rawJwks));
        } catch (JsonException je) {
            return Promises.newExceptionPromise(new FailedToLoadJWKException("Invalid JWKS json at claim: " + jwksClaimsName, je));
        }
    }

    /**
     * Heaplet responsible for constructing {@link FetchApiClientFilter} objects.
     *
     * Configuration:
     * - jwkSetService the name of the JwkSetService object on the heap to use to fetch remote JWKSets
     *
     * Example config:
     * {
     *      "comment": "Add the JWKS for the ApiClient to the context attributes",
     *      "name": "FetchApiClientJwksFilter",
     *      "type": "FetchApiClientJwksFilter",
     *      "config": {
     *          "jwkSetService": "OBJwkSetService"
     *       }
     * }
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            final JwkSetService jwkSetService = config.get("jwkSetService").as(requiredHeapObject(heap, JwkSetService.class));
            return new FetchApiClientJwksFilter(jwkSetService);
        }
    }
}
