package com.workshop.mcp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
