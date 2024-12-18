package tpzuk.eodh.keycloak;

import org.keycloak.models.*;
import org.keycloak.protocol.ProtocolMapperUtils;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;

import java.util.*;

/// This class is a ProtocolMapper that adds an existing claim to a token under a 
/// different path. Useful for when a certain client needs claims under a path that
/// differs from standard claims.
public class ExistingClaimProtocolMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "oidc-existing-claim-protocol-mapper";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final String EXISTING_CLAIM = "existingClaim";

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(EXISTING_CLAIM);
        property.setLabel("Existing Claim");
        property.setHelpText("The path to the existing claim to include in the token under a different path.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setRequired(true);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(ProtocolMapperUtils.MULTIVALUED);
        property.setLabel("Multivalued");
        property.setHelpText("If true, the claim will be an array of values.");
        property.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        property.setDefaultValue("false");
        configProperties.add(property);

        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties,
                DynamicScopeProtocolMapper.class);
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
            UserSessionModel userSession, KeycloakSession keycloakSession,
            ClientSessionContext clientSessionCtx) {

        Object claim = token.getOtherClaims().get(mappingModel.getConfig()
                .get(EXISTING_CLAIM));

        // Handle single value to multivalue conversion if required
        if (!(claim instanceof Collection)
                && Boolean.parseBoolean(mappingModel.getConfig()
                        .get(ProtocolMapperUtils.MULTIVALUED))) {
            claim = Collections.singletonList(claim);
        }

        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, claim);
    }

    @Override
    public String getDisplayCategory() {
        return "Token Mapper";
    }

    @Override
    public String getDisplayType() {
        return "Existing Claim Protocol Mapper";
    }

    @Override
    public String getHelpText() {
        return "Adds existing claim to token under a new path.";
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
        return 100;
    }
}