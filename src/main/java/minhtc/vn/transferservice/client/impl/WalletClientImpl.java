package minhtc.vn.transferservice.client.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import minhtc.vn.transferservice.client.WalletClient;
import minhtc.vn.transferservice.dto.request.WalletSummary;
import minhtc.vn.transferservice.dto.response.WalletCommandResult;
import minhtc.vn.transferservice.exception.WalletClientException;
import minhtc.vn.transferservice.exception.WalletClientTimeoutException;
import minhtc.vn.transferservice.exception.WalletIntegrationException;
import minhtc.vn.transferservice.dto.integration.wallet.CreditWalletRequest;
import minhtc.vn.transferservice.dto.integration.wallet.FinalizeReservationRequest;
import minhtc.vn.transferservice.dto.integration.wallet.ReleaseReservationRequest;
import minhtc.vn.transferservice.dto.integration.wallet.ReserveWalletRequest;
import minhtc.vn.transferservice.dto.integration.wallet.WalletCreditResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletFinalizeResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReleaseResult;
import minhtc.vn.transferservice.dto.integration.wallet.WalletReservationResult;
import minhtc.vn.transferservice.util.RequestContextProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletClientImpl implements WalletClient {

    private static final String CORRELATION_ID_HEADER =
            "X-Correlation-Id";

    private static final String CLIENT_IP_HEADER =
            "X-Client-IP";

    private static final String COMMAND_ID_HEADER =
            "X-Command-Id";

    private final RestClient walletRestClient;

    private final RequestContextProvider requestContextProvider;

    @Override
    public WalletSummary getWallet(UUID walletId) {
        log.info("Calling Wallet Service to get wallet summary for walletId={}", walletId);
        return execute(
                null,
                "GET_WALLET",
                () -> walletRestClient
                        .get()
                        .uri(
                                "/internal/wallets/{walletId}",
                                walletId
                        )
                        .headers(this::addCommonHeaders)
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::isError,
                                (request, response) -> {
                                    throw buildClientException(
                                            response.getStatusCode(),
                                            response.getStatusText(),
                                            "Failed to get wallet: "
                                                    + walletId
                                    );
                                }
                        )
                        .body(WalletSummary.class)
        );
    }

    @Override
    public WalletReservationResult reserve(
            UUID walletId,
            ReserveWalletRequest request
    ) {
        log.info("Calling Wallet Service to reserve balance for walletId={}, request={}", walletId, request);
        return execute(
                request.commandId(),
                "RESERVE",
                () -> walletRestClient
                        .post()
                        .uri(
                                "/internal/wallets/{walletId}/reservations",
                                walletId
                        )
                        .headers(
                                headers -> addCommandHeaders(
                                        headers,
                                        request.commandId(),
                                        request.correlationId()
                                )
                        )
                        .body(request)
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::isError,
                                (httpRequest, response) -> {
                                    throw buildClientException(
                                            response.getStatusCode(),
                                            response.getStatusText(),
                                            "Wallet reserve failed"
                                    );
                                }
                        )
                        .body(WalletReservationResult.class)
        );
    }

    @Override
    public WalletCreditResult credit(
            UUID walletId,
            CreditWalletRequest request
    ) {
        log.info("Calling Wallet Service to credit balance for walletId={}, request={}", walletId, request);
        return execute(
                request.commandId(),
                "CREDIT",
                () -> walletRestClient
                        .post()
                        .uri(
                                "/internal/wallets/{walletId}/credits",
                                walletId
                        )
                        .headers(
                                headers -> addCommandHeaders(
                                        headers,
                                        request.commandId(),
                                        request.correlationId()
                                )
                        )
                        .body(request)
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::isError,
                                (httpRequest, response) -> {
                                    throw buildClientException(
                                            response.getStatusCode(),
                                            response.getStatusText(),
                                            "Wallet credit failed"
                                    );
                                }
                        )
                        .body(WalletCreditResult.class)
        );
    }

    @Override
    public WalletFinalizeResult finalizeReservation(
            UUID walletId,
            UUID reservationId,
            FinalizeReservationRequest request
    ) {
        log.info("Calling Wallet Service to finalize reservation for walletId={}, reservationId={}, request={}", walletId, reservationId, request);
        return execute(
                request.commandId(),
                "FINALIZE_RESERVATION",
                () -> walletRestClient
                        .post()
                        .uri(
                                "/internal/wallets/{walletId}"
                                        + "/reservations/{reservationId}"
                                        + "/finalize",
                                walletId,
                                reservationId
                        )
                        .headers(
                                headers -> addCommandHeaders(
                                        headers,
                                        request.commandId(),
                                        request.correlationId()
                                )
                        )
                        .body(request)
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::isError,
                                (httpRequest, response) -> {
                                    throw buildClientException(
                                            response.getStatusCode(),
                                            response.getStatusText(),
                                            "Wallet reservation finalize failed"
                                    );
                                }
                        )
                        .body(WalletFinalizeResult.class)
        );
    }

    @Override
    public WalletReleaseResult releaseReservation(
            UUID walletId,
            UUID reservationId,
            ReleaseReservationRequest request
    ) {
        log.info("Calling Wallet Service to release reservation for walletId={}, reservationId={}, request={}", walletId, reservationId, request);
        return execute(
                request.commandId(),
                "RELEASE_RESERVATION",
                () -> walletRestClient
                        .post()
                        .uri(
                                "/internal/wallets/{walletId}"
                                        + "/reservations/{reservationId}"
                                        + "/release",
                                walletId,
                                reservationId
                        )
                        .headers(
                                headers -> addCommandHeaders(
                                        headers,
                                        request.commandId(),
                                        request.correlationId()
                                )
                        )
                        .body(request)
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::isError,
                                (httpRequest, response) -> {
                                    throw buildClientException(
                                            response.getStatusCode(),
                                            response.getStatusText(),
                                            "Wallet reservation release failed"
                                    );
                                }
                        )
                        .body(WalletReleaseResult.class)
        );
    }

    @Override
    public WalletCommandResult getCommandStatus(
            UUID commandId
    ) {
        log.info("Calling Wallet Service to get command status for commandId={}", commandId);
        return execute(
                commandId,
                "GET_COMMAND_STATUS",
                () -> walletRestClient
                        .get()
                        .uri(
                                "/internal/wallet-commands/{commandId}",
                                commandId
                        )
                        .headers(this::addCommonHeaders)
                        .header(
                                COMMAND_ID_HEADER,
                                commandId.toString()
                        )
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::isError,
                                (httpRequest, response) -> {
                                    throw buildClientException(
                                            response.getStatusCode(),
                                            response.getStatusText(),
                                            "Unable to get wallet command status"
                                    );
                                }
                        )
                        .body(WalletCommandResult.class)
        );
    }

    private void addCommonHeaders(HttpHeaders headers) {
        String correlationId =
                requestContextProvider.getCorrelationId();

        String clientIp =
                requestContextProvider.getClientIp();

        if (
                correlationId != null
                        && !correlationId.isBlank()
        ) {
            headers.set(
                    CORRELATION_ID_HEADER,
                    correlationId
            );
        }

        if (
                clientIp != null
                        && !clientIp.isBlank()
        ) {
            headers.set(
                    CLIENT_IP_HEADER,
                    clientIp
            );
        }
    }

    private <T> T execute(
            UUID commandId,
            String operation,
            Supplier<T> supplier
    ) {
        try {
            T result = supplier.get();

            if (result == null) {
                throw new WalletClientException(
                        HttpStatusCode.valueOf(502),
                        "WALLET_EMPTY_RESPONSE",
                        "Wallet Service returned an empty response"
                );
            }

            log.info("Wallet operation successful. operation={}, commandId={}, result={}", operation, commandId, result);
            return result;

        } catch (WalletClientException exception) {
            throw exception;

        } catch (ResourceAccessException exception) {
            log.warn(
                    "Wallet Service connection error. "
                            + "operation={}, commandId={}, correlationId={}",
                    operation,
                    commandId,
                    requestContextProvider.getCorrelationId(),
                    exception
            );

            throw new WalletClientTimeoutException(
                    commandId,
                    operation,
                    exception
            );

        } catch (RestClientResponseException exception) {
            log.warn(
                    "Wallet Service HTTP error. "
                            + "operation={}, commandId={}, status={}, response={}",
                    operation,
                    commandId,
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString()
            );

            throw new WalletClientException(
                    exception.getStatusCode(),
                    mapErrorCode(exception.getStatusCode()),
                    "Wallet Service request failed",
                    exception
            );

        } catch (RuntimeException exception) {
            log.error(
                    "Unexpected Wallet Service error. "
                            + "operation={}, commandId={}",
                    operation,
                    commandId,
                    exception
            );

            throw new WalletClientException(
                    HttpStatusCode.valueOf(502),
                    "WALLET_CLIENT_UNEXPECTED_ERROR",
                    "Unexpected error when calling Wallet Service",
                    exception
            );
        }
    }

    private WalletClientException buildClientException(
            HttpStatusCode statusCode,
            String statusText,
            String defaultMessage
    ) {
        return new WalletClientException(
                statusCode,
                mapErrorCode(statusCode),
                defaultMessage + ": " + statusText
        );
    }

    private String mapErrorCode(
            HttpStatusCode statusCode
    ) {
        return switch (statusCode.value()) {
            case 400 -> "WALLET_INVALID_REQUEST";
            case 401 -> "WALLET_INTERNAL_UNAUTHORIZED";
            case 403 -> "WALLET_INTERNAL_FORBIDDEN";
            case 404 -> "WALLET_RESOURCE_NOT_FOUND";
            case 409 -> "WALLET_COMMAND_CONFLICT";
            case 422 -> "WALLET_COMMAND_REJECTED";
            case 429 -> "WALLET_RATE_LIMITED";

            default -> statusCode.is5xxServerError()
                    ? "WALLET_SERVICE_UNAVAILABLE"
                    : "WALLET_SERVICE_ERROR";
        };
    }

    private void addCommandHeaders(
            HttpHeaders headers,
            UUID commandId,
            UUID correlationId
    ) {
        addCommonHeaders(headers);

        if (commandId != null) {
            headers.set(
                    COMMAND_ID_HEADER,
                    commandId.toString()
            );
        }

        if (correlationId != null) {
            headers.set(
                    CORRELATION_ID_HEADER,
                    correlationId.toString()
            );
        }
    }
}
