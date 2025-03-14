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
public class DynamicFilterProtocolMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper,
        TokenIntrospectionTokenMapper {

    public static final String PROVIDER_ID = "oidc-dynamic-filter-protocol-mapper";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    private static final Logger logger = Logger.getLogger(
            DynamicFilterProtocolMapper.class);

    static {
        ProviderConfigProperty multivaluedProperty = new ProviderConfigProperty();
        multivaluedProperty.setName(ProtocolMapperUtils.MULTIVALUED);
        multivaluedProperty.setLabel("Multivalued");
        multivaluedProperty.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        multivaluedProperty.setHelpText("If true, the claim will be an array of values.");
        configProperties.add(multivaluedProperty);

        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties,
                DynamicFilterProtocolMapper.class);
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
            UserSessionModel userSession, KeycloakSession keycloakSession,
            ClientSessionContext clientSessionCtx) {

        // Retrieve existing claims for the token
        Object claimsObject = token.getOtherClaims()
                .get(mappingModel.getConfig().get(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME));
        Set<String> claims = new HashSet<>();
        if (claimsObject instanceof Collection<?>) {
            for (Object item : (Collection<?>) claimsObject) {
                if (item instanceof String) {
                    claims.add((String) item);
                }
            }
        }

        // Remove claim to be filtered
        token.getOtherClaims().remove(mappingModel.getConfig().get(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME));

        // Initialise filtered claims
        Set<String> filteredClaims = new HashSet<>();

        // Apply filtering based on dynamic scopes
        for (AuthorizationDetails details : clientSessionCtx.getAuthorizationRequestContext()
                .getAuthorizationDetailEntries()) {

            if (!details.isDynamicScope() || !details.getClientScope().getName().equals(mappingModel.getConfig()
                    .get(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME))) {
                continue;
            }

            logger.debug("Applying dynamic scope " + details.getAuthorizationDetails().getScopeNameFromCustomData());
            filteredClaims.addAll(claims.stream().filter(claim -> claim.equals(details.getDynamicScopeParam()))
                    .collect(Collectors.toSet()));

            for (String claim : claims) {
                if (claim.equals(details.getDynamicScopeParam())) {
                    logger.debug("Dynamic scope parameter '" + claim + "' found in claims, added to filtered claims");
                    filteredClaims.add(claim);
                }
            }
        }

        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, filteredClaims);
    }

    @Override
    public String getDisplayCategory() {
        return "Token Mapper";
    }

    @Override
    public String getDisplayType() {
        return "Dynamic Filter Protocol Mapper";
    }

    @Override
    public String getHelpText() {
        return "Filter existing claims based on dynamic scope parameter";
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
        return 80;
    }
}