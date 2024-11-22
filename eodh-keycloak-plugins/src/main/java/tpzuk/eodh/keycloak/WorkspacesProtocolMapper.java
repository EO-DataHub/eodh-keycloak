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

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.HttpURLConnection;
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

        Collection<String> workspaces = Arrays.asList("workspace1", "workspace2");

        // force multivalued as the attribute is not defined for this mapper
        mappingModel.getConfig().put(ProtocolMapperUtils.MULTIVALUED, "true");

        logger.info("Setting workspaces claim...");
        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, workspaces);
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