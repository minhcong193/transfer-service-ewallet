package minhtc.vn.transferservice.otp.impl;

import lombok.RequiredArgsConstructor;
import minhtc.vn.transferservice.dto.request.WalletSummary;
import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.exception.TransferOwnershipException;
import minhtc.vn.transferservice.publisher.SecurityEventPublisher;
import minhtc.vn.transferservice.service.TransferOwnershipService;
import minhtc.vn.transferservice.util.SecurityUtil;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferOwnershipServiceImpl
        implements TransferOwnershipService {

    private final SecurityEventPublisher securityEventPublisher;

    @Override
    public void assertSourceWalletOwnership(
            UUID currentUserId,
            WalletSummary sourceWallet,
            UUID correlationId
    ) {
        boolean ownedByCurrentUser =
                currentUserId.equals(
                        sourceWallet.ownerKeycloakUserId()
                );

        if (!ownedByCurrentUser) {
            publishViolation();

            throw new TransferOwnershipException();
        }
    }

    @Override
    public void assertCanViewTransfer(
            UUID currentUserId,
            Transfer transfer,
            UUID correlationId
    ) {
        /*
         * Transfer được xem bởi:
         *
         * - Chủ ví nguồn.
         * - Chủ ví đích nếu Wallet Service đã cung cấp target owner.
         */
        if (!transfer.isRelatedTo(currentUserId)) {
            publishViolation();

            throw new TransferOwnershipException();
        }
    }

    @Override
    public void assertCanManageTransfer(
            UUID currentUserId,
            Transfer transfer,
            UUID correlationId
    ) {
        /*
         * Confirm OTP, resend OTP và cancel chỉ do chủ ví nguồn
         * thực hiện.
         */
        if (!transfer.isOwnedBySource(currentUserId)) {
            publishViolation();

            throw new TransferOwnershipException();
        }
    }

    private void publishViolation() {
        /*
         * Không để lỗi Kafka làm thay đổi response chính.
         * Publisher nên xử lý bất đồng bộ hoặc tự catch exception.
         */
        try {
            securityEventPublisher.publishOwnershipViolation(
                    SecurityUtil.getUsername()
            );
        } catch (Exception ignored) {
            /*
             * Có thể log warning tại đây.
             *
             * Không trả 500 chỉ vì publish security event thất bại.
             */
        }
    }
}