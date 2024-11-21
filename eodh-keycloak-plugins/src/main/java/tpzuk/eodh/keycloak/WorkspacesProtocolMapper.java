package tpzuk.eodh.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.IDToken;
import org.keycloak.services.ErrorResponseException;
import org.keycloak.services.util.UserSessionUtil;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class WorkspacesProtocolMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "oidc-workspaces-protocol-mapper";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    private static final Logger logger = Logger.getLogger(WorkspacesProtocolMapper.class);

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
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties, WorkspacesProtocolMapper.class);
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession,
            KeycloakSession keycloakSession, ClientSessionContext clientSessionCtx) {

        String username = userSession.getUser().getUsername();
        String workspace = userSession.getUser().getUsername();
        // String workspace = getWorkspaceFromScope(mappingModel,
        // clientSessionCtx);
        if (workspace == null) {
            return;
        }

        logger.info("Setting claim for user: " + username + " and workspace: " + workspace);

        if (isUserAuthorizedForWorkspace(mappingModel.getConfig().get("apiUrl"),
                username, workspace)) {
            OIDCAttributeMapperHelper.mapClaim(token, mappingModel, workspace);
        } else {
            throw new ErrorResponseException("forbidden", "User is not authorized for the workspace",
                    Response.Status.FORBIDDEN);
        }
    }

    private String getWorkspaceFromScope(ProtocolMapperModel mappingModel,
            ClientSessionContext clientSessionCtx) {

        String claimName = mappingModel.getConfig().get("claim.name") + ':';

        for (String scope : clientSessionCtx.getClientScopeIds()) {
            logger.info("Checking scope: " + scope);
            if (scope.startsWith(claimName)) {
                return scope.substring(claimName.length());
            }
        }
        return null;
    }

    private boolean isUserAuthorizedForWorkspace(String url, String username, String workspace) {
        try {
            URL url = new URL(apiUrl);
            connection.setDoOutput(true);
            String jsonInputString = String.format("{\"username\": \"%s\", \"workspace\": \"%s\"}", username,
                    workspace);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            connection.setRequestMethod("POST");
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // Parse the response body to check if the result is true
                // For simplicity, assume the response body is a plain text "true" or "false"
                Scanner scanner = new Scanner(connection.getInputStream());
                String responseBody = scanner.nextLine();
                scanner.close();
                return Boolean.parseBoolean(responseBody);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
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
    public String getDisplayType() {
        return "Workspaces Protocol Mapper";
    }

    @Override
    public String getHelpText() {
        return "Adds a workspaces claim to the access token based on user authorization.";
    }

    @Override
    public String getDisplayCategory() {
        return "Token Mapper";
    }

    public static ProtocolMapperModel create(String name, String apiUrl) {
        ProtocolMapperModel mapper = new ProtocolMapperModel();
        mapper.setName(name);
        mapper.setProtocolMapper(PROVIDER_ID);
        mapper.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        Map<String, String> config = new HashMap<>();
        config.put("apiUrl", apiUrl);
        mapper.setConfig(config);
        return mapper;
    }
}