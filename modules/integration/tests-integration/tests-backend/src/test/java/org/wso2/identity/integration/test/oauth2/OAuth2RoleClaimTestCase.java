/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.integration.test.oauth2;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.identity.application.common.model.xsd.Claim;
import org.wso2.carbon.identity.application.common.model.xsd.ClaimConfig;
import org.wso2.carbon.identity.application.common.model.xsd.ClaimMapping;
import org.wso2.carbon.identity.application.common.model.xsd.InboundAuthenticationRequestConfig;
import org.wso2.carbon.identity.application.common.model.xsd.OutboundProvisioningConfig;
import org.wso2.carbon.identity.application.common.model.xsd.Property;
import org.wso2.carbon.identity.application.common.model.xsd.ServiceProvider;
import org.wso2.carbon.identity.oauth.stub.dto.OAuthConsumerAppDTO;
import org.wso2.carbon.um.ws.api.stub.ClaimValue;
import org.wso2.identity.integration.test.rest.api.server.application.management.v1.model.ApplicationModel;
import org.wso2.identity.integration.test.rest.api.server.application.management.v1.model.UserCreationModel;
import org.wso2.identity.integration.test.utils.OAuth2Constant;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class OAuth2RoleClaimTestCase extends OAuth2ServiceAbstractIntegrationTest {

    private static final String OAUTH_ROLE = "Internal/oauthRole";
    private static final String ROLES_CLAIM_URI = "http://wso2.org/claims/roles";
    private static final String OIDC_ROLES_CLAIM_URI = "roles";
    private static final String FIRST_NAME_VALUE = "FirstName";
    private static final String LAST_NAME_VALUE = "LastName";
    private static final String EMAIL_VALUE = "email@wso2.com";
    private static final String OPENID_SCOPE_PROPERTY = "openid";
    private static final String OPENID_SCOPE_RESOURCE = "/_system/config/oidc";
    private static final String MULTI_ATTRIBUTE_SEPARATOR = ",";

    private String consumerKey;
    private String consumerSecret;
    private String applicationId;
    private String roleId;
    private String userId;

    private CloseableHttpClient client;
    private SCIM2RestClient scim2RestClient;

    private static final String FIRST_NAME_CLAIM_URI = "http://wso2.org/claims/givenname";
    private static final String LAST_NAME_CLAIM_URI = "http://wso2.org/claims/lastname";

    private String USERNAME;
    private String PASSWORD;

    @BeforeClass(alwaysRun = true)
    public void testInit() throws Exception {

        super.init(TestUserMode.TENANT_USER);

        this.USERNAME = tenantInfo.getContextUser().getUserName();
        this.PASSWORD = tenantInfo.getContextUser().getPassword();
        setSystemproperties();
        client = HttpClients.createDefault();
        scim2RestClient = new SCIM2RestClient(backendURL.replace("services/", ""), tenantInfo);

//        remoteUSMServiceClient.addRole(OAUTH_ROLE, null, null);
//        remoteUSMServiceClient.addUser(USERNAME, PASSWORD, null, getUserClaims(), "default", false);

        roleId = scim2RestClient.addRole("oauthRole");
        userId = scim2RestClient.createUser(getUserCreationInfo());
    }

    @AfterClass(alwaysRun = true)
    public void atEnd() throws Exception {

//        deleteApplication();
//        remoteUSMServiceClient.deleteRole(OAUTH_ROLE);
//        remoteUSMServiceClient.deleteUser(USERNAME);
//        consumerKey = null;

        deleteApp(applicationId);
        scim2RestClient.deleteRole(roleId);
        scim2RestClient.deleteUser(userId);
        consumerKey = null;
    }

    @Test(groups = "wso2.is", description = "Check Oauth2 application registration")
    public void testRegisterApplication() throws Exception {

        ApplicationModel app = addApplication();
        Assert.assertNotNull(app, "Application creation failed.");

        consumerKey = app.getInboundProtocolConfiguration().getOidc().getClientId();
        Assert.assertNotNull(consumerKey, "Application creation failed.");

        consumerSecret = app.getInboundProtocolConfiguration().getOidc().getClientSecret();
        applicationId = app.getId();
    }

    @Test(groups = "wso2.is", description = "Check id_token before updating roles.", dependsOnMethods =
            "testRegisterApplication")
    public void testSendAuthorizedPost() throws Exception {

        HttpPost request = new HttpPost(OAuth2Constant.ACCESS_TOKEN_ENDPOINT);
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("grant_type",
                OAuth2Constant.OAUTH2_GRANT_TYPE_RESOURCE_OWNER));
        urlParameters.add(new BasicNameValuePair("username", USERNAME ));
        urlParameters.add(new BasicNameValuePair("password", PASSWORD));
        urlParameters.add(new BasicNameValuePair("scope", "openid"));

        request.setHeader("User-Agent", OAuth2Constant.USER_AGENT);
        request.setHeader("Authorization", "Basic " + Base64.encodeBase64String((consumerKey + ":" + consumerSecret)
                .getBytes()).trim());
        request.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        request.setEntity(new UrlEncodedFormEntity(urlParameters));

        HttpResponse response = client.execute(request);

        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

        Object obj = JSONValue.parse(rd);
        EntityUtils.consume(response.getEntity());

        String encodedIdToken = ((JSONObject) obj).get("id_token").toString().split("\\.")[1];
        Object idToken = JSONValue.parse(new String(Base64.decodeBase64(encodedIdToken)));
        Assert.assertNull(((JSONObject) idToken).get(OIDC_ROLES_CLAIM_URI), 
                "Id token must not contain role claim which is not configured for the requested scope.");
    }

    @Test(groups = "wso2.is", description = "Check id_token after updating roles", dependsOnMethods =
            "testSendAuthorizedPost")
    public void testSendAuthorizedPostAfterRoleUpdate() throws Exception {

        //remoteUSMServiceClient.updateRoleListOfUser(USERNAME, null, new String[]{OAUTH_ROLE});
        scim2RestClient.updateUserRole(roleId, userId);
        HttpPost request = new HttpPost(OAuth2Constant.ACCESS_TOKEN_ENDPOINT);
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("grant_type",
                OAuth2Constant.OAUTH2_GRANT_TYPE_RESOURCE_OWNER));
        urlParameters.add(new BasicNameValuePair("username", USERNAME));
        urlParameters.add(new BasicNameValuePair("password", PASSWORD));
        urlParameters.add(new BasicNameValuePair("scope", "openid"));

        request.setHeader("User-Agent", OAuth2Constant.USER_AGENT);
        request.setHeader("Authorization", "Basic " + Base64.encodeBase64String((consumerKey + ":" + consumerSecret)
                .getBytes()).trim());
        request.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        request.setEntity(new UrlEncodedFormEntity(urlParameters));

        HttpResponse response = client.execute(request);

        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

        Object obj = JSONValue.parse(rd);
        EntityUtils.consume(response.getEntity());

        String encodedIdToken = ((JSONObject) obj).get("id_token").toString().split("\\.")[1];
        Object idToken = JSONValue.parse(new String(Base64.decodeBase64(encodedIdToken)));
        Assert.assertNull(((JSONObject) idToken).get(OIDC_ROLES_CLAIM_URI), 
                "Id token must not contain role claim which is not configured for the requested scope.");
    }

    private UserCreationModel getUserCreationInfo() {

        UserCreationModel userInfo = new UserCreationModel();

        userInfo.setUserName("testAshan");
        userInfo.setGivenName("Ashan");
        userInfo.setFamilyName("Palihakkara");
        userInfo.setPassword("ashan@123");
        userInfo.setHomeEmail("ashanthamara98@gmail.com");
        userInfo.setWorkEmail("ashant@wso2.com");

        return userInfo;
    }

    private ClaimValue[] getUserClaims() {

        ClaimValue[] claimValues = new ClaimValue[3];

        ClaimValue firstName = new ClaimValue();
        firstName.setClaimURI(FIRST_NAME_CLAIM_URI);
        firstName.setValue(FIRST_NAME_VALUE);
        claimValues[0] = firstName;

        ClaimValue lastName = new ClaimValue();
        lastName.setClaimURI(LAST_NAME_CLAIM_URI);
        lastName.setValue(LAST_NAME_VALUE);
        claimValues[1] = lastName;

        ClaimValue email = new ClaimValue();
        email.setClaimURI(EMAIL_CLAIM_URI);
        email.setValue(EMAIL_VALUE);
        claimValues[2] = email;

        return claimValues;
    }

    public OAuthConsumerAppDTO createApplication(OAuthConsumerAppDTO appDTO) throws Exception {

        OAuthConsumerAppDTO appDtoResult = null;

        adminClient.registerOAuthApplicationData(appDTO);
        OAuthConsumerAppDTO[] appDtos = adminClient.getAllOAuthApplicationData();

        for (OAuthConsumerAppDTO appDto : appDtos) {
            if (appDto.getApplicationName().equals(OAuth2Constant.OAUTH_APPLICATION_NAME)) {
                appDtoResult = appDto;
                consumerKey = appDto.getOauthConsumerKey();
                consumerSecret = appDto.getOauthConsumerSecret();
            }
        }
        ServiceProvider serviceProvider = new ServiceProvider();
        serviceProvider.setApplicationName(SERVICE_PROVIDER_NAME);
        serviceProvider.setDescription(SERVICE_PROVIDER_DESC);
        appMgtclient.createApplication(serviceProvider);

        serviceProvider = appMgtclient.getApplication(SERVICE_PROVIDER_NAME);

        ClaimConfig claimConfig = new ClaimConfig();

        Claim emailClaim = new Claim();
        emailClaim.setClaimUri(EMAIL_CLAIM_URI);
        ClaimMapping emailClaimMapping = new ClaimMapping();
        emailClaimMapping.setRequested(true);
        emailClaimMapping.setLocalClaim(emailClaim);
        emailClaimMapping.setRemoteClaim(emailClaim);

        Claim roleClaim = new Claim();
        roleClaim.setClaimUri(ROLES_CLAIM_URI);
        ClaimMapping roleClaimMapping = new ClaimMapping();
        roleClaimMapping.setRequested(true);
        roleClaimMapping.setLocalClaim(roleClaim);
        roleClaimMapping.setRemoteClaim(roleClaim);

        claimConfig.setClaimMappings(new org.wso2.carbon.identity.application.common.model.xsd
                .ClaimMapping[]{emailClaimMapping, roleClaimMapping});

        serviceProvider.setClaimConfig(claimConfig);
        serviceProvider.setOutboundProvisioningConfig(new OutboundProvisioningConfig());
        List<InboundAuthenticationRequestConfig> authRequestList =
                new ArrayList<InboundAuthenticationRequestConfig>();

        if (consumerKey != null) {
            InboundAuthenticationRequestConfig opicAuthenticationRequest =
                    new InboundAuthenticationRequestConfig();
            opicAuthenticationRequest.setInboundAuthKey(consumerKey);
            opicAuthenticationRequest.setInboundAuthType("oauth2");
            if (consumerSecret != null && !consumerSecret.isEmpty()) {
                Property property = new Property();
                property.setName("oauthConsumerSecret");
                property.setValue(consumerSecret);
                Property[] properties = {property};
                opicAuthenticationRequest.setProperties(properties);
            }
            authRequestList.add(opicAuthenticationRequest);
        }

        String passiveSTSRealm = SERVICE_PROVIDER_NAME;
        if (passiveSTSRealm != null) {
            InboundAuthenticationRequestConfig opicAuthenticationRequest =
                    new InboundAuthenticationRequestConfig();
            opicAuthenticationRequest.setInboundAuthKey(passiveSTSRealm);
            opicAuthenticationRequest.setInboundAuthType("passivests");
            authRequestList.add(opicAuthenticationRequest);
        }

        String openidRealm = SERVICE_PROVIDER_NAME;
        if (openidRealm != null) {
            InboundAuthenticationRequestConfig opicAuthenticationRequest =
                    new InboundAuthenticationRequestConfig();
            opicAuthenticationRequest.setInboundAuthKey(openidRealm);
            opicAuthenticationRequest.setInboundAuthType("openid");
            authRequestList.add(opicAuthenticationRequest);
        }

        if (authRequestList.size() > 0) {
            serviceProvider.getInboundAuthenticationConfig()
                    .setInboundAuthenticationRequestConfigs(authRequestList.toArray(new
                            InboundAuthenticationRequestConfig[authRequestList.size()]));
        }
        appMgtclient.updateApplicationData(serviceProvider);
        return appDtoResult;
    }
}
