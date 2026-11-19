package com.workshop.mcp;

import com.workshop.mcp.spec.RequestId;
import com.workshop.mcp.spec.SubscriptionFilter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the bookkeeping behind a held-open {@code subscriptions/listen}
 * stream: who is listening for what, and the two ways a stream ends.
 */
@Tag("chapter03")
@Tag("stretch")
class StreamRegistryTest {

    private static final SubscriptionFilter TOOL_CHANGES = new SubscriptionFilter(true, null, null, null);
    private static final SubscriptionFilter RESOURCE_URIS =
            new SubscriptionFilter(null, null, null, List.of("file:///a"));

    private final StreamRegistry streams = new StreamRegistry();

    @Test
    void onlyStreamsThatAgreedToToolChangesAreToldOfThem() {
        streams.open(RequestId.of("tools"), TOOL_CHANGES);
        streams.open(RequestId.of("uris"), RESOURCE_URIS);

        assertEquals(List.of(RequestId.of("tools")), streams.listeningForToolListChanges());
    }

    @Test
    void aCancelledStreamIsForgottenOnce() {
        streams.open(RequestId.of("listen:0"), TOOL_CHANGES);

        assertTrue(streams.forget(RequestId.of("listen:0")));
        assertFalse(streams.forget(RequestId.of("listen:0")));
        assertEquals(List.of(), streams.listeningForToolListChanges());
    }

    @Test
    void aCancellationNamingNoStreamForgetsNothing() {
        streams.open(RequestId.of("listen:0"), TOOL_CHANGES);

        assertFalse(streams.forget(RequestId.of("some-other-request")));
        assertFalse(streams.forget(null), "a cancellation without a requestId names nothing");
        assertEquals(List.of(RequestId.of("listen:0")), streams.listeningForToolListChanges());
    }

    @Test
    void closingAllEmptiesTheRegistryAndSaysWhatWasOpen() {
        streams.open(RequestId.of("a"), TOOL_CHANGES);
        streams.open(RequestId.of(7), RESOURCE_URIS);

        assertEquals(Set.of(RequestId.of("a"), RequestId.of(7)), Set.copyOf(streams.closeAll()));
        assertEquals(List.of(), streams.closeAll());
    }
}
