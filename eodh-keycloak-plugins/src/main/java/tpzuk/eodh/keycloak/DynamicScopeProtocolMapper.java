package tpzuk.eodh.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.protocol.ProtocolMapperUtils;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.TokenIntrospectionTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.rar.AuthorizationDetails;
import org.keycloak.representations.IDToken;

import java.util.*;

/// This class is a ProtocolMapper that adds dynamic scope parameters to claims.
/// No authorisation takes place, so the user can map any value(s) to the claim.
public class DynamicScopeProtocolMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper,
        TokenIntrospectionTokenMapper {

    public static final String PROVIDER_ID = "oidc-dynamic-scope-protocol-mapper";
    public static final String SCOPE_NAME = "scope.name";
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

        ProviderConfigProperty scopeNameProperty = new ProviderConfigProperty();
        scopeNameProperty.setName(SCOPE_NAME);
        scopeNameProperty.setLabel("Scope Name");
        scopeNameProperty.setType(ProviderConfigProperty.STRING_TYPE);
        scopeNameProperty.setHelpText(
                "Name of the client scope whose parameterized scope values are mapped. Defaults to the claim name.");
        configProperties.add(scopeNameProperty);

        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties,
                DynamicScopeProtocolMapper.class);
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
            UserSessionModel userSession, KeycloakSession keycloakSession,
            ClientSessionContext clientSessionCtx) {

        String scopeName = mappingModel.getConfig().get(SCOPE_NAME);
        if (scopeName == null || scopeName.isEmpty()) {
            scopeName = mappingModel.getConfig().get(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME);
        }

        List<String> scopes = new ArrayList<>();
        for (AuthorizationDetails details : clientSessionCtx.getAuthorizationRequestContext()
                .getAuthorizationDetailEntries()) {

            if (!details.isParameterizedScope() || !details.getClientScope().getName().equals(scopeName)) {
                continue;
            }

            logger.debug("Applying dynamic scope " + details.getAuthorizationDetails().getScopeNameFromCustomData());
            scopes.add(details.getParameterizedScopeParam());
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