package com.workshop.mcp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how the stretch goals' runtime switches are read out of the
 * environment, including every way a value can be absent or unreadable.
 */
@Tag("chapter03")
@Tag("stretch")
class SettingsTest {

    @Test
    void nothingConfiguredMeansTheDefaults() {
        Settings settings = Settings.fromEnvironment(Map.of());

        assertEquals(Settings.defaults(), settings);
        assertEquals(Settings.DEFAULT_LIST_CHANGED_MILLIS, settings.listChangedIntervalMillis());
        assertTrue(settings.announcesListChanges());
        assertFalse(settings.requireFormForListen());
    }

    @Test
    void theIntervalIsReadInMillisecondsAndMayBePadded() {
        assertEquals(250L, Settings.fromEnvironment(Map.of(Settings.LIST_CHANGED_MS, " 250 "))
                .listChangedIntervalMillis());
    }

    @Test
    void zeroOrLessTurnsTheTimerOff() {
        assertFalse(Settings.fromEnvironment(Map.of(Settings.LIST_CHANGED_MS, "0")).announcesListChanges());
        assertFalse(Settings.fromEnvironment(Map.of(Settings.LIST_CHANGED_MS, "-5")).announcesListChanges());
    }

    @Test
    void aBlankOrUnreadableIntervalFallsBackToTheDefault() {
        for (String value : new String[] {"", "   ", "soon"}) {
            assertEquals(Settings.DEFAULT_LIST_CHANGED_MILLIS,
                         Settings.fromEnvironment(Map.of(Settings.LIST_CHANGED_MS, value)).listChangedIntervalMillis(),
                         "'" + value + "'");
        }
    }

    @Test
    void requiringAFormIsOnOnlyForTrueInAnyCase() {
        assertTrue(Settings.fromEnvironment(Map.of(Settings.REQUIRE_FORM_FOR_LISTEN, "TRUE")).requireFormForListen());
        assertFalse(Settings.fromEnvironment(Map.of(Settings.REQUIRE_FORM_FOR_LISTEN, "yes")).requireFormForListen());
    }

    @Test
    @Tag("chapter05")
    void aStateKeyIsUsedOnlyWhenItIsBase64OfAtLeastThirtyTwoBytes() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        String encoded = Base64.getEncoder().encodeToString(key);

        assertArrayEquals(key, Settings.fromEnvironment(Map.of(Settings.STATE_KEY, " " + encoded + " "))
                .stateKeyBytes());
        assertNull(Settings.defaults().stateKeyBytes(), "no key means a random one per process");
        assertNull(Settings.defaults().withStateKey(Base64.getEncoder().encodeToString(new byte[31]))
                           .stateKeyBytes(), "a short key is refused");
        assertNull(Settings.defaults().withStateKey("not base64!").stateKeyBytes());
    }

    @Test
    @Tag("chapter05")
    void theStateKeyNeverReachesALog() {
        Settings settings = Settings.defaults().withStateKey("c2VjcmV0");

        assertFalse(settings.toString().contains("c2VjcmV0"), settings.toString());
        assertTrue(settings.toString().contains("stateKey=redacted"), settings.toString());
        assertTrue(Settings.defaults().toString().contains("stateKey=none"));
    }

    @Test
    @Tag("chapter05")
    void theElicitationPortIsReadWhenItIsAPort() {
        assertEquals(8765, Settings.fromEnvironment(Map.of(Settings.ELICITATION_PORT, "8765")).elicitationPort());
        assertEquals(0, Settings.fromEnvironment(Map.of(Settings.ELICITATION_PORT, "65536")).elicitationPort());
        assertEquals(0, Settings.fromEnvironment(Map.of(Settings.ELICITATION_PORT, "-1")).elicitationPort());
        assertEquals(0, Settings.fromEnvironment(Map.of()).elicitationPort());
    }

    @Test
    void eachWitherChangesOnlyItsOwnSwitch() {
        Settings changed = Settings.defaults()
                .withListChangedIntervalMillis(5L)
                .withRequireFormForListen(true)
                .withStateKey("key")
                .withElicitationPort(9);

        assertEquals(new Settings(5L, true, "key", 9), changed);
    }
}
