package com.workshop.mcp.tools;

import com.google.gson.JsonObject;
import com.workshop.mcp.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the seal that keeps a client from altering or replaying the
 * keyword-search tool's {@code requestState}.
 */
@Tag("chapter05")
@Tag("stretch")
class StateSignerTest {

    private static final byte[] KEY = "a test key that is thirty-two by".getBytes(StandardCharsets.US_ASCII);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final MutableClock clock = new MutableClock();
    private final StateSigner signer = StateSigner.withKey(KEY, clock, Duration.ofMinutes(10));

    @Test
    void aSealedStateOpensToWhatWasSealedWithoutItsExpiry() {
        JsonObject state = new JsonObject();
        state.addProperty("stage", "directory");

        JsonObject opened = signer.open(signer.seal(state));

        assertEquals(state, opened);
        assertFalse(state.has("exp"), "sealing must not change the caller's object");
    }

    @Test
    void aSealedStateIsTwoUrlSafeParts() {
        String sealed = signer.seal(new JsonObject());

        String[] parts = sealed.split("\\.");
        assertEquals(2, parts.length);
        for (String part : parts) {
            assertTrue(part.matches("[A-Za-z0-9_-]+"), "requestState travels in JSON, so padding buys nothing");
        }
    }

    @Test
    void theStateStaysReadableBecauseSigningIsNotEncryption() {
        JsonObject state = new JsonObject();
        state.addProperty("stage", "directory");

        String body = signer.seal(state).split("\\.")[0];

        String json = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"stage\":\"directory\""), json);
        assertTrue(json.contains("\"exp\":"), json);
    }

    @Test
    void nothingOpensFromNothing() {
        assertNull(signer.open(null));
        assertNull(signer.open(""));
        assertNull(signer.open("no-dot-at-all"));
    }

    @Test
    void anAlteredBodyDoesNotOpen() {
        String sealed = signer.seal(new JsonObject());
        String forged = ENCODER.encodeToString("{\"stage\":\"page\",\"exp\":9999999999999}"
                .getBytes(StandardCharsets.UTF_8));

        assertNull(signer.open(forged + sealed.substring(sealed.indexOf('.'))));
    }

    @Test
    void anAlteredSignatureDoesNotOpen() {
        String sealed = signer.seal(new JsonObject());

        assertNull(signer.open(sealed.substring(0, sealed.indexOf('.') + 1) + ENCODER.encodeToString(new byte[32])));
        assertNull(signer.open(sealed + "!"), "a signature that is not Base64 is rejected, not thrown");
    }

    @Test
    void aBodyThatIsNotBase64IsRejectedEvenWithAMatchingSignature() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
        String body = "not*base64";

        String sealed = body + "." + ENCODER.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.US_ASCII)));

        assertNull(signer.open(sealed));
    }

    @Test
    void aStateSealedWithAnotherKeyDoesNotOpen() {
        StateSigner other = StateSigner.withKey(StateSigner.randomKey(), clock, Duration.ofMinutes(10));

        assertNull(signer.open(other.seal(new JsonObject())));
    }

    @Test
    void aStateOpensUntilItsLifetimeIsUpAndNotAfter() {
        String sealed = signer.seal(new JsonObject());

        clock.advance(Duration.ofMinutes(10));
        assertNotNull(signer.open(sealed), "the last millisecond of its lifetime still counts");

        clock.advance(Duration.ofMillis(1));
        assertNull(signer.open(sealed), "an expired state is treated as a first attempt");
    }

    @Test
    void aRandomKeyIsThirtyTwoFreshBytes() {
        byte[] first = StateSigner.randomKey();
        byte[] second = StateSigner.randomKey();

        assertEquals(32, first.length);
        assertFalse(Arrays.equals(first, second));
    }

    @Test
    void aKeyThatCannotSignIsRefusedWhenTheSignerIsMade() {
        SecretKey empty = new SecretKey() {
            @Override
            public String getAlgorithm() {
                return "HmacSHA256";
            }

            @Override
            public String getFormat() {
                return "RAW";
            }

            @Override
            public byte[] getEncoded() {
                return null;
            }
        };

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new StateSigner(empty, clock, Duration.ofMinutes(10)));
        assertNotNull(thrown.getCause());
    }

    @Test
    void theDefaultLifetimeIsTenMinutes() {
        assertEquals(Duration.ofMinutes(10), StateSigner.DEFAULT_LIFETIME);
    }
}
