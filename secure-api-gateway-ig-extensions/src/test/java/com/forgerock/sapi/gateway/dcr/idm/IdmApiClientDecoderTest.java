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
package com.forgerock.sapi.gateway.dcr.idm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.jose.common.JwtReconstruction;
import org.forgerock.json.jose.exceptions.InvalidJwtException;
import org.forgerock.json.jose.jws.JwsAlgorithm;
import org.forgerock.json.jose.jws.JwsHeader;
import org.forgerock.json.jose.jws.SignedJwt;
import org.forgerock.json.jose.jws.handlers.SigningHandler;
import org.forgerock.json.jose.jwt.JwtClaimsSet;
import org.junit.jupiter.api.Test;

import com.forgerock.sapi.gateway.dcr.ApiClient;

public class IdmApiClientDecoderTest {

    private final IdmApiClientDecoder idmApiClientDecoder = new IdmApiClientDecoder();

    public static JsonValue createIdmApiClientDataAllFields(String clientId) {
        return createIdmApiClientDataRequiredFieldsOnly(clientId).put("jwksUri", "https://somelocation/jwks.jwks");
    }

    public static JsonValue createIdmApiClientDataRequiredFieldsOnly(String clientId) {
        return json(object(field("id", "ebSqTNqmQXFYz6VtWGXZAa"),
                field("name", "Automated Testing"),
                field("description", "Blah blah blah"),
                field("ssa", createTestSoftwareStatementAssertion().build()),
                field("oauth2ClientId", clientId),
                field("apiClientOrg", object(field("id", "98761234"),
                        field("name", "Test Organisation")))));
    }

    /**
     * @return SignedJwt which represents a Software Statement Assertion (ssa). This is a dummy JWT in place of a real
     * software statement, it does not contain a realistic set of claims and the signature (and kid) are junk.
     *
     * This is good enough for this test as the ssa is not processed, only decoded into a SignedJwt object
     */
    private static SignedJwt createTestSoftwareStatementAssertion() {
        final JwsHeader header = new JwsHeader();
        header.setKeyId("12345");
        header.setAlgorithm(JwsAlgorithm.PS256);
        return new SignedJwt(header, new JwtClaimsSet(Map.of("claim1", "value1")), new SigningHandler() {
            @Override
            public byte[] sign(JwsAlgorithm algorithm, byte[] data) {
                return "gYdMUpAvrotMnMP8tHj".getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public boolean verify(JwsAlgorithm algorithm, byte[] data, byte[] signature) {
                return false;
            }
        });
    }

    public static void verifyIdmClientDataMatchesApiClientObject(JsonValue idmClientData, ApiClient actualApiClient) {
        assertEquals(idmClientData.get("id").asString(), actualApiClient.getSoftwareClientId());
        assertEquals(idmClientData.get("name").asString(), actualApiClient.getClientName());
        assertEquals(idmClientData.get("oauth2ClientId").asString(), actualApiClient.getOauth2ClientId());
        final JsonValue jwksUri = idmClientData.get("jwksUri");
        if (jwksUri.isNotNull()) {
            assertEquals(jwksUri.asString(), actualApiClient.getJwksUri().toString());
        }
        assertEquals(idmClientData.get("apiClientOrg").get("id").asString(), actualApiClient.getOrganisation().getId());
        assertEquals(idmClientData.get("apiClientOrg").get("name").asString(), actualApiClient.getOrganisation().getName());

        final String ssaStr = idmClientData.get("ssa").asString();
        final SignedJwt expectedSignedJwt = new JwtReconstruction().reconstructJwt(ssaStr, SignedJwt.class);
        assertEquals(expectedSignedJwt.getHeader(), actualApiClient.getSoftwareStatementAssertion().getHeader());
        assertEquals(expectedSignedJwt.getClaimsSet(), actualApiClient.getSoftwareStatementAssertion().getClaimsSet());
    }

    @Test
    void decodeApiClientAllFieldsSet() {
        final JsonValue idmJson = createIdmApiClientDataAllFields("1234");
        final ApiClient apiClient = new IdmApiClientDecoder().decode(idmJson);
        verifyIdmClientDataMatchesApiClientObject(idmJson, apiClient);
    }

    @Test
    void decodeApiClientRequiredFieldsOnly() {
        final JsonValue idmJson = createIdmApiClientDataRequiredFieldsOnly("9999");
        final ApiClient apiClient = idmApiClientDecoder.decode(idmJson);
        verifyIdmClientDataMatchesApiClientObject(idmJson, apiClient);
        assertNull(apiClient.getJwksUri(), "jwksUri must be null");
    }

    @Test
    void failToDecodeMissingMandatoryFields() {
        JsonValueException decodeException = assertThrows(JsonValueException.class,
                () -> idmApiClientDecoder.decode(json(object())));
        assertEquals("/name: is a required field, failed to decode IDM ApiClient", decodeException.getMessage());

        final JsonValue missingSsaField = createIdmApiClientDataRequiredFieldsOnly("123454");
        missingSsaField.remove("ssa");

        decodeException = assertThrows(JsonValueException.class, () -> idmApiClientDecoder.decode(missingSsaField));
        assertEquals("/ssa: is a required field, failed to decode IDM ApiClient", decodeException.getMessage());
    }

    @Test
    void failToDecodeDueToUnexpectedException() {
        final JsonValue corruptSsaField = createIdmApiClientDataRequiredFieldsOnly("123454");
        corruptSsaField.put("ssa", "This is not a JWT");

        JsonValueException decodeException = assertThrows(JsonValueException.class, () -> idmApiClientDecoder.decode(corruptSsaField));
        assertEquals("/ssa: failed to decode JWT, raw jwt string: This is not a JWT", decodeException.getMessage());
        assertThat(decodeException.getCause()).isInstanceOf(InvalidJwtException.class);
        decodeException.getCause().printStackTrace();
    }
}