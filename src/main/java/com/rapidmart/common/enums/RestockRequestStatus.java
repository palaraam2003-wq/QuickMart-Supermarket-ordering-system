package com.rapidmart.common.enums;

import java.util.Set;

/**
 * Restock request lifecycle. The only legal transitions are
 * PENDING -> APPROVED -> RECEIVED and PENDING -> REJECTED; APPROVED/PENDING are "open".
 */
public enum RestockRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    RECEIVED;

    public static final Set<RestockRequestStatus> OPEN = Set.of(PENDING, APPROVED);

    public boolean canTransitionTo(RestockRequestStatus next) {
        return switch (this) {
            case PENDING -> next == APPROVED || next == REJECTED;
            case APPROVED -> next == RECEIVED;
            case REJECTED, RECEIVED -> false;
        };
    }
}
