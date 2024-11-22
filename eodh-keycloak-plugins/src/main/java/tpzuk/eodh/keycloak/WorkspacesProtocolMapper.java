package tpzuk.eodh.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.protocol.ProtocolMapperUtils;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.IDToken;
import org.keycloak.services.ErrorResponseException;
import org.keycloak.services.util.UserSessionUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

import jakarta.ws.rs.core.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class WorkspacesProtocolMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "oidc-workspaces-protocol-mapper";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    private static final Logger logger = Logger.getLogger(
            WorkspacesProtocolMapper.class);

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName("apiUrl");
        property.setLabel("API URL");
        property.setHelpText("The URL of the API to check workspace authorization.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setRequired(true);
        configProperties.add(property);

        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties,
                WorkspacesProtocolMapper.class);
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
            UserSessionModel userSession, KeycloakSession keycloakSession,
            ClientSessionContext clientSessionCtx) {

        Collection<String> workspaces = getWorkspaces(
                mappingModel.getConfig().get("apiUrl"));

        // force multivalued as the attribute is not defined for this mapper
        mappingModel.getConfig().put(ProtocolMapperUtils.MULTIVALUED, "true");

        logger.info("Setting workspaces claim...");
        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, workspaces);
    }

    protected Collection<String> getWorkspaces(String apiUrl) {
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

            // Check the response
            int status = con.getResponseCode();

            if (status >= 400) {
                throw new ErrorResponseException("API Error", "API request failed",
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
            JsonObject data = JsonParser.parseString(content.toString()).getAsJsonObject();
            JsonArray workspacesData;
            try {
                workspacesData = data.get("workspaces").getAsJsonArray();
            } catch (JsonParseException e) {
                logger.error("Error parsing user workspaces", e);
                throw new ErrorResponseException("Server Error", "Error parsing user workspaces",
                        Response.Status.INTERNAL_SERVER_ERROR);
            }
            Collection<String> workspaces = new ArrayList<>();
            workspacesData.forEach(workspace -> workspaces.add(workspace.getAsString()));

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
        return "Adds a user authorized workspaces to claims.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}