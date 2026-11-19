package com.workshop.mcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Seals a {@code requestState} so that a client can carry it but cannot
 * change it, and cannot use it after a time limit.
 * <p>
 * A sealed value is two Base64url parts joined by a dot: the state's JSON,
 * with its expiry added under {@code exp}, and an HMAC-SHA256 of that first
 * part. Only this server holds the key, so only this server can produce a
 * value that opens.
 * </p>
 * <p>
 * The state itself stays readable. Signing makes it tamper-evident, not
 * secret, so it must never hold anything a client should not see.
 * </p>
 */
public final class StateSigner {

    /** How long a sealed state can be used. */
    public static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(10);

    private static final String ALGORITHM = "HmacSHA256";
    private static final String EXPIRY = "exp";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKey key;
    private final Clock clock;
    private final Duration lifetime;

    /**
     * Creates a signer.
     *
     * @param key      the HMAC key
     * @param clock    where the time limit is read from
     * @param lifetime how long a sealed state stays usable
     * @throws IllegalArgumentException if HMAC-SHA256 cannot use the key
     */
    public StateSigner(SecretKey key, Clock clock, Duration lifetime) {
        this.key = key;
        this.clock = clock;
        this.lifetime = lifetime;
        sign(new byte[0]);
    }

    /**
     * Creates a signer from raw key bytes.
     *
     * @param key      the HMAC key; at least 32 bytes is recommended for SHA-256
     * @param clock    where the time limit is read from
     * @param lifetime how long a sealed state stays usable
     * @return the signer
     */
    public static StateSigner withKey(byte[] key, Clock clock, Duration lifetime) {
        return new StateSigner(new SecretKeySpec(key, ALGORITHM), clock, lifetime);
    }

    /**
     * Makes a key nobody else knows, for a server that was given none.
     * A state sealed with it does not survive a restart: it fails to open, and
     * the retry is treated as a first attempt.
     *
     * @return 32 random bytes
     */
    public static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    /**
     * Seals a state, adding its expiry.
     *
     * @param state the state to carry; it is not modified
     * @return the sealed value, safe to place in {@code requestState}
     */
    public String seal(JsonObject state) {
        JsonObject withExpiry = state.deepCopy();
        withExpiry.addProperty(EXPIRY, clock.millis() + lifetime.toMillis());
        String body = ENCODER.encodeToString(withExpiry.toString().getBytes(StandardCharsets.UTF_8));
        return body + "." + ENCODER.encodeToString(sign(body.getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * Opens a sealed state.
     *
     * @param sealed the value a client echoed back, may be null
     * @return the state without its expiry, or null when the value is absent,
     *         malformed, signed with another key, altered, or expired
     */
    public JsonObject open(String sealed) {
        if (sealed == null) {
            return null;
        }
        int dot = sealed.indexOf('.');
        if (dot < 0) {
            return null;
        }
        String body = sealed.substring(0, dot);
        try {
            byte[] signature = DECODER.decode(sealed.substring(dot + 1));
            if (!MessageDigest.isEqual(sign(body.getBytes(StandardCharsets.US_ASCII)), signature)) {
                return null;
            }
            // Only this server could have written a body that carries a valid
            // signature, so it is known to be a JSON object with an expiry.
            JsonObject state = JsonParser.parseString(new String(DECODER.decode(body), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (clock.millis() > state.remove(EXPIRY).getAsLong()) {
                return null;
            }
            return state;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] sign(byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("The state key cannot be used with " + ALGORITHM, e);
        }
    }
}
