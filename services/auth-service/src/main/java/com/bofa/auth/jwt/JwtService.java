package com.bofa.auth.jwt;

import com.bofa.auth.InvalidTokenException;
import com.bofa.auth.TokenExpiredException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hand-rolled HS256 JWT issuer/verifier. Produces standard
 * {@code header.payload.signature} tokens so they interoperate with the bank's
 * existing SSO tooling, without pulling in an external JWT dependency.
 */
public class JwtService {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String HMAC_ALGO = "HmacSHA256";

    private final byte[] secret;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    public JwtService(String secret, Clock clock) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException("Signing secret must be at least 16 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /** Issue a signed token valid for {@code ttlSeconds} from now. */
    public String issue(String subject, String roles, long ttlSeconds) {
        long now = clock.instant().getEpochSecond();
        Claims claims = new Claims(subject, now, now + ttlSeconds, roles);
        try {
            String headerSegment = base64Url(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            String payloadSegment = base64Url(mapper.writeValueAsBytes(claims));
            String signingInput = headerSegment + "." + payloadSegment;
            String signature = base64Url(hmac(signingInput));
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to issue token", e);
        }
    }

    /**
     * Verify a token's signature and expiry, returning its claims.
     *
     * @throws InvalidTokenException if the token is malformed or the signature is invalid
     * @throws TokenExpiredException if the token's {@code exp} has passed
     */
    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token is empty");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("Token must have three segments");
        }
        String signingInput = parts[0] + "." + parts[1];
        String expectedSig = base64Url(hmac(signingInput));
        if (!constantTimeEquals(expectedSig, parts[2])) {
            throw new InvalidTokenException("Signature verification failed");
        }
        Claims claims;
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            claims = mapper.readValue(payload, Claims.class);
        } catch (Exception e) {
            throw new InvalidTokenException("Token payload is not valid JSON");
        }
        if (claims.getExp() <= clock.instant().getEpochSecond()) {
            throw new TokenExpiredException("Token expired at " + claims.getExp());
        }
        return claims;
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC", e);
        }
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
