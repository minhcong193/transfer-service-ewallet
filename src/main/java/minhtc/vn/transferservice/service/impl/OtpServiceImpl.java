package minhtc.vn.transferservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import minhtc.vn.transferservice.config.TransferOtpProperties;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.dto.request.OtpChallenge;
import minhtc.vn.transferservice.dto.response.OtpVerificationResult;
import minhtc.vn.transferservice.enums.OtpVerificationStatus;
import minhtc.vn.transferservice.exception.TransferValidationException;
import minhtc.vn.transferservice.otp.OtpRecipient;
import minhtc.vn.transferservice.otp.TransferOtpDeliveryCommand;
import minhtc.vn.transferservice.notification.OtpDeliveryPort;
import minhtc.vn.transferservice.service.OtpService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Quản lý OTP xác nhận giao dịch chuyển tiền.
 *
 * <p>Trách nhiệm của class:</p>
 *
 * <ol>
 *     <li>Sinh OTP 6 chữ số bằng {@link SecureRandom}.</li>
 *     <li>Không lưu plaintext OTP vào Redis.</li>
 *     <li>Hash OTP bằng HMAC-SHA256.</li>
 *     <li>Lưu challenge OTP vào Redis với TTL.</li>
 *     <li>Gửi OTP qua {@link OtpDeliveryPort}.</li>
 *     <li>Verify OTP atomically bằng Redis Lua Script.</li>
 *     <li>Giới hạn số lần nhập sai.</li>
 *     <li>Giới hạn resend và áp dụng resend cooldown.</li>
 *     <li>Giới hạn tổng số OTP gửi cho một user trong ngày.</li>
 * </ol>
 *
 * <p>Email người nhận không được truyền từ request body. Nó được lấy từ
 * JWT và đóng gói trong {@link OtpRecipient} trước khi gọi service này.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final int OTP_BOUND = 1_000_000;

    private static final int OTP_LENGTH = 6;

    private static final String HMAC_ALGORITHM =
            "HmacSHA256";

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    /*
     * Redis hash fields.
     */
    private static final String FIELD_CHALLENGE_ID =
            "challengeId";

    private static final String FIELD_OWNER_USER_ID =
            "ownerKeycloakUserId";

    private static final String FIELD_OTP_HASH =
            "otpHash";

    private static final String FIELD_ATTEMPTS =
            "attempts";

    private static final String FIELD_MAX_ATTEMPTS =
            "maxAttempts";

    private static final String FIELD_LOCKED =
            "locked";

    private static final String FIELD_CREATED_AT =
            "createdAt";

    private static final String FIELD_EXPIRES_AT =
            "expiresAt";

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    /*
     * Kết quả trả về từ Lua script verifyOtp:
     *
     *  1  -> OTP đúng, challenge đã bị consume.
     *  0  -> OTP sai nhưng vẫn còn lượt nhập.
     * -1  -> Challenge không tồn tại hoặc đã hết TTL.
     * -2  -> Đã vượt quá số lần nhập sai.
     */
    private static final long LUA_VERIFIED = 1L;

    private static final long LUA_INVALID = 0L;

    private static final long LUA_EXPIRED = -1L;

    private static final long LUA_MAX_ATTEMPTS = -2L;

    /**
     * Script thực hiện toàn bộ verify trong một Redis operation:
     *
     * <ul>
     *     <li>Kiểm tra challenge tồn tại.</li>
     *     <li>Kiểm tra challenge đã bị khóa chưa.</li>
     *     <li>So sánh OTP hash.</li>
     *     <li>OTP đúng: xóa challenge ngay để chống replay.</li>
     *     <li>OTP sai: tăng attempts.</li>
     *     <li>Đủ max attempts: đánh dấu challenge bị khóa.</li>
     * </ul>
     *
     * <p>Không thực hiện GET → compare → DELETE bằng ba lệnh Java riêng
     * vì hai request confirm đồng thời có thể cùng xác nhận thành công.</p>
     */
    private static final DefaultRedisScript<List>
            VERIFY_OTP_SCRIPT = createVerifyOtpScript();

    private final StringRedisTemplate redisTemplate;

    private final TransferOtpProperties otpProperties;

    private final OtpDeliveryPort otpDeliveryPort;

    /**
     * Tạo OTP lần đầu cho một transfer.
     *
     * <p>Luồng:</p>
     *
     * <pre>
     * Validate input
     *     ↓
     * Kiểm tra giới hạn OTP trong ngày
     *     ↓
     * Sinh OTP + challengeId
     *     ↓
     * Lưu HMAC của OTP vào Redis
     *     ↓
     * Tạo resend cooldown
     *     ↓
     * Gửi email SMTP qua OtpDeliveryPort
     * </pre>
     *
     * <p>Nếu gửi email thất bại, challenge vừa tạo sẽ bị xóa để user
     * không phải xác nhận bằng một OTP mà họ chưa nhận được.</p>
     */
    @Override
    public OtpChallenge createOtp(
            Transfer transfer,
            OtpRecipient recipient
    ) {
        validateCreateInput(
                transfer.getId(),
                recipient
        );

        validateProperties();

        /*
         * Mỗi lần gửi OTP đều được tính vào daily quota.
         * Việc tăng counter thực hiện trước khi gửi để chống các request
         * đồng thời cùng vượt giới hạn.
         */
        incrementDailyOtpCount(
                recipient.ownerKeycloakUserId()
        );

        GeneratedOtp generatedOtp =
                generateOtpChallenge(transfer.getId());

        try {
            saveOtpChallenge(
                    transfer.getId(),
                    recipient.ownerKeycloakUserId(),
                    generatedOtp
            );

            /*
             * Sau khi tạo hoặc gửi lại OTP, user phải đợi một khoảng thời
             * gian mới được resend tiếp.
             */
            createResendCooldown(transfer.getId());

            sendOtp(
                    recipient,
                    transfer,
                    generatedOtp
            );

            log.info(
                    "Transfer OTP challenge created and delivered. "
                            + "transferId={}, ownerUserId={}, "
                            + "challengeId={}, expiresAt={}",
                    transfer.getId(),
                    recipient.ownerKeycloakUserId(),
                    generatedOtp.challengeId(),
                    generatedOtp.expiresAt()
            );

            return new OtpChallenge(
                    generatedOtp.challengeId(),
                    generatedOtp.expiresAt()
            );

        } catch (RuntimeException exception) {
            /*
             * Redis không tham gia transaction PostgreSQL.
             * Vì vậy phải tự cleanup dữ liệu Redis nếu gửi mail thất bại.
             */
            cleanupAfterDeliveryFailure(
                    transfer.getId(),
                    recipient.ownerKeycloakUserId(),
                    false
            );

            throw exception;
        }
    }

    /**
     * Xác minh OTP.
     *
     * <p>OTP do client gửi lên được hash lại bằng cùng secret. Service
     * không cần và không thể đọc plaintext OTP cũ từ Redis.</p>
     */
    @Override
    public OtpVerificationResult verifyOtp(
            UUID transferId,
            String rawOtp
    ) {
        validateVerifyInput(
                transferId,
                rawOtp
        );

        validateProperties();

        String otpHash =
                hashOtp(
                        transferId,
                        rawOtp.trim()
                );

        List<?> scriptResult =
                redisTemplate.execute(
                        VERIFY_OTP_SCRIPT,
                        List.of(otpKey(transferId)),
                        otpHash
                );

        if (
                scriptResult == null
                        || scriptResult.isEmpty()
        ) {
            throw new IllegalStateException(
                    "Redis returned an invalid OTP verification result"
            );
        }

        long resultCode =
                toLong(scriptResult.get(0));

        int remainingAttempts =
                scriptResult.size() > 1
                        ? Math.toIntExact(
                        toLong(scriptResult.get(1))
                )
                        : 0;

        if (resultCode == LUA_VERIFIED) {
            log.info(
                    "Transfer OTP verified successfully. transferId={}",
                    transferId
            );

            return new OtpVerificationResult(
                    OtpVerificationStatus.VERIFIED,
                    remainingAttempts
            );
        }

        if (resultCode == LUA_EXPIRED) {
            log.warn(
                    "Transfer OTP expired or not found. transferId={}",
                    transferId
            );

            return new OtpVerificationResult(
                    OtpVerificationStatus.EXPIRED,
                    0
            );
        }

        if (resultCode == LUA_MAX_ATTEMPTS) {
            log.warn(
                    "Transfer OTP max attempts exceeded. transferId={}",
                    transferId
            );

            return new OtpVerificationResult(
                    OtpVerificationStatus.MAX_ATTEMPTS_EXCEEDED,
                    0
            );
        }

        if (resultCode == LUA_INVALID) {
            log.warn(
                    "Invalid transfer OTP. transferId={}, "
                            + "remainingAttempts={}",
                    transferId,
                    remainingAttempts
            );

            return new OtpVerificationResult(
                    OtpVerificationStatus.INVALID,
                    remainingAttempts
            );
        }

        throw new IllegalStateException(
                "Unknown OTP verification result code: "
                        + resultCode
        );
    }

    /**
     * Gửi lại OTP.
     *
     * <p>Mỗi lần resend sinh một OTP mới. OTP cũ lập tức mất hiệu lực vì
     * field {@code otpHash} của challenge được thay thế.</p>
     */
    @Override
    public OtpChallenge resendOtp(
            Transfer transfer,
            OtpRecipient recipient
    ) {
        validateCreateInput(
                transfer.getId(),
                recipient
        );

        validateProperties();

        /*
         * SET NX bảo đảm hai request resend đồng thời chỉ có một request
         * chiếm được cooldown.
         */
        acquireResendCooldown(transfer.getId());

        boolean resendCountIncremented = false;
        boolean dailyCountIncremented = false;

        try {
            incrementResendCount(transfer.getId());
            resendCountIncremented = true;

            incrementDailyOtpCount(
                    recipient.ownerKeycloakUserId()
            );
            dailyCountIncremented = true;

            GeneratedOtp generatedOtp =
                    generateOtpChallenge(transfer.getId());

            saveOtpChallenge(
                    transfer.getId(),
                    recipient.ownerKeycloakUserId(),
                    generatedOtp
            );

            sendOtp(
                    recipient,
                    transfer,
                    generatedOtp
            );

            log.info(
                    "Transfer OTP resent successfully. "
                            + "transferId={}, ownerUserId={}, "
                            + "challengeId={}, expiresAt={}",
                    transfer.getId(),
                    recipient.ownerKeycloakUserId(),
                    generatedOtp.challengeId(),
                    generatedOtp.expiresAt()
            );

            return new OtpChallenge(
                    generatedOtp.challengeId(),
                    generatedOtp.expiresAt()
            );

        } catch (RuntimeException exception) {
            /*
             * Nếu resend thất bại do SMTP hoặc Redis:
             *
             * - Xóa challenge mới.
             * - Xóa cooldown để user có thể thử lại.
             * - Rollback best-effort resend counter.
             * - Rollback best-effort daily counter.
             */
            deleteKey(otpKey(transfer.getId()));
            deleteKey(resendCooldownKey(transfer.getId()));

            if (resendCountIncremented) {
                decrementCounterSafely(
                        resendCountKey(transfer.getId())
                );
            }

            if (dailyCountIncremented) {
                decrementCounterSafely(
                        dailyOtpCountKey(
                                recipient.ownerKeycloakUserId()
                        )
                );
            }

            throw exception;
        }
    }

    /**
     * Xóa challenge OTP của transfer.
     *
     * <p>Được gọi khi:</p>
     *
     * <ul>
     *     <li>User hủy transfer.</li>
     *     <li>Gửi email thất bại.</li>
     *     <li>Cần vô hiệu hóa OTP bằng thao tác nghiệp vụ khác.</li>
     * </ul>
     *
     * <p>Không xóa daily counter vì email đã được gửi và vẫn phải được
     * tính vào giới hạn chống abuse.</p>
     */
    @Override
    public void deleteOtp(UUID transferId) {
        if (transferId == null) {
            return;
        }

        redisTemplate.delete(
                List.of(
                        otpKey(transferId),
                        resendCooldownKey(transferId)
                )
        );

        log.debug(
                "Transfer OTP challenge deleted. transferId={}",
                transferId
        );
    }

    /**
     * Sinh OTP và thông tin challenge.
     */
    private GeneratedOtp generateOtpChallenge(
            UUID transferId
    ) {
        String rawOtp =
                String.format(
                        "%0" + OTP_LENGTH + "d",
                        SECURE_RANDOM.nextInt(OTP_BOUND)
                );

        UUID challengeId =
                UUID.randomUUID();

        LocalDateTime createdAt =
                LocalDateTime.now(BUSINESS_ZONE);

        LocalDateTime expiresAt =
                createdAt.plus(
                        otpProperties.ttl()
                );

        String otpHash =
                hashOtp(
                        transferId,
                        rawOtp
                );

        return new GeneratedOtp(
                challengeId,
                rawOtp,
                otpHash,
                createdAt,
                expiresAt
        );
    }

    /**
     * Lưu challenge dưới dạng Redis Hash.
     *
     * <p>Redis chỉ chứa {@code otpHash}; không chứa {@code rawOtp}.</p>
     */
    private void saveOtpChallenge(
            UUID transferId,
            UUID ownerKeycloakUserId,
            GeneratedOtp generatedOtp
    ) {
        String key =
                otpKey(transferId);

        Map<String, String> fields =
                new HashMap<>();

        fields.put(
                FIELD_CHALLENGE_ID,
                generatedOtp.challengeId().toString()
        );

        fields.put(
                FIELD_OWNER_USER_ID,
                ownerKeycloakUserId.toString()
        );

        fields.put(
                FIELD_OTP_HASH,
                generatedOtp.otpHash()
        );

        fields.put(
                FIELD_ATTEMPTS,
                "0"
        );

        fields.put(
                FIELD_MAX_ATTEMPTS,
                String.valueOf(
                        otpProperties.maxAttempts()
                )
        );

        fields.put(
                FIELD_LOCKED,
                "0"
        );

        fields.put(
                FIELD_CREATED_AT,
                generatedOtp.createdAt().toString()
        );

        fields.put(
                FIELD_EXPIRES_AT,
                generatedOtp.expiresAt().toString()
        );

        redisTemplate
                .opsForHash()
                .putAll(
                        key,
                        fields
                );

        Boolean expirationConfigured =
                redisTemplate.expire(
                        key,
                        otpProperties.ttl()
                );

        if (!Boolean.TRUE.equals(expirationConfigured)) {
            /*
             * Không chấp nhận challenge không có TTL vì OTP có thể tồn tại
             * vô hạn trong Redis.
             */
            redisTemplate.delete(key);

            throw new IllegalStateException(
                    "Unable to configure OTP expiration in Redis"
            );
        }
    }

    /**
     * Chuyển OTP sang adapter gửi mail.
     *
     * <p>Với production:</p>
     *
     * <pre>
     * OtpDeliveryPort
     *     → BrevoEmailOtpDeliveryAdapter
     *     → JavaMailSender
     *     → Brevo SMTP Relay
     * </pre>
     */
    private void sendOtp(
            OtpRecipient recipient,
            Transfer transfer,
            GeneratedOtp generatedOtp
    ) {
        TransferOtpDeliveryCommand command =
                new TransferOtpDeliveryCommand(
                        recipient.ownerKeycloakUserId(),
                        transfer.getId(),
                        transfer.getTransferCode(),
                        recipient.email(),
                        recipient.displayName(),
                        generatedOtp.rawOtp(),
                        generatedOtp.expiresAt()
                );

        /*
         * Không log rawOtp tại đây.
         * Adapter log chỉ được bật trong môi trường local/dev.
         */
        otpDeliveryPort.sendTransferOtp(command);
    }

    /**
     * Hash OTP bằng HMAC-SHA256.
     *
     * <p>Payload có dạng:</p>
     *
     * <pre>
     * transferId:otp
     * </pre>
     *
     * <p>Vì có secret server-side nên kẻ tấn công lấy được dữ liệu Redis
     * cũng không thể đơn giản hash toàn bộ 1.000.000 mã OTP để tìm mã thật
     * nếu không có HMAC secret.</p>
     */
    private String hashOtp(
            UUID transferId,
            String rawOtp
    ) {
        try {
            Mac mac =
                    Mac.getInstance(HMAC_ALGORITHM);

            SecretKeySpec keySpec =
                    new SecretKeySpec(
                            otpProperties
                                    .hashSecret()
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    ),
                            HMAC_ALGORITHM
                    );

            mac.init(keySpec);

            String payload =
                    transferId
                            + ":"
                            + rawOtp;

            byte[] digest =
                    mac.doFinal(
                            payload.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat
                    .of()
                    .formatHex(digest);

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Unable to calculate OTP HMAC",
                    exception
            );
        }
    }

    /**
     * Tạo cooldown sau lần gửi đầu tiên.
     */
    private void createResendCooldown(
            UUID transferId
    ) {
        redisTemplate
                .opsForValue()
                .set(
                        resendCooldownKey(transferId),
                        "1",
                        otpProperties.resendCooldown()
                );
    }

    /**
     * Chiếm cooldown bằng SET NX.
     *
     * <p>Nếu key đã tồn tại nghĩa là user resend quá sớm.</p>
     */
    private void acquireResendCooldown(
            UUID transferId
    ) {
        Boolean acquired =
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(
                                resendCooldownKey(transferId),
                                "1",
                                otpProperties.resendCooldown()
                        );

        if (!Boolean.TRUE.equals(acquired)) {
            throw new TransferValidationException(
                    "Tạo lại OTP không thành công  vì còn thời gian cooldown cũ"
                            + "Vui lòng đợi thời gian cooldown hết"
            );
        }
    }

    /**
     * Tăng số lần resend của một transfer.
     *
     * <p>Counter nằm ở key riêng nên không bị mất khi challenge OTP cũ
     * hết TTL hoặc bị thay bằng challenge mới.</p>
     */
    private void incrementResendCount(
            UUID transferId
    ) {
        String key =
                resendCountKey(transferId);

        Long resendCount =
                redisTemplate
                        .opsForValue()
                        .increment(key);

        if (resendCount == null) {
            throw new IllegalStateException(
                    "Unable to increment OTP resend count"
            );
        }

        if (resendCount == 1L) {
            /*
             * Giữ counter đủ lâu để giới hạn resend trong vòng đời
             * transfer. Một ngày là đủ cho flow OTP hiện tại.
             */
            redisTemplate.expire(
                    key,
                    Duration.ofDays(1)
            );
        }

        if (
                resendCount
                        > otpProperties.maxResend()
        ) {
            decrementCounterSafely(key);

            throw new TransferValidationException(
                    "Maximum OTP resend limit has been exceeded"
            );
        }
    }

    /**
     * Tăng tổng số OTP được gửi cho user trong ngày.
     *
     * <p>Giới hạn này chống việc tạo nhiều transfer hoặc resend liên tục
     * để spam email và lạm dụng Brevo quota.</p>
     */
    private void incrementDailyOtpCount(
            UUID ownerKeycloakUserId
    ) {
        String key =
                dailyOtpCountKey(
                        ownerKeycloakUserId
                );

        Long dailyCount =
                redisTemplate
                        .opsForValue()
                        .increment(key);

        if (dailyCount == null) {
            throw new IllegalStateException(
                    "Unable to increment daily OTP count"
            );
        }

        if (dailyCount == 1L) {
            redisTemplate.expire(
                    key,
                    durationUntilNextBusinessDay()
            );
        }

        if (
                dailyCount
                        > otpProperties.maxOtpPerUserPerDay()
        ) {
            decrementCounterSafely(key);

            throw new TransferValidationException(
                    "Daily OTP delivery limit has been exceeded"
            );
        }
    }

    /**
     * Cleanup khi lần tạo OTP đầu tiên gửi mail thất bại.
     */
    private void cleanupAfterDeliveryFailure(
            UUID transferId,
            UUID ownerKeycloakUserId,
            boolean decrementResend
    ) {
        deleteKey(otpKey(transferId));
        deleteKey(resendCooldownKey(transferId));

        decrementCounterSafely(
                dailyOtpCountKey(
                        ownerKeycloakUserId
                )
        );

        if (decrementResend) {
            decrementCounterSafely(
                    resendCountKey(transferId)
            );
        }
    }

    /**
     * Giảm counter theo kiểu best-effort.
     *
     * <p>Counter không được phép âm. Nếu kết quả nhỏ hơn hoặc bằng 0,
     * key sẽ bị xóa.</p>
     */
    private void decrementCounterSafely(
            String key
    ) {
        try {
            Long value =
                    redisTemplate
                            .opsForValue()
                            .decrement(key);

            if (
                    value != null
                            && value <= 0
            ) {
                redisTemplate.delete(key);
            }
        } catch (RuntimeException exception) {
            /*
             * Đây là cleanup bổ trợ. Không che mất exception gốc như lỗi
             * gửi SMTP hoặc lỗi Redis chính.
             */
            log.warn(
                    "Unable to rollback Redis counter. key={}",
                    key,
                    exception
            );
        }
    }

    private void deleteKey(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.warn(
                    "Unable to delete Redis key during cleanup. key={}",
                    key,
                    exception
            );
        }
    }

    private void validateCreateInput(
            UUID transferId,
            OtpRecipient recipient
    ) {
        if (transferId == null) {
            throw new TransferValidationException(
                    "transferId is required"
            );
        }

        if (recipient == null) {
            throw new TransferValidationException(
                    "OTP recipient is required"
            );
        }

        if (
                recipient.ownerKeycloakUserId()
                        == null
        ) {
            throw new TransferValidationException(
                    "OTP recipient owner ID is required"
            );
        }

        if (!StringUtils.hasText(
                recipient.email()
        )) {
            throw new TransferValidationException(
                    "OTP recipient email is required"
            );
        }
    }

    private void validateVerifyInput(
            UUID transferId,
            String rawOtp
    ) {
        if (transferId == null) {
            throw new TransferValidationException(
                    "transferId is required"
            );
        }

        if (!StringUtils.hasText(rawOtp)) {
            throw new TransferValidationException(
                    "otp is required"
            );
        }

        if (!rawOtp.trim().matches("\\d{6}")) {
            throw new TransferValidationException(
                    "otp must contain exactly 6 digits"
            );
        }
    }

    /**
     * Fail-fast nếu cấu hình OTP bị thiếu hoặc không hợp lệ.
     */
    private void validateProperties() {
        if (
                otpProperties.ttl() == null
                        || otpProperties.ttl().isZero()
                        || otpProperties.ttl().isNegative()
        ) {
            throw new IllegalStateException(
                    "OTP TTL must be greater than zero"
            );
        }

        if (otpProperties.maxAttempts() <= 0) {
            throw new IllegalStateException(
                    "OTP maxAttempts must be greater than zero"
            );
        }

        if (
                otpProperties.resendCooldown()
                        == null
                        || otpProperties.resendCooldown()
                        .isNegative()
        ) {
            throw new IllegalStateException(
                    "OTP resendCooldown is invalid"
            );
        }

        if (otpProperties.maxResend() < 0) {
            throw new IllegalStateException(
                    "OTP maxResend must not be negative"
            );
        }

        if (
                otpProperties.maxOtpPerUserPerDay()
                        <= 0
        ) {
            throw new IllegalStateException(
                    "OTP daily limit must be greater than zero"
            );
        }

        if (!StringUtils.hasText(
                otpProperties.hashSecret()
        )) {
            throw new IllegalStateException(
                    "OTP hash secret is required"
            );
        }
    }

    private Duration durationUntilNextBusinessDay() {
        LocalDateTime now =
                LocalDateTime.now(BUSINESS_ZONE);

        LocalDateTime nextDay =
                LocalDate.now(BUSINESS_ZONE)
                        .plusDays(1)
                        .atStartOfDay();

        Duration duration =
                Duration.between(
                        now,
                        nextDay
                );

        /*
         * Tránh trường hợp duration bằng 0 vì sai lệch thời điểm rất nhỏ.
         */
        return duration.isZero()
                || duration.isNegative()
                ? Duration.ofDays(1)
                : duration;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(
                String.valueOf(value)
        );
    }

    private String otpKey(UUID transferId) {
        return "otp:transfer:" + transferId;
    }

    private String resendCooldownKey(
            UUID transferId
    ) {
        return "otp:transfer:cooldown:"
                + transferId;
    }

    private String resendCountKey(
            UUID transferId
    ) {
        return "otp:transfer:resend-count:"
                + transferId;
    }

    private String dailyOtpCountKey(
            UUID ownerKeycloakUserId
    ) {
        LocalDate businessDate =
                LocalDate.now(BUSINESS_ZONE);

        return "otp:user:daily-count:"
                + ownerKeycloakUserId
                + ":"
                + businessDate;
    }

    /**
     * Khởi tạo Lua Script để xác thực OTP trong Redis đảm bảo tính nguyên tử (Thread-safe).
     * Trả về mảng (List) gồm 2 phần tử: [Mã_trạng_thái, Số_lần_thử_còn_lại].
     *
     * Ý nghĩa Mã_trạng_thái:
     *   1 : Xác thực thành công (Khớp mã Hash).
     *   0 : Mã không đúng, vẫn còn lượt thử.
     *  -1 : Không tìm thấy (Key không tồn tại hoặc đã hết hạn TTL).
     *  -2 : Bị khóa (Do đã nhập sai quá số lần tối đa cho phép).
     */
    private static DefaultRedisScript<List> createVerifyOtpScript() {
        String lua = """
            -- KEYS[1]: Tên key lưu trữ OTP (VD: otp:transfer:123)
            local key = KEYS[1]
            
            -- ARGV[1]: Mã OTP (đã hash) từ request của user gửi lên
            local submittedHash = ARGV[1]

            -- 1. Kiểm tra sự tồn tại của OTP record trong Redis
            if redis.call('EXISTS', key) == 0 then
                return {-1, 0}
            end

            -- 2. Kiểm tra cờ 'locked'. Nếu đã bị khóa do vi phạm limit trước đó thì chặn luôn
            local locked = redis.call('HGET', key, 'locked')
            if locked == '1' then
                return {-2, 0}
            end

            -- 3. Lấy mã Hash chuẩn đã lưu để đối chiếu
            local storedHash = redis.call('HGET', key, 'otpHash')
            if not storedHash then
                return {-1, 0}
            end

            -- 4. Lấy cấu hình số lần thử hiện tại và tối đa (Fallback mặc định là 0 và 5)
            local attempts = tonumber(redis.call('HGET', key, 'attempts') or '0')
            local maxAttempts = tonumber(redis.call('HGET', key, 'maxAttempts') or '5')

            -- ==========================================
            -- KỊCH BẢN 1: USER NHẬP ĐÚNG MÃ OTP
            -- ==========================================
            if storedHash == submittedHash then
                -- Xóa vĩnh viễn key này ngay lập tức để chống xài lại (Replay Attack)
                redis.call('DEL', key)
                return {1, maxAttempts - attempts}
            end

            -- ==========================================
            -- KỊCH BẢN 2: USER NHẬP SAI MÃ OTP
            -- ==========================================
            
            -- Tăng biến đếm số lần sai lên 1 và lưu lại vào Hash
            attempts = attempts + 1
            redis.call('HSET', key, 'attempts', attempts)

            local remaining = maxAttempts - attempts

            -- Nếu số lần sai đã chạm nóc (VD: 5/5)
            if attempts >= maxAttempts then
                -- Bật cờ khóa chết OTP này lại (dù thời gian TTL vẫn còn)
                redis.call('HSET', key, 'locked', '1')
                return {-2, 0}
            end

            -- Vẫn chưa chạm nóc, báo mã lỗi 0 và trả về số lượt thử còn lại
            return {0, remaining}
            """;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();

        script.setScriptText(lua);

        // Ép kiểu trả về thành List (mảng) để lấy đồng thời [Mã kết quả, Lượt còn lại]
        // mà không cần phải gọi xuống Redis lần 2.
        script.setResultType(List.class);

        return script;
    }

    /**
     * Object nội bộ chứa cả plaintext OTP và hash.
     *
     * <p>Object này chỉ tồn tại trong memory. Không serialize nó xuống
     * Redis hoặc PostgreSQL.</p>
     */
    private record GeneratedOtp(
            UUID challengeId,
            String rawOtp,
            String otpHash,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
    }
}