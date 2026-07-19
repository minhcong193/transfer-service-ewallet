package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.config.TransferRedisKeyFactory;
import minhtc.vn.transferservice.enums.OtpVerificationStatus;
import minhtc.vn.transferservice.exception.OtpChallengeNotFoundException;
import minhtc.vn.transferservice.exception.OtpDeliveryException;
import minhtc.vn.transferservice.exception.OtpRateLimitException;
import minhtc.vn.transferservice.notification.OtpDeliveryPort;
import minhtc.vn.transferservice.otp.TransferOtpProperties;
import minhtc.vn.transferservice.service.OtpService;
import minhtc.vn.transferservice.dto.request.OtpChallenge;
import minhtc.vn.transferservice.dto.response.OtpVerificationResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.*;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    /*
     * Return code:
     *
     *  1  = verified
     * -1  = key not found
     * -2  = already consumed
     * -3  = expired
     * -4  = max attempts already exceeded
     * -5  = current request causes max attempts
     * -6  = invalid OTP
     */
    private static final DefaultRedisScript<Long>
            VERIFY_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end

            local consumed = redis.call(
                'HGET',
                KEYS[1],
                'consumed'
            )

            if consumed == '1' then
                return -2
            end

            local expiresAt = tonumber(
                redis.call('HGET', KEYS[1], 'expiresAt')
            )

            local now = tonumber(ARGV[1])

            if expiresAt == nil or now > expiresAt then
                return -3
            end

            local attempts = tonumber(
                redis.call('HGET', KEYS[1], 'attempts') or '0'
            )

            local maxAttempts = tonumber(
                redis.call('HGET', KEYS[1], 'maxAttempts') or '5'
            )

            if attempts >= maxAttempts then
                return -4
            end

            local storedHash = redis.call(
                'HGET',
                KEYS[1],
                'otpHash'
            )

            if storedHash ~= ARGV[2] then
                attempts = attempts + 1

                redis.call(
                    'HSET',
                    KEYS[1],
                    'attempts',
                    attempts
                )

                if attempts >= maxAttempts then
                    return -5
                end

                return -6
            end

            redis.call(
                'HSET',
                KEYS[1],
                'consumed',
                '1'
            )

            return 1
            """,
            Long.class
    );

    /*
     * Return code:
     *
     * >= 0 = resend count mới
     * -1   = OTP record không tồn tại
     * -2   = owner không đúng
     * -3   = OTP đã được consume
     * -4   = đang trong cooldown
     * -5   = vượt max resend
     * -6   = vượt daily limit
     */
    private static final DefaultRedisScript<Long>
            RESEND_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end

            local ownerId = redis.call(
                'HGET',
                KEYS[1],
                'ownerId'
            )

            if ownerId ~= ARGV[1] then
                return -2
            end

            local consumed = redis.call(
                'HGET',
                KEYS[1],
                'consumed'
            )

            if consumed == '1' then
                return -3
            end

            if redis.call('EXISTS', KEYS[2]) == 1 then
                return -4
            end

            local resendCount = tonumber(
                redis.call('HGET', KEYS[1], 'resendCount') or '0'
            )

            local maxResends = tonumber(ARGV[6])

            if resendCount >= maxResends then
                return -5
            end

            local dailyCount = redis.call('INCR', KEYS[3])

            if dailyCount == 1 then
                redis.call('EXPIRE', KEYS[3], ARGV[10])
            end

            if dailyCount > tonumber(ARGV[9]) then
                redis.call('DECR', KEYS[3])
                return -6
            end

            resendCount = resendCount + 1

            redis.call(
                'HSET',
                KEYS[1],
                'challengeId', ARGV[2],
                'otpHash', ARGV[3],
                'expiresAt', ARGV[4],
                'attempts', '0',
                'consumed', '0',
                'resendCount', resendCount
            )

            redis.call('EXPIRE', KEYS[1], ARGV[8])

            redis.call(
                'SET',
                KEYS[2],
                '1',
                'EX',
                ARGV[7]
            )

            return resendCount
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final TransferOtpProperties properties;
    private final OtpDeliveryPort otpDeliveryPort;
    private final Clock clock;
    private final TransferRedisKeyFactory redisKeyFactory;

    @Override
    public OtpChallenge createOtp(
            UUID transferId,
            UUID ownerKeycloakUserId
    ) {
        LocalDateTime now = LocalDateTime.now(clock);

        incrementDailyCount(ownerKeycloakUserId, now);

        UUID challengeId = UUID.randomUUID();
        String otp = generateOtp();

        LocalDateTime expiresAt =
                now.plus(properties.ttl());

        LocalDateTime resendAvailableAt =
                now.plus(properties.resendCooldown());

        String otpKey = otpKey(transferId);

        Map<String, String> data = Map.ofEntries(
                Map.entry(
                        "challengeId",
                        challengeId.toString()
                ),
                Map.entry(
                        "ownerId",
                        ownerKeycloakUserId.toString()
                ),
                Map.entry(
                        "otpHash",
                        hashOtp(transferId, otp)
                ),
                Map.entry(
                        "attempts",
                        "0"
                ),
                Map.entry(
                        "maxAttempts",
                        String.valueOf(properties.maxAttempts())
                ),
                Map.entry(
                        "resendCount",
                        "0"
                ),
                Map.entry(
                        "consumed",
                        "0"
                ),
                Map.entry(
                        "expiresAt",
                        String.valueOf(toEpochMilli(expiresAt))
                )
        );

        redisTemplate.opsForHash().putAll(
                otpKey,
                data
        );

        redisTemplate.expire(
                otpKey,
                properties.recordTtl()
        );

        redisTemplate.opsForValue().set(
                cooldownKey(transferId),
                "1",
                properties.resendCooldown()
        );

        try {
            otpDeliveryPort.sendTransferOtp(
                    ownerKeycloakUserId,
                    transferId,
                    otp
            );
        } catch (Exception exception) {
            /*
             * OTP đã được tạo nhưng không gửi được.
             * Xóa challenge để người dùng có thể yêu cầu lại.
             */
            invalidateOtp(transferId);

            throw new OtpDeliveryException(exception);
        }

        return new OtpChallenge(
                challengeId,
                transferId,
                expiresAt,
                resendAvailableAt
        );
    }

    @Override
    public OtpVerificationResult verifyOtp(
            UUID transferId,
            String otp
    ) {
        if (otp == null || !otp.matches("\\d{6}")) {
            return new OtpVerificationResult(
                    OtpVerificationStatus.INVALID,
                    0
            );
        }

        String otpKey = otpKey(transferId);

        Long result = redisTemplate.execute(
                VERIFY_SCRIPT,
                List.of(otpKey),
                String.valueOf(
                        Instant.now(clock).toEpochMilli()
                ),
                hashOtp(transferId, otp)
        );

        long code = result != null ? result : -1;

        int remainingAttempts =
                getRemainingAttempts(otpKey);

        return switch ((int) code) {
            case 1 -> new OtpVerificationResult(
                    OtpVerificationStatus.VERIFIED,
                    remainingAttempts
            );

            case -2 -> new OtpVerificationResult(
                    OtpVerificationStatus.ALREADY_CONSUMED,
                    remainingAttempts
            );

            case -4, -5 -> new OtpVerificationResult(
                    OtpVerificationStatus.MAX_ATTEMPTS_EXCEEDED,
                    0
            );

            case -6 -> new OtpVerificationResult(
                    OtpVerificationStatus.INVALID,
                    remainingAttempts
            );

            default -> new OtpVerificationResult(
                    OtpVerificationStatus.EXPIRED,
                    remainingAttempts
            );
        };
    }

    @Override
    public OtpChallenge resendOtp(
            UUID transferId,
            UUID ownerKeycloakUserId
    ) {
        LocalDateTime now = LocalDateTime.now(clock);

        UUID challengeId = UUID.randomUUID();
        String otp = generateOtp();

        LocalDateTime expiresAt =
                now.plus(properties.ttl());

        LocalDateTime resendAvailableAt =
                now.plus(properties.resendCooldown());

        Long result = redisTemplate.execute(
                RESEND_SCRIPT,
                List.of(
                        otpKey(transferId),
                        cooldownKey(transferId),
                        dailyCountKey(
                                ownerKeycloakUserId,
                                now.toLocalDate()
                        )
                ),
                ownerKeycloakUserId.toString(),
                challengeId.toString(),
                hashOtp(transferId, otp),
                String.valueOf(toEpochMilli(expiresAt)),
                String.valueOf(
                        Instant.now(clock).toEpochMilli()
                ),
                String.valueOf(properties.maxResends()),
                String.valueOf(
                        properties.resendCooldown().toSeconds()
                ),
                String.valueOf(
                        properties.recordTtl().toSeconds()
                ),
                String.valueOf(properties.maxDailyOtp()),
                String.valueOf(secondsUntilTomorrow(now))
        );

        long code = result != null ? result : -1;

        if (code == -1) {
            throw new OtpChallengeNotFoundException();
        }

        if (code == -2) {
            throw new SecurityException(
                    "OTP challenge owner mismatch"
            );
        }

        if (code == -3) {
            throw new IllegalStateException(
                    "OTP challenge has already been consumed"
            );
        }

        if (code == -4) {
            Long ttl = redisTemplate.getExpire(
                    cooldownKey(transferId),
                    TimeUnit.SECONDS
            );

            throw new OtpRateLimitException(
                    "OTP resend is available after "
                            + Math.max(ttl != null ? ttl : 0, 0)
                            + " seconds"
            );
        }

        if (code == -5) {
            throw new OtpRateLimitException(
                    "Maximum OTP resend count exceeded"
            );
        }

        if (code == -6) {
            throw new OtpRateLimitException(
                    "Daily OTP limit exceeded"
            );
        }

        try {
            otpDeliveryPort.sendTransferOtp(
                    ownerKeycloakUserId,
                    transferId,
                    otp
            );
        } catch (Exception exception) {
            /*
             * Challenge mới đã thay thế OTP cũ.
             * Xóa record để tránh giữ OTP không được gửi.
             */
            invalidateOtp(transferId);

            throw new OtpDeliveryException(exception);
        }

        return new OtpChallenge(
                challengeId,
                transferId,
                expiresAt,
                resendAvailableAt
        );
    }

    @Override
    public void invalidateOtp(UUID transferId) {
        redisTemplate.delete(
                List.of(
                        otpKey(transferId),
                        cooldownKey(transferId)
                )
        );
    }

    private void incrementDailyCount(
            UUID ownerKeycloakUserId,
            LocalDateTime now
    ) {
        String key = dailyCountKey(
                ownerKeycloakUserId,
                now.toLocalDate()
        );

        Long count =
                redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(
                    key,
                    Duration.ofSeconds(
                            secondsUntilTomorrow(now)
                    )
            );
        }

        if (
                count != null
                        && count > properties.maxDailyOtp()
        ) {
            redisTemplate.opsForValue().decrement(key);

            throw new OtpRateLimitException(
                    "Daily OTP limit exceeded"
            );
        }
    }

    private int getRemainingAttempts(String otpKey) {
        Object attemptsValue =
                redisTemplate.opsForHash().get(
                        otpKey,
                        "attempts"
                );

        Object maxAttemptsValue =
                redisTemplate.opsForHash().get(
                        otpKey,
                        "maxAttempts"
                );

        if (
                attemptsValue == null
                        || maxAttemptsValue == null
        ) {
            return 0;
        }

        int attempts =
                Integer.parseInt(attemptsValue.toString());

        int maxAttempts =
                Integer.parseInt(maxAttemptsValue.toString());

        return Math.max(maxAttempts - attempts, 0);
    }

    private String generateOtp() {
        return String.format(
                "%06d",
                SECURE_RANDOM.nextInt(1_000_000)
        );
    }

    private String hashOtp(
            UUID transferId,
            String otp
    ) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            mac.init(
                    new SecretKeySpec(
                            properties.hmacSecret()
                                    .getBytes(StandardCharsets.UTF_8),
                            HMAC_ALGORITHM
                    )
            );

            byte[] hash = mac.doFinal(
                    (transferId + ":" + otp)
                            .getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Cannot hash OTP",
                    exception
            );
        }
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime
                .atZone(clock.getZone())
                .toInstant()
                .toEpochMilli();
    }

    private long secondsUntilTomorrow(
            LocalDateTime now
    ) {
        ZonedDateTime current =
                now.atZone(clock.getZone());

        ZonedDateTime tomorrow =
                now.toLocalDate()
                        .plusDays(1)
                        .atStartOfDay(clock.getZone());

        return Math.max(
                Duration.between(
                        current,
                        tomorrow
                ).toSeconds(),
                1
        );
    }

    private String otpKey(UUID transferId) {
        return redisKeyFactory.otpChallenge(
                transferId
        );
    }

    private String cooldownKey(UUID transferId) {
        return redisKeyFactory.otpCooldown(
                transferId
        );
    }

    private String dailyCountKey(
            UUID ownerId,
            LocalDate date
    ) {
        return redisKeyFactory.otpDailyCount(
                ownerId,
                date
        );
    }
}
