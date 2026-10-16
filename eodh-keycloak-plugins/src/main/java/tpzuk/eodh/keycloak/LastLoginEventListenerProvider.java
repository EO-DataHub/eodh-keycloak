package tpzuk.eodh.keycloak;

import org.keycloak.Config;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/// Records the time of each user's latest successful login in the
/// `lastLoginTime` user attribute, as milliseconds since the epoch.
public class LastLoginEventListenerProvider implements EventListenerProvider {

    public static final String ATTRIBUTE = "lastLoginTime";

    private static final long MAX_CLOCK_SKEW_MS = 24 * 60 * 60 * 1000;

    private final KeycloakSession session;

    LastLoginEventListenerProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() != EventType.LOGIN || event.getUserId() == null) {
            return;
        }

        RealmModel realm = session.realms().getRealm(event.getRealmId());
        UserModel user = realm == null ? null : session.users().getUserById(realm, event.getUserId());
        if (user == null || user.getServiceAccountClientLink() != null) {
            return;
        }

        // A stale event, e.g. from a pod with a slower clock, never overwrites a
        // newer stored time.
        if (isNewer(user.getFirstAttribute(ATTRIBUTE), event.getTime())) {
            return;
        }
        user.setSingleAttribute(ATTRIBUTE, Long.toString(event.getTime()));
    }

    // A value more than a day ahead is corrupt and would otherwise block every
    // later write.
    private static boolean isNewer(String value, long time) {
        try {
            long stored = Long.parseLong(value);
            return stored >= time && stored - time <= MAX_CLOCK_SKEW_MS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
    }

    @Override
    public void close() {
    }

    public static class Factory implements EventListenerProviderFactory {

        public static final String ID = "eodh-last-login";

        @Override
        public EventListenerProvider create(KeycloakSession session) {
            return new LastLoginEventListenerProvider(session);
        }

        @Override
        public void init(Config.Scope config) {
        }

        @Override
        public void postInit(KeycloakSessionFactory factory) {
        }

        @Override
        public void close() {
        }

        @Override
        public String getId() {
            return ID;
        }
    }
}
