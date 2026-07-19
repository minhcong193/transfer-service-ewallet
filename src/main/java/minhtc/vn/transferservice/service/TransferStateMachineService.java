package minhtc.vn.transferservice.service;

import minhtc.vn.transferservice.domain.Transfer;
import minhtc.vn.transferservice.dto.TransferTransitionContext;
import minhtc.vn.transferservice.enums.TransferStatus;


import java.util.Set;
import java.util.UUID;

public interface TransferStateMachineService {

    /**
     * Tìm Transfer theo ID, lock bản ghi và chuyển trạng thái.
     *
     * <p>Dùng khi caller chưa có entity managed.</p>
     */
    Transfer transition(
            UUID transferId,
            TransferStatus targetStatus,
            TransferTransitionContext context
    );

    /**
     * Chuyển trạng thái cho Transfer entity đã được load và đang managed
     * trong transaction hiện tại.
     *
     * <p>Dùng trong các service như TransferSagaResultService để update
     * aggregate, đổi trạng thái, ghi history và append outbox trong cùng
     * một transaction.</p>
     */
    Transfer transitionManaged(
            Transfer transfer,
            TransferStatus targetStatus,
            TransferTransitionContext context
    );

    /**
     * Kiểm tra transition có hợp lệ không.
     */
    boolean canTransition(
            TransferStatus sourceStatus,
            TransferStatus targetStatus
    );

    /**
     * Validate transition, nếu không hợp lệ thì ném exception.
     */
    void validateTransition(
            UUID transferId,
            TransferStatus sourceStatus,
            TransferStatus targetStatus
    );
}