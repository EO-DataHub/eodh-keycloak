package tpzuk.eodh.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.protocol.ProtocolMapperUtils;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.rar.AuthorizationDetails;
import org.keycloak.representations.IDToken;

import java.util.*;

/// This class is a ProtocolMapper that adds dynamic scope parameters to claims.
/// No authorisation takes place, so the user can map any value(s) to the claim.
public class DynamicScopeProtocolMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "oidc-dynamic-scope-protocol-mapper";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    private static final Logger logger = Logger.getLogger(
            DynamicScopeProtocolMapper.class);

    static {
        ProviderConfigProperty multivaluedProperty = new ProviderConfigProperty();
        multivaluedProperty.setName(ProtocolMapperUtils.MULTIVALUED);
        multivaluedProperty.setLabel("Multivalued");
        multivaluedProperty.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        multivaluedProperty.setHelpText("If true, the claim will be an array of values.");
        configProperties.add(multivaluedProperty);

        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties,
                DynamicScopeProtocolMapper.class);
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
            UserSessionModel userSession, KeycloakSession keycloakSession,
            ClientSessionContext clientSessionCtx) {

        List<String> scopes = new ArrayList<>();
        for (AuthorizationDetails details : clientSessionCtx.getAuthorizationRequestContext()
                .getAuthorizationDetailEntries()) {

            if (!details.isDynamicScope() || !details.getClientScope().getName().equals(mappingModel.getConfig()
                    .get(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME))) {
                continue;
            }

            logger.debug("Applying dynamic scope " + details.getAuthorizationDetails().getScopeNameFromCustomData());
            scopes.add(details.getDynamicScopeParam());
        }

        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, scopes);
    }

    @Override
    public String getDisplayCategory() {
        return "Token Mapper";
    }

    @Override
    public String getDisplayType() {
        return "Dynamic Scope Protocol Mapper";
    }

    @Override
    public String getHelpText() {
        return "Adds dynamic scope parameters to claims.";
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