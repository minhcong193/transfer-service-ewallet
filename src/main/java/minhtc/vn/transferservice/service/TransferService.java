package minhtc.vn.transferservice.service;

import minhtc.vn.transferservice.dto.request.CancelTransferRequest;
import minhtc.vn.transferservice.dto.request.ConfirmTransferRequest;
import minhtc.vn.transferservice.dto.request.CreateTransferRequest;
import minhtc.vn.transferservice.dto.response.OtpResendResponse;
import minhtc.vn.transferservice.dto.response.TransferResponse;

import java.util.UUID;

public interface TransferService {

    TransferResponse createTransfer(
            CreateTransferRequest request,
            String idempotencyKey
    );

    TransferResponse confirmTransfer(
            UUID transferId,
            ConfirmTransferRequest request,
            String idempotencyKey
    );

    OtpResendResponse resendOtp(UUID transferId);

    TransferResponse cancelTransfer(
            UUID transferId,
            CancelTransferRequest request
    );
}
