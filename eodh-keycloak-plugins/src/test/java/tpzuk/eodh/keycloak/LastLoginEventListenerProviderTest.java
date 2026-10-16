package tpzuk.eodh.keycloak;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;

class LastLoginEventListenerProviderTest {

    private static final long TIME = 1760000000000L;

    private final KeycloakSession session = mock(KeycloakSession.class);
    private final RealmProvider realms = mock(RealmProvider.class);
    private final UserProvider users = mock(UserProvider.class);
    private final RealmModel realm = mock(RealmModel.class);
    private final UserModel user = mock(UserModel.class);
    private final LastLoginEventListenerProvider listener = new LastLoginEventListenerProvider(session);

    @BeforeEach
    void setUp() {
        when(session.realms()).thenReturn(realms);
        when(session.users()).thenReturn(users);
        when(realms.getRealm("realm-id")).thenReturn(realm);
        when(users.getUserById(realm, "user-id")).thenReturn(user);
    }

    private static Event event(EventType type, String userId) {
        Event event = new Event();
        event.setType(type);
        event.setRealmId("realm-id");
        event.setUserId(userId);
        event.setTime(TIME);
        return event;
    }

    @Test
    void writesLoginTime() {
        listener.onEvent(event(EventType.LOGIN, "user-id"));

        verify(user).setSingleAttribute(LastLoginEventListenerProvider.ATTRIBUTE, Long.toString(TIME));
    }

    @ParameterizedTest
    @EnumSource(names = {"LOGIN_ERROR", "REFRESH_TOKEN", "CODE_TO_TOKEN", "TOKEN_EXCHANGE"})
    void ignoresOtherEvents(EventType type) {
        listener.onEvent(event(type, "user-id"));

        verify(user, never()).setSingleAttribute(anyString(), anyString());
    }

    @Test
    void ignoresEventWithoutUser() {
        listener.onEvent(event(EventType.LOGIN, null));

        verify(users, never()).getUserById(any(), any());
    }

    @Test
    void ignoresMissingUser() {
        listener.onEvent(event(EventType.LOGIN, "deleted-user-id"));

        verify(user, never()).setSingleAttribute(anyString(), anyString());
    }

    @Test
    void skipsServiceAccounts() {
        when(user.getServiceAccountClientLink()).thenReturn("client-id");

        listener.onEvent(event(EventType.LOGIN, "user-id"));

        verify(user, never()).setSingleAttribute(anyString(), anyString());
    }

    @Test
    void keepsNewerValue() {
        when(user.getFirstAttribute(LastLoginEventListenerProvider.ATTRIBUTE))
                .thenReturn(Long.toString(TIME + 1));

        listener.onEvent(event(EventType.LOGIN, "user-id"));

        verify(user, never()).setSingleAttribute(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "yesterday", "99999999999999999999", "9223372036854775807"})
    void overwritesInvalidValue(String previous) {
        when(user.getFirstAttribute(LastLoginEventListenerProvider.ATTRIBUTE)).thenReturn(previous);

        listener.onEvent(event(EventType.LOGIN, "user-id"));

        verify(user).setSingleAttribute(LastLoginEventListenerProvider.ATTRIBUTE, Long.toString(TIME));
    }
}
