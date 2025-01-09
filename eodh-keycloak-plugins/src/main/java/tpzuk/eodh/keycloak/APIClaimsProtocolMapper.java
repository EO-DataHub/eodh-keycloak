package tpzuk.eodh.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.TokenIntrospectionTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.protocol.ProtocolMapperUtils;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.rar.AuthorizationDetails;
import org.keycloak.representations.IDToken;
import org.keycloak.services.ErrorResponseException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import jakarta.ws.rs.core.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/// This class is a ProtocolMapper that adds calls an API to get claims to add
/// to the token. The API is expected to return a JSON object containing a list
/// at a defined path, from which a field is extracted from each object in the
/// list. The fields are then added to the token as a multi-valued claim. 
/// 
/// The mapper can be configured to be dynamic, where it will filter the claims 
/// based on the dynamic scope parameter and return it as a single-valued claim.
/// Dynamic scoped claims that are not found in the API response will result in
/// a 403 Forbidden response.
/// 
/// The API request is made with a signed OIDC token for authorization. Any
/// claims required in the token should be composed using previous mappers.
public class APIClaimsProtocolMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper,
        TokenIntrospectionTokenMapper {

    public static final String PROVIDER_ID = "oidc-api-claims-protocol-mapper";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final String API_URL = "apiUrl";
    private static final String RESPONSE_LIST_PATH = "responseListPath";
    private static final String OBJECT_FIELD_PATH = "objectFieldPath";

    private static final Logger logger = Logger.getLogger(
            APIClaimsProtocolMapper.class);

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(API_URL);
        property.setLabel("API URL");
        property.setHelpText("The URL of the API to call.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setRequired(true);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(RESPONSE_LIST_PATH);
        property.setLabel("Response List Path");
        property.setHelpText("The path within the response to the object array. Separate children with dots.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setRequired(false);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(OBJECT_FIELD_PATH);
        property.setLabel("Object Field Path");
        property.setHelpText(
                "The path within the list object to the field. Separate children with dots.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setRequired(true);
        property.setDefaultValue("name");
        configProperties.add(property);

        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties,
                APIClaimsProtocolMapper.class);
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
            UserSessionModel userSession, KeycloakSession keycloakSession,
            ClientSessionContext clientSessionCtx) {

        // Get API response
        Set<String> claims = getUserClaims(
                mappingModel, keycloakSession.tokens().encode(token));

        // Check if there are any dynamic scopes to be applied
        boolean isDynamic = false;
        for (AuthorizationDetails details : clientSessionCtx.getAuthorizationRequestContext()
                .getAuthorizationDetailEntries()) {

            if (!details.isDynamicScope() || !details.getClientScope().getName().equals(mappingModel.getConfig()
                    .get(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME))) {
                continue;
            }

            if (!isDynamic) {
                isDynamic = true;
            }

            logger.debug("Applying dynamic scope " + details.getAuthorizationDetails().getScopeNameFromCustomData());
            claims = claims.stream().filter(claim -> claim.equals(details.getDynamicScopeParam()))
                    .collect(Collectors.toSet());

            if (claims.isEmpty()) {
                throw new ErrorResponseException("Forbidden", "Requested claim not authorized",
                        Response.Status.FORBIDDEN);
            }
        }

        if (!isDynamic) {
            mappingModel.getConfig().put(ProtocolMapperUtils.MULTIVALUED, "true");
        }

        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, claims);
    }

    private Set<String> getUserClaims(ProtocolMapperModel mapping, String token) {
        String apiUrl = mapping.getConfig().get(API_URL);
        String responseListPath = Optional.ofNullable(mapping.getConfig()
                .get(RESPONSE_LIST_PATH)).map(String::trim).orElse(null);
        String objectFieldPath = mapping.getConfig().get(OBJECT_FIELD_PATH);

        try {
            // Prepare the request
            URL url;
            try {
                url = new URI(apiUrl).toURL();
            } catch (Exception e) {
                logger.error("Error parsing API URL", e);
                throw new ErrorResponseException("Server Error", "Error parsing API URL",
                        Response.Status.INTERNAL_SERVER_ERROR);
            }
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + token);

            // Check the response status
            int status = con.getResponseCode();

            if (status >= 400) {
                throw new ErrorResponseException("Error retrieving claims",
                        "Get claims request failed with status " + status,
                        Response.Status.fromStatusCode(status));
            }

            // Read the response
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer content = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            con.disconnect();

            // Parse the response
            Set<String> claims = new HashSet<String>();
            try {
                // Get the response array
                JsonElement el = JsonParser.parseString(content.toString());
                if (responseListPath != null && !responseListPath.isEmpty()) {
                    for (String path : responseListPath.split("\\.")) {
                        logger.info("Path: " + path);
                        el = el.getAsJsonObject().get(path);
                    }
                }
                JsonArray array = el.getAsJsonArray();

                // Get the claim field for each object in list
                for (JsonElement el2 : array) {
                    for (String path : objectFieldPath.split("\\.")) {
                        el2 = el2.getAsJsonObject().get(path);
                    }
                    claims.add(el2.getAsString());
                }
            } catch (JsonParseException e) {
                logger.error(String.format("Error parsing user claims, list path: %s, field path: %s",
                        responseListPath, objectFieldPath), e);
                throw new ErrorResponseException("Server Error", "Error parsing user claims",
                        Response.Status.INTERNAL_SERVER_ERROR);
            }

            // Return the claims
            return claims;
        } catch (IOException e) {
            logger.error("Error getting user claims", e);
            throw new ErrorResponseException("Server Error", "Error getting user claims",
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String getDisplayCategory() {
        return "Token Mapper";
    }

    @Override
    public String getDisplayType() {
        return "API Claims Protocol Mapper";
    }

    @Override
    public String getHelpText() {
        return "Adds user claims to token from API response. Dynamic scoping can be used to filter claims.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public int getPriority() {
        return 60;
    }
}