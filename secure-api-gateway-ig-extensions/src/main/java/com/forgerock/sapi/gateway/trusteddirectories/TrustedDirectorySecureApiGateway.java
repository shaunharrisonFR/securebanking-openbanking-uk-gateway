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

/**
 * Holds static configuration information for the Trusted Directory provided by the Secure API Gateway itself. For
 * development and test purposes it is useful to be able to use the Secure API Gateway to issue SSAs and Certificates
 * associated with the SSA itself.
 */
public class TrustedDirectorySecureApiGateway implements TrustedDirectory {

    /*
     * The value expected to be found in the issuer field of software statements issued by the Open Banking Test
     * directory
     */
    final static String issuer = "test-publisher";

    /*
     * The URL at which the Open Banking Test Directory JWKS are held, containing public certificates that may be used
     * to validate Open Banking Test directory issues Software Statements.
     */
    String secureApiGatewayJwksUri = null;

    final static boolean softwareStatementHoldsJwksUri = false;

    final static String softwareStatementJwksClaimName = "software_jwks";

    /*
     * The name of the claim in the Open Banking Test Directory issued software statement that holds a uid for the
     * organisation
     */
    final static String softwareStatementOrgIdClaimName = "org_id";
    /*
     * The name of the claim in the Open Banking Test Directory issued software statement that holds a uid for the
     * software statement
     */
    final static String softwareStatementSoftwareIdClaimName = "software_id";

    /**
     * Constructor
     * @param secureApiGatewayJwksUri The jwks_uri against which SSAs issued by the Secure API Gateway can be
     *                                validated
     */
    public TrustedDirectorySecureApiGateway(String secureApiGatewayJwksUri){
        this.secureApiGatewayJwksUri = secureApiGatewayJwksUri;
    }

    @Override
    public String getIssuer() {
        return issuer;
    }

    @Override
    public String getDirectoryJwksUri() {
        return this.secureApiGatewayJwksUri;
    }

    @Override
    public boolean softwareStatementHoldsJwksUri() {
        return softwareStatementHoldsJwksUri;
    }

    @Override
    public String getSoftwareStatementJwksUriClaimName() {
        return null;
    }

    @Override
    public String getSoftwareStatementJwksClaimName() {
        return softwareStatementJwksClaimName;
    }

    @Override
    public String getSoftwareStatementOrgIdClaimName() {
        return softwareStatementOrgIdClaimName;
    }

    @Override
    public String getSoftwareStatementSoftwareIdClaimName() {
        return softwareStatementSoftwareIdClaimName;
    }
}
