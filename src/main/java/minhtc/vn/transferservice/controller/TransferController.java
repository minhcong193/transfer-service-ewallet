package minhtc.vn.transferservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.dto.request.CancelTransferRequest;
import minhtc.vn.transferservice.dto.request.ConfirmTransferRequest;
import minhtc.vn.transferservice.dto.request.CreateTransferRequest;
import minhtc.vn.transferservice.dto.transfer.TransferResponse;
import minhtc.vn.transferservice.service.TransferQueryService;
import minhtc.vn.transferservice.service.TransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    private final TransferQueryService transferQueryService;

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        return ResponseEntity.ok(
                transferService.createTransfer(
                        jwt,
                        idempotencyKey,
                        request
                )
        );
    }

    @PostMapping("/{transferId}/confirm")
    public ResponseEntity<TransferResponse> confirmTransfer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID transferId,
            @Valid @RequestBody ConfirmTransferRequest request
    ) {
        return ResponseEntity.ok(
                transferService.confirmTransfer(
                        jwt,
                        transferId,
                        request
                )
        );
    }

    @PostMapping("/{transferId}/otp/resend")
    public ResponseEntity<TransferResponse> resendOtp(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID transferId
    ) {
        return ResponseEntity.ok(
                transferService.resendOtp(
                        jwt,
                        transferId
                )
        );
    }

    @PostMapping("/{transferId}/cancel")
    public ResponseEntity<TransferResponse> cancelTransfer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID transferId,
            @Valid @RequestBody CancelTransferRequest request
    ) {
        return ResponseEntity.ok(
                transferService.cancelTransfer(
                        jwt,
                        transferId,
                        request
                )
        );
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponse> getTransfer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID transferId
    ) {
        return ResponseEntity.ok(
                transferQueryService.getMyTransferById(
                        jwt,
                        transferId
                )
        );
    }
}