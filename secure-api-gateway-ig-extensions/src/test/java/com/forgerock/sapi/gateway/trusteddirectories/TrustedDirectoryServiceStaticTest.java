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
package com.forgerock.sapi.gateway.trusteddirectories;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrustedDirectoryServiceStaticTest {

    @BeforeEach
    void setUp() {

    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void getTrustedDirectoryConfiguration_IGTestDirectoryEnabled() {
        // Given
        Boolean enableIGTestTrustedDirectory = true;
        String testDirectoryFQDN = "https://sapi.bigbank.com/jwkms/apiclient/jwks";
        TrustedDirectoryService trustedDirectoryService = new TrustedDirectoryServiceStatic(enableIGTestTrustedDirectory, testDirectoryFQDN);
        // When
        TrustedDirectory directoryConfig = trustedDirectoryService.getTrustedDirectoryConfiguration(TrustedDirectorySecureApiGateway.issuer);

        // Then
        assertThat(directoryConfig).isNotNull();
        assertThat(directoryConfig.getIssuer()).isEqualTo(TrustedDirectorySecureApiGateway.issuer);
        assertThat(directoryConfig.getDirectoryJwksUri()).isEqualTo(testDirectoryFQDN);
        assertThat(directoryConfig.softwareStatementHoldsJwksUri()).isEqualTo(false);
        assertThat(directoryConfig.getSoftwareStatementJwksUriClaimName()).isNull();
        assertThat(directoryConfig.getSoftwareStatementJwksClaimName()).isEqualTo(TrustedDirectorySecureApiGateway.softwareStatementJwksClaimName);
        assertThat(directoryConfig.getSoftwareStatementOrgIdClaimName()).isEqualTo(TrustedDirectorySecureApiGateway.softwareStatementOrgIdClaimName);
        assertThat(directoryConfig.getSoftwareStatementSoftwareIdClaimName()).isEqualTo(TrustedDirectorySecureApiGateway.softwareStatementSoftwareIdClaimName);
    }

    @Test
    void getTrustedDirectoryConfiguration_IGTestDirectoryNotEnabled() {
        // Given
        Boolean enableIGTestTrustedDirectory = false;
        String testDirectoryFQDN = "https://ig.bigbank.com/jwkms/apiclient/jwks";
        TrustedDirectoryService trustedDirectoryService = new TrustedDirectoryServiceStatic(enableIGTestTrustedDirectory, testDirectoryFQDN);
        // When
        TrustedDirectory directoryConfig = trustedDirectoryService.getTrustedDirectoryConfiguration(TrustedDirectorySecureApiGateway.issuer);

        // Then
        assertThat(directoryConfig).isNull();
    }

    @Test
    void getTrustedDirectoryConfiguration_getOpenBankingTestTrustedDirectory(){
        // Given
        Boolean enableIGTestTrustedDirectory = false;
        String testDirectoryFQDN = "https://ig.bigbank.com/jwkms/apiclient/jwks";
        TrustedDirectoryService trustedDirectoryService = new TrustedDirectoryServiceStatic(enableIGTestTrustedDirectory, testDirectoryFQDN);
        // When
        TrustedDirectory directoryConfig = trustedDirectoryService.getTrustedDirectoryConfiguration(TrustedDirectoryOpenBankingTest.issuer);

        // Then
        assertThat(directoryConfig).isNotNull();
        assertThat(directoryConfig.getIssuer()).isEqualTo(TrustedDirectoryOpenBankingTest.issuer);
    }
}