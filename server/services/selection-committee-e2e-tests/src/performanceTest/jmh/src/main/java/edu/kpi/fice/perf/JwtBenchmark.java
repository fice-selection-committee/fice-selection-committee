package edu.kpi.fice.perf;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * JwtBenchmark
 * =============
 * Measures the throughput and latency of RSA-256 JWT generation and validation
 * as implemented in JwtServiceImpl.
 *
 * Context:
 *   - Every HTTP request to any protected endpoint validates the access token
 *     in AccessTokenFilter.
 *   - Every login, token refresh generates a new access + refresh token pair.
 *   - At 200 concurrent applicants × avg 5 requests each = 1 000 JWT verifications
 *     in a short window. At 20 operators × 10 request each = 200 more.
 *
 * Benchmarks:
 *   - generateAccessToken   — RS256 sign (private key)
 *   - validateAccessToken   — RS256 verify (public key) + claims parse
 *   - sha256HexToken        — SHA-256 of refresh token (used for DB lookup)
 *   - fullLoginCycle        — generate access + refresh + sha256
 *
 * Expected baseline (RSA-2048, JVM warmed):
 *   - generateAccessToken:  > 2 000 ops/s  (RSA sign is expensive)
 *   - validateAccessToken:  > 5 000 ops/s  (RSA verify is cheaper than sign)
 *   - sha256HexToken:       > 500 000 ops/s
 *
 * If generateAccessToken drops below 1 000 ops/s, consider:
 *   - Switching to RSA-4096 only for refresh tokens (use RSA-2048 for access)
 *   - Caching the loaded private key object (KeyLoader already caches it)
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xmx256m"})
public class JwtBenchmark {

    private RSAPrivateKey privateKey;
    private RSAPublicKey  publicKey;

    // Pre-built token for validation benchmarks
    private String sampleAccessToken;

    private static final long  ACCESS_EXPIRY_MS  = 15 * 60 * 1000L;  // 15 min
    private static final long  REFRESH_EXPIRY_MS = 7  * 24 * 60 * 60 * 1000L;
    private static final String KID              = "perf-key-1";

    @Setup(Level.Trial)
    public void setup() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        publicKey  = (RSAPublicKey)  pair.getPublic();

        // Build a sample token to use in validation benchmarks
        sampleAccessToken = buildToken(
            "42",
            ACCESS_EXPIRY_MS,
            Map.of(
                "typ",   "ACCESS",
                "uid",   42L,
                "role",  "APPLICANT",
                "perms", List.of("submit_application")
            )
        );
    }

    /**
     * Benchmark RSA-256 access token generation.
     * Mirrors JwtServiceImpl.generateAccessToken().
     */
    @Benchmark
    public void generateAccessToken(Blackhole bh) {
        String token = buildToken(
            "42",
            ACCESS_EXPIRY_MS,
            Map.of(
                "typ",   "ACCESS",
                "uid",   42L,
                "role",  "OPERATOR",
                "perms", List.of("checking_documents", "working_with_contracts")
            )
        );
        bh.consume(token);
    }

    /**
     * Benchmark RSA-256 access token validation.
     * Mirrors JwtServiceImpl.validateAccessToken().
     */
    @Benchmark
    public void validateAccessToken(Blackhole bh) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(sampleAccessToken)
                .getPayload();
            String tokenType = String.valueOf(claims.get("typ"));
            bh.consume("ACCESS".equals(tokenType));
        } catch (Exception e) {
            bh.consume(false);
        }
    }

    /**
     * Benchmark SHA-256 hex encoding of refresh token.
     * Mirrors JwtServiceImpl.sha256Hex().
     */
    @Benchmark
    public void sha256HexToken(Blackhole bh) throws Exception {
        MessageDigest md     = MessageDigest.getInstance("SHA-256");
        byte[]        digest = md.digest(sampleAccessToken.getBytes(UTF_8));
        StringBuilder sb     = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        bh.consume(sb.toString());
    }

    /**
     * Full login cycle: generate access token + refresh token + sha256 of refresh.
     * Represents the work done on every successful login or token refresh.
     */
    @Benchmark
    public void fullLoginCycle(Blackhole bh) throws Exception {
        String accessToken = buildToken(
            "42",
            ACCESS_EXPIRY_MS,
            Map.of("typ", "ACCESS", "uid", 42L, "role", "APPLICANT", "perms", List.of())
        );

        String refreshToken = buildToken(
            "42",
            REFRESH_EXPIRY_MS,
            Map.of("typ", "REFRESH", "uid", 42L)
        );

        // SHA-256 the refresh token for DB storage (mirrors sha256Hex)
        MessageDigest md      = MessageDigest.getInstance("SHA-256");
        byte[]        digest  = md.digest(refreshToken.getBytes(UTF_8));
        StringBuilder sb      = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));

        bh.consume(accessToken);
        bh.consume(sb.toString());
    }

    // ---------------------------------------------------------------------------
    // Internal helpers (mirrors JwtServiceImpl.buildToken)
    // ---------------------------------------------------------------------------

    private String buildToken(String subject, long expirationMs, Map<String, Object> extraClaims) {
        long   now      = System.currentTimeMillis();
        Date   issuedAt = new Date(now);
        Date   expiry   = new Date(now + expirationMs);
        String jti      = UUID.randomUUID().toString();

        JwtBuilder builder = Jwts.builder()
            .subject(subject)
            .issuedAt(issuedAt)
            .expiration(expiry)
            .id(jti);

        extraClaims.forEach(builder::claim);

        builder.header().add("kid", KID);
        builder.signWith(privateKey, Jwts.SIG.RS256);

        return builder.compact();
    }
}
