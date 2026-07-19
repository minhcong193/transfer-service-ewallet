package minhtc.vn.transferservice.enums;

public enum IdempotencyDecision {
    PROCEED,
    REPLAY_SUCCESS,
    REPLAY_FAILURE,
    IN_PROGRESS
}
