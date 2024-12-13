package tpzuk.eodh.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
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

/// This class is a ProtocolMapper that adds user authorized workspaces to claims.
/// Authorized workflows are fetched from an API and added to the token. The path to
/// the workspaces array and the workspace name within the array can be configured.
public class WorkspacesProtocolMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "oidc-workspaces-protocol-mapper";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final String API_URL = "apiUrl";
    private static final String WORKSPACES_RESPONSE_PATH = "workspacesResponsePath";
    private static final String WORKSPACE_NAME_PATH = "workspaceNamePath";

    private static final Logger logger = Logger.getLogger(
            WorkspacesProtocolMapper.class);

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(API_URL);
        property.setLabel("API URL");
        property.setHelpText("The URL of the API to check workspace authorization.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setRequired(true);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(WORKSPACES_RESPONSE_PATH);
        property.setLabel("Workspaces Response Path");
        property.setHelpText("The path within the response to the workspaces array. Separate children with dots.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setDefaultValue("workspaces");
        property.setRequired(false);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(WORKSPACE_NAME_PATH);
        property.setLabel("Workspace Name Path");
        property.setHelpText(
                "The path within the workspace object to the workspace name. Separate children with dots.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setRequired(false);
        property.setDefaultValue("name");
        configProperties.add(property);

        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties,
                WorkspacesProtocolMapper.class);
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
            UserSessionModel userSession, KeycloakSession keycloakSession,
            ClientSessionContext clientSessionCtx) {

        // Get available workspaces for user
        Set<String> workspaces = getWorkspaces(
                mappingModel, keycloakSession.tokens().encode(token));

        // Check if there are any dynamic scopes to be applied
        for (AuthorizationDetails details : clientSessionCtx.getAuthorizationRequestContext()
                .getAuthorizationDetailEntries()) {

            if (!details.isDynamicScope() || !details.getClientScope().getName().equals(mappingModel.getConfig()
                    .get(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME))) {
                continue;
            }

            logger.debug("Applying dynamic scope " + details.getAuthorizationDetails().getScopeNameFromCustomData());
            workspaces = workspaces.stream().filter(workspace -> workspace.equals(details.getDynamicScopeParam()))
                    .collect(Collectors.toSet());

            if (workspaces.isEmpty()) {
                throw new ErrorResponseException("Forbidden", "Requested workspace not authorized",
                        Response.Status.FORBIDDEN);
            }
        }

        // Force multivalued as the attribute is not defined for this mapper
        mappingModel.getConfig().put(ProtocolMapperUtils.MULTIVALUED, "true");

        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, workspaces);
    }

    private Set<String> getWorkspaces(ProtocolMapperModel mapping, String token) {
        String apiUrl = mapping.getConfig().get(API_URL);
        String workspacesPath = mapping.getConfig().get(WORKSPACES_RESPONSE_PATH);
        String workspaceNamePath = mapping.getConfig().get(WORKSPACE_NAME_PATH);

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
                throw new ErrorResponseException("Error Retrieving Workspaces",
                        "Get Workspaces request failed with status " + status,
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
            Set<String> workspaces = new HashSet<String>();
            try {
                // Get the workspaces array
                JsonElement el = JsonParser.parseString(content.toString());
                for (String path : workspacesPath.split("\\.")) {
                    logger.info("Path: " + path);
                    el = el.getAsJsonObject().get(path);
                }
                JsonArray array = el.getAsJsonArray();

                // Get the workspace name for each workspace in list
                for (JsonElement el2 : array) {
                    for (String path : workspaceNamePath.split("\\.")) {
                        el2 = el2.getAsJsonObject().get(path);
                    }
                    workspaces.add(el2.getAsString());
                }
            } catch (JsonParseException e) {
                logger.error(String.format("Error parsing user workspaces, workspaces path: %s, name path: %s",
                        workspacesPath, workspaceNamePath), e);
                throw new ErrorResponseException("Server Error", "Error parsing user workspaces",
                        Response.Status.INTERNAL_SERVER_ERROR);
            }

            // Return the workspaces
            return workspaces;
        } catch (IOException e) {
            logger.error("Error getting user workspaces", e);
            throw new ErrorResponseException("Server Error", "Error getting user workspaces",
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String getDisplayCategory() {
        return "Token Mapper";
    }

    @Override
    public String getDisplayType() {
        return "Workspaces Protocol Mapper";
    }

    @Override
    public String getHelpText() {
        return "Adds user authorized workspaces to claims. Dynamic scoping can be used to filter workspaces.";
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